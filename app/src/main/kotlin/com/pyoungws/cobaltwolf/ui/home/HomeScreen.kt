package com.pyoungws.cobaltwolf.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.playback.PlaybackSettings
import com.pyoungws.cobaltwolf.shared.playback.PlaybackSettingsRepository
import com.pyoungws.cobaltwolf.shared.presentation.PlaybackSettingsViewModel
import org.koin.compose.koinInject
import com.pyoungws.cobaltwolf.shared.model.HomeFeed
import com.pyoungws.cobaltwolf.shared.model.Library
import com.pyoungws.cobaltwolf.shared.presentation.HomeUiState
import com.pyoungws.cobaltwolf.shared.presentation.HomeViewModel
import com.pyoungws.cobaltwolf.shared.presentation.LibraryHomeUiState
import com.pyoungws.cobaltwolf.shared.presentation.LibraryHomeViewModel
import com.pyoungws.cobaltwolf.ui.common.Artwork
import com.pyoungws.cobaltwolf.ui.common.CardRow
import com.pyoungws.cobaltwolf.ui.common.ErrorBox
import com.pyoungws.cobaltwolf.ui.common.LandscapeCard
import com.pyoungws.cobaltwolf.ui.common.LoadingBox
import com.pyoungws.cobaltwolf.ui.common.PosterCard
import com.pyoungws.cobaltwolf.ui.common.SectionTitle
import org.koin.compose.viewmodel.koinViewModel

/** Home: Continue Watching, the user's libraries and a Recently Added row per library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenLibrary: (Library) -> Unit,
    onOpenItem: (String) -> Unit,
    onSearch: () -> Unit,
    onDiscover: () -> Unit,
    onCalendar: () -> Unit,
    onDashboard: () -> Unit,
    onOffline: () -> Unit,
    onFavorites: () -> Unit,
    viewModel: LibraryHomeViewModel = koinViewModel(),
    accountViewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSigningOut by accountViewModel.isSigningOut.collectAsStateWithLifecycle()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var moreMenuOpen by rememberSaveable { mutableStateOf(false) }
    var notificationsOpen by rememberSaveable { mutableStateOf(false) }
    val accountState by accountViewModel.uiState.collectAsStateWithLifecycle()
    val isAdmin = (accountState as? HomeUiState.Loaded)?.user?.isAdministrator == true

    // Watch progress changes while playing, so refresh whenever the screen comes back.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.height(56.dp),
                        )
                        Text(
                            stringResource(R.string.app_name),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // The icon vector has ~14dp of transparent padding on each side at this
                            // size — pull the title in past it so it sits next to the wolf head.
                            modifier = Modifier.offset(x = (-12).dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.home_search))
                    }
                    IconButton(onClick = onDiscover) {
                        Icon(Icons.Outlined.TravelExplore, contentDescription = stringResource(R.string.home_requests))
                    }
                    if (isSigningOut) {
                        CircularProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp).size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onCalendar) {
                            Icon(Icons.Outlined.DateRange, contentDescription = stringResource(R.string.home_calendar))
                        }
                        if (isAdmin) {
                            IconButton(onClick = { notificationsOpen = true }) {
                                Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.home_notifications))
                            }
                        }
                        Box {
                            IconButton(onClick = { moreMenuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.home_more))
                            }
                            DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                                // Servers and Playback live on the admin dashboard now.
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.home_dashboard)) },
                                    onClick = { moreMenuOpen = false; onDashboard() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.nav_favorites)) },
                                    onClick = { moreMenuOpen = false; onFavorites() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.offline_title)) },
                                    onClick = { moreMenuOpen = false; onOffline() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.home_sign_out)) },
                                    onClick = { moreMenuOpen = false; confirmSignOut = true },
                                )
                            }
                        }
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

    if (notificationsOpen) {
        com.pyoungws.cobaltwolf.ui.common.NotificationsDialog { notificationsOpen = false }
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
    val settingsRepo: PlaybackSettingsRepository = koinInject()
    val settings by settingsRepo.settings.collectAsStateWithLifecycle()
    val order = (settings.homeRowOrder + PlaybackSettingsViewModel.HOME_ROW_IDS).distinct()
        .filter { it !in settings.hiddenHomeRows }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        order.forEach { rowId ->
            when (rowId) {
                PlaybackSettings.ROW_LIBRARIES -> item(key = "libraries") {
                    Column {
                        SectionTitle(stringResource(R.string.home_libraries))
                        CardRow(feed.libraries, key = { it.id }) { LibraryCard(it, onClick = { onOpenLibrary(it) }) }
                    }
                }
                PlaybackSettings.ROW_CONTINUE -> if (feed.continueWatching.isNotEmpty()) {
                    item(key = "continue") {
                        Column {
                            SectionTitle(stringResource(R.string.home_continue_watching))
                            CardRow(feed.continueWatching, key = { it.id }) { LandscapeCard(it, onClick = { onOpenItem(it.id) }) }
                        }
                    }
                }
                PlaybackSettings.ROW_NEXT_UP -> if (feed.nextUp.isNotEmpty()) {
                    item(key = "nextup") {
                        Column {
                            SectionTitle(stringResource(R.string.detail_next_up))
                            CardRow(feed.nextUp, key = { it.id }) { LandscapeCard(it, onClick = { onOpenItem(it.id) }) }
                        }
                    }
                }
                PlaybackSettings.ROW_RESUME_AUDIO -> if (feed.resumeAudio.isNotEmpty()) {
                    item(key = "resumeaudio") {
                        Column {
                            SectionTitle(stringResource(R.string.home_resume_audio))
                            CardRow(feed.resumeAudio, key = { it.id }) { LandscapeCard(it, onClick = { onOpenItem(it.id) }) }
                        }
                    }
                }
                PlaybackSettings.ROW_LIVE_TV -> if (feed.liveTvChannels.isNotEmpty()) {
                    item(key = "livetv") {
                        Column {
                            SectionTitle(stringResource(R.string.home_live_tv))
                            CardRow(feed.liveTvChannels, key = { it.id }) { PosterCard(it, onClick = { onOpenItem(it.id) }) }
                        }
                    }
                }
                PlaybackSettings.ROW_ON_NOW -> if (feed.liveTvOnNow.isNotEmpty()) {
                    item(key = "onnow") {
                        Column {
                            SectionTitle(stringResource(R.string.home_on_now))
                            CardRow(feed.liveTvOnNow, key = { it.id }) { LandscapeCard(it, onClick = { onOpenItem(it.id) }) }
                        }
                    }
                }
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
