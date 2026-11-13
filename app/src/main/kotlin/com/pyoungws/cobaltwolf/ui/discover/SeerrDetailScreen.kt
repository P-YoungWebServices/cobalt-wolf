package com.pyoungws.cobaltwolf.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.presentation.RequestActionState
import com.pyoungws.cobaltwolf.shared.presentation.SeerrDetailUiState
import com.pyoungws.cobaltwolf.shared.presentation.SeerrDetailViewModel
import com.pyoungws.cobaltwolf.shared.seerr.SeerrMediaStatus
import com.pyoungws.cobaltwolf.shared.seerr.SeerrMediaType
import com.pyoungws.cobaltwolf.shared.seerr.SeerrSeason
import com.pyoungws.cobaltwolf.ui.common.Artwork
import com.pyoungws.cobaltwolf.ui.common.ErrorBox
import com.pyoungws.cobaltwolf.ui.common.LoadingBox
import com.pyoungws.cobaltwolf.ui.message
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeerrDetailScreen(
    type: SeerrMediaType,
    tmdbId: Int,
    onBack: () -> Unit,
    viewModel: SeerrDetailViewModel = koinViewModel(key = "seerr-detail-${type.name}-$tmdbId") { parametersOf(type, tmdbId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (type) {
                            SeerrMediaType.Movie -> stringResource(R.string.request_kind_movie)
                            SeerrMediaType.Series -> stringResource(R.string.request_kind_series)
                        },
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                SeerrDetailUiState.Loading -> LoadingBox()
                is SeerrDetailUiState.Error -> ErrorBox(current.error, onRetry = viewModel::load)
                is SeerrDetailUiState.Loaded -> Content(current, viewModel)
            }
        }
    }
}

@Composable
private fun Content(state: SeerrDetailUiState.Loaded, viewModel: SeerrDetailViewModel) {
    val title = state.details.title
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Artwork(title.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.width(120.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title.title, style = MaterialTheme.typography.titleLarge)
                    listOfNotNull(
                        title.year?.toString(),
                        title.rating?.let { "★ " + String.format(Locale.US, "%.1f", it) },
                    ).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SeerrStatusBadge(title)
                }
            }
        }
        title.overview?.let { overview ->
            item {
                Text(
                    overview,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item { RequestResult(state) }
        if (state.details.seasons.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.detail_seasons), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::selectAllSeasons) { Text(stringResource(R.string.request_select_all)) }
                }
            }
            items(state.details.seasons, key = { it.seasonNumber }) { season ->
                SeasonRow(
                    season = season,
                    checked = season.seasonNumber in state.selectedSeasons,
                    enabled = !season.isAvailable && state.requestState !is RequestActionState.InProgress,
                    onToggle = { viewModel.toggleSeason(season.seasonNumber) },
                )
            }
        }
        item { RequestButton(state, viewModel) }
    }
}

@Composable
private fun SeasonRow(season: SeerrSeason, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                season.name ?: if (season.seasonNumber == 0) stringResource(R.string.request_season_specials) else stringResource(R.string.request_season_label, season.seasonNumber),
                style = MaterialTheme.typography.bodyLarge,
            )
            season.episodeCount?.let {
                Text(stringResource(R.string.request_episodes_count, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (season.status != SeerrMediaStatus.NotRequested) {
            SeerrStatusBadge(placeholderTitle(season.status))
        }
    }
}

@Composable
private fun RequestButton(state: SeerrDetailUiState.Loaded, viewModel: SeerrDetailViewModel) {
    val title = state.details.title
    val inProgress = state.requestState is RequestActionState.InProgress
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        when {
            title.isInLibrary -> Text(stringResource(R.string.request_available), color = MaterialTheme.colorScheme.tertiary)
            title.isRequested -> Text(stringResource(R.string.request_already), color = MaterialTheme.colorScheme.secondary)
            else -> {
                Button(
                    onClick = viewModel::request,
                    enabled = !inProgress && (state.details.seasons.isEmpty() || state.selectedSeasons.isNotEmpty()),
                ) {
                    if (inProgress) CircularProgressIndicator(Modifier.padding(end = 8.dp).width(18.dp).aspectRatio(1f), strokeWidth = 2.dp)
                    Text(
                        if (state.details.seasons.isEmpty()) {
                            stringResource(R.string.request_movie)
                        } else {
                            stringResource(R.string.request_series_count, state.selectedSeasons.size)
                        },
                    )
                }
                if (state.details.seasons.isNotEmpty() && state.selectedSeasons.isEmpty()) {
                    Text(stringResource(R.string.request_none_selected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun RequestResult(state: SeerrDetailUiState.Loaded) {
    when (val result = state.requestState) {
        is RequestActionState.Done -> Text(
            stringResource(R.string.request_sent),
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        is RequestActionState.Failed -> Text(
            result.error.message(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        else -> Unit
    }
}

/** Wraps a bare status so the shared badge can be reused for seasons. */
private fun placeholderTitle(status: SeerrMediaStatus) =
    com.pyoungws.cobaltwolf.shared.seerr.SeerrTitle(0, SeerrMediaType.Series, "", mediaStatus = status, requestStatus = null)
