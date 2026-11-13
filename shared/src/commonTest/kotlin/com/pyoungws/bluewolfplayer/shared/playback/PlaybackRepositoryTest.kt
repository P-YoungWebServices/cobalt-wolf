package com.pyoungws.bluewolfplayer.shared.playback

import com.pyoungws.bluewolfplayer.shared.library.FakeLibraryDataSource
import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.library.episode
import com.pyoungws.bluewolfplayer.shared.library.movie
import com.pyoungws.bluewolfplayer.shared.library.season
import com.pyoungws.bluewolfplayer.shared.library.signedInAuthRepository
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.model.ProgramInfo
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `a bitrate cap reaches the device profile and the playback info request`() = runTest {
        repository(settings = PlaybackSettings(maxBitrate = StreamBitrate.Mbps20)).prepare(item)

        assertEquals(20_000_000, playback.profiles.single().maxStreamingBitrate)
        assertEquals(20_000_000, playback.bitrates.single())
    }

    @Test
    fun `the auto bitrate sends no cap on the request`() = runTest {
        repository().prepare(item)

        assertNull(playback.bitrates.single())
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
    fun `a program plays its channel's live stream`() = runTest {
        library.item = {
            MediaItem(
                it, "Evening News", MediaKind.Program,
                programInfo = ProgramInfo(channelId = "channel-7"),
            )
        }

        val request = (repository().prepare(PlaybackTarget.Item("prog-1")) as Outcome.Success).value

        assertEquals("channel-7", playback.sourceRequests.single().first)
        assertEquals("Evening News", request.item.name)
    }

    @Test
    fun `prepare reports failures`() = runTest {
        library.item = { throw InvalidStatusException(404) }

        assertEquals(Outcome.Failure(LoadError.NotFound), repository().prepare(item))
    }

    @Test
    fun `next episode comes from the same season`() = runTest {
        library.episodes = { _, _ -> listOf(episode("e1", 1), episode("e2", 2), episode("e3", 3)) }

        assertEquals("e3", repository().nextEpisode(episode("e2", 2))?.id)
    }

    @Test
    fun `the last episode of a season rolls into the next season`() = runTest {
        library.episodes = { _, seasonId ->
            if (seasonId == "season-1") listOf(episode("e1", 1), episode("e2", 2)) else listOf(episode("e3", 1, seasonNumber = 2))
        }
        library.seasons = { listOf(season("season-1", 1), season("season-2", 2)) }

        assertEquals("e3", repository().nextEpisode(episode("e2", 2))?.id)
    }

    @Test
    fun `a series finale and a movie have no next episode`() = runTest {
        library.episodes = { _, _ -> listOf(episode("e2", 2)) }
        library.seasons = { listOf(season("season-1", 1)) }

        val repository = repository()
        assertNull(repository.nextEpisode(episode("e2", 2)))
        assertNull(repository.nextEpisode(movie("m1")))
    }

    @Test
    fun `media segments come back empty when the server has none`() = runTest {
        val intro = listOf(MediaSegment(MediaSegmentKind.Intro, 60_000, 120_000))
        playback.segments = { if (it == "e1") intro else emptyList() }

        val repository = repository()
        assertEquals(intro, repository.mediaSegments("e1"))
        assertEquals(emptyList(), repository.mediaSegments("e2"))
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
