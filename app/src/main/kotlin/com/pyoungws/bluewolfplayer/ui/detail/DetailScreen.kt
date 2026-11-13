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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.AddToQueue
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Shuffle
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
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
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
import com.pyoungws.bluewolfplayer.ui.common.ManageMediaDialog
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
    onPlayVersion: (String, String, Int?, Int?) -> Unit = { id, _, _, _ -> onPlay(id) },
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
    onPlayVersion: (String, String, Int?, Int?) -> Unit,
    onOpenItem: (String) -> Unit,
    onWatchTogether: (String) -> Unit,
    onPlayFromBeginning: (String) -> Unit,
    onOpenPerson: (String, String) -> Unit,
) {
    val item = state.item
    val playQueue: com.pyoungws.bluewolfplayer.shared.playback.PlayQueueRepository = org.koin.compose.koinInject()
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "header") {
            Column {
                Box(Modifier.fillMaxWidth()) {
                    val options = com.pyoungws.bluewolfplayer.ui.common.rememberDisplayOptions().first
                    val image = item.backdropUrl ?: item.landscapeUrl
                    if (image != null && options.showBackdrops) {
                        AsyncImage(
                            model = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                                .then(if (options.blurBackdrops) Modifier.blur(16.dp) else Modifier),
                        )
                        if (options.blurBackdrops) {
                            Box(Modifier.fillMaxWidth().height(220.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)))
                        }
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
                // Every action is an icon so the row stays compact; SpaceBetween spreads
                // them evenly whichever subset a given item type shows.
                val detailScope = androidx.compose.runtime.rememberCoroutineScope()
                val detailRepository: com.pyoungws.bluewolfplayer.shared.library.LibraryRepository = org.koin.compose.koinInject()
                val detailAuth: com.pyoungws.bluewolfplayer.shared.auth.AuthRepository = org.koin.compose.koinInject()
                val detailIsAdmin by androidx.compose.runtime.produceState(false) {
                    value = (detailAuth.currentUser() as? com.pyoungws.bluewolfplayer.shared.auth.SignInResult.Success)?.user?.isAdministrator == true
                }
                var moreOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                var trailersOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                var manageOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                val context = androidx.compose.ui.platform.LocalContext.current
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    state.playTarget?.let { target ->
                        val playContainer: (Boolean) -> Unit = { shuffled ->
                            val playable = state.containerItems.filter { it.isPlayable }
                            if (item.isContainer && playable.size > 1) {
                                val ordered = if (shuffled) playable.shuffled() else playable
                                playQueue.playNow(ordered, ordered.indexOfFirst { it.id == target.id }.coerceAtLeast(0))
                            }
                            onPlayVersion(target.id, state.selectedMediaSourceId ?: "", state.selectedAudioStreamIndex, state.selectedSubtitleStreamIndex)
                        }
                        IconButton(onClick = { playContainer(false) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = playLabel(item, target), tint = MaterialTheme.colorScheme.primary)
                        }
                        if (target.hasProgress) {
                            IconButton(onClick = { onPlayFromBeginning(target.id) }) {
                                Icon(Icons.Outlined.Replay, contentDescription = stringResource(R.string.detail_play), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (item.isContainer && state.containerItems.count { it.isPlayable } > 1) {
                            IconButton(onClick = { playContainer(true) }) {
                                Icon(Icons.Outlined.Shuffle, contentDescription = stringResource(R.string.detail_shuffle), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
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
                        IconButton(onClick = { playQueue.playNext(target) }) {
                            Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, contentDescription = stringResource(R.string.player_play_next), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { playQueue.enqueue(target) }) {
                            Icon(Icons.Outlined.AddToQueue, contentDescription = stringResource(R.string.player_add_to_queue), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Box {
                        IconButton(onClick = { moreOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.home_more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            state.playTarget?.let { target ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.syncplay_title)) },
                                    onClick = { moreOpen = false; onWatchTogether(target.id) },
                                )
                            }
                            if (item.isPlayable) {
                                val downloads: com.pyoungws.bluewolfplayer.ui.downloads.OfflineDownloads = org.koin.compose.koinInject()
                                val jobs by downloads.jobs.collectAsStateWithLifecycle()
                                val saved by downloads.items.collectAsStateWithLifecycle()
                                val job = jobs[item.id]
                                val isSaved = saved.any { it.itemId == item.id }
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(when {
                                        job?.failed == true -> R.string.offline_failed
                                        job != null -> R.string.offline_downloading
                                        isSaved -> R.string.offline_saved
                                        else -> R.string.offline_save
                                    })) },
                                    enabled = (job == null || job.failed) && !isSaved,
                                    onClick = {
                                        (detailAuth.sessionState.value as? com.pyoungws.bluewolfplayer.shared.auth.SessionState.SignedIn)?.let { s2 ->
                                            detailScope.launch { downloads.download(s2.session.serverAddress, s2.session.accessToken, item.id, item.name) }
                                        }
                                        moreOpen = false
                                    },
                                )
                            }
                            state.previousEpisode?.let { prev ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.detail_previous_episode)) },
                                    onClick = { moreOpen = false; onPlay(prev.id) },
                                )
                            }
                            if (state.localTrailers.isNotEmpty() || item.remoteTrailers.isNotEmpty()) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.detail_play_trailers)) },
                                    onClick = { moreOpen = false; trailersOpen = true },
                                )
                            }
                            if (item.kind == MediaKind.Program && item.programInfo?.timerId == null) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.livetv_record)) },
                                    onClick = { moreOpen = false; detailScope.launch { detailRepository.recordProgram(item.id); viewModel.refresh() } },
                                )
                            }
                            if (item.kind == MediaKind.Program && item.programInfo?.seriesTimerId == null && item.programInfo?.isSeries == true) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.livetv_record_series)) },
                                    onClick = { moreOpen = false; detailScope.launch { detailRepository.recordSeries(item.id); viewModel.refresh() } },
                                )
                            }
                            if (item.kind == MediaKind.Song || item.kind == MediaKind.MusicAlbum || item.kind == MediaKind.MusicArtist) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.detail_instant_mix)) },
                                    onClick = {
                                        moreOpen = false
                                        detailScope.launch {
                                            val mix = (detailRepository.instantMix(item.id) as? com.pyoungws.bluewolfplayer.shared.model.Outcome.Success)?.value.orEmpty().filter { it.isPlayable }
                                            if (mix.isNotEmpty()) {
                                                playQueue.playNow(mix, 0)
                                                onPlay(mix.first().id)
                                            }
                                        }
                                    },
                                )
                            }
                            item.seriesId?.takeIf { item.kind == MediaKind.Episode }?.let { seriesId ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.detail_view_series)) },
                                    onClick = { moreOpen = false; onOpenItem(seriesId) },
                                )
                            }
                            if (detailIsAdmin) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.manage_title)) },
                                    onClick = { moreOpen = false; manageOpen = true },
                                )
                            }
                        }
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
                // Stream/version pickers keep their labels so the current choice is
                // visible — they sit as dropdowns below the icon bar.
                if (item.streams.any { it.kind == com.pyoungws.bluewolfplayer.shared.model.StreamKind.Audio } ||
                    item.streams.any { it.kind == com.pyoungws.bluewolfplayer.shared.model.StreamKind.Subtitle } ||
                    item.mediaSources.size > 1) {
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val audioStreams = item.streams.filter { it.kind == com.pyoungws.bluewolfplayer.shared.model.StreamKind.Audio }
                        if (audioStreams.isNotEmpty()) {
                            var audioOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { audioOpen = true }) {
                                    Text(audioStreams.firstOrNull { it.index == state.selectedAudioStreamIndex }?.describe() ?: stringResource(R.string.detail_default_audio))
                                }
                                androidx.compose.material3.DropdownMenu(expanded = audioOpen, onDismissRequest = { audioOpen = false }) {
                                    androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.detail_default)) }, onClick = { viewModel.selectAudioStream(null); audioOpen = false })
                                    audioStreams.forEach { stream ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(stream.describe()) },
                                            onClick = { viewModel.selectAudioStream(stream.index); audioOpen = false },
                                        )
                                    }
                                }
                            }
                        }
                        val subStreams = item.streams.filter { it.kind == com.pyoungws.bluewolfplayer.shared.model.StreamKind.Subtitle }
                        if (subStreams.isNotEmpty()) {
                            var subsOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { subsOpen = true }) {
                                    Text(when {
                                        state.selectedSubtitleStreamIndex == -1 -> stringResource(R.string.player_subtitles_off)
                                        else -> subStreams.firstOrNull { it.index == state.selectedSubtitleStreamIndex }?.describe() ?: stringResource(R.string.detail_default_subtitle)
                                    })
                                }
                                androidx.compose.material3.DropdownMenu(expanded = subsOpen, onDismissRequest = { subsOpen = false }) {
                                    androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.detail_default)) }, onClick = { viewModel.selectSubtitleStream(null); subsOpen = false })
                                    androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.player_subtitles_off)) }, onClick = { viewModel.selectSubtitleStream(-1); subsOpen = false })
                                    subStreams.forEach { stream ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(stream.describe()) },
                                            onClick = { viewModel.selectSubtitleStream(stream.index); subsOpen = false },
                                        )
                                    }
                                }
                            }
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
                    }
                }
                if (manageOpen) {
                    ManageMediaDialog(item, onChanged = { viewModel.refresh() }, onDismiss = { manageOpen = false })
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
        if (state.containerItems.isNotEmpty() && item.kind == MediaKind.MusicAlbum) {
            item(key = "tracks-title") { SectionTitle(stringResource(R.string.detail_contents)) }
            val songs = state.containerItems
            items(songs.size, key = { songs[it].id }) { index ->
                val song = songs[index]
                SongRow(song, index + 1, onClick = {
                    val playable = songs.filter { it.isPlayable }
                    playQueue.playNow(playable, playable.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
                    onPlay(song.id)
                })
            }
        } else if (state.containerItems.isNotEmpty()) {
            item(key = "contents") {
                SectionTitle(stringResource(if (item.kind == MediaKind.Person) R.string.detail_filmography else R.string.detail_contents))
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
        state.lyrics?.takeIf { it.lines.isNotEmpty() }?.let { lyrics ->
            item(key = "lyrics") {
                SectionTitle(stringResource(R.string.detail_lyrics))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    lyrics.lines.forEach { line -> Text(line.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun SongRow(song: MediaItem, position: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (song.trackNumber ?: position).toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(song.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(
                song.artists.firstOrNull(),
                song.runtimeMinutes?.let { stringResource(R.string.detail_runtime_minutes, it) },
            ).joinToString(" · ")
            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
