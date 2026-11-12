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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.presentation.SyncPlayUiState
import com.pyoungws.bluewolfplayer.shared.presentation.SyncPlayViewModel
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayGroup
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayGroupState
import org.koin.compose.viewmodel.koinViewModel

/** "Watch together" on TV: join an existing group, or create one with the default name. */
@Composable
fun TvWatchTogetherScreen(
    onWatch: (String) -> Unit,
    queueItemId: String? = null,
    viewModel: SyncPlayViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TvContentPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") { TvSectionTitle(stringResource(R.string.syncplay_title)) }
        when (val current = state) {
            SyncPlayUiState.Loading -> item(key = "loading") { TvLoadingBox() }
            is SyncPlayUiState.Error -> item(key = "error") { TvErrorBox(current.error, onRetry = viewModel::refresh) }
            is SyncPlayUiState.Ready -> {
                current.membership?.let { member ->
                    item(key = "membership") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(member.groupName, style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(tvGroupStateLabel(member.groupState)) + " · " +
                                    stringResource(R.string.syncplay_member_count, member.participants.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (member.participants.isNotEmpty()) {
                                Text(member.participants.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                member.playingItem?.let { item ->
                                    Button(onClick = { onWatch(item.itemId) }) { Text(stringResource(R.string.syncplay_watch)) }
                                }
                                queueItemId?.takeIf { member.playingItem?.itemId != it }?.let { itemId ->
                                    OutlinedButton(onClick = { viewModel.queueItem(itemId) }, enabled = !current.busy) {
                                        Text(stringResource(R.string.syncplay_queue_item))
                                    }
                                }
                                OutlinedButton(onClick = viewModel::leave, enabled = !current.busy) {
                                    Text(stringResource(R.string.syncplay_leave))
                                }
                            }
                            if (member.playingItem == null) {
                                Text(
                                    stringResource(R.string.syncplay_nothing_playing),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    return@LazyColumn
                }
                item(key = "create") {
                    Button(onClick = { viewModel.createAndJoin("", queueItemId) }, enabled = !current.busy) {
                        Text(stringResource(R.string.syncplay_create))
                    }
                }
                if (current.groups.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.syncplay_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(current.groups, key = { it.id }) { group ->
                    TvGroupRow(group, busy = current.busy, onJoin = { viewModel.join(group, queueItemId) })
                }
            }
        }
    }
}

@Composable
private fun TvGroupRow(group: SyncPlayGroup, busy: Boolean, onJoin: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(tvGroupStateLabel(group.state)) + " · " +
                    stringResource(R.string.syncplay_member_count, group.participants.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onJoin, enabled = !busy) { Text(stringResource(R.string.syncplay_join)) }
    }
}

private fun tvGroupStateLabel(state: SyncPlayGroupState) = when (state) {
    SyncPlayGroupState.Idle -> R.string.syncplay_state_idle
    SyncPlayGroupState.Waiting -> R.string.syncplay_state_waiting
    SyncPlayGroupState.Paused -> R.string.syncplay_state_paused
    SyncPlayGroupState.Playing -> R.string.syncplay_state_playing
}
