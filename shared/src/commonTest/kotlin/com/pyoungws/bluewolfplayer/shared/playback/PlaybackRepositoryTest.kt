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

    private suspend fun repository(capabilities: DeviceCapabilities = PHONE_CAPABILITIES, settings: PlaybackSettings = PlaybackSettings()) =
        playbackRepository(library, playback, SessionGuard(signedInAuthRepository()), capabilities, settings)

    private val item = PlaybackTarget.Item("m1")

    @Test
    fun `prepare resumes from the saved position`() = runTest {
        library.item = { movie(it, positionTicks = 30_000_000_000L) }

        val request = (repository().prepare(item) as Outcome.Success).value

        assertEquals(3_000_000L, request.startPositionMs)
        assertEquals(Triple("m1", 30_000_000_000L, false), playback.sourceRequests.single())
        assertEquals(DIRECT_SOURCE, request.source)
    }

    @Test
    fun `prepare starts from the beginning for watched or new items`() = runTest {
        library.item = { movie(it, positionTicks = 30_000_000_000L, played = true) }

        val request = (repository().prepare(item) as Outcome.Success).value

        assertEquals(0, request.startPositionMs)
    }

    @Test
    fun `prepare can force a transcode`() = runTest {
        val request = (repository().prepare(item, forceTranscode = true) as Outcome.Success).value

        assertEquals(PlaybackMethod.Transcode, request.source.method)
        assertEquals(true, playback.sourceRequests.single().third)
    }

    @Test
    fun `the server is told what this device can play`() = runTest {
        repository(TV_CAPABILITIES).prepare(item)

        assertEquals(buildDeviceProfile(TV_CAPABILITIES, PlaybackSettings()), playback.profiles.single())
    }

    @Test
    fun `the request carries the settings, capabilities and starting decode plan`() = runTest {
        val settings = PlaybackSettings(decoding = DecodingPreference.PreferLocal)

        val request = (repository(TV_CAPABILITIES, settings).prepare(item) as Outcome.Success).value

        assertEquals(settings, request.settings)
        assertEquals(TV_CAPABILITIES, request.capabilities)
        assertEquals(DecodePlan(DecoderChoice.Local, DecoderChoice.Local), request.plan)
        assertEquals(PlaybackLevel.LocalDecode, request.level)
    }

    @Test
    fun `nothing is held back from a server that will not transcode video for this user`() = runTest {
        playback.transcodesVideo = false

        val request = (repository().prepare(item) as Outcome.Success).value

        assertEquals(false, request.serverTranscodesVideo)
        assertEquals(buildDeviceProfile(PHONE_CAPABILITIES, PlaybackSettings(), serverTranscodesVideo = false), playback.profiles.single())
    }

    @Test
    fun `the always transcode override skips direct play`() = runTest {
        repository(settings = PlaybackSettings(decoding = DecodingPreference.AlwaysTranscode)).prepare(item)

        assertEquals(true, playback.sourceRequests.single().third)
    }

    @Test
    fun `a direct uri plays without asking the server`() = runTest {
        val request = (repository().prepare(PlaybackTarget.DirectUri("file:///sdcard/sample.mkv")) as Outcome.Success).value

        assertEquals("file:///sdcard/sample.mkv", request.source.url)
        assertEquals("sample.mkv", request.item.name)
        assertEquals(PlaybackMethod.DirectPlay, request.source.method)
        assertEquals(false, request.isServerItem)
        assertEquals(emptyList(), playback.sourceRequests)
    }

    @Test
    fun `prepare reports failures`() = runTest {
        library.item = { throw InvalidStatusException(404) }

        assertEquals(Outcome.Failure(LoadError.NotFound), repository().prepare(item))
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
