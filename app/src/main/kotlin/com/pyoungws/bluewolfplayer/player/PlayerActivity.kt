package com.pyoungws.bluewolfplayer.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pyoungws.bluewolfplayer.BuildConfig
import com.pyoungws.bluewolfplayer.isTelevision
import com.pyoungws.bluewolfplayer.shared.playback.AudioOutputPreference
import com.pyoungws.bluewolfplayer.shared.playback.CapabilityDetector
import com.pyoungws.bluewolfplayer.shared.playback.DecodingPreference
import com.pyoungws.bluewolfplayer.shared.playback.FrameRateMatching
import com.pyoungws.bluewolfplayer.shared.playback.HdrPreference
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackSettingsRepository
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackTarget
import com.pyoungws.bluewolfplayer.shared.playback.StreamBitrate
import com.pyoungws.bluewolfplayer.shared.presentation.PlayerViewModel
import org.koin.android.ext.android.get
import com.pyoungws.bluewolfplayer.ui.theme.BlueWolfTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/** Full-screen video player. Lives in its own activity so Picture-in-Picture shrinks only the video. */
class PlayerActivity : ComponentActivity() {
    private val target by lazy { playbackTarget(intent) }
    private val viewModel: PlayerViewModel by viewModel { parametersOf(target) }
    private val displayModeController by lazy { DisplayModeController(this) }

    private var player: ExoPlayer? = null
    private var isInPip by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (BuildConfig.DEBUG) applyDebugOverrides(intent)

        setContent {
            BlueWolfTheme {
                PlayerScreen(
                    viewModel = viewModel,
                    isInPip = isInPip,
                    displayModeController = displayModeController,
                    // Debug only: `--es audio_mime audio/vnd.dts` picks a non-default audio track by type.
                    debugPreferredAudioMimeType = intent.getStringExtra("audio_mime").takeIf { BuildConfig.DEBUG },
                    // Debug only: `--es subtitle_mime application/pgs` turns on the first subtitle track of that type.
                    debugSubtitleMimeType = intent.getStringExtra("subtitle_mime").takeIf { BuildConfig.DEBUG },
                    // Debug only: `--ei max_channels 6` caps PCM output channel count.
                    debugMaxChannels = intent.takeIf { it.hasExtra("max_channels") }?.getIntExtra("max_channels", 0)?.takeIf { BuildConfig.DEBUG },
                    // Debug only: `--ez stats` opens the stats overlay for this run.
                    debugShowStats = BuildConfig.DEBUG && intent.getBooleanExtra("stats", false),
                    onPlayerChanged = { player = it },
                    onPlayingChanged = ::updatePipParams,
                    onEnterPip = ::enterPip,
                    onClose = ::finish,
                )
            }
        }
    }

    private val supportsPip: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            !isTelevision()

    private fun pipParams(autoEnter: Boolean): PictureInPictureParams {
        val size = player?.videoSize
        val ratio = if (size != null && size.width > 0 && size.height > 0) Rational(size.width, size.height) else Rational(16, 9)
        return PictureInPictureParams.Builder()
            .setAspectRatio(ratio.clamped())
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(autoEnter) }
            .build()
    }

    private fun Rational.clamped(): Rational {
        val value = toFloat()
        return when {
            value > 2.39f -> Rational(239, 100)
            value < 1 / 2.39f -> Rational(100, 239)
            else -> this
        }
    }

    /** With auto-enter (Android 12+) pressing home while playing shrinks the video into a PiP window. */
    private fun updatePipParams(isPlaying: Boolean) {
        if (supportsPip) setPictureInPictureParams(pipParams(autoEnter = isPlaying))
    }

    private fun enterPip() {
        if (supportsPip) enterPictureInPictureMode(pipParams(autoEnter = true))
    }

    /**
     * Dedicated transport keys on TV remotes (play/pause, fast-forward, rewind, skip, stop).
     * The D-pad is left to the player view and the overlay buttons.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val exoPlayer = player
        if (exoPlayer != null && event.repeatCount == 0) {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (exoPlayer.playWhenReady) exoPlayer.pause() else exoPlayer.play(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { exoPlayer.play(); return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { exoPlayer.pause(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT -> { exoPlayer.seekForward(); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { exoPlayer.seekBack(); return true }
                KeyEvent.KEYCODE_MEDIA_STOP -> { finish(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 8-11 has no auto-enter; Android 12+ already handles this via the params.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && player?.isPlaying == true) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        val player = player ?: return
        // Leaving without PiP pauses; a PiP window keeps playing. The position is saved either way.
        if (!isInPip) player.pause()
        viewModel.onProgress(player.currentPosition, isPaused = !player.isPlaying)
    }

    override fun onDestroy() {
        viewModel.onStopped(player?.takeIf { it.playbackState != Player.STATE_IDLE }?.currentPosition ?: -1L)
        super.onDestroy()
    }

    /**
     * Debug builds can be driven from adb to test devices without a TV layout or a server:
     * `am start -n <app id>/.player.DebugPlayerActivity -d <file or http uri>` plays the uri, and
     * `--es item_id <id>` plays a library item of the signed-in user.
     */
    private fun playbackTarget(intent: Intent): PlaybackTarget {
        val uri = intent.data?.takeIf { BuildConfig.DEBUG }
        if (uri != null) return PlaybackTarget.DirectUri(uri.toString())
        intent.getStringExtra(EXTRA_LOCAL_URI)?.let {
            return PlaybackTarget.DirectUri(it, intent.getStringExtra(EXTRA_LOCAL_TITLE) ?: it.substringAfterLast('/'))
        }
        val startMs = if (intent.hasExtra(EXTRA_START_MS)) intent.getLongExtra(EXTRA_START_MS, 0L) else null
        val startPaused = intent.getBooleanExtra(EXTRA_START_PAUSED, false)
        val mediaSourceId = intent.getStringExtra(EXTRA_MEDIA_SOURCE_ID)
        return PlaybackTarget.Item(requireNotNull(intent.getStringExtra(EXTRA_ITEM_ID)), startPositionMs = startMs, startPaused = startPaused, mediaSourceId = mediaSourceId)
    }

    /**
     * Debug only: `--es decoding Auto|PreferLocal|AlwaysTranscode`, `--es audio_output Auto|PcmOnly`,
     * `--es hdr Auto|SdrOnly`, `--es max_bitrate Auto|Mbps120|...`, `--ez dolby_vision` and
     * `--ez styled_subtitles` change the
     * saved Playback settings before playing, and the detected capabilities are logged.
     */
    private fun applyDebugOverrides(intent: Intent) {
        val extras = intent.extras
        get<PlaybackSettingsRepository>().update { settings ->
            settings.copy(
                decoding = extras?.getString("decoding")?.let(DecodingPreference::valueOf) ?: settings.decoding,
                audioOutput = extras?.getString("audio_output")?.let(AudioOutputPreference::valueOf) ?: settings.audioOutput,
                hdr = extras?.getString("hdr")?.let(HdrPreference::valueOf) ?: settings.hdr,
                dolbyVision = if (extras?.containsKey("dolby_vision") == true) extras.getBoolean("dolby_vision") else settings.dolbyVision,
                styledSubtitles = if (extras?.containsKey("styled_subtitles") == true) extras.getBoolean("styled_subtitles") else settings.styledSubtitles,
                frameRateMatching = extras?.getString("frame_rate")?.let(FrameRateMatching::valueOf) ?: settings.frameRateMatching,
                maxBitrate = extras?.getString("max_bitrate")?.let(StreamBitrate::valueOf) ?: settings.maxBitrate,
            )
        }
        // `--ez over_lock_screen true` lets a test run play on a locked, sleeping device.
        if (extras?.getBoolean("over_lock_screen") == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        Log.i("CobaltWolfPlayback", "settings ${get<PlaybackSettingsRepository>().settings.value}")
        Log.i("CobaltWolfPlayback", "capabilities ${get<CapabilityDetector>().detect()}")
    }

    companion object {
        private const val EXTRA_ITEM_ID = "item_id"
        private const val EXTRA_START_MS = "start_ms"
        private const val EXTRA_START_PAUSED = "start_paused"
        private const val EXTRA_MEDIA_SOURCE_ID = "media_source_id"
        private const val EXTRA_LOCAL_URI = "local_uri"
        private const val EXTRA_LOCAL_TITLE = "local_title"

        fun intent(context: Context, itemId: String = "", startPositionMs: Long? = null, startPaused: Boolean = false, mediaSourceId: String? = null, localUri: String? = null, title: String? = null): Intent {
            val i = Intent(context, PlayerActivity::class.java)
            if (localUri != null) {
                return i.putExtra(EXTRA_LOCAL_URI, localUri).putExtra(EXTRA_LOCAL_TITLE, title)
            }
            i.putExtra(EXTRA_ITEM_ID, itemId)
            startPositionMs?.let { i.putExtra(EXTRA_START_MS, it) }
            i.putExtra(EXTRA_START_PAUSED, startPaused)
            mediaSourceId?.let { i.putExtra(EXTRA_MEDIA_SOURCE_ID, it) }
            return i
        }
    }
}
