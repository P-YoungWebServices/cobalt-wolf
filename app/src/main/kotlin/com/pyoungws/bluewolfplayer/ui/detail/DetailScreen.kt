package com.pyoungws.bluewolfplayer.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.SectionTitle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Detail for a movie, series (with seasons and episodes) or episode. [onPlay] starts the player for an item id. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onWatchTogether: (String) -> Unit,
) {
    val viewModel: ItemDetailViewModel = koinViewModel(key = "detail-$itemId", parameters = { parametersOf(itemId) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                ItemDetailUiState.Loading -> LoadingBox()
                is ItemDetailUiState.Error -> ErrorBox(current.error, onRetry = viewModel::load)
                is ItemDetailUiState.Loaded -> DetailContent(current, viewModel, onPlay, onOpenItem, onWatchTogether)
            }
        }
    }
}

@Composable
private fun DetailContent(
    state: ItemDetailUiState.Loaded,
    viewModel: ItemDetailViewModel,
    onPlay: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onWatchTogether: (String) -> Unit,
) {
    val item = state.item
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "header") {
            Column {
                Box(Modifier.fillMaxWidth()) {
                    val image = item.backdropUrl ?: item.landscapeUrl
                    if (image != null) {
                        AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(220.dp))
                    } else {
                        Box(Modifier.fillMaxWidth().height(120.dp))
                    }
                }
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Artwork(item.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.width(110.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, style = MaterialTheme.typography.headlineSmall)
                        item.seriesName?.takeIf { item.kind == MediaKind.Episode }?.let {
                            Text(listOfNotNull(it, item.episodeLabel()).joinToString(" "), style = MaterialTheme.typography.titleSmall)
                        }
                        Text(metaLine(item), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        streamLine(item)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (item.genres.isNotEmpty()) {
                            Text(item.genres.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item.progressFraction?.let {
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(4.dp))
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.playTarget?.let { target ->
                        Button(onClick = { onPlay(target.id) }) { Text(playLabel(item, target)) }
                        OutlinedButton(onClick = { onWatchTogether(target.id) }) { Text(stringResource(R.string.syncplay_title)) }
                    }
                    item.seriesId?.takeIf { item.kind == MediaKind.Episode }?.let { seriesId ->
                        OutlinedButton(onClick = { onOpenItem(seriesId) }) { Text(stringResource(R.string.detail_view_series)) }
                    }
                }
                item.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
        }
        if (item.kind == MediaKind.Series) seriesSection(state, viewModel, onPlay)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.seriesSection(
    state: ItemDetailUiState.Loaded,
    viewModel: ItemDetailViewModel,
    onPlay: (String) -> Unit,
) {
    item(key = "seasons") {
        Column {
            SectionTitle(stringResource(R.string.detail_seasons))
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.seasons, key = { it.id }) { season ->
                    FilterChip(
                        selected = season.id == state.selectedSeasonId,
                        onClick = { viewModel.selectSeason(season.id) },
                        label = { Text(if (season.seasonNumber == 0) stringResource(R.string.detail_season_specials) else season.name) },
                    )
                }
            }
            SectionTitle(stringResource(R.string.detail_episodes))
        }
    }
    when {
        state.isLoadingEpisodes -> item(key = "episodes-loading") { LoadingBox(Modifier.fillMaxWidth().height(120.dp)) }
        state.episodesError != null -> item(key = "episodes-error") {
            ErrorBox(state.episodesError!!, onRetry = viewModel::retryEpisodes, modifier = Modifier.fillMaxWidth())
        }
        state.episodes.isEmpty() -> item(key = "episodes-empty") {
            Text(stringResource(R.string.detail_no_episodes), modifier = Modifier.padding(horizontal = 16.dp))
        }
        else -> items(state.episodes, key = { it.id }) { EpisodeRow(it, onClick = { onPlay(it.id) }) }
    }
}

@Composable
private fun EpisodeRow(episode: MediaItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(140.dp)) {
            Artwork(episode.landscapeUrl, aspectRatio = 16f / 9f, modifier = Modifier.fillMaxWidth())
            episode.progressFraction?.let {
                LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(episode.episodeNumber?.toString(), episode.name).joinToString(". "),
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                episode.runtimeMinutes?.let { stringResource(R.string.detail_runtime_minutes, it) },
                if (episode.isPlayed) stringResource(R.string.detail_watched) else null,
            ).joinToString(" · ")
            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            episode.overview?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun metaLine(item: MediaItem): String = listOfNotNull(
    item.year?.toString(),
    item.officialRating,
    item.runtimeMinutes?.let { stringResource(R.string.detail_runtime_minutes, it) },
    item.communityRating?.let { "★ %.1f".format(it) },
).joinToString(" · ")

private fun streamLine(item: MediaItem): String? = listOfNotNull(
    item.videoStreamSummary(),
    item.audioStreamSummaries().joinToString(" · ").takeIf { it.isNotBlank() },
).joinToString(" · ").takeIf { it.isNotBlank() }

@Composable
private fun playLabel(item: MediaItem, target: MediaItem): String = when {
    item.kind == MediaKind.Series -> stringResource(R.string.detail_play_episode, target.episodeLabel() ?: target.name)
    target.hasProgress -> stringResource(R.string.detail_resume)
    else -> stringResource(R.string.detail_play)
}
