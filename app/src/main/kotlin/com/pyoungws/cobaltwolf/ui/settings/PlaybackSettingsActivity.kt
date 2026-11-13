package com.pyoungws.cobaltwolf.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pyoungws.cobaltwolf.BuildConfig
import com.pyoungws.cobaltwolf.ui.theme.CobaltWolfTheme

/** Hosts the Playback settings screen. A separate activity so it can be opened before sign-in and from adb on TV devices. */
class PlaybackSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Debug only: `--ez over_lock_screen true` shows the screen on a locked test device.
        if (BuildConfig.DEBUG && intent.getBooleanExtra("over_lock_screen", false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        setContent {
            CobaltWolfTheme {
                PlaybackSettingsScreen(onBack = ::finish)
            }
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, PlaybackSettingsActivity::class.java)
    }
}
