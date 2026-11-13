package com.pyoungws.cobaltwolf.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.presentation.DownloadsUiState
import com.pyoungws.cobaltwolf.shared.presentation.DownloadsViewModel
import com.pyoungws.cobaltwolf.shared.qbittorrent.Torrent
import com.pyoungws.cobaltwolf.ui.common.ErrorBox
import com.pyoungws.cobaltwolf.ui.common.LoadingBox
import org.koin.compose.viewmodel.koinViewModel

/** qBittorrent monitoring: live torrent list with speeds, progress and ETAs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    viewModel: DownloadsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
                actions = {
                    TextButton(onClick = onSettings) { Text(stringResource(R.string.calendar_settings)) }
                    TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                DownloadsUiState.Loading -> LoadingBox()
                DownloadsUiState.NotConfigured -> NotConfigured(onSettings)
                is DownloadsUiState.Error -> ErrorBox(current.error, onRetry = viewModel::refresh)
                is DownloadsUiState.Ready -> DownloadList(
                    current,
                    actionError = actionError,
                    onDismissActionError = viewModel::consumeActionError,
                    onToggleTorrent = viewModel::toggle,
                    onToggleAltSpeeds = viewModel::toggleAlternativeSpeeds,
                )
            }
        }
    }
}

@Composable
private fun NotConfigured(onSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.downloads_not_configured),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onSettings, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.calendar_settings))
        }
    }
}

@Composable
private fun DownloadList(
    state: DownloadsUiState.Ready,
    actionError: LoadError?,
    onDismissActionError: () -> Unit,
    onToggleTorrent: (Torrent) -> Unit,
    onToggleAltSpeeds: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "↓ ${formatSpeed(state.downloads.totalDownloadSpeedBps)}   ↑ ${formatSpeed(state.downloads.totalUploadSpeedBps)}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onToggleAltSpeeds) {
                        Text(
                            stringResource(
                                if (state.downloads.alternativeSpeedLimits) R.string.downloads_alt_speeds_on
                                else R.string.downloads_alt_speeds_off,
                            ),
                        )
                    }
                }
                if (state.stale) {
                    Text(
                        stringResource(R.string.downloads_stale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        actionError?.let { error ->
            item(key = "action-error") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        error.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissActionError) { Text(stringResource(R.string.downloads_dismiss)) }
                }
            }
        }
        if (state.downloads.torrents.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.downloads_empty),
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.downloads.torrents, key = { it.hash }) { torrent ->
            TorrentRow(torrent, onToggle = { onToggleTorrent(torrent) })
        }
    }
}

@Composable
private fun TorrentRow(torrent: Torrent, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(torrent.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        LinearProgressIndicator(
            progress = { torrent.progressFraction },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        val stateLabel = torrentStateRes(torrent.state)?.let { stringResource(it) } ?: torrent.state
        val eta = stringResource(R.string.downloads_eta, formatEta(torrent.etaSeconds))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                torrent.detailLine(stateLabel, eta),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onToggle) {
                Text(stringResource(if (torrent.isPaused) R.string.downloads_resume else R.string.downloads_pause))
            }
        }
    }
}
