package com.pyoungws.bluewolfplayer.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.AddToQueue
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.pyoungws.bluewolfplayer.shared.model.detailInfoItems
import com.pyoungws.bluewolfplayer.shared.model.endTime
import com.pyoungws.bluewolfplayer.shared.model.infoBadges
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import com.pyoungws.bluewolfplayer.shared.model.episodeLabel
import com.pyoungws.bluewolfplayer.shared.presentation.ItemDetailUiState
import com.pyoungws.bluewolfplayer.shared.presentation.ItemDetailViewModel
import com.pyoungws.bluewolfplayer.ui.common.Artwork
import com.pyoungws.bluewolfplayer.ui.common.InfoBadgeRow
import com.pyoungws.bluewolfplayer.ui.common.labelRes
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
    onPlayVersion: (String, String) -> Unit = { id, _ -> onPlay(id) },
    onPlayFromBeginning: (String) -> Unit = onPlay,
    onOpenItem: (String) -> Unit,
    onWatchTogether: (String) -> Unit,
    onOpenPerson: (String, String) -> Unit = { _, _ -> },
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
                is ItemDetailUiState.Loaded -> DetailContent(current, viewModel, onPlay, onPlayVersion, onOpenItem, onWatchTogether, onPlayFromBeginning, onOpenPerson)
            }
        }
    }
}

@Composable
private fun DetailContent(
    state: ItemDetailUiState.Loaded,
    viewModel: ItemDetailViewModel,
    onPlay: (String) -> Unit,
    onPlayVersion: (String, String) -> Unit,
    onOpenItem: (String) -> Unit,
    onWatchTogether: (String) -> Unit,
    onPlayFromBeginning: (String) -> Unit,
    onOpenPerson: (String, String) -> Unit,
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
                        InfoBadgeRow(item.infoBadges())
                        if (item.genres.isNotEmpty()) {
                            Text(item.genres.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        item.detailInfoItems(item.endTime(kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()))).forEach { info ->
                            Text(
                                "${stringResource(info.label.labelRes())}: ${info.value}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item.progressFraction?.let {
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(4.dp))
                }
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.playTarget?.let { target ->
                        Button(onClick = { state.selectedMediaSourceId?.let { onPlayVersion(target.id, it) } ?: onPlay(target.id) }) { Text(playLabel(item, target)) }
                        if (target.hasProgress) {
                            OutlinedButton(onClick = { onPlayFromBeginning(target.id) }) { Text(stringResource(R.string.detail_play)) }
                        }
                        OutlinedButton(onClick = { onWatchTogether(target.id) }) { Text(stringResource(R.string.syncplay_title)) }
                    }
                    if (item.mediaSources.size > 1) {
                        var versionsOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { versionsOpen = true }) {
                                Text(item.mediaSources.firstOrNull { it.id == state.selectedMediaSourceId }?.name ?: stringResource(R.string.detail_select_version))
                            }
                            androidx.compose.material3.DropdownMenu(expanded = versionsOpen, onDismissRequest = { versionsOpen = false }) {
                                item.mediaSources.forEach { source ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(source.name) },
                                        onClick = { viewModel.selectMediaSource(source.id); versionsOpen = false },
                                    )
                                }
                            }
                        }
                    }
                    if (item.isPlayable) {
                        val downloads: com.pyoungws.bluewolfplayer.ui.downloads.OfflineDownloads = org.koin.compose.koinInject()
                        val auth: com.pyoungws.bluewolfplayer.shared.auth.AuthRepository = org.koin.compose.koinInject()
                        val jobs by downloads.jobs.collectAsStateWithLifecycle()
                        val saved by downloads.items.collectAsStateWithLifecycle()
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        val job = jobs[item.id]
                        val isSaved = saved.any { it.itemId == item.id }
                        OutlinedButton(onClick = {
                            (auth.sessionState.value as? com.pyoungws.bluewolfplayer.shared.auth.SessionState.SignedIn)?.let { s2 ->
                                scope.launch { downloads.download(s2.session.serverAddress, s2.session.accessToken, item.id, item.name) }
                            }
                        }, enabled = (job == null || job.failed) && !isSaved) {
                            Text(stringResource(when {
                                job?.failed == true -> R.string.offline_failed
                                job != null -> R.string.offline_downloading
                                isSaved -> R.string.offline_saved
                                else -> R.string.offline_save
                            }))
                        }
                    }
                    state.previousEpisode?.let { prev ->
                        OutlinedButton(onClick = { onPlay(prev.id) }) { Text(stringResource(R.string.detail_previous_episode)) }
                    }
                    if (state.localTrailers.isNotEmpty() || item.remoteTrailers.isNotEmpty()) {
                        var trailersOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Box {
                            OutlinedButton(onClick = { trailersOpen = true }) { Text(stringResource(R.string.detail_play_trailers)) }
                            androidx.compose.material3.DropdownMenu(expanded = trailersOpen, onDismissRequest = { trailersOpen = false }) {
                                state.localTrailers.forEach { trailer ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(trailer.name) },
                                        onClick = { onPlay(trailer.id); trailersOpen = false },
                                    )
                                }
                                item.remoteTrailers.forEach { trailer ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(trailer.name ?: trailer.url) },
                                        onClick = {
                                            trailersOpen = false
                                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(trailer.url)))
                                        },
                                    )
                                }
                            }
                        }
                    }
                    item.seriesId?.takeIf { item.kind == MediaKind.Episode }?.let { seriesId ->
                        OutlinedButton(onClick = { onOpenItem(seriesId) }) { Text(stringResource(R.string.detail_view_series)) }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { viewModel.toggleWatched() }) {
                        Icon(
                            if (item.isPlayed) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                            contentDescription = stringResource(if (item.isPlayed) R.string.detail_mark_unwatched else R.string.detail_mark_watched),
                            tint = if (item.isPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(if (item.isFavorite) R.string.detail_unfavorite else R.string.detail_favorite),
                            tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.playTarget?.let { target ->
                        val playQueue: com.pyoungws.bluewolfplayer.shared.playback.PlayQueueRepository = org.koin.compose.koinInject()
                        IconButton(onClick = { playQueue.playNext(target) }) {
                            Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, contentDescription = stringResource(R.string.player_play_next), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { playQueue.enqueue(target) }) {
                            Icon(Icons.Outlined.AddToQueue, contentDescription = stringResource(R.string.player_add_to_queue), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item.tagline?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.padding(horizontal = 16.dp))
                }
                item.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
        }
        state.nextUp?.let { next ->
            item(key = "nextup") {
                SectionTitle(stringResource(R.string.detail_next_up))
                EpisodeRow(next, onClick = { onPlay(next.id) })
            }
        }
        if (state.specialFeatures.isNotEmpty()) {
            item(key = "specials") {
                SectionTitle(stringResource(R.string.detail_special_features))
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.specialFeatures, key = { it.id }) { extra ->
                        Column(Modifier.width(160.dp)) {
                            Artwork(extra.landscapeUrl ?: extra.posterUrl, aspectRatio = 16f / 9f, modifier = Modifier.fillMaxWidth().clickable { onPlay(extra.id) })
                            Text(extra.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        if (item.people.isNotEmpty()) {
            item(key = "cast") {
                SectionTitle(stringResource(R.string.detail_cast_crew))
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(item.people, key = { it.id }) { person ->
                        Column(Modifier.width(88.dp).clickable { onOpenPerson(person.id, person.name) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Artwork(person.imageUrl, aspectRatio = 1f, modifier = Modifier.fillMaxWidth())
                            Text(person.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            person.role?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
        if (state.containerItems.isNotEmpty()) {
            item(key = "contents") {
                SectionTitle(stringResource(R.string.detail_contents))
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.containerItems, key = { it.id }) { entry ->
                        Column(Modifier.width(120.dp)) {
                            Artwork(entry.posterUrl ?: entry.landscapeUrl, aspectRatio = 2f / 3f, modifier = Modifier.fillMaxWidth().clickable { onOpenItem(entry.id) })
                            Text(entry.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        if (item.chapters.isNotEmpty()) {
            item(key = "chapters") {
                SectionTitle(stringResource(R.string.detail_chapters))
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(item.chapters.size) { index ->
                        val chapter = item.chapters[index]
                        Column(Modifier.width(160.dp)) {
                            chapter.imageUrl?.let { Artwork(it, aspectRatio = 16f / 9f, modifier = Modifier.fillMaxWidth()) }
                            Text(
                                chapter.name ?: stringResource(R.string.detail_chapter_n, index + 1),
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Text(formatChapterTime(chapter.startTicks), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (item.streams.isNotEmpty()) {
            item(key = "mediainfo") {
                SectionTitle(stringResource(R.string.detail_media_info))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item.streams.forEach { stream ->
                        Text(
                            stream.describe(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (state.similar.isNotEmpty()) {
            item(key = "similar") {
                SectionTitle(stringResource(R.string.detail_more_like_this))
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.similar, key = { it.id }) { related ->
                        Column(Modifier.width(110.dp)) {
                            Artwork(related.posterUrl ?: related.landscapeUrl, aspectRatio = 2f / 3f, modifier = Modifier.fillMaxWidth().clickable { onOpenItem(related.id) })
                            Text(related.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
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
private fun playLabel(item: MediaItem, target: MediaItem): String = when {
    item.kind == MediaKind.Series -> stringResource(R.string.detail_play_episode, target.episodeLabel() ?: target.name)
    target.hasProgress -> stringResource(R.string.detail_resume)
    else -> stringResource(R.string.detail_play)
}

/** "h:mm:ss" style position text for a chapter start. */
private fun formatChapterTime(ticks: Long): String {
    val totalSeconds = ticks / 10_000_000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** One-line stream summary for the media info section, e.g. "Video · hevc · 3840x2160 · Dolby Vision". */
private fun com.pyoungws.bluewolfplayer.shared.model.MediaStreamInfo.describe(): String {
    val kindLabel = when (kind) {
        com.pyoungws.bluewolfplayer.shared.model.StreamKind.Video -> "Video"
        com.pyoungws.bluewolfplayer.shared.model.StreamKind.Audio -> "Audio"
        com.pyoungws.bluewolfplayer.shared.model.StreamKind.Subtitle -> "Subtitle"
    }
    return listOfNotNull(
        kindLabel,
        codec,
        if (width != null && height != null) "${width}x${height}" else null,
        videoRange,
        channelLayout ?: channels?.let { "$it ch" },
        profile,
        language,
    ).joinToString(" · ")
}
