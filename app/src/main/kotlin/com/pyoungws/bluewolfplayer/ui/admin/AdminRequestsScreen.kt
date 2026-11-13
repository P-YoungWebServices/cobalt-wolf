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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.presentation.AdminActionStatus
import com.pyoungws.bluewolfplayer.shared.presentation.AdminRequestDetails
import com.pyoungws.bluewolfplayer.shared.presentation.AdminRequestsUiState
import com.pyoungws.bluewolfplayer.shared.presentation.AdminRequestsViewModel
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrMediaStatus
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrMediaType
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrRequest
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrRequestStatus
import com.pyoungws.bluewolfplayer.ui.common.Artwork
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.formatTimestamp
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel

/** The admin approval queue: pending Seerr requests with approve, decline and a details sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRequestsScreen(
    onBack: () -> Unit,
    viewModel: AdminRequestsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    LaunchedEffect(state.action) {
        if (state.action is AdminActionStatus.Succeeded) viewModel.consumeAction()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_requests_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
                actions = { TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> LoadingBox()
                state.error != null && state.requests.isEmpty() -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
                state.requests.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.admin_requests_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.requests, key = { it.id }) { request ->
                        RequestCard(
                            request,
                            action = state.action,
                            onOpen = { viewModel.openDetails(request.id) },
                            onApprove = { viewModel.approve(request.id) },
                            onDecline = { viewModel.decline(request.id) },
                        )
                    }
                }
            }
        }
    }

    state.details?.let { details ->
        RequestDetailsDialog(
            details = details,
            action = state.action,
            onApprove = viewModel::approve,
            onDecline = viewModel::decline,
            onDismiss = viewModel::closeDetails,
        )
    }
}

@Composable
private fun RequestCard(
    request: SeerrRequest,
    action: AdminActionStatus?,
    onOpen: () -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Artwork(request.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.width(56.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    request.title ?: "#${request.tmdbId}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        stringResource(if (request.type == SeerrMediaType.Movie) R.string.request_kind_movie else R.string.request_kind_series),
                        if (request.is4k) stringResource(R.string.admin_request_4k) else null,
                        if (request.type == SeerrMediaType.Series && request.seasons.isNotEmpty())
                            stringResource(R.string.request_seasons, request.seasons.sorted().joinToString(", "))
                        else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                request.requestedByName?.let {
                    Text(
                        stringResource(R.string.admin_request_requested_by, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                request.createdAt?.let {
                    Text(
                        stringResource(R.string.admin_request_created, formatTimestamp(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ActionRow(request.id, action, onApprove, onDecline)
            }
        }
    }
}

@Composable
private fun ActionRow(requestId: Int, action: AdminActionStatus?, onApprove: () -> Unit, onDecline: () -> Unit) {
    val approveKey = "${AdminRequestsViewModel.APPROVE_KEY}$requestId"
    val declineKey = "${AdminRequestsViewModel.DECLINE_KEY}$requestId"
    val running = (action as? AdminActionStatus.Running)?.key
    val failed = action as? AdminActionStatus.Failed

    Row(verticalAlignment = Alignment.CenterVertically) {
        when (running) {
            approveKey, declineKey ->
                CircularProgressIndicator(Modifier.padding(horizontal = 8.dp).size(20.dp), strokeWidth = 2.dp)
            else -> {
                TextButton(onClick = onApprove) { Text(stringResource(R.string.admin_request_approve)) }
                TextButton(onClick = onDecline) { Text(stringResource(R.string.admin_request_decline)) }
            }
        }
    }
    if (failed != null && (failed.key == approveKey || failed.key == declineKey)) {
        Text(failed.error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RequestDetailsDialog(
    details: AdminRequestDetails,
    action: AdminActionStatus?,
    onApprove: (Int) -> Unit,
    onDecline: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = action is AdminActionStatus.Running
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.admin_request_details_title)) },
        text = {
            when (details) {
                is AdminRequestDetails.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is AdminRequestDetails.Failed ->
                    Text(details.error.message(), color = MaterialTheme.colorScheme.error)
                is AdminRequestDetails.Loaded -> RequestDetailsBody(details.request)
            }
        },
        confirmButton = {
            if (details is AdminRequestDetails.Loaded && details.request.status == SeerrRequestStatus.Pending) {
                Row {
                    TextButton(onClick = { onApprove(details.requestId) }, enabled = !busy) {
                        Text(stringResource(R.string.admin_request_approve))
                    }
                    TextButton(onClick = { onDecline(details.requestId) }, enabled = !busy) {
                        Text(stringResource(R.string.admin_request_decline))
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.home_sign_out_cancel)) } },
    )
}

@Composable
private fun RequestDetailsBody(request: SeerrRequest) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Artwork(request.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.width(72.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(request.title ?: "#${request.tmdbId}", style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        stringResource(if (request.type == SeerrMediaType.Movie) R.string.request_kind_movie else R.string.request_kind_series),
                        if (request.is4k) stringResource(R.string.admin_request_4k) else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusChip(request)
            }
        }
        if (request.type == SeerrMediaType.Series && request.seasons.isNotEmpty()) {
            DetailLine(stringResource(R.string.request_seasons, request.seasons.sorted().joinToString(", ")))
        }
        request.requestedByName?.let { DetailLine(stringResource(R.string.admin_request_requested_by, it)) }
        DetailLine(stringResource(R.string.admin_request_media_status, mediaStatusLabel(request.mediaStatus)))
        request.createdAt?.let { DetailLine(stringResource(R.string.admin_request_created, formatTimestamp(it))) }
        request.updatedAt?.let { DetailLine(stringResource(R.string.admin_request_updated, formatTimestamp(it))) }
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatusChip(request: SeerrRequest) {
    val (label, container) = when {
        request.isAvailable -> R.string.request_status_available to MaterialTheme.colorScheme.tertiaryContainer
        else -> when (request.status) {
            SeerrRequestStatus.Pending -> R.string.request_status_pending to MaterialTheme.colorScheme.secondaryContainer
            SeerrRequestStatus.Approved -> R.string.request_status_approved to MaterialTheme.colorScheme.primaryContainer
            SeerrRequestStatus.Declined -> R.string.request_status_declined to MaterialTheme.colorScheme.errorContainer
            SeerrRequestStatus.Failed -> R.string.request_status_failed to MaterialTheme.colorScheme.errorContainer
        }
    }
    Surface(color = container, shape = RoundedCornerShape(4.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun mediaStatusLabel(status: SeerrMediaStatus): String = stringResource(
    when (status) {
        SeerrMediaStatus.NotRequested -> R.string.media_status_not_requested
        SeerrMediaStatus.Unknown -> R.string.media_status_unknown
        SeerrMediaStatus.Pending -> R.string.media_status_pending
        SeerrMediaStatus.Processing -> R.string.media_status_processing
        SeerrMediaStatus.PartiallyAvailable -> R.string.media_status_partially_available
        SeerrMediaStatus.Available -> R.string.request_status_available
        SeerrMediaStatus.Deleted -> R.string.media_status_deleted
    },
)
