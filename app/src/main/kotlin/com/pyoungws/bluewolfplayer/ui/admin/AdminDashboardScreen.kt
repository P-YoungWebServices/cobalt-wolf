package com.pyoungws.bluewolfplayer.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.admin.ActiveSession
import com.pyoungws.bluewolfplayer.shared.admin.MANAGEMENT_DESTINATIONS
import com.pyoungws.bluewolfplayer.shared.admin.NavDestination
import com.pyoungws.bluewolfplayer.shared.admin.SessionPlayMethod
import com.pyoungws.bluewolfplayer.shared.admin.TranscodeDetails
import com.pyoungws.bluewolfplayer.shared.presentation.AdminDashboardUiState
import com.pyoungws.bluewolfplayer.shared.presentation.AdminDashboardViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.MessageStatus
import com.pyoungws.bluewolfplayer.ui.common.CardRow
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.PosterCard
import com.pyoungws.bluewolfplayer.ui.common.SectionTitle
import com.pyoungws.bluewolfplayer.ui.common.cardSubtitle
import com.pyoungws.bluewolfplayer.ui.common.cardTitle
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel

/** Admin dashboard: live sessions with a configurable auto-refresh, plus recently added media. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenManage: (NavDestination) -> Unit,
    viewModel: AdminDashboardViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var messageTarget by remember { mutableStateOf<ActiveSession?>(null) }
    var messageText by rememberSaveable { mutableStateOf("") }
    var intervalMenuOpen by remember { mutableStateOf(false) }

    // Close the dialog once the message for the open target has been sent.
    LaunchedEffect(state.message) {
        val sent = state.message as? MessageStatus.Sent
        if (sent != null && sent.sessionId == messageTarget?.id) {
            messageTarget = null
            messageText = ""
            viewModel.consumeMessageStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_dashboard_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
                actions = {
                    Box {
                        TextButton(onClick = { intervalMenuOpen = true }) {
                            Text(stringResource(R.string.admin_refresh_every, state.refreshSeconds))
                        }
                        DropdownMenu(expanded = intervalMenuOpen, onDismissRequest = { intervalMenuOpen = false }) {
                            AdminDashboardViewModel.REFRESH_OPTIONS_SECONDS.forEach { seconds ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.admin_refresh_seconds, seconds)) },
                                    onClick = {
                                        viewModel.setRefreshSeconds(seconds)
                                        intervalMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> LoadingBox()
                state.error != null && state.sessions.isEmpty() && state.recentlyAdded.isEmpty() ->
                    ErrorBox(state.error!!, onRetry = viewModel::refresh)
                else -> DashboardContent(state, onMessage = { messageTarget = it }, onOpenItem = onOpenItem, onOpenManage = onOpenManage)
            }
        }
    }

    messageTarget?.let { session ->
        MessageDialog(
            session = session,
            status = state.message,
            text = messageText,
            onTextChange = { messageText = it },
            onSend = { viewModel.sendMessage(session.id, messageText) },
            onDismiss = {
                if (state.message !is MessageStatus.Sending) {
                    messageTarget = null
                    messageText = ""
                    viewModel.consumeMessageStatus()
                }
            },
        )
    }
}

@Composable
private fun DashboardContent(
    state: AdminDashboardUiState,
    onMessage: (ActiveSession) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenManage: (NavDestination) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "sessions-title") { SectionTitle(stringResource(R.string.admin_active_sessions)) }
        if (state.sessions.isEmpty()) {
            item(key = "sessions-empty") {
                Text(
                    stringResource(R.string.admin_no_sessions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            items(state.sessions, key = { it.id }) { session ->
                SessionCard(session, onMessage = { onMessage(session) })
            }
        }
        if (state.recentlyAdded.isNotEmpty()) {
            item(key = "recent-title") { SectionTitle(stringResource(R.string.admin_recently_added)) }
            item(key = "recent-row") {
                CardRow(state.recentlyAdded, key = { it.id }) { PosterCard(it, onClick = { onOpenItem(it.id) }) }
            }
        }
        item(key = "manage-title") { SectionTitle(stringResource(R.string.admin_manage_title)) }
        items(MANAGEMENT_DESTINATIONS, key = { "manage-${it.name}" }) { destination ->
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp)).clickable { onOpenManage(destination) },
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(manageTitle(destination), style = MaterialTheme.typography.titleMedium)
                    Text(
                        manageSubtitle(destination),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun manageTitle(destination: NavDestination): String = when (destination) {
    NavDestination.Users -> stringResource(R.string.admin_manage_users)
    NavDestination.Devices -> stringResource(R.string.admin_manage_devices)
    NavDestination.Libraries -> stringResource(R.string.admin_manage_libraries)
    NavDestination.Tasks -> stringResource(R.string.admin_manage_tasks)
    NavDestination.Logs -> stringResource(R.string.admin_manage_logs)
    NavDestination.SystemInfo -> stringResource(R.string.admin_manage_system)
    else -> ""
}

@Composable
private fun manageSubtitle(destination: NavDestination): String = when (destination) {
    NavDestination.Users -> stringResource(R.string.admin_manage_users_subtitle)
    NavDestination.Devices -> stringResource(R.string.admin_manage_devices_subtitle)
    NavDestination.Libraries -> stringResource(R.string.admin_manage_libraries_subtitle)
    NavDestination.Tasks -> stringResource(R.string.admin_manage_tasks_subtitle)
    NavDestination.Logs -> stringResource(R.string.admin_manage_logs_subtitle)
    NavDestination.SystemInfo -> stringResource(R.string.admin_manage_system_subtitle)
    else -> ""
}

@Composable
private fun SessionCard(session: ActiveSession, onMessage: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.admin_session_user_device, session.userName, session.deviceName),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    session.client?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onMessage) { Text(stringResource(R.string.admin_message)) }
            }

            val item = session.item
            if (item == null) {
                Text(
                    stringResource(R.string.admin_session_idle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    listOfNotNull(item.cardTitle(), item.cardSubtitle()).joinToString(" — "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                session.progressFraction?.let { progress ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.admin_progress_percent, (progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(
                    playMethodLabel(session) + pausedSuffix(session),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                codecLine(session)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                session.transcoding?.let { TranscodeDetails(it) }
            }
        }
    }
}

@Composable
private fun playMethodLabel(session: ActiveSession): String = when (session.playMethod) {
    SessionPlayMethod.DirectPlay -> stringResource(R.string.admin_method_direct_play)
    SessionPlayMethod.DirectStream -> stringResource(R.string.admin_method_direct_stream)
    SessionPlayMethod.Transcode -> stringResource(R.string.admin_method_transcode)
    null -> stringResource(R.string.admin_method_unknown)
}

@Composable
private fun pausedSuffix(session: ActiveSession): String =
    if (session.isPaused) stringResource(R.string.admin_paused_suffix) else ""

/** "hevc · truehd" from the source media streams. */
private fun codecLine(session: ActiveSession): String? =
    listOfNotNull(session.videoCodec, session.audioCodec).takeIf { it.isNotEmpty() }?.joinToString(" · ")

@Composable
private fun TranscodeDetails(details: TranscodeDetails) {
    val parts = listOfNotNull(
        details.container,
        details.videoCodec,
        details.audioCodec,
        details.bitrate?.let { stringResource(R.string.admin_bitrate_mbps, it / 1_000_000f) },
        if (details.width != null && details.height != null) "${details.width}×${details.height}" else null,
        details.audioChannels?.let { stringResource(R.string.admin_channels, it) },
    )
    if (parts.isEmpty()) return
    Text(
        stringResource(R.string.admin_transcoding_to, parts.joinToString(" · ")),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (details.reasons.isNotEmpty()) {
        Text(
            stringResource(R.string.admin_transcode_reasons, details.reasons.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageDialog(
    session: ActiveSession,
    status: MessageStatus?,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sending = (status as? MessageStatus.Sending)?.sessionId == session.id
    val failed = status as? MessageStatus.Failed

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_message_title, session.userName, session.deviceName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text(stringResource(R.string.admin_message_hint)) },
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (failed != null && failed.sessionId == session.id) {
                    Text(failed.error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSend, enabled = text.isNotBlank() && !sending) {
                if (sending) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.admin_message_send))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text(stringResource(R.string.home_sign_out_cancel)) } },
    )
}
