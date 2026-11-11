package com.pyoungws.bluewolfplayer.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.model.HomeFeed
import com.pyoungws.bluewolfplayer.shared.model.Library
import com.pyoungws.bluewolfplayer.shared.presentation.HomeViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.LibraryHomeUiState
import com.pyoungws.bluewolfplayer.shared.presentation.LibraryHomeViewModel
import com.pyoungws.bluewolfplayer.ui.common.Artwork
import com.pyoungws.bluewolfplayer.ui.common.CardRow
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LandscapeCard
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.PosterCard
import com.pyoungws.bluewolfplayer.ui.common.SectionTitle
import org.koin.compose.viewmodel.koinViewModel

/** Home: Continue Watching, the user's libraries and a Recently Added row per library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenLibrary: (Library) -> Unit,
    onOpenItem: (String) -> Unit,
    onSearch: () -> Unit,
    onDiscover: () -> Unit,
    onWatchTogether: () -> Unit,
    onServers: () -> Unit,
    onCalendar: () -> Unit,
    onPlaybackSettings: () -> Unit,
    isAdmin: Boolean,
    onDashboard: () -> Unit,
    viewModel: LibraryHomeViewModel = koinViewModel(),
    accountViewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSigningOut by accountViewModel.isSigningOut.collectAsStateWithLifecycle()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    // Watch progress changes while playing, so refresh whenever the screen comes back.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onSearch) { Text(stringResource(R.string.home_search)) }
                    TextButton(onClick = onDiscover) { Text(stringResource(R.string.home_requests)) }
                    TextButton(onClick = onWatchTogether) { Text(stringResource(R.string.home_watch_together)) }
                    TextButton(onClick = onServers) { Text(stringResource(R.string.home_servers)) }
                    TextButton(onClick = onCalendar) { Text(stringResource(R.string.home_calendar)) }
                    TextButton(onClick = onPlaybackSettings) { Text(stringResource(R.string.home_playback_settings)) }
                    if (isAdmin) {
                        TextButton(onClick = onDashboard) { Text(stringResource(R.string.home_dashboard)) }
                    }
                    if (isSigningOut) {
                        CircularProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp).size(20.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { confirmSignOut = true }) { Text(stringResource(R.string.home_sign_out)) }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                LibraryHomeUiState.Loading -> LoadingBox()
                is LibraryHomeUiState.Error -> ErrorBox(current.error, onRetry = viewModel::load)
                is LibraryHomeUiState.Loaded -> HomeFeedContent(current.feed, onOpenLibrary, onOpenItem)
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.home_sign_out_title)) },
            text = { Text(stringResource(R.string.home_sign_out_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    accountViewModel.signOut()
                }) { Text(stringResource(R.string.home_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.home_sign_out_cancel)) }
            },
        )
    }
}

@Composable
private fun HomeFeedContent(feed: HomeFeed, onOpenLibrary: (Library) -> Unit, onOpenItem: (String) -> Unit) {
    if (feed.libraries.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.home_empty), textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (feed.continueWatching.isNotEmpty()) {
            item(key = "continue") {
                Column {
                    SectionTitle(stringResource(R.string.home_continue_watching))
                    CardRow(feed.continueWatching, key = { it.id }) { LandscapeCard(it, onClick = { onOpenItem(it.id) }) }
                }
            }
        }
        item(key = "libraries") {
            Column {
                SectionTitle(stringResource(R.string.home_libraries))
                CardRow(feed.libraries, key = { it.id }) { LibraryCard(it, onClick = { onOpenLibrary(it) }) }
            }
        }
        items(feed.recentlyAdded, key = { "recent-${it.library.id}" }) { row ->
            Column {
                SectionTitle(stringResource(R.string.home_recently_added, row.library.name))
                CardRow(row.items, key = { it.id }) { PosterCard(it, onClick = { onOpenItem(it.id) }) }
            }
        }
    }
}

@Composable
private fun LibraryCard(library: Library, onClick: () -> Unit) {
    Column(
        Modifier.size(width = 200.dp, height = 140.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = library.isBrowsable, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Artwork(library.imageUrl, aspectRatio = 16f / 9f, modifier = Modifier.fillMaxSize())
        }
        Text(library.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        if (!library.isBrowsable) {
            Text(stringResource(R.string.library_unsupported), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
