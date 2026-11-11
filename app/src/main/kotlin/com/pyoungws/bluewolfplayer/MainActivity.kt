package com.pyoungws.bluewolfplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.pyoungws.bluewolfplayer.ui.BlueWolfApp
import com.pyoungws.bluewolfplayer.ui.theme.BlueWolfTheme
import com.pyoungws.bluewolfplayer.ui.tv.CobaltWolfTvTheme
import com.pyoungws.bluewolfplayer.ui.tv.TvApp

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Item id from a home screen channel or other deep link; consumed by the signed-in UI. */
    private var deepLinkItemId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        deepLinkItemId = intent.itemIdDeepLink()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val tv = isTelevision()
        setContent {
            if (tv) {
                CobaltWolfTvTheme {
                    TvApp(initialItemId = deepLinkItemId)
                }
            } else {
                BlueWolfTheme {
                    BlueWolfApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.itemIdDeepLink()?.let { deepLinkItemId = it }
    }
}

/** The `bluewolf://item/<id>` links published on the Android TV home screen channels. */
private fun Intent.itemIdDeepLink(): String? =
    data?.takeIf { it.scheme == "bluewolf" && it.host == "item" }?.pathSegments?.firstOrNull()
