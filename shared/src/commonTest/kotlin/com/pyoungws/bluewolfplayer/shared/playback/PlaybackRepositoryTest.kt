package com.pyoungws.bluewolfplayer.shared.playback

import com.pyoungws.bluewolfplayer.shared.library.FakeLibraryDataSource
import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.library.movie
import com.pyoungws.bluewolfplayer.shared.library.signedInAuthRepository
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackRepositoryTest {
    private val library = FakeLibraryDataSource()
    private val playback = FakePlaybackDataSource()

    private suspend fun repository() = PlaybackRepository(library, playback, SessionGuard(signedInAuthRepository()))

    @Test
    fun `prepare resumes from the saved position`() = runTest {
        library.item = { movie(it, positionTicks = 30_000_000_000L) }

        val request = (repository().prepare("m1") as Outcome.Success).value

        assertEquals(3_000_000L, request.startPositionMs)
        assertEquals(Triple("m1", 30_000_000_000L, false), playback.sourceRequests.single())
        assertEquals(DIRECT_SOURCE, request.source)
    }

    @Test
    fun `prepare starts from the beginning for watched or new items`() = runTest {
        library.item = { movie(it, positionTicks = 30_000_000_000L, played = true) }

        val request = (repository().prepare("m1") as Outcome.Success).value

        assertEquals(0, request.startPositionMs)
    }

    @Test
    fun `prepare can force a transcode`() = runTest {
        val request = (repository().prepare("m1", forceTranscode = true) as Outcome.Success).value

        assertEquals(PlaybackMethod.Transcode, request.source.method)
        assertEquals(true, playback.sourceRequests.single().third)
    }

    @Test
    fun `prepare reports failures`() = runTest {
        library.item = { throw InvalidStatusException(404) }

        assertEquals(Outcome.Failure(LoadError.NotFound), repository().prepare("m1"))
    }

    @Test
    fun `reports are forwarded to the server`() = runTest {
        val repository = repository()
        val report = PlaybackReport("m1", "ms-1", "ps-1", 10, false, PlaybackMethod.DirectPlay)

        repository.reportStart(report)
        repository.reportProgress(report)
        repository.reportStopped(report)

        assertEquals(listOf("start", "progress", "stopped"), playback.reports)
    }
}
