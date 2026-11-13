package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.presentation.SearchViewModel
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrMediaType
import com.pyoungws.bluewolfplayer.ui.common.cardTitle
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel

/**
 * Search across movies, series and episodes. The field opens the TV on-screen keyboard;
 * results are a poster grid below it.
 */
@Composable
fun TvSearchScreen(
    onOpenItem: (String) -> Unit,
    onOpenTitle: (SeerrMediaType, Int) -> Unit,
    initialQuery: String? = null,
    viewModel: SearchViewModel = koinViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) viewModel.onQueryChange(initialQuery)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        TvSearchField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { androidx.compose.material3.Text(stringResource(R.string.search_hint)) },
            modifier = Modifier
                .padding(horizontal = TvContentPadding, vertical = 8.dp)
                .focusRequester(focusRequester),
        )
        Box(Modifier.fillMaxSize()) {
            when {
                state.error != null -> TvErrorBox(state.error!!, onRetry = null)
                state.isSearching && state.results.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { CircularProgressIndicator() }
                state.showNoResults ->
                    Text(stringResource(R.string.search_no_results), Modifier.align(Alignment.TopCenter).padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.query.isBlank() ->
                    Text(stringResource(R.string.search_prompt), Modifier.align(Alignment.TopCenter).padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(start = TvContentPadding, end = TvContentPadding, top = 8.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.results.isNotEmpty()) {
                        item(key = "header-local", span = { GridItemSpan(maxLineSpan) }) {
                            TvSectionTitle(stringResource(R.string.search_on_server))
                        }
                        items(state.results.size, key = { state.results[it].id }) {
                            TvPosterCard(state.results[it], onClick = { onOpenItem(state.results[it].id) })
                        }
                    }
                    if (state.seerrResults.isNotEmpty() || state.seerrError != null) {
                        item(key = "header-seerr", span = { GridItemSpan(maxLineSpan) }) {
                            TvSectionTitle(stringResource(R.string.search_on_seerr))
                        }
                        items(state.seerrResults.size, key = { "seerr-${state.seerrResults[it].type}-${state.seerrResults[it].tmdbId}" }) {
                            val title = state.seerrResults[it]
                            TvSeerrCard(title, onClick = { onOpenTitle(title.type, title.tmdbId) })
                        }
                        state.seerrError?.let { error ->
                            item(key = "seerr-error", span = { GridItemSpan(maxLineSpan) }) {
                                Text(error.message(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
