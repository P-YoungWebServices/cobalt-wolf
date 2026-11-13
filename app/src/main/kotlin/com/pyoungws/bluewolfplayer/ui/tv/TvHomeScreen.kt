package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.pyoungws.bluewolfplayer.shared.library.LibraryRepository
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackSettings
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackSettingsRepository
import com.pyoungws.bluewolfplayer.shared.presentation.PlaybackSettingsViewModel
import com.pyoungws.bluewolfplayer.shared.model.HomeFeed
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.pyoungws.bluewolfplayer.shared.model.Library
import com.pyoungws.bluewolfplayer.shared.presentation.LibraryHomeUiState
import com.pyoungws.bluewolfplayer.shared.presentation.LibraryHomeViewModel
import org.koin.compose.viewmodel.koinViewModel

/** TV home: Continue Watching, the user's libraries and a Recently Added row per library. */
@Composable
fun TvHomeScreen(
    onOpenLibrary: (Library) -> Unit,
    onOpenItem: (String) -> Unit,
    onPlay: (String) -> Unit,
    onWatchTogether: (String) -> Unit,
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
            is LibraryHomeUiState.Loaded -> TvHomeContent(current.feed, onOpenLibrary, onOpenItem, onPlay, onWatchTogether, feedViewModel)
        }
    }
}

@Composable
private fun TvHomeContent(
    feed: HomeFeed,
    onOpenLibrary: (Library) -> Unit,
    onOpenItem: (String) -> Unit,
    onPlay: (String) -> Unit,
    onWatchTogether: (String) -> Unit,
    feedViewModel: LibraryHomeViewModel,
) {
    if (feed.libraries.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.home_empty), textAlign = TextAlign.Center)
        }
        return
    }
    // Remote "menu" on a focused card opens the item's extra actions — same as on detail pages.
    val repository = koinInject<LibraryRepository>()
    val scope = rememberCoroutineScope()
    var menuItem by remember { mutableStateOf<MediaItem?>(null) }
    fun togglePlayed(item: MediaItem) = scope.launch {
        if (repository.setPlayed(item, !item.isPlayed) is Outcome.Success) feedViewModel.refresh()
    }
    fun toggleFavorite(item: MediaItem) = scope.launch {
        if (repository.setFavorite(item, !item.isFavorite) is Outcome.Success) feedViewModel.refresh()
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
        val n = ev.nativeKeyEvent
        if (menuItem != null && n.action == android.view.KeyEvent.ACTION_DOWN &&
            (n.keyCode == android.view.KeyEvent.KEYCODE_BACK || n.keyCode == android.view.KeyEvent.KEYCODE_MENU)) {
            menuItem = null; true
        } else false
    }) {
    val settingsRepo: PlaybackSettingsRepository = koinInject()
    val settings by settingsRepo.settings.collectAsStateWithLifecycle()
    val order = (settings.homeRowOrder + PlaybackSettingsViewModel.HOME_ROW_IDS).distinct()
        .filter { it !in settings.hiddenHomeRows }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        order.forEach { rowId ->
            when (rowId) {
                PlaybackSettings.ROW_LIBRARIES -> item(key = "libraries") {
                    TvSectionTitle(stringResource(R.string.home_libraries))
                    TvCardRow(feed.libraries, key = { it.id }) {
                        TvLibraryCard(it.name, it.imageUrl, enabled = it.isBrowsable, onClick = { onOpenLibrary(it) })
                    }
                }
                PlaybackSettings.ROW_CONTINUE -> if (feed.continueWatching.isNotEmpty()) {
                    item(key = "continue") {
                        TvSectionTitle(stringResource(R.string.home_continue_watching))
                        TvCardRow(feed.continueWatching, key = { it.id }) { it ->
                            TvLandscapeCard(it, onClick = { onOpenItem(it.id) }, onMenu = { menuItem = it })
                        }
                    }
                }
                PlaybackSettings.ROW_NEXT_UP -> if (feed.nextUp.isNotEmpty()) {
                    item(key = "nextup") {
                        TvSectionTitle(stringResource(R.string.detail_next_up))
                        TvCardRow(feed.nextUp, key = { it.id }) { it ->
                            TvLandscapeCard(it, onClick = { onOpenItem(it.id) }, onMenu = { menuItem = it })
                        }
                    }
                }
                PlaybackSettings.ROW_RESUME_AUDIO -> if (feed.resumeAudio.isNotEmpty()) {
                    item(key = "resumeaudio") {
                        TvSectionTitle(stringResource(R.string.home_resume_audio))
                        TvCardRow(feed.resumeAudio, key = { it.id }) { it ->
                            TvLandscapeCard(it, onClick = { onOpenItem(it.id) }, onMenu = { menuItem = it })
                        }
                    }
                }
                PlaybackSettings.ROW_LIVE_TV -> if (feed.liveTvChannels.isNotEmpty()) {
                    item(key = "livetv") {
                        TvSectionTitle(stringResource(R.string.home_live_tv))
                        TvCardRow(feed.liveTvChannels, key = { it.id }) { it ->
                            TvPosterCard(it, onClick = { onOpenItem(it.id) }, onMenu = { menuItem = it })
                        }
                    }
                }
                PlaybackSettings.ROW_ON_NOW -> if (feed.liveTvOnNow.isNotEmpty()) {
                    item(key = "onnow") {
                        TvSectionTitle(stringResource(R.string.home_on_now))
                        TvCardRow(feed.liveTvOnNow, key = { it.id }) { it ->
                            TvLandscapeCard(it, onClick = { onOpenItem(it.id) }, onMenu = { menuItem = it })
                        }
                    }
                }
            }
        }
        feed.recentlyAdded.forEach { row ->
            item(key = "recent-${row.library.id}") {
                TvSectionTitle(stringResource(R.string.home_recently_added, row.library.name))
                TvCardRow(row.items, key = { it.id }) { it ->
                    TvPosterCard(it, onClick = { onOpenItem(it.id) }, onMenu = { menuItem = it })
                }
            }
        }
    }
    menuItem?.let { item ->
        TvItemOptionsOverlay(
            title = item.name,
            options = buildList {
                if (item.isPlayable) add(TvMenuOption(stringResource(if (item.hasProgress) R.string.detail_resume else R.string.detail_play)) { onPlay(item.id); menuItem = null })
                if (item.isContainer && (item.unplayedCount ?: 0) > 0) add(TvMenuOption(stringResource(R.string.detail_play_first_unwatched)) {
                    menuItem = null
                    scope.launch {
                        (repository.firstUnwatchedItem(item) as? Outcome.Success)?.value?.let { onPlay(it.id) }
                    }
                })
                add(TvMenuOption(stringResource(R.string.detail_options_details)) { onOpenItem(item.id); menuItem = null })
                if (item.isPlayable) add(TvMenuOption(stringResource(R.string.syncplay_title)) { onWatchTogether(item.id); menuItem = null })
                add(TvMenuOption(stringResource(if (item.isPlayed) R.string.detail_mark_unwatched else R.string.detail_mark_watched)) { togglePlayed(item); menuItem = null })
                add(TvMenuOption(stringResource(if (item.isFavorite) R.string.detail_unfavorite else R.string.detail_favorite)) { toggleFavorite(item); menuItem = null })
            },
            onDismiss = { menuItem = null },
        )
    }
    }
}
