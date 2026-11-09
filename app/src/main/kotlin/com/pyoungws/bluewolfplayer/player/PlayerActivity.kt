package com.pyoungws.bluewolfplayer.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
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
import com.pyoungws.bluewolfplayer.shared.presentation.PlayerViewModel
import com.pyoungws.bluewolfplayer.ui.theme.BlueWolfTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/** Full-screen video player. Lives in its own activity so Picture-in-Picture shrinks only the video. */
class PlayerActivity : ComponentActivity() {
    private val itemId by lazy { requireNotNull(intent.getStringExtra(EXTRA_ITEM_ID)) }
    private val viewModel: PlayerViewModel by viewModel { parametersOf(itemId) }

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

        setContent {
            BlueWolfTheme {
                PlayerScreen(
                    viewModel = viewModel,
                    isInPip = isInPip,
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
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

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

    companion object {
        private const val EXTRA_ITEM_ID = "item_id"

        fun intent(context: Context, itemId: String) =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_ITEM_ID, itemId)
    }
}
