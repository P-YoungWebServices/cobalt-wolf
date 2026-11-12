@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import com.pyoungws.bluewolfplayer.shared.model.audioStreamSummaries
import com.pyoungws.bluewolfplayer.shared.model.episodeLabel
import com.pyoungws.bluewolfplayer.shared.model.videoStreamSummary
import com.pyoungws.bluewolfplayer.shared.presentation.ItemDetailUiState
import com.pyoungws.bluewolfplayer.shared.presentation.ItemDetailViewModel
import com.pyoungws.bluewolfplayer.ui.common.Artwork
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** TV detail: backdrop, meta and a Play/Resume button; series get a season picker and episode cards. */
@Composable
fun TvDetailScreen(itemId: String, onPlay: (String) -> Unit, onOpenItem: (String) -> Unit, onWatchTogether: (String) -> Unit) {
    val viewModel: ItemDetailViewModel = koinViewModel(key = "detail-$itemId", parameters = { parametersOf(itemId) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Box(Modifier.fillMaxSize()) {
        when (val current = state) {
            ItemDetailUiState.Loading -> TvLoadingBox()
            is ItemDetailUiState.Error -> TvErrorBox(current.error, onRetry = viewModel::load)
            is ItemDetailUiState.Loaded -> TvDetailContent(current, viewModel, onPlay, onOpenItem, onWatchTogether)
        }
    }
}

@Composable
private fun TvDetailContent(
    state: ItemDetailUiState.Loaded,
    viewModel: ItemDetailViewModel,
    onPlay: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onWatchTogether: (String) -> Unit,
) {
    val item = state.item
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item(key = "backdrop") {
            Box(Modifier.fillMaxWidth()) {
                val image = item.backdropUrl ?: item.landscapeUrl
                if (image != null) {
                    AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(260.dp))
                } else {
                    Box(Modifier.fillMaxWidth().height(100.dp))
                }
            }
        }
        item(key = "header") {
            Row(Modifier.padding(horizontal = TvContentPadding, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Artwork(item.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.width(130.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.name, style = MaterialTheme.typography.headlineMedium)
                    item.seriesName?.takeIf { item.kind == MediaKind.Episode }?.let {
                        Text(listOfNotNull(it, item.episodeLabel()).joinToString(" "), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(tvMetaLine(item), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    tvStreamLine(item)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.genres.isNotEmpty()) {
                        Text(item.genres.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item.progressFraction?.let {
            item(key = "progress") {
                LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(horizontal = TvContentPadding).height(4.dp))
            }
        }
        item(key = "actions") {
            Row(Modifier.padding(horizontal = TvContentPadding, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                state.playTarget?.let { target ->
                    Button(onClick = { onPlay(target.id) }) { Text(tvPlayLabel(item, target)) }
                    OutlinedButton(onClick = { onWatchTogether(target.id) }) { Text(stringResource(R.string.syncplay_title)) }
                }
                item.seriesId?.takeIf { item.kind == MediaKind.Episode }?.let { seriesId ->
                    OutlinedButton(onClick = { onOpenItem(seriesId) }) { Text(stringResource(R.string.detail_view_series)) }
                }
            }
        }
        item.overview?.let { overview ->
            item(key = "overview") {
                Text(overview, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = TvContentPadding))
            }
        }
        if (item.kind == MediaKind.Series) {
            item(key = "seasons") {
                TvSectionTitle(stringResource(R.string.detail_seasons))
                TvCardRow(state.seasons, key = { it.id }) { season ->
                    androidx.tv.material3.FilterChip(
                        selected = season.id == state.selectedSeasonId,
                        onClick = { viewModel.selectSeason(season.id) },
                        content = { Text(if (season.seasonNumber == 0) stringResource(R.string.detail_season_specials) else season.name) },
                    )
                }
            }
            item(key = "episodes-title") { TvSectionTitle(stringResource(R.string.detail_episodes)) }
            when {
                state.isLoadingEpisodes -> item(key = "episodes-loading") {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.episodesError != null -> item(key = "episodes-error") {
                    TvErrorBox(state.episodesError!!, onRetry = viewModel::retryEpisodes, modifier = Modifier.fillMaxWidth())
                }
                state.episodes.isEmpty() -> item(key = "episodes-empty") {
                    Text(stringResource(R.string.detail_no_episodes), modifier = Modifier.padding(horizontal = TvContentPadding))
                }
                else -> item(key = "episodes") {
                    TvCardRow(state.episodes, key = { it.id }) { episode ->
                        TvLandscapeCard(episode, onClick = { onPlay(episode.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun tvMetaLine(item: MediaItem): String = listOfNotNull(
    item.year?.toString(),
    item.officialRating,
    item.runtimeMinutes?.let { stringResource(R.string.detail_runtime_minutes, it) },
    item.communityRating?.let { "★ %.1f".format(it) },
).joinToString(" · ")

private fun tvStreamLine(item: MediaItem): String? = listOfNotNull(
    item.videoStreamSummary(),
    item.audioStreamSummaries().joinToString(" · ").takeIf { it.isNotBlank() },
).joinToString(" · ").takeIf { it.isNotBlank() }

@Composable
private fun tvPlayLabel(item: MediaItem, target: MediaItem): String = when {
    item.kind == MediaKind.Series -> stringResource(R.string.detail_play_episode, target.episodeLabel() ?: target.name)
    target.hasProgress -> stringResource(R.string.detail_resume)
    else -> stringResource(R.string.detail_play)
}
