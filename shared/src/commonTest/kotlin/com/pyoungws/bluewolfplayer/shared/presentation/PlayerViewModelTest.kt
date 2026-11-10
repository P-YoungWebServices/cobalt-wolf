package com.pyoungws.bluewolfplayer.shared.presentation

import com.pyoungws.bluewolfplayer.shared.library.FakeLibraryDataSource
import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.library.movie
import com.pyoungws.bluewolfplayer.shared.library.signedInAuthRepository
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.playback.DIRECT_SOURCE
import com.pyoungws.bluewolfplayer.shared.playback.FakePlaybackDataSource
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackMethod
import com.pyoungws.bluewolfplayer.shared.playback.AudioStreamInfo
import com.pyoungws.bluewolfplayer.shared.playback.DecodePlan
import com.pyoungws.bluewolfplayer.shared.playback.DecoderChoice
import com.pyoungws.bluewolfplayer.shared.playback.DeviceCapabilities
import com.pyoungws.bluewolfplayer.shared.playback.PHONE_CAPABILITIES
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackLevel
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackTarget
import com.pyoungws.bluewolfplayer.shared.playback.PlayerErrorKind
import com.pyoungws.bluewolfplayer.shared.playback.SourceMediaInfo
import com.pyoungws.bluewolfplayer.shared.playback.VideoRange
import com.pyoungws.bluewolfplayer.shared.playback.VideoStreamInfo
import com.pyoungws.bluewolfplayer.shared.playback.playbackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
    ) = PlayerViewModel(
        target,
        playbackRepository(library, playback, SessionGuard(signedInAuthRepository()), capabilities),
        reportScope = this,
    )

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
