package com.pyoungws.bluewolfplayer.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.library.ItemQuery
import com.pyoungws.bluewolfplayer.shared.presentation.ItemBrowseViewModel
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.PosterCard
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jellyfin.sdk.model.api.ItemSortBy
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
)

/** A paged poster grid for any [ItemQuery] with sort and filter controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemBrowseScreen(title: String, query: ItemQuery, onBack: () -> Unit, onOpenItem: (String) -> Unit) {
    val viewModel: ItemBrowseViewModel = koinViewModel(key = "browse-$query", parameters = { parametersOf(query) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val queryState by viewModel.queryState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    LoadMoreEffect(gridState, onLoadMore = viewModel::loadMore)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BrowseControls(
                sortBy = queryState.sortBy,
                sortOrder = queryState.sortOrder,
                unwatchedOnly = queryState.unwatchedOnly,
                favoritesOnly = queryState.favoritesOnly,
                onSort = viewModel::setSort,
                onToggleUnwatched = viewModel::toggleUnwatchedOnly,
                onToggleFavorites = viewModel::toggleFavoritesOnly,
            )
            Box(Modifier.fillMaxSize()) {
                when {
                    state.items.isEmpty() && state.isLoading -> LoadingBox()
                    state.items.isEmpty() && state.error != null -> ErrorBox(state.error!!, onRetry = viewModel::retry)
                    state.items.isEmpty() -> Text(stringResource(R.string.library_empty), modifier = Modifier.align(Alignment.Center))
                    else -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(120.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.items, key = { it.id }) { PosterCard(it, onClick = { onOpenItem(it.id) }, width = 120.dp) }
                        if (state.isLoading) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            }
                        }
                        state.error?.let { error ->
                            item(span = { GridItemSpan(maxLineSpan) }) { ErrorBox(error, onRetry = viewModel::retry, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseControls(
    sortBy: ItemSortBy?,
    sortOrder: SortOrder,
    unwatchedOnly: Boolean,
    favoritesOnly: Boolean,
    onSort: (ItemSortBy, SortOrder) -> Unit,
    onToggleUnwatched: () -> Unit,
    onToggleFavorites: () -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    val sortLabel = SORT_CHOICES.firstOrNull { it.sortBy == sortBy && it.order == sortOrder }?.labelRes ?: R.string.sort_name_asc
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
        FilterChip(selected = unwatchedOnly, onClick = onToggleUnwatched, label = { Text(stringResource(R.string.filter_unwatched)) })
        FilterChip(selected = favoritesOnly, onClick = onToggleFavorites, label = { Text(stringResource(R.string.filter_favorites)) })
    }
}

/** Requests the next page when the last few items scroll into view. */
@Composable
internal fun LoadMoreEffect(gridState: LazyGridState, onLoadMore: () -> Unit) {
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }
}

private const val LOAD_MORE_THRESHOLD = 12
