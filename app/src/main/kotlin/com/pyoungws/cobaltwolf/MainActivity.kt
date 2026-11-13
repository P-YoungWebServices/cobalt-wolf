package com.pyoungws.cobaltwolf

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
import com.pyoungws.cobaltwolf.ui.CobaltWolfApp
import com.pyoungws.cobaltwolf.ui.theme.CobaltWolfTheme
import com.pyoungws.cobaltwolf.ui.tv.CobaltWolfTvTheme
import com.pyoungws.cobaltwolf.ui.tv.TvApp

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Item id from a home screen channel or other deep link; consumed by the signed-in UI. */
    private var deepLinkItemId by mutableStateOf<String?>(null)

    /** Voice/global-search query for the TV search section. */
    private var searchQuery by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        deepLinkItemId = intent.itemIdDeepLink()
        searchQuery = intent.searchQuery()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val tv = isTelevision()
        setContent {
            if (tv) {
                CobaltWolfTvTheme {
                    TvApp(initialItemId = deepLinkItemId, initialQuery = searchQuery)
                }
            } else {
                CobaltWolfTheme {
                    CobaltWolfApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.itemIdDeepLink()?.let { deepLinkItemId = it }
        intent.searchQuery()?.let { searchQuery = it }
    }
}

/** Voice/text queries from the Android TV global search (android.intent.action.SEARCH). */
private fun Intent.searchQuery(): String? =
    takeIf { it.action == Intent.ACTION_SEARCH }?.getStringExtra(android.app.SearchManager.QUERY)

/** The `cobaltwolf://item/<id>` links published on the Android TV home screen channels. */
private fun Intent.itemIdDeepLink(): String? =
    data?.takeIf { it.scheme == "cobaltwolf" && it.host == "item" }?.pathSegments?.firstOrNull()
