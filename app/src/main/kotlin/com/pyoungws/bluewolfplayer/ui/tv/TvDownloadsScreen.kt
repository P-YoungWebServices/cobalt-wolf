package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.presentation.DownloadsUiState
import com.pyoungws.bluewolfplayer.shared.presentation.DownloadsViewModel
import com.pyoungws.bluewolfplayer.ui.downloads.detailLine
import com.pyoungws.bluewolfplayer.ui.downloads.formatEta
import com.pyoungws.bluewolfplayer.ui.downloads.formatSpeed
import com.pyoungws.bluewolfplayer.ui.downloads.torrentStateRes
import org.koin.compose.viewmodel.koinViewModel

/** qBittorrent monitoring on TV; the "Services" button swaps the list for the settings form. */
@Composable
fun TvDownloadsScreen(
    viewModel: DownloadsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    if (showSettings) {
        TvServiceSettings(onDone = { showSettings = false })
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TvContentPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title") { TvSectionTitle(stringResource(R.string.downloads_title)) }
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showSettings = true }) { Text(stringResource(R.string.calendar_settings)) }
                OutlinedButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) }
            }
        }
        when (val current = state) {
            DownloadsUiState.Loading -> item(key = "loading") { TvLoadingBox() }
            DownloadsUiState.NotConfigured -> item(key = "empty") {
                Text(
                    stringResource(R.string.downloads_not_configured),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is DownloadsUiState.Error -> item(key = "error") { TvErrorBox(current.error, onRetry = viewModel::refresh) }
            is DownloadsUiState.Ready -> {
                item(key = "totals") {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "↓ ${formatSpeed(current.downloads.totalDownloadSpeedBps)}   ↑ ${formatSpeed(current.downloads.totalUploadSpeedBps)}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            OutlinedButton(onClick = viewModel::toggleAlternativeSpeeds) {
                                Text(
                                    stringResource(
                                        if (current.downloads.alternativeSpeedLimits) R.string.downloads_alt_speeds_on
                                        else R.string.downloads_alt_speeds_off,
                                    ),
                                )
                            }
                        }
                        if (current.stale) {
                            Text(
                                stringResource(R.string.downloads_stale),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                if (current.downloads.torrents.isEmpty()) {
                    item(key = "none") {
                        Text(
                            stringResource(R.string.downloads_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(current.downloads.torrents, key = { it.hash }) { torrent ->
                    Column {
                        Text(torrent.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        LinearProgressIndicator(
                            progress = { torrent.progressFraction },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                        val stateLabel = torrentStateRes(torrent.state)?.let { stringResource(it) } ?: torrent.state
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                torrent.detailLine(stateLabel, stringResource(R.string.downloads_eta, formatEta(torrent.etaSeconds))),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = { viewModel.toggle(torrent) }) {
                                Text(stringResource(if (torrent.isPaused) R.string.downloads_resume else R.string.downloads_pause))
                            }
                        }
                    }
                }
            }
        }
    }
}
