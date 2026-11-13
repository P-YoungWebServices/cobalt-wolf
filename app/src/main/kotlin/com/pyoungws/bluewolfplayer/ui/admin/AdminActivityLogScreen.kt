package com.pyoungws.bluewolfplayer.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.presentation.AdminActivityLogViewModel
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.tvListScroll
import org.koin.compose.viewmodel.koinViewModel

/** Server activity log — the events Jellyfin records (logins, scans, playback, changes). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminActivityLogScreen(
    onBack: () -> Unit,
    viewModel: AdminActivityLogViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_activity_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingBox(Modifier.fillMaxSize().padding(padding))
            state.error != null -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).tvListScroll(listState)) {
                itemsIndexed(state.entries, key = { _, entry -> entry.id }) { index, entry ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            entry.severity?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        entry.overview?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        listOfNotNull(entry.type, entry.date).takeIf { it.isNotEmpty() }?.let {
                            Text(it.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                    if (index == state.entries.lastIndex && state.hasMore) {
                        viewModel.loadMore()
                    }
                }
                if (state.isLoadingMore) {
                    item { Text(stringResource(R.string.loading), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
