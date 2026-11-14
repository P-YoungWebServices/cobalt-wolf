package com.pyoungws.cobaltwolf.ui.tv

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.isTelevision
import com.pyoungws.cobaltwolf.player.PlayerActivity
import com.pyoungws.cobaltwolf.shared.library.ItemQuery
import com.pyoungws.cobaltwolf.shared.admin.NavDestination
import com.pyoungws.cobaltwolf.shared.admin.canNavigateTo
import com.pyoungws.cobaltwolf.shared.auth.SessionState
import com.pyoungws.cobaltwolf.shared.model.ServerInfo
import com.pyoungws.cobaltwolf.shared.presentation.AppViewModel
import com.pyoungws.cobaltwolf.shared.presentation.HomeUiState
import com.pyoungws.cobaltwolf.shared.presentation.HomeViewModel
import com.pyoungws.cobaltwolf.shared.presentation.LibraryHomeUiState
import com.pyoungws.cobaltwolf.shared.presentation.LibraryHomeViewModel
import com.pyoungws.cobaltwolf.supportsHomeScreenChannels
import com.pyoungws.cobaltwolf.ui.admin.AdminDashboardScreen
import com.pyoungws.cobaltwolf.ui.admin.AdminDevicesScreen
import com.pyoungws.cobaltwolf.ui.admin.AdminLibrariesScreen
import com.pyoungws.cobaltwolf.ui.admin.AdminLogsScreen
import com.pyoungws.cobaltwolf.ui.admin.AdminRequestsScreen
import com.pyoungws.cobaltwolf.ui.admin.AdminSessionsScreen
import com.pyoungws.cobaltwolf.ui.admin.AdminSystemInfoScreen
import com.pyoungws.cobaltwolf.ui.admin.AdminTasksScreen
import com.pyoungws.cobaltwolf.ui.admin.AdminUsersScreen
import com.pyoungws.cobaltwolf.ui.arrmedia.MediaDetailScreen
import com.pyoungws.cobaltwolf.ui.arrmedia.MediaManagerScreen
import com.pyoungws.cobaltwolf.ui.bazarr.BazarrScreen
import com.pyoungws.cobaltwolf.ui.navigation.Route
import com.pyoungws.cobaltwolf.ui.navigation.decodeRoute
import com.pyoungws.cobaltwolf.ui.navigation.encode
import com.pyoungws.cobaltwolf.ui.navigation.isAllowedFor
import com.pyoungws.cobaltwolf.ui.navigation.managementRoute
import com.pyoungws.cobaltwolf.ui.prowlarr.ProwlarrScreen
import com.pyoungws.cobaltwolf.ui.settings.AboutScreen
import com.pyoungws.cobaltwolf.ui.settings.PlaybackSettingsActivity
import com.pyoungws.cobaltwolf.ui.tv.channels.TvHomeChannels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

/** Root of the TV layout: session flow, then the 10-foot signed-in experience. */
@Composable
fun TvApp(initialItemId: String?, initialQuery: String? = null, appViewModel: AppViewModel = koinViewModel()) {
    val context = LocalContext.current
    val sessionState by appViewModel.sessionState.collectAsStateWithLifecycle()

    // Channels published by a previous session belong to that user; drop them on sign-out.
    LaunchedEffect(sessionState) {
        if (sessionState is SessionState.SignedOut && context.supportsHomeScreenChannels()) {
            withContext(Dispatchers.IO) { runCatching { TvHomeChannels(context).clear() } }
        }
    }

    when (val state = sessionState) {
        SessionState.Unknown -> TvLoadingBox()
        is SessionState.SignedOut -> TvSignedOutFlow(state.lastServerAddress)
        is SessionState.SignedIn -> TvSignedInNavigation(initialItemId, initialQuery)
    }
}

private val TvServerInfoSaver = androidx.compose.runtime.saveable.listSaver<ServerInfo?, String?>(
    save = { server -> server?.let { listOf(it.address, it.name, it.version, it.id) } ?: emptyList() },
    restore = { values -> values[0]?.let { ServerInfo(address = it, name = values[1], version = values[2], id = values[3]) } },
)

@Composable
private fun TvSignedOutFlow(lastServerAddress: String?) {
    var server by rememberSaveable(stateSaver = TvServerInfoSaver) {
        mutableStateOf(lastServerAddress?.let { ServerInfo(address = it, name = null, version = null, id = null) })
    }
    val current = server
    if (current == null) {
        TvServerConnectScreen(onServerValidated = { server = it })
    } else {
        TvSignInScreen(server = current, onChangeServer = { server = null })
    }
}

private enum class TvSection { Home, Search, Requests, MyRequests, Servers, Calendar }

/** Section for a saved name, tolerating entries removed in newer versions. */
private fun tvSection(name: String): TvSection =
    TvSection.entries.firstOrNull { it.name == name } ?: TvSection.Home

/** Routes that can be pushed over the current section. */
private fun decodeTvRoute(value: String): Route? = when (val route = decodeRoute(value)) {
    is Route.LibraryRoute, is Route.Detail, is Route.Person, is Route.SeerrDetail, is Route.SyncPlay, Route.About,
    Route.Dashboard, Route.Sessions, Route.AdminRequests, Route.AdminUsers, Route.AdminDevices,
    Route.AdminLibraries, Route.AdminTasks, Route.AdminLogs, Route.AdminSystem,
    Route.AdminActivityLog, Route.AdminApiKeys, Route.AdminPlugins, Route.AdminServerSettings,
    Route.MediaManager, is Route.MediaDetail, Route.ArrSettings, Route.Downloads,
    Route.Bazarr, Route.Prowlarr -> route
    else -> null
}

@Composable
private fun TvSignedInNavigation(
    initialItemId: String?,
    initialQuery: String? = null,
    feedViewModel: LibraryHomeViewModel = koinViewModel(),
    accountViewModel: HomeViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val feedState by feedViewModel.uiState.collectAsStateWithLifecycle()
    val accountState by accountViewModel.uiState.collectAsStateWithLifecycle()
    val isSigningOut by accountViewModel.isSigningOut.collectAsStateWithLifecycle()
    val user = (accountState as? HomeUiState.Loaded)?.user

    var section by rememberSaveable { mutableStateOf(TvSection.Home.name) }
    var stack by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val routes = stack.mapNotNull(::decodeTvRoute).filter { it.isAllowedFor(user) }
    val push = { route: Route -> stack = stack + route.encode() }
    val pop = { stack = stack.dropLast(1) }
    val play = { itemId: String -> context.startActivity(PlayerActivity.intent(context, itemId)) }
            val playVersion = { itemId: String, mediaSourceId: String, audioIndex: Int?, subtitleIndex: Int? -> context.startActivity(PlayerActivity.intent(context, itemId, mediaSourceId = mediaSourceId, audioStreamIndex = audioIndex, subtitleStreamIndex = subtitleIndex)) }

    // A channel or deep link opens the matching item on top of Home.
    LaunchedEffect(initialItemId) {
        initialItemId?.let { push(Route.Detail(it)) }
    }
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) section = TvSection.Search.name
    }

    // The feed that just loaded also refreshes the Android TV home screen channels.
    val feed = (feedState as? LibraryHomeUiState.Loaded)?.feed
    LaunchedEffect(feed) {
        if (feed == null) return@LaunchedEffect
        if (context.supportsHomeScreenChannels()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    TvHomeChannels(context).sync(feed, continueName = context.getString(R.string.home_continue_watching), recentName = context.getString(R.string.home_channel_recently_added))
                }.onFailure { Log.w(TAG, "Could not publish home screen channels", it) }
            }
        } else if (context.isTelevision()) {
            Log.i(TAG, "Home screen channels are not supported on this device")
        }
    }

    // Back unwinds pushed routes, then returns to Home before leaving the app.
    BackHandler(enabled = routes.isNotEmpty() || tvSection(section) != TvSection.Home) {
        if (routes.isNotEmpty()) pop() else section = TvSection.Home.name
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TvTopBar(
            section = tvSection(section),
            userName = user?.name,
            isAdmin = user?.isAdministrator == true,
            isSigningOut = isSigningOut,
            onSelect = { selected ->
                if (routes.isNotEmpty()) stack = emptyList()
                section = selected.name
            },
            onOpenRoute = push,
            onSignOut = { confirmSignOut = true },
        )
        Box(Modifier.weight(1f)) {
            // Each stack entry and section saves its remembered state under its own key while
            // covered, so going back restores scroll position instead of reloading at the top.
            // The key also keeps pushed screens in distinct composition slots, so state never
            // leaks between detail pages or across sections.
            val stateHolder = rememberSaveableStateHolder()
            val activeKey = routes.lastOrNull()?.let { "route:${routes.lastIndex}:${it.encode()}" } ?: "section:$section"
            stateHolder.SaveableStateProvider(key = activeKey) {
            when (val route = routes.lastOrNull()) {
                is Route.LibraryRoute -> if (route.library.type == com.pyoungws.cobaltwolf.shared.model.LibraryType.LiveTv) {
                    com.pyoungws.cobaltwolf.ui.livetv.LiveTvScreen(onBack = pop, onOpenItem = { push(Route.Detail(it)) })
                } else {
                    TvLibraryScreen(route.library, onOpenItem = { push(Route.Detail(it)) })
                }
                is Route.Detail -> TvDetailScreen(
                    route.itemId,
                    onPlay = play,
                    onPlayVersion = playVersion,
                    onOpenItem = { push(Route.Detail(it)) },
                    onWatchTogether = { push(Route.SyncPlay(it)) },
                    onPlayFromBeginning = { itemId -> context.startActivity(PlayerActivity.intent(context, itemId, startPositionMs = 0)) },
                    onOpenPerson = { id, _ -> push(Route.Detail(id)) },
                )
                is Route.Person -> TvItemBrowseScreen(
                    title = route.name,
                    query = ItemQuery(personIds = listOf(route.personId)),
                    onOpenItem = { push(Route.Detail(it)) },
                )
                Route.About -> AboutScreen(onBack = pop)
                is Route.SeerrDetail -> TvSeerrDetailScreen(route.type, route.tmdbId)
                is Route.SyncPlay -> TvWatchTogetherScreen(onWatch = play, queueItemId = route.itemId)
                Route.Dashboard -> AdminDashboardScreen(
                    onBack = pop,
                    onOpenItem = { push(Route.Detail(it)) },
                    onOpenManage = { destination -> managementRoute(destination)?.let(push) },
                    onOpenSessions = { push(Route.Sessions) },
                    onOpenServers = { stack = emptyList(); section = TvSection.Servers.name },
                    onOpenPlayback = { context.startActivity(PlaybackSettingsActivity.intent(context)) },
                    onAbout = { push(Route.About) },
                )
                Route.Sessions -> AdminSessionsScreen(onBack = pop)
                Route.AdminRequests -> AdminRequestsScreen(onBack = pop)
                Route.AdminUsers -> AdminUsersScreen(onBack = pop)
                Route.AdminDevices -> AdminDevicesScreen(onBack = pop)
                Route.AdminLibraries -> AdminLibrariesScreen(onBack = pop)
                Route.AdminTasks -> AdminTasksScreen(onBack = pop)
                Route.AdminLogs -> AdminLogsScreen(onBack = pop)
                Route.AdminSystem -> AdminSystemInfoScreen(onBack = pop)
                Route.AdminActivityLog -> com.pyoungws.cobaltwolf.ui.admin.AdminActivityLogScreen(onBack = pop)
                Route.AdminApiKeys -> com.pyoungws.cobaltwolf.ui.admin.AdminApiKeysScreen(onBack = pop)
                Route.AdminPlugins -> com.pyoungws.cobaltwolf.ui.admin.AdminPluginsScreen(onBack = pop)
                Route.AdminServerSettings -> com.pyoungws.cobaltwolf.ui.admin.AdminServerSettingsScreen(onBack = pop)
                Route.MediaManager -> MediaManagerScreen(
                    onBack = pop,
                    onOpenItem = { service, itemId -> push(Route.MediaDetail(service, itemId)) },
                    onSettings = { push(Route.ArrSettings) },
                )
                is Route.MediaDetail -> MediaDetailScreen(route.service, route.itemId, onBack = pop)
                Route.ArrSettings -> TvServiceSettings(onDone = pop)
                Route.Downloads -> TvDownloadsScreen()
                Route.Bazarr -> BazarrScreen(onBack = pop, onSettings = { push(Route.ArrSettings) })
                Route.Prowlarr -> ProwlarrScreen(onBack = pop, onSettings = { push(Route.ArrSettings) })
                null -> when (tvSection(section)) {
                    TvSection.Home -> TvHomeScreen(
                        feedViewModel = feedViewModel,
                        onOpenLibrary = { push(Route.LibraryRoute(it)) },
                        onOpenItem = { push(Route.Detail(it)) },
                        onPlay = play,
                        onWatchTogether = { push(Route.SyncPlay(it)) },
                    )
                    TvSection.Search -> TvSearchScreen(
                        onOpenItem = { push(Route.Detail(it)) },
                        onOpenTitle = { type, tmdbId -> push(Route.SeerrDetail(type, tmdbId)) },
                        initialQuery = initialQuery,
                    )
                    TvSection.Requests -> TvDiscoverScreen(onOpenTitle = { type, tmdbId -> push(Route.SeerrDetail(type, tmdbId)) })
                    TvSection.MyRequests -> TvMyRequestsScreen(onOpenTitle = { type, tmdbId -> push(Route.SeerrDetail(type, tmdbId)) })
                    TvSection.Servers -> TvServersScreen()
                    TvSection.Calendar -> TvCalendarScreen()
                }
                else -> Unit
            }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { androidx.compose.material3.Text(stringResource(R.string.home_sign_out_title)) },
            text = { androidx.compose.material3.Text(stringResource(R.string.home_sign_out_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    accountViewModel.signOut()
                }) { androidx.compose.material3.Text(stringResource(R.string.home_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { androidx.compose.material3.Text(stringResource(R.string.home_sign_out_cancel)) }
            },
        )
    }
}

/** Always-visible section navigation; first stop for the D-pad from any screen. */
@Composable
private fun TvTopBar(
    section: TvSection,
    userName: String?,
    isAdmin: Boolean,
    isSigningOut: Boolean,
    onSelect: (TvSection) -> Unit,
    onOpenRoute: (Route) -> Unit,
    onSignOut: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var notificationsOpen by remember { mutableStateOf(false) }
    if (notificationsOpen) {
        com.pyoungws.cobaltwolf.ui.common.NotificationsDialog { notificationsOpen = false }
    }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = TvContentPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.height(72.dp).padding(end = 8.dp),
        )
        TvNavButton(stringResource(R.string.tv_nav_home), selected = section == TvSection.Home) { onSelect(TvSection.Home) }
        TvNavButton(stringResource(R.string.home_search), selected = section == TvSection.Search) { onSelect(TvSection.Search) }
        TvNavButton(stringResource(R.string.tv_nav_seerr), selected = section == TvSection.Requests) { onSelect(TvSection.Requests) }
        TvNavButton(stringResource(R.string.home_calendar), selected = section == TvSection.Calendar) { onSelect(TvSection.Calendar) }
        Box {
            TvNavButton(stringResource(R.string.home_more), selected = section in TvMoreSections) { menuOpen = true }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                // Admin requests auto-approve, so admins have no list of their own to watch.
                if (!isAdmin) {
                    TvMenuItem(R.string.discover_my_requests) { menuOpen = false; onSelect(TvSection.MyRequests) }
                }
                // Servers and Settings live on the dashboard now.
                TvMenuItem(R.string.home_dashboard) { menuOpen = false; onOpenRoute(Route.Dashboard) }
                if (isAdmin) {
                    TvMenuItem(R.string.home_notifications) { menuOpen = false; notificationsOpen = true }
                }
                TvMenuItem(R.string.about_title) { menuOpen = false; onOpenRoute(Route.About) }
                TvMenuItem(R.string.home_sign_out) { menuOpen = false; onSignOut() }
            }
        }
        Box(Modifier.weight(1f))
        if (userName != null) {
            Text(userName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (com.pyoungws.cobaltwolf.ui.common.rememberDisplayOptions().first.clock) {
            var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(30_000)
                    now = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())
                }
            }
            Text(now, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isSigningOut) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
        }
    }
}

private val TvMoreSections = setOf(TvSection.MyRequests, TvSection.Servers)

@Composable
private fun TvMenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { androidx.compose.material3.Text(stringResource(labelRes)) },
        onClick = onClick,
    )
}

@Composable
private fun TvNavButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = androidx.tv.material3.ButtonDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
        scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1.08f),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

private const val TAG = "CobaltWolfTv"
