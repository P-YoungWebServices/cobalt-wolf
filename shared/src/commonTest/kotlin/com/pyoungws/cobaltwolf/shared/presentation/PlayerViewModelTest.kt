package com.pyoungws.cobaltwolf.shared.presentation

import com.pyoungws.cobaltwolf.shared.library.FakeLibraryDataSource
import com.pyoungws.cobaltwolf.shared.library.SessionGuard
import com.pyoungws.cobaltwolf.shared.library.episode
import com.pyoungws.cobaltwolf.shared.library.movie
import com.pyoungws.cobaltwolf.shared.library.signedInAuthRepository
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.playback.DIRECT_SOURCE
import com.pyoungws.cobaltwolf.shared.playback.FakePlaybackDataSource
import com.pyoungws.cobaltwolf.shared.playback.MediaSegment
import com.pyoungws.cobaltwolf.shared.playback.MediaSegmentKind
import com.pyoungws.cobaltwolf.shared.playback.PlaybackMethod
import com.pyoungws.cobaltwolf.shared.playback.AudioStreamInfo
import com.pyoungws.cobaltwolf.shared.playback.DecodePlan
import com.pyoungws.cobaltwolf.shared.playback.DecoderChoice
import com.pyoungws.cobaltwolf.shared.playback.DeviceCapabilities
import com.pyoungws.cobaltwolf.shared.playback.PHONE_CAPABILITIES
import com.pyoungws.cobaltwolf.shared.playback.PlaybackLevel
import com.pyoungws.cobaltwolf.shared.playback.PlaybackTarget
import com.pyoungws.cobaltwolf.shared.playback.PlayerErrorKind
import com.pyoungws.cobaltwolf.shared.playback.SourceMediaInfo
import com.pyoungws.cobaltwolf.shared.playback.StreamBitrate
import com.pyoungws.cobaltwolf.shared.playback.VideoRange
import com.pyoungws.cobaltwolf.shared.playback.VideoStreamInfo
import com.pyoungws.cobaltwolf.shared.playback.playbackRepository
import com.pyoungws.cobaltwolf.shared.syncplay.CorrectionType
import com.pyoungws.cobaltwolf.shared.syncplay.FakeSyncPlayDataSource
import com.pyoungws.cobaltwolf.shared.syncplay.SyncPlayEvent
import com.pyoungws.cobaltwolf.shared.syncplay.SyncPlayGroupState
import com.pyoungws.cobaltwolf.shared.syncplay.SyncPlayRepository
import com.pyoungws.cobaltwolf.shared.syncplay.group
import com.pyoungws.cobaltwolf.shared.syncplay.queueItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val library = FakeLibraryDataSource()
    private val playback = FakePlaybackDataSource()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.viewModel(
        target: PlaybackTarget = PlaybackTarget.Item("m1"),
        capabilities: DeviceCapabilities = PHONE_CAPABILITIES,
        syncPlay: SyncPlayRepository? = null,
    ) = PlayerViewModel(
        target,
        playbackRepository(library, playback, SessionGuard(signedInAuthRepository()), capabilities),
        reportScope = this,
        syncPlay = syncPlay ?: syncPlayRepository(),
        playQueue = com.pyoungws.cobaltwolf.shared.playback.PlayQueueRepository(),
    )

    private suspend fun TestScope.viewModelWithSettings(
        settings: com.pyoungws.cobaltwolf.shared.playback.PlaybackSettings,
        target: PlaybackTarget = PlaybackTarget.Item("m1"),
    ) = PlayerViewModel(
        target,
        playbackRepository(library, playback, SessionGuard(signedInAuthRepository()), PHONE_CAPABILITIES, settings),
        reportScope = this,
        syncPlay = syncPlayRepository(),
        playQueue = com.pyoungws.cobaltwolf.shared.playback.PlayQueueRepository(),
    )

    private suspend fun TestScope.syncPlayRepository(
        dataSource: FakeSyncPlayDataSource = FakeSyncPlayDataSource(),
    ): SyncPlayRepository {
        val auth = signedInAuthRepository()
        return SyncPlayRepository(dataSource, SessionGuard(auth), auth, scope = backgroundScope)
    }

    private fun sourceWith(video: VideoStreamInfo? = null, audio: AudioStreamInfo? = null) =
        DIRECT_SOURCE.copy(info = SourceMediaInfo(container = "mkv", video = video, audio = audio))

    @Test
    fun `becomes ready with the resume position`() = runTest {
        library.item = { movie(it, positionTicks = 20_000_000L) }
        val viewModel = viewModel()
        assertEquals(PlayerUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        val ready = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals(2_000L, ready.request.startPositionMs)
    }

    @Test
    fun `playback errors surface`() = runTest {
        library.item = { throw InvalidStatusException(404) }
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(PlayerUiState.Error(LoadError.NotFound), viewModel.uiState.value)
    }

    @Test
    fun `reports start once, then progress with ticks, then stopped once`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onPlaybackStarted(1_000, isPaused = false)
        viewModel.onPlaybackStarted(2_000, isPaused = false)
        viewModel.onProgress(10_000, isPaused = false)
        viewModel.onProgress(12_000, isPaused = true)
        viewModel.onStopped(15_000)
        viewModel.onStopped(16_000)
        advanceUntilIdle()

        assertEquals(listOf("start", "progress", "progress", "stopped"), playback.reports)
        assertEquals(listOf(10_000_000L, 100_000_000L, 120_000_000L, 150_000_000L), playback.reported.map { it.positionTicks })
        assertEquals(listOf(false, false, true, false), playback.reported.map { it.isPaused })
        assertTrue(playback.reported.all { it.itemId == "m1" && it.mediaSourceId == "ms-1" && it.playSessionId == "ps-1" })
    }

    @Test
    fun `progress and stop before start are ignored`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onProgress(1_000, isPaused = false)
        viewModel.onStopped(1_000)
        advanceUntilIdle()

        assertTrue(playback.reports.isEmpty())
    }

    @Test
    fun `no progress is sent after stopping`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPlaybackStarted(0, isPaused = false)
        viewModel.onStopped(5_000)

        viewModel.onProgress(6_000, isPaused = false)
        advanceUntilIdle()

        assertEquals(listOf("start", "stopped"), playback.reports)
    }

    @Test
    fun `a decode error falls back to a transcode once and resumes where it stopped`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPlaybackStarted(0, isPaused = false)

        assertTrue(viewModel.onPlayerError(42_000))
        advanceUntilIdle()

        val ready = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals(PlaybackMethod.Transcode, ready.request.source.method)
        assertEquals(42_000L, ready.request.startPositionMs)
        assertEquals(2, ready.generation)
        assertEquals(true, playback.sourceRequests.last().third)
        assertEquals(listOf("start", "stopped"), playback.reports)

        assertFalse(viewModel.onPlayerError(50_000))
        assertEquals(PlayerUiState.PlaybackFailed, viewModel.uiState.value)
    }

    @Test
    fun `changing the bitrate re-requests the source and resumes in place`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPlaybackStarted(0, isPaused = false)

        viewModel.changeBitrate(StreamBitrate.Mbps10, 40_000)
        advanceUntilIdle()

        val ready = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals(2, ready.generation)
        assertEquals(40_000L, ready.request.startPositionMs)
        assertEquals(StreamBitrate.Mbps10, ready.request.settings.maxBitrate)
        assertEquals(10_000_000, playback.bitrates.last())
        // The first session was reported stopped at the resume point.
        assertEquals(listOf("start", "stopped"), playback.reports)
    }

    @Test
    fun `re-selecting the current bitrate does nothing`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.changeBitrate(StreamBitrate.Auto, 5_000)
        advanceUntilIdle()

        val ready = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals(1, ready.generation)
        assertEquals(1, playback.sourceRequests.size)
    }

    @Test
    fun `the fallback session reports its own start`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPlaybackStarted(0, isPaused = false)
        viewModel.onPlayerError(1_000)
        advanceUntilIdle()

        viewModel.onPlaybackStarted(1_000, isPaused = false)
        viewModel.onStopped(9_000)
        advanceUntilIdle()

        assertEquals(listOf("start", "stopped", "start", "stopped"), playback.reports)
        assertIs<PlayerUiState.Ready>(viewModel.uiState.value)
    }

    @Test
    fun `a failing transcode source is not retried`() = runTest {
        playback.source = { DIRECT_SOURCE.copy(method = PlaybackMethod.Transcode) }
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPlaybackStarted(0, isPaused = false)

        assertFalse(viewModel.onPlayerError(1_000))
        advanceUntilIdle()

        assertEquals(PlayerUiState.PlaybackFailed, viewModel.uiState.value)
        assertEquals(1, playback.sourceRequests.size)
        assertEquals(listOf("start", "stopped"), playback.reports)
    }

    @Test
    fun `a video decoder error replays the same stream with ffmpeg, then transcodes, then fails`() = runTest {
        playback.source = { force ->
            if (force) DIRECT_SOURCE.copy(method = PlaybackMethod.Transcode) else sourceWith(video = VideoStreamInfo(codec = "h264", width = 1920, height = 1080, range = VideoRange.Sdr))
        }
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPlaybackStarted(0, isPaused = false)

        assertTrue(viewModel.onPlayerError(7_000, PlayerErrorKind.VideoDecoder))

        val local = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals(PlaybackLevel.LocalDecode, local.request.level)
        assertEquals(DecodePlan(video = DecoderChoice.Local), local.request.plan)
        assertEquals(PlaybackMethod.DirectPlay, local.request.source.method)
        assertEquals(7_000L, local.request.startPositionMs)
        assertEquals(2, local.generation)
        assertEquals(1, playback.sourceRequests.size)

        viewModel.onPlaybackStarted(7_000, isPaused = false)
        assertTrue(viewModel.onPlayerError(8_000, PlayerErrorKind.VideoDecoder))
        advanceUntilIdle()

        val transcode = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals(PlaybackLevel.Transcode, transcode.request.level)
        assertEquals(8_000L, transcode.request.startPositionMs)
        assertEquals(3, transcode.generation)
        assertEquals(listOf("start", "stopped", "start", "stopped"), playback.reports)

        // The transcoded stream gets one more chance on FFmpeg before giving up.
        assertTrue(viewModel.onPlayerError(9_000, PlayerErrorKind.VideoDecoder))
        assertEquals(PlaybackMethod.Transcode, (viewModel.uiState.value as PlayerUiState.Ready).request.source.method)
        assertFalse(viewModel.onPlayerError(9_000, PlayerErrorKind.VideoDecoder))
        assertEquals(PlayerUiState.PlaybackFailed, viewModel.uiState.value)
    }

    @Test
    fun `an audio error keeps the video decoder and moves only audio to ffmpeg`() = runTest {
        playback.source = { sourceWith(audio = AudioStreamInfo(codec = "truehd", channels = 8)) }
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.onPlayerError(0, PlayerErrorKind.AudioDecoder))

        assertEquals(DecodePlan(audio = DecoderChoice.Local), (viewModel.uiState.value as PlayerUiState.Ready).request.plan)
    }

    @Test
    fun `hdr video that fails to decode goes straight to the server, which can tone-map it`() = runTest {
        playback.source = { force ->
            if (force) DIRECT_SOURCE.copy(method = PlaybackMethod.Transcode) else sourceWith(video = VideoStreamInfo(codec = "hevc", width = 3840, height = 2160, range = VideoRange.Hdr10))
        }
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.onPlayerError(0, PlayerErrorKind.VideoDecoder))
        advanceUntilIdle()

        assertEquals(PlaybackLevel.Transcode, (viewModel.uiState.value as PlayerUiState.Ready).request.level)
    }

    @Test
    fun `an episode loads its next episode and segments`() = runTest {
        library.item = { episode(it, 1) }
        library.episodes = { _, _ -> listOf(episode("e1", 1), episode("e2", 2)) }
        playback.segments = { listOf(MediaSegment(MediaSegmentKind.Intro, 0, 90_000)) }
        val viewModel = viewModel(PlaybackTarget.Item("e1"))
        advanceUntilIdle()

        assertEquals("e2", viewModel.nextItem.value?.id)
        assertEquals(listOf(MediaSegment(MediaSegmentKind.Intro, 0, 90_000)), viewModel.segments.value)
    }

    @Test
    fun `the episode end advances to the next episode`() = runTest {
        library.item = { episode(it, it.removePrefix("e").toInt()) }
        library.episodes = { _, _ -> listOf(episode("e1", 1), episode("e2", 2)) }
        val viewModel = viewModel(PlaybackTarget.Item("e1"))
        advanceUntilIdle()
        viewModel.onPlaybackStarted(0, isPaused = false)

        assertTrue(viewModel.onEnded(2_700_000))
        advanceUntilIdle()

        val ready = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals("e2", ready.request.item.id)
        assertEquals(2, ready.generation)
        assertEquals(listOf("start", "stopped"), playback.reports)
        // The new episode is the finale: nothing follows it.
        assertNull(viewModel.nextItem.value)
    }

    @Test
    fun `the still-watching prompt pauses auto-advance after the episode limit`() = runTest {
        library.item = { episode(it, it.removePrefix("e").toInt()) }
        library.episodes = { _, _ -> listOf(episode("e1", 1), episode("e2", 2), episode("e3", 3), episode("e4", 4)) }
        val viewModel = viewModelWithSettings(
            com.pyoungws.cobaltwolf.shared.playback.PlaybackSettings(stillWatchingEpisodes = 2),
            PlaybackTarget.Item("e1"),
        )
        advanceUntilIdle()
        viewModel.onPlaybackStarted(0, isPaused = false)

        assertTrue(viewModel.onEnded(2_700_000)) // e1 -> e2, streak 1
        advanceUntilIdle()
        assertTrue(viewModel.onEnded(2_700_000)) // e2 -> e3, streak 2
        advanceUntilIdle()
        assertTrue(viewModel.onEnded(2_700_000)) // streak reached limit -> prompt, stays on e3
        advanceUntilIdle()

        assertTrue(viewModel.stillWatching.value)
        assertEquals("e3", (viewModel.uiState.value as PlayerUiState.Ready).request.item.id)

        viewModel.confirmStillWatching(0)
        advanceUntilIdle()
        assertFalse(viewModel.stillWatching.value)
        assertEquals("e4", (viewModel.uiState.value as PlayerUiState.Ready).request.item.id)
    }

    @Test
    fun `manual actions reset the still-watching streak`() = runTest {
        library.item = { episode(it, it.removePrefix("e").toInt()) }
        library.episodes = { _, _ -> listOf(episode("e1", 1), episode("e2", 2), episode("e3", 3)) }
        val viewModel = viewModelWithSettings(
            com.pyoungws.cobaltwolf.shared.playback.PlaybackSettings(stillWatchingEpisodes = 1),
            PlaybackTarget.Item("e1"),
        )
        advanceUntilIdle()
        viewModel.onUserSeek(1_000) // counts as interaction
        viewModel.onPlaybackStarted(0, isPaused = false)

        assertTrue(viewModel.onEnded(2_700_000))
        advanceUntilIdle()
        assertFalse(viewModel.stillWatching.value)
    }

    @Test
    fun `resume rewind shifts the start position back`() = runTest {
        library.item = { movie(it, positionTicks = 20_000_000L) }
        val viewModel = viewModelWithSettings(
            com.pyoungws.cobaltwolf.shared.playback.PlaybackSettings(resumeRewindSeconds = 10),
        )
        advanceUntilIdle()
        // 2s resume minus 10s rewind clamps to 0.
        assertEquals(0L, (viewModel.uiState.value as PlayerUiState.Ready).request.startPositionMs)
    }

    @Test
    fun `a movie or last episode just ends`() = runTest {
        library.item = { movie(it) }
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPlaybackStarted(0, isPaused = false)

        assertFalse(viewModel.onEnded(5_000))
        advanceUntilIdle()

        assertEquals(listOf("start", "stopped"), playback.reports)
    }

    @Test
    fun `a group does not auto-advance on its own`() = runTest {
        val syncPlay = syncPlayRepository()
        syncPlay.join(group("g1"))
        runCurrent()
        library.item = { episode(it, 1) }
        library.episodes = { _, _ -> listOf(episode("e1", 1), episode("e2", 2)) }
        val viewModel = viewModel(PlaybackTarget.Item("e1"), syncPlay = syncPlay)
        advanceUntilIdle()

        assertFalse(viewModel.onEnded(5_000))
        assertIs<PlayerUiState.Ready>(viewModel.uiState.value)
    }

    @Test
    fun `a group load command for another item switches the target`() = runTest {
        val dataSource = FakeSyncPlayDataSource()
        val syncPlay = syncPlayRepository(dataSource)
        syncPlay.join(group("g1"))
        val viewModel = viewModel(syncPlay = syncPlay)
        runCurrent()

        dataSource.emit(SyncPlayEvent.QueueChanged(listOf(queueItem("m2")), 0, 600_000_000, isPlaying = true))
        runCurrent()

        val ready = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals("m2", ready.request.item.id)
        assertEquals(60_000L, ready.request.startPositionMs)
        assertEquals(false, ready.request.startPaused)
    }

    @Test
    fun `a paused group starts the player paused`() = runTest {
        val dataSource = FakeSyncPlayDataSource()
        val syncPlay = syncPlayRepository(dataSource)
        syncPlay.join(group("g1"))
        val viewModel = viewModel(syncPlay = syncPlay)
        runCurrent()

        dataSource.emit(SyncPlayEvent.QueueChanged(listOf(queueItem("m2")), 0, 0, isPlaying = false))
        runCurrent()

        val ready = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals("m2", ready.request.item.id)
        assertEquals(true, ready.request.startPaused)
    }

    @Test
    fun `group pause and seek commands reach the ui`() = runTest {
        val dataSource = FakeSyncPlayDataSource()
        val syncPlay = syncPlayRepository(dataSource)
        syncPlay.join(group("g1"))
        val viewModel = viewModel(syncPlay = syncPlay)
        runCurrent()
        dataSource.emit(SyncPlayEvent.QueueChanged(listOf(queueItem("m1")), 0, 0, isPlaying = true))
        runCurrent()

        val commands = mutableListOf<SyncPlayUiCommand>()
        backgroundScope.launch { viewModel.commands.collect { commands += it } }
        dataSource.emit(SyncPlayEvent.StateChanged(SyncPlayGroupState.Paused))
        dataSource.emit(SyncPlayEvent.Correction(CorrectionType.Seek, 42_000_000))
        runCurrent()

        assertEquals(SyncPlayUiCommand.Pause, commands.first())
        assertEquals(SyncPlayUiCommand.SeekTo(4_200), commands.last())
    }

    @Test
    fun `local transport is only reported while the group controls the item`() = runTest {
        val dataSource = FakeSyncPlayDataSource()
        val syncPlay = syncPlayRepository(dataSource)
        val viewModel = viewModel(syncPlay = syncPlay)
        runCurrent()

        // Outside a group nothing is reported.
        viewModel.onUserPause(1_000)
        viewModel.onUserSeek(2_000)
        runCurrent()
        assertTrue(dataSource.calls.isEmpty())

        // Inside a group on the same item the actions are relayed.
        syncPlay.join(group("g1"))
        runCurrent()
        dataSource.emit(SyncPlayEvent.QueueChanged(listOf(queueItem("m1")), 0, 0, isPlaying = true))
        runCurrent()
        viewModel.onUserPause(1_000)
        viewModel.onUserSeek(20_000)
        runCurrent()
        assertEquals(listOf("join(g1)", "pause", "seek"), dataSource.calls.filter { it != "groups" })
    }

    @Test
    fun `a direct uri is not reported and falls back to ffmpeg only`() = runTest {
        val viewModel = viewModel(PlaybackTarget.DirectUri("file:///sdcard/sample.mkv"))
        advanceUntilIdle()
        viewModel.onPlaybackStarted(0, isPaused = false)
        viewModel.onProgress(1_000, isPaused = false)

        assertTrue(viewModel.onPlayerError(2_000, PlayerErrorKind.VideoDecoder))
        assertEquals(PlaybackLevel.LocalDecode, (viewModel.uiState.value as PlayerUiState.Ready).request.level)

        assertFalse(viewModel.onPlayerError(2_000, PlayerErrorKind.VideoDecoder))
        viewModel.onStopped(2_000)
        advanceUntilIdle()

        assertEquals(PlayerUiState.PlaybackFailed, viewModel.uiState.value)
        assertTrue(playback.reports.isEmpty())
    }
}
