package com.pyoungws.cobaltwolf.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pyoungws.cobaltwolf.BuildConfig
import com.pyoungws.cobaltwolf.R

private val LICENSES = listOf(
    "Jellyfin Kotlin SDK" to "LGPL-3.0",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization, kotlinx.datetime" to "Apache-2.0",
    "Ktor client" to "Apache-2.0",
    "AndroidX (Compose, Media3/ExoPlayer, TV, Glance, TV Provider)" to "Apache-2.0",
    "Koin" to "Apache-2.0",
    "Coil 3" to "Apache-2.0",
    "NextLib media3ext (bundles FFmpeg)" to "GPL-3.0 / LGPL-3.0+",
    "libass-android ass-media (libass, FreeType, FriBidi, HarfBuzz)" to "MIT / ISC / FTL / LGPL-2.1+",
)

/** Version, disclaimer and dependency licenses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.about_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.about_licenses), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            items(LICENSES.size) { i ->
                val (name, license) = LICENSES[i]
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                    Text(license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
