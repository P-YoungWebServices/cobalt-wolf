@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.AddToQueue
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.draw.blur
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import kotlinx.coroutines.launch
import com.pyoungws.bluewolfplayer.shared.model.detailInfoItems
import com.pyoungws.bluewolfplayer.shared.model.endTime
import com.pyoungws.bluewolfplayer.shared.model.episodeLabel
import com.pyoungws.bluewolfplayer.shared.model.infoBadges
import com.pyoungws.bluewolfplayer.shared.presentation.ItemDetailUiState
import com.pyoungws.bluewolfplayer.shared.presentation.ItemDetailViewModel
import com.pyoungws.bluewolfplayer.ui.common.Artwork
import com.pyoungws.bluewolfplayer.ui.common.ManageMediaDialog
import com.pyoungws.bluewolfplayer.ui.common.InfoBadgeRow
import com.pyoungws.bluewolfplayer.ui.common.labelRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private fun now() = kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())

/** TV detail: backdrop, meta and a Play/Resume button; series get a season picker and episode cards. */
@Composable
fun TvDetailScreen(itemId: String, onPlay: (String) -> Unit, onPlayVersion: (String, String, Int?, Int?) -> Unit = { id, _, _, _ -> onPlay(id) }, onOpenItem: (String) -> Unit, onWatchTogether: (String) -> Unit, onPlayFromBeginning: (String) -> Unit = onPlay, onOpenPerson: (String, String) -> Unit = { _, _ -> }) {
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
            is ItemDetailUiState.Loaded -> TvDetailContent(current, viewModel, onPlay, onPlayVersion, onOpenItem, onWatchTogether, onPlayFromBeginning, onOpenPerson)
        }
    }
}

@Composable
private fun TvDetailContent(
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
    val repository: com.pyoungws.bluewolfplayer.shared.library.LibraryRepository = org.koin.compose.koinInject()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var trackMenuIndex by remember { mutableStateOf<Int?>(null) }
    var episodeMenuItem by remember { mutableStateOf<MediaItem?>(null) }
    // The first thing on the page is "what can I do with this": hand focus to Play/Resume once,
    // on first load — not again when a season switch swaps the episode behind the button.
    val playFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var autoFocused by remember { mutableStateOf(false) }
    var entryReady by remember { mutableStateOf(false) }
    var actionsFocused by remember { mutableStateOf(false) }
    // autoFocused flips only on real focus success so the options-restore effect below
    // can't steal focus before the button exists. Focus is requested only once the
    // actions row is fully inside the viewport — grabbing focus while it is still
    // partially off screen makes Compose scroll the page to reveal it.
    LaunchedEffect(state.playTarget?.id) {
        if (autoFocused || state.playTarget == null) return@LaunchedEffect
        // Wait until the masthead item is placed before requesting focus — Play lives
        // inside item 0, so the focus scroll can never move past the top of the page.
        kotlinx.coroutines.withTimeoutOrNull(2000) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.any { it.key == "masthead" }
            }.first { it }
        }
        entryReady = true
        repeat(20) {
            if (runCatching { playFocusRequester.requestFocus() }.getOrDefault(false)) {
                autoFocused = true
                return@LaunchedEffect
            }
            delay(50)
        }
    }
    var optionsOpen by remember { mutableStateOf(false) }
    // Closing the options overlay can orphan focus (the focused menu node is disposed);
    // hand it back to Play/Resume so the remote never lands on nothing.
    LaunchedEffect(optionsOpen) {
        if (optionsOpen || !autoFocused) return@LaunchedEffect
        repeat(40) {
            if (runCatching { playFocusRequester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            delay(50)
        }
    }
    var subMenu by remember { mutableStateOf<TvSubMenu?>(null) }
    // Entering this page from the top bar (or any direction) lands on Play/Resume —
    // not on whichever focusable happens to sit nearest the nav item pressed. Before
    // autofocus has run, Cancel the auto-entry instead: requesting Play focus while
    // its row is still being laid out makes the list scroll it into view on open.
    // The platform TV bring-into-view spec pivots focused children to 30% of the
    // viewport, which reads as the page jumping when Play grabs focus. While focus
    // is inside the masthead (or autofocus hasn't landed yet) we swap in the
    // minimal spec so focus on the buttons never scrolls; lower content keeps the
    // normal TV pivot behavior.
    val platformBringIntoViewSpec = androidx.compose.foundation.gestures.LocalBringIntoViewSpec.current
    val minimalBringIntoViewSpec = remember { object : androidx.compose.foundation.gestures.BringIntoViewSpec {} }
    val bringIntoViewSpec = if (!autoFocused || actionsFocused) minimalBringIntoViewSpec else platformBringIntoViewSpec
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides bringIntoViewSpec,
    ) {
    Box(Modifier.fillMaxSize().focusGroup().focusProperties {
        enter = { if (entryReady) playFocusRequester else FocusRequester.Cancel }
    // onKeyEvent (bubbles up after the focused child) rather than onPreviewKeyEvent
    // (parent-first): episode cards and playlist rows claim MENU for their own
    // context menus; it only opens the page menu when nothing below consumed it.
    }.onKeyEvent { ev ->
        val n = ev.nativeKeyEvent
        when {
            n.action == android.view.KeyEvent.ACTION_DOWN &&
                n.keyCode == android.view.KeyEvent.KEYCODE_MENU -> {
                when {
                    episodeMenuItem != null -> episodeMenuItem = null
                    trackMenuIndex != null -> trackMenuIndex = null
                    else -> optionsOpen = !optionsOpen
                }
                true
            }
            optionsOpen && n.action == android.view.KeyEvent.ACTION_DOWN &&
                n.keyCode == android.view.KeyEvent.KEYCODE_BACK -> { optionsOpen = false; true }
            else -> false
        }
    }) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 48.dp)) {
        // The whole masthead — backdrop, meta and the action buttons — is ONE item so
        // that focusing Play means "bring item 0 into view": it is already at the top,
        // so the focus scroll clamps to offset 0 instead of sliding the header away.
        item(key = "masthead") {
            Box(Modifier.fillMaxWidth()) {
                // The backdrop fills the whole masthead behind the meta/actions rather than
                // being a thin strip above them — a 110dp-tall center crop only shows the
                // middle ~27% of a 16:9 image and reads as a cut-off/scrolled page.
                val options = com.pyoungws.bluewolfplayer.ui.common.rememberDisplayOptions().first
                val image = item.backdropUrl ?: item.landscapeUrl
                if (image != null && options.showBackdrops) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.matchParentSize()
                            .then(if (options.blurBackdrops) Modifier.blur(20.dp) else Modifier),
                    )
                    if (options.blurBackdrops) {
                        Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)))
                    }
                }
                // Fade the bottom of the backdrop into the page so the meta row and buttons
                // stay readable over it.
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            1f to MaterialTheme.colorScheme.surface,
                        )
                    )
                )
                Column {
                Spacer(Modifier.height(if (image != null && options.showBackdrops) 120.dp else 40.dp))
            Row(Modifier.padding(horizontal = TvContentPadding, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (item.logoUrl != null) {
                    AsyncImage(model = item.logoUrl, contentDescription = item.name, contentScale = ContentScale.Fit, modifier = Modifier.width(150.dp).height(96.dp))
                } else {
                    Artwork(item.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.width(110.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.name, style = MaterialTheme.typography.headlineMedium)
                    item.seriesName?.takeIf { item.kind == MediaKind.Episode }?.let {
                        Text(listOfNotNull(it, item.episodeLabel()).joinToString(" "), style = MaterialTheme.typography.titleMedium)
                    }
                    InfoBadgeRow(item.infoBadges())
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        item.detailInfoItems(item.endTime(now())).forEach { info ->
                            Column {
                                Text(stringResource(info.label.labelRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(info.value, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (item.genres.isNotEmpty()) {
                        Text(item.genres.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item.progressFraction?.let {
                LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(horizontal = TvContentPadding).height(4.dp))
            }
            // Focus landing here from a lower row means the user scrolled back up —
            // bring the whole masthead into view rather than stopping at the buttons.
            Row(
                Modifier
                    .padding(horizontal = TvContentPadding, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState())
                    .onFocusChanged {
                        actionsFocused = it.hasFocus
                        if (it.hasFocus && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.playTarget?.let { target ->
                    TvActionButton(
                        icon = Icons.Outlined.PlayArrow,
                        label = tvPlayLabel(item, target),
                        onClick = {
                            val playable = state.containerItems.filter { it.isPlayable }
                            if (item.isContainer && playable.size > 1) {
                                playQueue.playNow(playable, playable.indexOfFirst { it.id == target.id }.coerceAtLeast(0))
                            }
                            onPlayVersion(target.id, state.selectedMediaSourceId ?: "", state.selectedAudioStreamIndex, state.selectedSubtitleStreamIndex)
                        },
                        modifier = Modifier.focusRequester(playFocusRequester),
                    )
                    if (target.hasProgress) {
                        TvActionButton(icon = Icons.Outlined.Replay, label = stringResource(R.string.detail_play), onClick = { onPlayFromBeginning(target.id) })
                    }
                    if (item.isContainer && state.containerItems.count { it.isPlayable } > 1) {
                        TvActionButton(icon = Icons.Outlined.Shuffle, label = stringResource(R.string.detail_shuffle), onClick = {
                            val playable = state.containerItems.filter { it.isPlayable }
                            val ordered = playable.shuffled()
                            playQueue.playNow(ordered, 0)
                            onPlay(ordered.first().id)
                        })
                    }
                }
                TvActionButton(
                    icon = Icons.Outlined.Check,
                    label = stringResource(R.string.detail_watched),
                    toggled = item.isPlayed,
                    onClick = viewModel::toggleWatched,
                )
                TvActionButton(
                    icon = if (item.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    label = stringResource(R.string.detail_favorite),
                    toggled = item.isFavorite,
                    onClick = viewModel::toggleFavorite,
                )
                state.playTarget?.let { target ->
                    TvActionButton(icon = Icons.AutoMirrored.Outlined.PlaylistPlay, label = stringResource(R.string.player_play_next), onClick = { playQueue.playNext(target) })
                    TvActionButton(icon = Icons.Outlined.AddToQueue, label = stringResource(R.string.player_add_to_queue), onClick = { playQueue.enqueue(target) })
                }
                // Stream/version pickers share the row — it scrolls sideways if a
                // fully-loaded item (resume + shuffle + all three pickers) overflows.
                val audioStreams = item.streams.filter { it.kind == com.pyoungws.bluewolfplayer.shared.model.StreamKind.Audio }
                if (audioStreams.isNotEmpty()) {
                    // 48dp box matches the icon-circle height, so the chip's centre
                    // lands exactly on the circles' centre line.
                    Box(Modifier.align(Alignment.Top).height(48.dp), contentAlignment = Alignment.Center) {
                        androidx.tv.material3.FilterChip(
                            selected = false,
                            onClick = { subMenu = TvSubMenu.Audio },
                            content = { Text(audioStreams.firstOrNull { it.index == state.selectedAudioStreamIndex }?.describe() ?: stringResource(R.string.detail_default_audio)) },
                        )
                    }
                }
                val subStreams = item.streams.filter { it.kind == com.pyoungws.bluewolfplayer.shared.model.StreamKind.Subtitle }
                if (subStreams.isNotEmpty()) {
                    Box(Modifier.align(Alignment.Top).height(48.dp), contentAlignment = Alignment.Center) {
                        androidx.tv.material3.FilterChip(
                            selected = false,
                            onClick = { subMenu = TvSubMenu.Subtitles },
                            content = { Text(when {
                                state.selectedSubtitleStreamIndex == -1 -> stringResource(R.string.player_subtitles_off)
                                else -> subStreams.firstOrNull { it.index == state.selectedSubtitleStreamIndex }?.describe() ?: stringResource(R.string.detail_default_subtitle)
                            }) },
                        )
                    }
                }
                if (item.mediaSources.size > 1) {
                    Box(Modifier.align(Alignment.Top).height(48.dp), contentAlignment = Alignment.Center) {
                        androidx.tv.material3.FilterChip(
                            selected = false,
                            onClick = { subMenu = TvSubMenu.Versions },
                            content = { Text(item.mediaSources.firstOrNull { it.id == state.selectedMediaSourceId }?.name ?: stringResource(R.string.detail_select_version)) },
                        )
                    }
                }
                TvActionButton(icon = Icons.Outlined.MoreVert, label = stringResource(R.string.home_more), onClick = { optionsOpen = true })
            }
            }
            }
        }
        if (item.tagline != null || item.overview != null) {
            item(key = "overview") {
                Column(Modifier.padding(horizontal = TvContentPadding).padding(top = 12.dp)) {
                    item.tagline?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        state.nextUp?.let { next ->
            item(key = "nextup-title") { TvSectionTitle(stringResource(R.string.detail_next_up)) }
            item(key = "nextup") {
                TvCardRow(listOf(next), key = { it.id }) { ep -> TvLandscapeCard(ep, onClick = { onPlay(ep.id) }) }
            }
        }
        if (state.specialFeatures.isNotEmpty()) {
            item(key = "specials-title") { TvSectionTitle(stringResource(R.string.detail_special_features)) }
            item(key = "specials") {
                TvCardRow(state.specialFeatures, key = { it.id }) { extra -> TvLandscapeCard(extra, onClick = { onPlay(extra.id) }) }
            }
        }
        if (item.chapters.isNotEmpty()) {
            item(key = "chapters-title") { TvSectionTitle(stringResource(R.string.detail_chapters)) }
            item(key = "chapters") {
                TvCardRow(item.chapters, key = { it.startTicks }) { chapter ->
                    Column(Modifier.width(160.dp)) {
                        chapter.imageUrl?.let { CardArtwork(it, aspectRatio = 16f / 9f, modifier = Modifier.fillMaxWidth()) }
                        CardLabel(chapter.name ?: "", formatChapterTime(chapter.startTicks))
                    }
                }
            }
        }
        if (item.streams.isNotEmpty()) {
            item(key = "mediainfo-title") { TvSectionTitle(stringResource(R.string.detail_media_info)) }
            item(key = "mediainfo") {
                Column(Modifier.padding(horizontal = TvContentPadding), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item.streams.forEach { stream ->
                        Text(stream.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (item.people.isNotEmpty()) {
            item(key = "cast-title") { TvSectionTitle(stringResource(R.string.detail_cast_crew)) }
            item(key = "cast") {
                TvCardRow(item.people, key = { it.id }) { person -> TvPersonCard(person, onClick = { onOpenPerson(person.id, person.name) }) }
            }
        }
        if (state.containerItems.isNotEmpty()) {
            item(key = "contents-title") { TvSectionTitle(stringResource(R.string.detail_contents)) }
            if (item.kind == MediaKind.MusicAlbum || item.kind == MediaKind.Playlist) {
                // Track-style list: numbered rows, play on select. Playlists also get a
                // remote-menu overlay with reorder and remove.
                items(state.containerItems.size, key = { "${state.containerItems[it].id}-$it" }) { index ->
                    val entry = state.containerItems[index]
                    TvTrackRow(
                        entry, index + 1,
                        onClick = {
                            if (entry.isPlayable) {
                                val playable = state.containerItems.filter { it.isPlayable }
                                playQueue.playNow(playable, playable.indexOfFirst { it.id == entry.id }.coerceAtLeast(0))
                                onPlay(entry.id)
                            } else {
                                onOpenItem(entry.id)
                            }
                        },
                        onMenu = if (item.kind == MediaKind.Playlist) { { trackMenuIndex = index } } else null,
                    )
                }
            } else {
                item(key = "contents") {
                    TvCardRow(state.containerItems, key = { it.id }) { entry -> TvPosterCard(entry, onClick = { onOpenItem(entry.id) }) }
                }
            }
        }
        if (state.similar.isNotEmpty()) {
            item(key = "similar-title") { TvSectionTitle(stringResource(R.string.detail_more_like_this)) }
            item(key = "similar") {
                TvCardRow(state.similar, key = { it.id }) { related -> TvPosterCard(related, onClick = { onOpenItem(related.id) }) }
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
                        TvLandscapeCard(episode, onClick = { onPlay(episode.id) }, onMenu = { episodeMenuItem = episode })
                    }
                }
            }
        }
    }
    trackMenuIndex?.let { index ->
        state.containerItems.getOrNull(index)?.let { entry ->
            TvItemOptionsOverlay(
                title = entry.name,
                options = buildList {
                    add(TvMenuOption(stringResource(R.string.detail_options_details)) { onOpenItem(entry.id); trackMenuIndex = null })
                    entry.playlistItemId?.let { entryId ->
                        if (index > 0) {
                            add(TvMenuOption(stringResource(R.string.playlist_move_up)) {
                                scope.launch { repository.movePlaylistItem(item.id, entryId, index - 1); viewModel.refresh() }
                                trackMenuIndex = null
                            })
                        }
                        if (index < state.containerItems.lastIndex) {
                            add(TvMenuOption(stringResource(R.string.playlist_move_down)) {
                                scope.launch { repository.movePlaylistItem(item.id, entryId, index + 1); viewModel.refresh() }
                                trackMenuIndex = null
                            })
                        }
                        add(TvMenuOption(stringResource(R.string.playlist_remove)) {
                            scope.launch { repository.removeFromPlaylist(item.id, listOf(entryId)); viewModel.refresh() }
                            trackMenuIndex = null
                        })
                    }
                },
                onDismiss = { trackMenuIndex = null },
            )
        }
    }
    episodeMenuItem?.let { episode ->
        TvItemOptionsOverlay(
            title = episode.name,
            options = buildList {
                add(TvMenuOption(stringResource(R.string.detail_options_details)) { onOpenItem(episode.id); episodeMenuItem = null })
                if (episode.isPlayable) {
                    add(TvMenuOption(stringResource(if (episode.hasProgress) R.string.detail_resume else R.string.detail_play)) { onPlay(episode.id); episodeMenuItem = null })
                }
                add(TvMenuOption(stringResource(if (episode.isPlayed) R.string.detail_mark_unwatched else R.string.detail_mark_watched)) {
                    scope.launch { repository.setPlayed(episode, !episode.isPlayed); viewModel.refresh() }
                    episodeMenuItem = null
                })
                add(TvMenuOption(stringResource(if (episode.isFavorite) R.string.detail_unfavorite else R.string.detail_favorite)) {
                    scope.launch { repository.setFavorite(episode, !episode.isFavorite); viewModel.refresh() }
                    episodeMenuItem = null
                })
            },
            onDismiss = { episodeMenuItem = null },
        )
    }
    if (optionsOpen) {
        TvItemOptionsOverlay(
            title = item.name,
            options = detailMenuOptions(state, viewModel, onPlay, onPlayFromBeginning, onOpenItem, onWatchTogether, onManage = { optionsOpen = false; subMenu = TvSubMenu.Manage }, onPlayTrailers = { optionsOpen = false; subMenu = TvSubMenu.Trailers }) { optionsOpen = false },
            onDismiss = { optionsOpen = false },
        )
    }
    when (subMenu) {
        TvSubMenu.Versions -> TvItemOptionsOverlay(
            title = stringResource(R.string.detail_select_version),
            options = item.mediaSources.map { source ->
                TvMenuOption(source.name) { viewModel.selectMediaSource(source.id); subMenu = null }
            },
            onDismiss = { subMenu = null },
        )
        TvSubMenu.Audio -> TvItemOptionsOverlay(
            title = stringResource(R.string.detail_audio_track),
            options = listOf(TvMenuOption(stringResource(R.string.detail_default)) { viewModel.selectAudioStream(null); subMenu = null }) +
                item.streams.filter { it.kind == com.pyoungws.bluewolfplayer.shared.model.StreamKind.Audio }.map { stream ->
                    TvMenuOption(stream.describe() + if (stream.index == state.selectedAudioStreamIndex) " ✓" else "") { viewModel.selectAudioStream(stream.index); subMenu = null }
                },
            onDismiss = { subMenu = null },
        )
        TvSubMenu.Subtitles -> TvItemOptionsOverlay(
            title = stringResource(R.string.detail_subtitle_track),
            options = listOf(
                TvMenuOption(stringResource(R.string.detail_default)) { viewModel.selectSubtitleStream(null); subMenu = null },
                TvMenuOption(stringResource(R.string.player_subtitles_off)) { viewModel.selectSubtitleStream(-1); subMenu = null },
            ) + item.streams.filter { it.kind == com.pyoungws.bluewolfplayer.shared.model.StreamKind.Subtitle }.map { stream ->
                TvMenuOption(stream.describe() + if (stream.index == state.selectedSubtitleStreamIndex) " ✓" else "") { viewModel.selectSubtitleStream(stream.index); subMenu = null }
            },
            onDismiss = { subMenu = null },
        )
        TvSubMenu.Trailers -> {
            val context = LocalContext.current
            TvItemOptionsOverlay(
                title = stringResource(R.string.detail_play_trailers),
                options = state.localTrailers.map { trailer -> TvMenuOption(trailer.name) { onPlay(trailer.id); subMenu = null } } +
                    item.remoteTrailers.map { trailer ->
                        TvMenuOption(trailer.name ?: trailer.url) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(trailer.url)))
                            subMenu = null
                        }
                    },
                onDismiss = { subMenu = null },
            )
        }
        TvSubMenu.Manage -> ManageMediaDialog(item, onChanged = { viewModel.refresh() }, onDismiss = { subMenu = null })
        null -> Unit
    }
    }
    }
}

private enum class TvSubMenu { Versions, Audio, Subtitles, Trailers, Manage }

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

/** Context-menu actions for an item — the same set the reference clients expose. */
@Composable
private fun detailMenuOptions(
    state: ItemDetailUiState.Loaded,
    viewModel: ItemDetailViewModel,
    onPlay: (String) -> Unit,
    onPlayFromBeginning: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onWatchTogether: (String) -> Unit,
    onManage: () -> Unit,
    onPlayTrailers: () -> Unit,
    onDismiss: () -> Unit,
): List<TvMenuOption> {
    val item = state.item
    val target = state.playTarget
    val playQueue: com.pyoungws.bluewolfplayer.shared.playback.PlayQueueRepository = org.koin.compose.koinInject()
    return buildList {
        target?.let { t ->
            if (t.hasProgress) add(TvMenuOption(stringResource(R.string.detail_play_beginning)) { onPlayFromBeginning(t.id); onDismiss() })
            add(TvMenuOption(stringResource(R.string.syncplay_title)) { onWatchTogether(t.id); onDismiss() })
            add(TvMenuOption(stringResource(R.string.player_play_next)) { playQueue.playNext(t); onDismiss() })
            add(TvMenuOption(stringResource(R.string.player_add_to_queue)) { playQueue.enqueue(t); onDismiss() })
        }
        if (state.localTrailers.isNotEmpty() || item.remoteTrailers.isNotEmpty()) {
            add(TvMenuOption(stringResource(R.string.detail_play_trailers)) { onPlayTrailers() })
        }
        if (item.kind == MediaKind.Series && state.episodes.isNotEmpty()) {
            add(TvMenuOption(stringResource(R.string.detail_shuffle)) { onPlay(state.episodes.random().id); onDismiss() })
            state.episodes.firstOrNull { !it.isPlayed }?.let { first ->
                add(TvMenuOption(stringResource(R.string.detail_play_first_unwatched)) { onPlay(first.id); onDismiss() })
            }
        } else {
            val playable = state.containerItems.filter { it.isPlayable }
            if (item.isContainer && playable.size > 1) {
                add(TvMenuOption(stringResource(R.string.detail_shuffle)) {
                    playQueue.playNow(playable.shuffled(), 0)
                    onPlay(playQueue.queue.value.current?.item?.id ?: return@TvMenuOption)
                    onDismiss()
                })
            }
        }
        if (item.kind == MediaKind.Program) {
            val repository: com.pyoungws.bluewolfplayer.shared.library.LibraryRepository = org.koin.compose.koinInject()
            val recordScope = androidx.compose.runtime.rememberCoroutineScope()
            if (item.programInfo?.timerId == null) {
                add(TvMenuOption(stringResource(R.string.livetv_record)) { recordScope.launch { repository.recordProgram(item.id) }; onDismiss() })
            }
            if (item.programInfo?.seriesTimerId == null && item.programInfo?.isSeries == true) {
                add(TvMenuOption(stringResource(R.string.livetv_record_series)) { recordScope.launch { repository.recordSeries(item.id) }; onDismiss() })
            }
        }
        if (item.kind == MediaKind.Song || item.kind == MediaKind.MusicAlbum || item.kind == MediaKind.MusicArtist) {
            val repository: com.pyoungws.bluewolfplayer.shared.library.LibraryRepository = org.koin.compose.koinInject()
            val mixScope = androidx.compose.runtime.rememberCoroutineScope()
            add(TvMenuOption(stringResource(R.string.detail_instant_mix)) {
                mixScope.launch {
                    val mix = (repository.instantMix(item.id) as? com.pyoungws.bluewolfplayer.shared.model.Outcome.Success)?.value.orEmpty().filter { it.isPlayable }
                    if (mix.isNotEmpty()) {
                        playQueue.playNow(mix, 0)
                        onPlay(mix.first().id)
                    }
                }
                onDismiss()
            })
        }
        add(TvMenuOption(stringResource(if (item.isPlayed) R.string.detail_mark_unwatched else R.string.detail_mark_watched)) { viewModel.toggleWatched(); onDismiss() })
        add(TvMenuOption(stringResource(if (item.isFavorite) R.string.detail_unfavorite else R.string.detail_favorite)) { viewModel.toggleFavorite(); onDismiss() })
        item.seriesId?.takeIf { item.kind == MediaKind.Episode }?.let { sid ->
            add(TvMenuOption(stringResource(R.string.detail_view_series)) { onOpenItem(sid); onDismiss() })
        }
        add(TvMenuOption(stringResource(R.string.manage_title)) { onManage() })
    }
}

/** Numbered, D-pad-focusable track row for album/playlist contents. */
@Composable
private fun TvTrackRow(item: MediaItem, position: Int, onClick: () -> Unit, onMenu: (() -> Unit)?) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TvContentPadding)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White.copy(alpha = 0.15f) else Color.Transparent)
            .onKeyEvent { ev ->
                val n = ev.nativeKeyEvent
                if (onMenu != null && n.action == android.view.KeyEvent.ACTION_DOWN && n.keyCode == android.view.KeyEvent.KEYCODE_MENU) {
                    onMenu(); true
                } else false
            }
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (item.trackNumber ?: position).toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(
                item.artists.firstOrNull(),
                item.runtimeMinutes?.let { stringResource(R.string.detail_runtime_minutes, it) },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Icon-with-label action button in the same shape as the reference clients' detail actions. */
@Composable
private fun TvActionButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, toggled: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .border(2.dp, if (focused) Color.White else Color.Transparent, CircleShape)
                .clip(CircleShape)
                .background(if (toggled) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f))
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = Color.White) }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun tvPlayLabel(item: MediaItem, target: MediaItem): String = when {
    item.kind == MediaKind.Series -> stringResource(R.string.detail_play_episode, target.episodeLabel() ?: target.name)
    target.hasProgress -> stringResource(R.string.detail_resume)
    else -> stringResource(R.string.detail_play)
}
