package com.pyoungws.bluewolfplayer.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.presentation.MyRequestsUiState
import com.pyoungws.bluewolfplayer.shared.presentation.MyRequestsViewModel
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrMediaType
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrRequest
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrRequestStatus
import com.pyoungws.bluewolfplayer.ui.common.Artwork
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import org.koin.compose.viewmodel.koinViewModel

/** The user's own requests with their status: pending, approved, available or declined. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRequestsScreen(onBack: () -> Unit, viewModel: MyRequestsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Statuses change on the server while the screen is away.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_requests_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                MyRequestsUiState.Loading -> LoadingBox()
                is MyRequestsUiState.Error -> ErrorBox(current.error, onRetry = viewModel::load)
                is MyRequestsUiState.Loaded -> {
                    if (current.requests.isEmpty()) {
                        Text(
                            stringResource(R.string.my_requests_empty),
                            Modifier.align(Alignment.Center).padding(24.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(current.requests, key = { it.id }) { RequestRow(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestRow(request: SeerrRequest) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
        RequestStatusChip(request)
    }
}

@Composable
private fun RequestStatusChip(request: SeerrRequest) {
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
