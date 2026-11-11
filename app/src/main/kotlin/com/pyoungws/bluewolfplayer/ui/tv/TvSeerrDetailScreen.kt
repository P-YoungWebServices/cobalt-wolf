@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.tv.material3.Button
import androidx.tv.material3.Checkbox
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.presentation.RequestActionState
import com.pyoungws.bluewolfplayer.shared.presentation.SeerrDetailUiState
import com.pyoungws.bluewolfplayer.shared.presentation.SeerrDetailViewModel
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrMediaType
import com.pyoungws.bluewolfplayer.ui.common.Artwork
import com.pyoungws.bluewolfplayer.ui.discover.SeerrStatusBadge
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.Locale

/** Seerr title details on TV: season chips for series plus a request action. */
@Composable
fun TvSeerrDetailScreen(
    type: SeerrMediaType,
    tmdbId: Int,
    viewModel: SeerrDetailViewModel = koinViewModel(key = "seerr-detail-${type.name}-$tmdbId") { parametersOf(type, tmdbId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when (val current = state) {
            SeerrDetailUiState.Loading -> TvLoadingBox()
            is SeerrDetailUiState.Error -> TvErrorBox(current.error, onRetry = viewModel::load)
            is SeerrDetailUiState.Loaded -> TvSeerrContent(current, viewModel)
        }
    }
}

@Composable
private fun TvSeerrContent(state: SeerrDetailUiState.Loaded, viewModel: SeerrDetailViewModel) {
    val title = state.details.title
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(horizontal = TvContentPadding, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Artwork(title.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.width(130.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title.title, style = MaterialTheme.typography.headlineMedium)
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
            item(key = "overview") {
                Text(
                    overview,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = TvContentPadding),
                )
            }
        }
        item(key = "request-result") {
            when (val result = state.requestState) {
                is RequestActionState.Done -> Text(
                    stringResource(R.string.request_sent),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = TvContentPadding),
                )
                is RequestActionState.Failed -> Text(
                    result.error.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = TvContentPadding),
                )
                else -> Unit
            }
        }
        if (state.details.seasons.isNotEmpty()) {
            item(key = "seasons-title") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = TvContentPadding, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.detail_seasons), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    androidx.tv.material3.OutlinedButton(onClick = viewModel::selectAllSeasons) {
                        Text(stringResource(R.string.request_select_all))
                    }
                }
            }
            item(key = "seasons") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = TvContentPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.details.seasons.size, key = { state.details.seasons[it].seasonNumber }) {
                        val season = state.details.seasons[it]
                        FilterChip(
                            selected = season.seasonNumber in state.selectedSeasons,
                            onClick = { viewModel.toggleSeason(season.seasonNumber) },
                            enabled = !season.isAvailable && state.requestState !is RequestActionState.InProgress,
                            content = {
                                Text(
                                    season.name
                                        ?: if (season.seasonNumber == 0) stringResource(R.string.request_season_specials)
                                        else stringResource(R.string.request_season_label, season.seasonNumber),
                                )
                            },
                            leadingIcon = {
                                Checkbox(checked = season.seasonNumber in state.selectedSeasons, onCheckedChange = null)
                            },
                        )
                    }
                }
            }
        }
        item(key = "request") {
            Column(Modifier.padding(horizontal = TvContentPadding, vertical = 12.dp)) {
                val inProgress = state.requestState is RequestActionState.InProgress
                when {
                    title.isInLibrary -> Text(stringResource(R.string.request_available), color = MaterialTheme.colorScheme.tertiary)
                    title.isRequested -> Text(stringResource(R.string.request_already), color = MaterialTheme.colorScheme.secondary)
                    else -> {
                        Button(
                            onClick = viewModel::request,
                            enabled = !inProgress && (state.details.seasons.isEmpty() || state.selectedSeasons.isNotEmpty()),
                        ) {
                            Text(
                                if (state.details.seasons.isEmpty()) {
                                    stringResource(R.string.request_movie)
                                } else {
                                    stringResource(R.string.request_series_count, state.selectedSeasons.size)
                                },
                            )
                        }
                        if (state.details.seasons.isNotEmpty() && state.selectedSeasons.isEmpty()) {
                            Text(
                                stringResource(R.string.request_none_selected),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
