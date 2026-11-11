package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.model.HomeFeed
import com.pyoungws.bluewolfplayer.shared.model.Library
import com.pyoungws.bluewolfplayer.shared.presentation.LibraryHomeUiState
import com.pyoungws.bluewolfplayer.shared.presentation.LibraryHomeViewModel
import org.koin.compose.viewmodel.koinViewModel

/** TV home: Continue Watching, the user's libraries and a Recently Added row per library. */
@Composable
fun TvHomeScreen(
    onOpenLibrary: (Library) -> Unit,
    onOpenItem: (String) -> Unit,
    feedViewModel: LibraryHomeViewModel = koinViewModel(),
) {
    val state by feedViewModel.uiState.collectAsStateWithLifecycle()

    // Watch progress changes while playing, so refresh whenever the screen comes back.
    LifecycleResumeEffect(Unit) {
        feedViewModel.refresh()
        onPauseOrDispose {}
    }

    Box(Modifier.fillMaxSize()) {
        when (val current = state) {
            LibraryHomeUiState.Loading -> TvLoadingBox()
            is LibraryHomeUiState.Error -> TvErrorBox(current.error, onRetry = feedViewModel::load)
            is LibraryHomeUiState.Loaded -> TvHomeContent(current.feed, onOpenLibrary, onOpenItem)
        }
    }
}

@Composable
private fun TvHomeContent(feed: HomeFeed, onOpenLibrary: (Library) -> Unit, onOpenItem: (String) -> Unit) {
    if (feed.libraries.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.home_empty), textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        if (feed.continueWatching.isNotEmpty()) {
            item(key = "continue") {
                TvSectionTitle(stringResource(R.string.home_continue_watching))
                TvCardRow(feed.continueWatching, key = { it.id }) { TvLandscapeCard(it, onClick = { onOpenItem(it.id) }) }
            }
        }
        item(key = "libraries") {
            TvSectionTitle(stringResource(R.string.home_libraries))
            TvCardRow(feed.libraries, key = { it.id }) {
                TvLibraryCard(it.name, it.imageUrl, enabled = it.isBrowsable, onClick = { onOpenLibrary(it) })
            }
        }
        feed.recentlyAdded.forEach { row ->
            item(key = "recent-${row.library.id}") {
                TvSectionTitle(stringResource(R.string.home_recently_added, row.library.name))
                TvCardRow(row.items, key = { it.id }) { TvPosterCard(it, onClick = { onOpenItem(it.id) }) }
            }
        }
    }
}
