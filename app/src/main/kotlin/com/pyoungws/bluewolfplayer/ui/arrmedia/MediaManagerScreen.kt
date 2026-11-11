package com.pyoungws.bluewolfplayer.ui.arrmedia

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.presentation.MediaManagerUiState
import com.pyoungws.bluewolfplayer.shared.presentation.MediaManagerViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.MediaRow
import com.pyoungws.bluewolfplayer.ui.common.Artwork
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import org.koin.compose.viewmodel.koinViewModel

/**
 * The admin media manager: the movies/series tracked by the configured Radarr/Sonarr
 * services, with a service picker and a name filter. Opens the edit screen on tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaManagerScreen(
    onBack: () -> Unit,
    onOpenItem: (ArrService, Int) -> Unit,
    onSettings: () -> Unit,
    viewModel: MediaManagerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.media_manager_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
                actions = {
                    if (state.configuredServices.isNotEmpty()) {
                        TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.configuredServices.isEmpty() -> NotConfigured(onSettings)
                else -> ManagerContent(state, viewModel, onOpenItem)
            }
        }
    }
}

@Composable
private fun NotConfigured(onSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.media_manager_not_configured),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onSettings) { Text(stringResource(R.string.calendar_settings)) }
        }
    }
}

@Composable
private fun ManagerContent(
    state: MediaManagerUiState,
    viewModel: MediaManagerViewModel,
    onOpenItem: (ArrService, Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.configuredServices) { service ->
                FilterChip(
                    selected = service == state.service,
                    onClick = { viewModel.selectService(service) },
                    label = { Text(service.name) },
                )
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text(stringResource(R.string.media_manager_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(Modifier.fillMaxSize()) {
            when {
                state.isLoading -> LoadingBox()
                state.error != null && state.rows.isEmpty() -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
                state.filteredRows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (state.rows.isEmpty()) R.string.media_manager_empty else R.string.media_manager_no_match),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.filteredRows, key = { it.id }) { row ->
                        MediaRowCard(row, onClick = { onOpenItem(state.service, row.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaRowCard(row: MediaRow, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(row.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                row.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(if (row.monitored) R.string.media_monitored else R.string.media_unmonitored),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.monitored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(if (row.hasFile) R.string.media_has_file else R.string.media_no_file),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
