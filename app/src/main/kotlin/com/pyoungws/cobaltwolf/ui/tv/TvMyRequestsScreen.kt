package com.pyoungws.cobaltwolf.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.tv.material3.Border
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.presentation.MyRequestsUiState
import com.pyoungws.cobaltwolf.shared.presentation.MyRequestsViewModel
import com.pyoungws.cobaltwolf.shared.seerr.SeerrMediaType
import com.pyoungws.cobaltwolf.shared.seerr.SeerrRequest
import com.pyoungws.cobaltwolf.shared.seerr.SeerrRequestStatus
import com.pyoungws.cobaltwolf.ui.common.Artwork
import org.koin.compose.viewmodel.koinViewModel

/** The user's own Seerr requests with their status; selecting one opens the title. */
@Composable
fun TvMyRequestsScreen(
    onOpenTitle: (SeerrMediaType, Int) -> Unit,
    viewModel: MyRequestsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Statuses change on the server while the screen is away.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Box(Modifier.fillMaxSize()) {
        when (val current = state) {
            MyRequestsUiState.Loading -> TvLoadingBox()
            is MyRequestsUiState.Error -> TvErrorBox(current.error, onRetry = viewModel::load)
            is MyRequestsUiState.Loaded -> {
                if (current.requests.isEmpty()) {
                    Text(
                        stringResource(R.string.my_requests_empty),
                        Modifier.align(Alignment.Center).padding(24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = TvContentPadding, end = TvContentPadding, top = 8.dp, bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(current.requests.size, key = { current.requests[it].id }) {
                            val request = current.requests[it]
                            TvRequestRow(request, onClick = { onOpenTitle(request.type, request.tmdbId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvRequestRow(request: SeerrRequest, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    androidx.tv.material3.Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.tv.material3.CardDefaults.shape(shape),
        border = androidx.tv.material3.CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
        scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(request.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.width(56.dp))
            Column(Modifier.weight(1f)) {
                Text(request.title ?: "#${request.tmdbId}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (request.type == SeerrMediaType.Movie) R.string.request_kind_movie else R.string.request_kind_series),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (request.type == SeerrMediaType.Series && request.seasons.isNotEmpty()) {
                    Text(
                        stringResource(R.string.request_seasons, request.seasons.sorted().joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TvRequestStatusChip(request)
        }
    }
}

@Composable
private fun TvRequestStatusChip(request: SeerrRequest) {
    val label = when {
        request.isAvailable -> R.string.request_status_available
        else -> when (request.status) {
            SeerrRequestStatus.Pending -> R.string.request_status_pending
            SeerrRequestStatus.Approved -> R.string.request_status_approved
            SeerrRequestStatus.Declined -> R.string.request_status_declined
            SeerrRequestStatus.Failed -> R.string.request_status_failed
        }
    }
    Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
}
