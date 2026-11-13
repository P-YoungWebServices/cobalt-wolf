package com.pyoungws.bluewolfplayer.ui.syncplay

import android.widget.Toast
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.presentation.SyncPlayUiState
import com.pyoungws.bluewolfplayer.shared.presentation.SyncPlayViewModel
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayGroup
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayGroupState
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayMembership
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayNotice
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Watch together": browse the server's SyncPlay groups, create and join them, leave.
 * When opened from a media detail page, [queueItemId] is that item: joining or creating
 * a group queues it so it starts playing for everyone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchTogetherScreen(
    onBack: () -> Unit,
    onWatch: (String) -> Unit,
    queueItemId: String? = null,
    viewModel: SyncPlayViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var newGroupName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.notices.collect { notice ->
            val text = when (notice) {
                SyncPlayNotice.GroupClosed -> R.string.syncplay_notice_group_closed
                SyncPlayNotice.GroupGone -> R.string.syncplay_notice_group_gone
                SyncPlayNotice.LibraryAccessDenied -> R.string.syncplay_notice_access_denied
                SyncPlayNotice.EventsLost -> R.string.syncplay_notice_events_lost
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.syncplay_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
                actions = { TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                SyncPlayUiState.Loading -> LoadingBox()
                is SyncPlayUiState.Error -> ErrorBox(current.error, onRetry = viewModel::refresh)
                is SyncPlayUiState.Ready -> SyncPlayContent(
                    state = current,
                    queueItemId = queueItemId,
                    newGroupName = newGroupName,
                    onNewGroupName = { newGroupName = it },
                    onCreate = {
                        viewModel.createAndJoin(newGroupName, queueItemId)
                        newGroupName = ""
                    },
                    onJoin = { viewModel.join(it, queueItemId) },
                    onLeave = viewModel::leave,
                    onWatch = onWatch,
                    onQueueItem = viewModel::queueItem,
                )
            }
        }
    }
}

@Composable
private fun SyncPlayContent(
    state: SyncPlayUiState.Ready,
    queueItemId: String?,
    newGroupName: String,
    onNewGroupName: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: (SyncPlayGroup) -> Unit,
    onLeave: () -> Unit,
    onWatch: (String) -> Unit,
    onQueueItem: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.actionError?.let { error ->
            item(key = "error") {
                Text(error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        state.membership?.let { member ->
            // One group at a time: inside a group the browser is replaced by the membership card.
            item(key = "membership") {
                MembershipCard(member, busy = state.busy, queueItemId = queueItemId, onWatch = onWatch, onLeave = onLeave, onQueueItem = onQueueItem)
            }
            return@LazyColumn
        }
        item(key = "create") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = onNewGroupName,
                    label = { Text(stringResource(R.string.syncplay_create_label)) },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onCreate, enabled = !state.busy) {
                    Text(stringResource(R.string.syncplay_create))
                }
            }
        }
        if (state.groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.syncplay_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
        items(state.groups, key = { it.id }) { group ->
            GroupRow(group, busy = state.busy, onJoin = { onJoin(group) })
        }
    }
}

@Composable
private fun MembershipCard(
    member: SyncPlayMembership,
    busy: Boolean,
    queueItemId: String?,
    onWatch: (String) -> Unit,
    onLeave: () -> Unit,
    onQueueItem: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(member.groupName, style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(groupStateLabel(member.groupState)) + " · " +
                stringResource(R.string.syncplay_member_count, member.participants.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (member.participants.isNotEmpty()) {
            Text(member.participants.joinToString(", "), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            member.playingItem?.let { item ->
                Button(onClick = { onWatch(item.itemId) }) { Text(stringResource(R.string.syncplay_watch)) }
            }
            queueItemId?.takeIf { member.playingItem?.itemId != it }?.let { itemId ->
                TextButton(onClick = { onQueueItem(itemId) }, enabled = !busy) { Text(stringResource(R.string.syncplay_queue_item)) }
            }
            TextButton(onClick = onLeave, enabled = !busy) { Text(stringResource(R.string.syncplay_leave)) }
            if (busy) CircularProgressIndicator(Modifier.padding(8.dp).align(Alignment.CenterVertically), strokeWidth = 2.dp)
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

@Composable
private fun GroupRow(group: SyncPlayGroup, busy: Boolean, onJoin: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(groupStateLabel(group.state)) + " · " +
                    stringResource(R.string.syncplay_member_count, group.participants.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onJoin, enabled = !busy) { Text(stringResource(R.string.syncplay_join)) }
    }
}

private fun groupStateLabel(state: SyncPlayGroupState) = when (state) {
    SyncPlayGroupState.Idle -> R.string.syncplay_state_idle
    SyncPlayGroupState.Waiting -> R.string.syncplay_state_waiting
    SyncPlayGroupState.Paused -> R.string.syncplay_state_paused
    SyncPlayGroupState.Playing -> R.string.syncplay_state_playing
}
