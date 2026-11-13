package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.model.Library
import com.pyoungws.bluewolfplayer.shared.presentation.LibraryViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** A paged poster grid of one library, driven entirely by the D-pad. */
@Composable
fun TvLibraryScreen(library: Library, onOpenItem: (String) -> Unit) {
    val viewModel: LibraryViewModel = koinViewModel(key = "library-${library.id}", parameters = { parametersOf(library) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    TvLoadMoreEffect(gridState, onLoadMore = viewModel::loadMore)
    var menuItem by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.pyoungws.bluewolfplayer.shared.model.MediaItem?>(null) }

    Box(Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
        val n = ev.nativeKeyEvent
        if (menuItem != null && n.action == android.view.KeyEvent.ACTION_DOWN &&
            (n.keyCode == android.view.KeyEvent.KEYCODE_BACK || n.keyCode == android.view.KeyEvent.KEYCODE_MENU)) {
            menuItem = null; true
        } else false
    }) {
        when {
            state.items.isEmpty() && state.isLoading -> TvLoadingBox()
            state.items.isEmpty() && state.error != null -> TvErrorBox(state.error!!, onRetry = viewModel::retry)
            state.items.isEmpty() -> Text(
                stringResource(R.string.library_empty),
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = TvContentPadding, end = TvContentPadding, top = 8.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.items.size, key = { state.items[it].id }) {
                    TvPosterCard(state.items[it], onClick = { onOpenItem(state.items[it].id) }, onMenu = { menuItem = state.items[it] })
                }
                if (state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
                state.error?.let { error ->
                    item { TvErrorBox(error, onRetry = viewModel::retry, modifier = Modifier.fillMaxWidth()) }
                }
            }
        }
        menuItem?.let { TvItemMenuOverlay(it, onOpenItem, onDismiss = { menuItem = null }) }
    }
}

/** Requests the next page when the last few items scroll into view. */
@Composable
internal fun TvLoadMoreEffect(gridState: LazyGridState, onLoadMore: () -> Unit) {
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
