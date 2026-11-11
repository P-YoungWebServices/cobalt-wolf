package com.pyoungws.bluewolfplayer.shared.qbittorrent

import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class QBittorrentRepositoryTest {

    private fun repository(
        dataSource: FakeQBittorrentDataSource,
        settings: QBittorrentSettings = CONFIGURED,
    ) = QBittorrentRepository(dataSource, QBittorrentSettingsRepository(InMemoryQBittorrentSettingsStore(settings)))

    @Test
    fun `downloads combines the torrent list and global speeds`() = runTest {
        val dataSource = FakeQBittorrentDataSource().apply {
            torrentsResult = listOf(torrent("a"), torrent("b", state = "uploading"))
            speeds = TransferSpeeds(downloadBps = 5_000_000, uploadBps = 500_000)
        }

        val result = repository(dataSource).downloads()

        val state = (result as Outcome.Success).value
        assertEquals(listOf("a", "b"), state.torrents.map { it.hash })
        assertEquals(5_000_000, state.totalDownloadSpeedBps)
        assertEquals(listOf("torrents", "transferInfo"), dataSource.calls.sorted())
    }

    @Test
    fun `an expired session triggers one login and a retry`() = runTest {
        val dataSource = FakeQBittorrentDataSource().apply {
            failOnce = SessionExpiredException()
            torrentsResult = listOf(torrent("a"))
        }

        val result = repository(dataSource).downloads()

        assertIs<Outcome.Success<DownloadState>>(result)
        assertEquals(1, dataSource.calls.count { it.startsWith("login(") })
    }

    @Test
    fun `a failed re-login surfaces as forbidden`() = runTest {
        val dataSource = FakeQBittorrentDataSource().apply {
            failOnce = SessionExpiredException()
            loginResult = false
        }

        val result = repository(dataSource).downloads()

        assertEquals(Outcome.Failure(LoadError.Forbidden), result)
    }

    @Test
    fun `a persistent failure fails the call`() = runTest {
        val dataSource = FakeQBittorrentDataSource().apply { error = IOException("down") }

        val result = repository(dataSource).downloads()

        assertIs<Outcome.Failure>(result)
    }

    @Test
    fun `unconfigured settings fail without calling the service`() = runTest {
        val dataSource = FakeQBittorrentDataSource()

        val result = repository(dataSource, QBittorrentSettings()).downloads()

        assertIs<Outcome.Failure>(result)
        assertEquals(emptyList(), dataSource.calls)
    }

    @Test
    fun `testConnection validates input before calling the service`() = runTest {
        val dataSource = FakeQBittorrentDataSource()
        val repository = repository(dataSource)

        assertIs<Outcome.Failure>(repository.testConnection("not a host", "user", "pass"))
        assertIs<Outcome.Failure>(repository.testConnection("qbit.lan", " ", "pass"))
        assertIs<Outcome.Failure>(repository.testConnection("qbit.lan", "user", ""))
        assertEquals(emptyList(), dataSource.calls)
    }

    @Test
    fun `testConnection reports rejected credentials`() = runTest {
        val dataSource = FakeQBittorrentDataSource().apply { loginResult = false }

        val result = repository(dataSource).testConnection("qbit.lan:8080", "admin", "wrong")

        assertIs<Outcome.Failure>(result)
        assertEquals(listOf("login(https://qbit.lan:8080,admin)"), dataSource.calls)
    }

    private companion object {
        val CONFIGURED = QBittorrentSettings("qbit.example.com", "admin", "hunter2")
    }
}
