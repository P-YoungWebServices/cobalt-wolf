package com.pyoungws.bluewolfplayer.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.library.ItemQuery
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.presentation.ItemBrowseViewModel
import com.pyoungws.bluewolfplayer.ui.common.Artwork
import com.pyoungws.bluewolfplayer.ui.common.CardImage
import com.pyoungws.bluewolfplayer.ui.common.CardSize
import com.pyoungws.bluewolfplayer.ui.common.CardView
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.PosterCard
import com.pyoungws.bluewolfplayer.ui.common.ViewPrefs
import com.pyoungws.bluewolfplayer.ui.common.cardSubtitle
import com.pyoungws.bluewolfplayer.ui.common.cardTitle
import com.pyoungws.bluewolfplayer.ui.common.rememberViewPrefs
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SeriesStatus
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private data class SortChoice(val labelRes: Int, val sortBy: ItemSortBy, val order: SortOrder)

private val SORT_CHOICES = listOf(
    SortChoice(R.string.sort_name_asc, ItemSortBy.SORT_NAME, SortOrder.ASCENDING),
    SortChoice(R.string.sort_name_desc, ItemSortBy.SORT_NAME, SortOrder.DESCENDING),
    SortChoice(R.string.sort_date_added, ItemSortBy.DATE_CREATED, SortOrder.DESCENDING),
    SortChoice(R.string.sort_date_released, ItemSortBy.PREMIERE_DATE, SortOrder.DESCENDING),
    SortChoice(R.string.sort_rating, ItemSortBy.COMMUNITY_RATING, SortOrder.DESCENDING),
    SortChoice(R.string.sort_critic_rating, ItemSortBy.CRITIC_RATING, SortOrder.DESCENDING),
    SortChoice(R.string.sort_parental_rating, ItemSortBy.OFFICIAL_RATING, SortOrder.ASCENDING),
    SortChoice(R.string.sort_date_played, ItemSortBy.DATE_PLAYED, SortOrder.DESCENDING),
    SortChoice(R.string.sort_play_count, ItemSortBy.PLAY_COUNT, SortOrder.DESCENDING),
    SortChoice(R.string.sort_date_episode_added, ItemSortBy.DATE_LAST_CONTENT_ADDED, SortOrder.DESCENDING),
    SortChoice(R.string.sort_runtime, ItemSortBy.RUNTIME, SortOrder.DESCENDING),
    SortChoice(R.string.sort_year, ItemSortBy.PRODUCTION_YEAR, SortOrder.DESCENDING),
    SortChoice(R.string.sort_random, ItemSortBy.RANDOM, SortOrder.ASCENDING),
)

private data class FilterChoice(val labelRes: Int, val apply: (ItemQuery) -> ItemQuery)

/** Toggleable server-side filters; each flips one flag on the query. */
private fun filterChoices(query: ItemQuery) = listOf(
    FilterChoice(R.string.filter_played) { it.copy(filters = it.filters.toggle(ItemFilter.IS_PLAYED)) },
    FilterChoice(R.string.filter_resumable) { it.copy(filters = it.filters.toggle(ItemFilter.IS_RESUMABLE)) },
    FilterChoice(R.string.filter_continuing) { it.copy(seriesStatus = it.seriesStatus.toggle(SeriesStatus.CONTINUING)) },
    FilterChoice(R.string.filter_ended) { it.copy(seriesStatus = it.seriesStatus.toggle(SeriesStatus.ENDED)) },
    FilterChoice(R.string.filter_hd) { it.copy(isHd = if (it.isHd == true) null else true) },
    FilterChoice(R.string.filter_4k) { it.copy(is4k = if (it.is4k == true) null else true) },
    FilterChoice(R.string.filter_has_subtitles) { it.copy(hasSubtitles = if (it.hasSubtitles == true) null else true) },
    FilterChoice(R.string.filter_has_trailer) { it.copy(hasTrailer = if (it.hasTrailer == true) null else true) },
    FilterChoice(R.string.filter_has_extras) { it.copy(hasSpecialFeature = if (it.hasSpecialFeature == true) null else true) },
    FilterChoice(R.string.filter_theme_song) { it.copy(hasThemeSong = if (it.hasThemeSong == true) null else true) },
    FilterChoice(R.string.filter_theme_video) { it.copy(hasThemeVideo = if (it.hasThemeVideo == true) null else true) },
)

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

private fun filterActive(query: ItemQuery, index: Int): Boolean = when (index) {
    0 -> ItemFilter.IS_PLAYED in query.filters
    1 -> ItemFilter.IS_RESUMABLE in query.filters
    2 -> SeriesStatus.CONTINUING in query.seriesStatus
    3 -> SeriesStatus.ENDED in query.seriesStatus
    4 -> query.isHd == true
    5 -> query.is4k == true
    6 -> query.hasSubtitles == true
    7 -> query.hasTrailer == true
    8 -> query.hasSpecialFeature == true
    9 -> query.hasThemeSong == true
    10 -> query.hasThemeVideo == true
    else -> false
}

/** A paged grid or list of items for one [ItemQuery] with sort, filter and view controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemBrowseScreen(title: String, query: ItemQuery, onBack: () -> Unit, onOpenItem: (String) -> Unit, prefsKey: String = title) {
    val viewModel: ItemBrowseViewModel = koinViewModel(key = "browse-$query", parameters = { parametersOf(query) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val queryState by viewModel.queryState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val (viewPrefs, setViewPrefs) = rememberViewPrefs("browse:$prefsKey")
    LoadMoreEffect(gridState, listState, onLoadMore = viewModel::loadMore)

    var selected by remember { mutableStateOf(setOf<String>()) }
    val selecting = selected.isNotEmpty()
    val toggleSelect = { item: MediaItem -> selected = if (item.id in selected) selected - item.id else selected + item.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selecting) stringResource(R.string.select_count, selected.size) else title) },
                navigationIcon = {
                    TextButton(onClick = { if (selecting) selected = emptySet() else onBack() }) {
                        Text(stringResource(if (selecting) R.string.action_cancel else R.string.nav_back))
                    }
                },
                actions = { if (selecting) SelectionActions(selected, state.items, onDone = { selected = emptySet() }) else ViewMenu(viewPrefs, setViewPrefs) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BrowseControls(
                query = queryState,
                onSort = viewModel::setSort,
                onToggleUnwatched = viewModel::toggleUnwatchedOnly,
                onToggleFavorites = viewModel::toggleFavoritesOnly,
                onApplyFilter = { viewModel.updateQuery(it) },
            )
            Box(Modifier.fillMaxSize()) {
                when {
                    state.items.isEmpty() && state.isLoading -> LoadingBox()
                    state.items.isEmpty() && state.error != null -> ErrorBox(state.error!!, onRetry = viewModel::retry)
                    state.items.isEmpty() -> Text(stringResource(R.string.library_empty), modifier = Modifier.align(Alignment.Center))
                    viewPrefs.view == CardView.List -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        listItems(state.items, key = { it.id }) {
                            ItemListRow(
                                it,
                                onClick = { if (selecting) toggleSelect(it) else onOpenItem(it.id) },
                                onLongClick = { toggleSelect(it) },
                                selected = it.id in selected,
                            )
                        }
                        footerItems(state, viewModel)
                    }
                    else -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(viewPrefs.size.width),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.items, key = { it.id }) {
                            PosterCard(
                                it,
                                onClick = { if (selecting) toggleSelect(it) else onOpenItem(it.id) },
                                width = viewPrefs.size.width,
                                image = viewPrefs.image,
                                selected = it.id in selected,
                                onLongClick = { toggleSelect(it) },
                            )
                        }
                        footerGridItems(state, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemListRow(item: MediaItem, onClick: () -> Unit, onLongClick: () -> Unit = {}, selected: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(item.landscapeUrl ?: item.posterUrl, aspectRatio = 16f / 9f, modifier = Modifier.width(140.dp))
        Column(Modifier.weight(1f)) {
            Text(item.cardTitle(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.cardSubtitle()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val details = listOfNotNull(
                item.year?.toString(),
                item.runtimeMinutes?.let { stringResource(R.string.detail_runtime_minutes, it) },
                item.communityRating?.let { "★ %.1f".format(it) },
                if (item.isPlayed) stringResource(R.string.detail_watched) else null,
            ).joinToString(" · ")
            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.footerGridItems(
    state: com.pyoungws.bluewolfplayer.shared.presentation.LibraryUiState,
    viewModel: ItemBrowseViewModel,
) {
    if (state.isLoading) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
    state.error?.let { error ->
        item(span = { GridItemSpan(maxLineSpan) }) { ErrorBox(error, onRetry = viewModel::retry, modifier = Modifier.fillMaxWidth()) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.footerItems(
    state: com.pyoungws.bluewolfplayer.shared.presentation.LibraryUiState,
    viewModel: ItemBrowseViewModel,
) {
    if (state.isLoading) {
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
    state.error?.let { error ->
        item { ErrorBox(error, onRetry = viewModel::retry, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun BrowseControls(
    query: ItemQuery,
    onSort: (ItemSortBy, SortOrder) -> Unit,
    onToggleUnwatched: () -> Unit,
    onToggleFavorites: () -> Unit,
    onApplyFilter: ((ItemQuery) -> ItemQuery) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    val sortLabel = SORT_CHOICES.firstOrNull { it.sortBy == query.sortBy && it.order == query.sortOrder }?.labelRes ?: R.string.sort_name_asc
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            FilterChip(selected = true, onClick = { sortMenuOpen = true }, label = { Text(stringResource(sortLabel)) })
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                SORT_CHOICES.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(stringResource(choice.labelRes)) },
                        onClick = { sortMenuOpen = false; onSort(choice.sortBy, choice.order) },
                    )
                }
            }
        }
        FilterChip(selected = query.unwatchedOnly, onClick = onToggleUnwatched, label = { Text(stringResource(R.string.filter_unwatched)) })
        FilterChip(selected = query.favoritesOnly, onClick = onToggleFavorites, label = { Text(stringResource(R.string.filter_favorites)) })
        Box {
            FilterChip(selected = false, onClick = { filterMenuOpen = true }, label = { Text(stringResource(R.string.filter_more)) })
            DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                filterChoices(query).forEachIndexed { index, choice ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = filterActive(query, index), onCheckedChange = null)
                                Text(stringResource(choice.labelRes))
                            }
                        },
                        onClick = { onApplyFilter(choice.apply) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewMenu(prefs: ViewPrefs, onChange: (ViewPrefs) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.view_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (prefs.view == CardView.List) R.string.view_as_grid else R.string.view_as_list)) },
                onClick = { onChange(prefs.copy(view = if (prefs.view == CardView.List) CardView.Grid else CardView.List)) },
            )
            HorizontalDivider()
            CardSize.entries.forEach { size ->
                DropdownMenuItem(
                    text = { Text(stringResource(when (size) { CardSize.Small -> R.string.cards_small; CardSize.Medium -> R.string.cards_medium; CardSize.Large -> R.string.cards_large }) + if (size == prefs.size) " ✓" else "") },
                    onClick = { onChange(prefs.copy(size = size)) },
                )
            }
            HorizontalDivider()
            CardImage.entries.forEach { image ->
                DropdownMenuItem(
                    text = { Text(stringResource(when (image) { CardImage.Poster -> R.string.image_poster; CardImage.Banner -> R.string.image_banner; CardImage.Thumb -> R.string.image_thumb }) + if (image == prefs.image) " ✓" else "") },
                    onClick = { onChange(prefs.copy(image = image)) },
                )
            }
        }
    }
}

/** Requests the next page when the last few items scroll into view. */
@Composable
internal fun LoadMoreEffect(gridState: LazyGridState, listState: androidx.compose.foundation.lazy.LazyListState, onLoadMore: () -> Unit) {
    LaunchedEffect(gridState, listState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val listInfo = listState.layoutInfo
            (info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - LOAD_MORE_THRESHOLD) ||
                (listInfo.totalItemsCount > 0 && (listInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listInfo.totalItemsCount - LOAD_MORE_THRESHOLD)
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }
}

private const val LOAD_MORE_THRESHOLD = 12

/** Contextual top-bar actions while a multi-select is active. */
@Composable
private fun SelectionActions(selectedIds: Set<String>, items: List<MediaItem>, onDone: () -> Unit) {
    val repository: com.pyoungws.bluewolfplayer.shared.library.LibraryRepository = org.koin.compose.koinInject()
    val playQueue: com.pyoungws.bluewolfplayer.shared.playback.PlayQueueRepository = org.koin.compose.koinInject()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val chosen = items.filter { it.id in selectedIds }
    var menuOpen by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var playlistPicker by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { menuOpen = true }) { Text(stringResource(R.string.action_selection_menu)) }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val playable = chosen.filter { it.isPlayable }
            if (playable.isNotEmpty()) {
                DropdownMenuItem(text = { Text(stringResource(R.string.player_add_to_queue)) }, onClick = { playable.forEach(playQueue::enqueue); menuOpen = false; onDone() })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.detail_mark_watched)) }, onClick = { scope.launch { chosen.forEach { repository.setPlayed(it, true) } }; menuOpen = false; onDone() })
            DropdownMenuItem(text = { Text(stringResource(R.string.detail_mark_unwatched)) }, onClick = { scope.launch { chosen.forEach { repository.setPlayed(it, false) } }; menuOpen = false; onDone() })
            DropdownMenuItem(text = { Text(stringResource(R.string.detail_favorite)) }, onClick = { scope.launch { chosen.forEach { repository.setFavorite(it, true) } }; menuOpen = false; onDone() })
            DropdownMenuItem(text = { Text(stringResource(R.string.detail_unfavorite)) }, onClick = { scope.launch { chosen.forEach { repository.setFavorite(it, false) } }; menuOpen = false; onDone() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_add_to_playlist)) }, onClick = { menuOpen = false; playlistPicker = true })
            if (chosen.any { it.canDelete }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menuOpen = false; deleteConfirm = true })
            }
        }
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(stringResource(R.string.action_delete_count_confirm, chosen.count { it.canDelete })) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteItems(chosen.filter { it.canDelete }) }
                    deleteConfirm = false; onDone()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (playlistPicker) {
        BatchPlaylistPicker(items = chosen, onDismiss = { playlistPicker = false; onDone() })
    }
}

@Composable
private fun BatchPlaylistPicker(items: List<MediaItem>, onDismiss: () -> Unit) {
    val repository: com.pyoungws.bluewolfplayer.shared.library.LibraryRepository = org.koin.compose.koinInject()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val playlists by androidx.compose.runtime.produceState<List<MediaItem>>(emptyList()) {
        value = (repository.items(ItemQuery(includeItemKinds = setOf(com.pyoungws.bluewolfplayer.shared.model.MediaKind.Playlist), limit = 100)) as? com.pyoungws.bluewolfplayer.shared.model.Outcome.Success)?.value?.items.orEmpty()
    }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_to_playlist)) },
        text = {
            Column {
                playlists.forEach { playlist ->
                    TextButton(
                        onClick = { scope.launch { repository.addToPlaylist(playlist.id, items.map { it.id }); onDismiss() } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(playlist.name, modifier = Modifier.fillMaxWidth()) }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.action_new_playlist)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { scope.launch { if (newName.isNotBlank()) repository.createPlaylist(newName.trim(), items.map { it.id }); onDismiss() } },
                enabled = newName.isNotBlank(),
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
