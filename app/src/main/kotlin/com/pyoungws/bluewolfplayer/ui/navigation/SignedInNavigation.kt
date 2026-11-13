package com.pyoungws.bluewolfplayer.ui.navigation

import androidx.compose.ui.res.stringResource
import com.pyoungws.bluewolfplayer.R

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.player.PlayerActivity
import com.pyoungws.bluewolfplayer.shared.admin.NavDestination
import com.pyoungws.bluewolfplayer.shared.admin.canNavigateTo
import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.library.ItemQuery
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import com.pyoungws.bluewolfplayer.shared.model.Library
import com.pyoungws.bluewolfplayer.shared.model.LibraryType
import com.pyoungws.bluewolfplayer.shared.model.UserProfile
import com.pyoungws.bluewolfplayer.shared.presentation.HomeUiState
import com.pyoungws.bluewolfplayer.shared.presentation.HomeViewModel
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrMediaType
import com.pyoungws.bluewolfplayer.ui.admin.AdminDashboardScreen
import com.pyoungws.bluewolfplayer.ui.admin.AdminDevicesScreen
import com.pyoungws.bluewolfplayer.ui.admin.AdminLibrariesScreen
import com.pyoungws.bluewolfplayer.ui.admin.AdminLogsScreen
import com.pyoungws.bluewolfplayer.ui.admin.AdminRequestsScreen
import com.pyoungws.bluewolfplayer.ui.admin.AdminSessionsScreen
import com.pyoungws.bluewolfplayer.ui.admin.AdminSystemInfoScreen
import com.pyoungws.bluewolfplayer.ui.admin.AdminTasksScreen
import com.pyoungws.bluewolfplayer.ui.admin.AdminUsersScreen
import com.pyoungws.bluewolfplayer.ui.arrmedia.MediaDetailScreen
import com.pyoungws.bluewolfplayer.ui.arrmedia.MediaManagerScreen
import com.pyoungws.bluewolfplayer.ui.bazarr.BazarrScreen
import com.pyoungws.bluewolfplayer.ui.detail.DetailScreen
import com.pyoungws.bluewolfplayer.ui.discover.DiscoverScreen
import com.pyoungws.bluewolfplayer.ui.discover.MyRequestsScreen
import com.pyoungws.bluewolfplayer.ui.discover.SeerrDetailScreen
import com.pyoungws.bluewolfplayer.ui.home.HomeScreen
import com.pyoungws.bluewolfplayer.ui.library.ItemBrowseScreen
import com.pyoungws.bluewolfplayer.ui.library.LibraryScreen
import com.pyoungws.bluewolfplayer.ui.prowlarr.ProwlarrScreen
import com.pyoungws.bluewolfplayer.ui.search.SearchScreen
import com.pyoungws.bluewolfplayer.ui.calendar.ArrSettingsScreen
import com.pyoungws.bluewolfplayer.ui.calendar.CalendarScreen
import com.pyoungws.bluewolfplayer.ui.downloads.DownloadsScreen
import com.pyoungws.bluewolfplayer.ui.downloads.OfflineScreen
import com.pyoungws.bluewolfplayer.ui.downloads.OfflineDownloads
import com.pyoungws.bluewolfplayer.ui.servers.ServersScreen
import com.pyoungws.bluewolfplayer.ui.settings.AboutScreen
import com.pyoungws.bluewolfplayer.ui.settings.PlaybackSettingsActivity
import com.pyoungws.bluewolfplayer.ui.syncplay.WatchTogetherScreen
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Screens above the home screen. Encoded as strings so the back stack survives process death. */
internal sealed interface Route {
    data object Search : Route
    data object Discover : Route
    data object MyRequests : Route
    /** The group's item to queue once inside a group; null for the plain group browser. */
    data class SyncPlay(val itemId: String? = null) : Route
    data object Servers : Route
    data object Calendar : Route
    data object About : Route
    data object ArrSettings : Route
    data object Downloads : Route
    data object Offline : Route
    data object Favorites : Route
    data object Bazarr : Route
    data object Prowlarr : Route
    data object Dashboard : Route
    data object AdminRequests : Route
    data object AdminUsers : Route
    data object AdminDevices : Route
    data object AdminLibraries : Route
    data object AdminTasks : Route
    data object AdminLogs : Route
    data object AdminSystem : Route
    data object Sessions : Route
    data object MediaManager : Route
    data class MediaDetail(val service: ArrService, val itemId: Int) : Route
    data class SeerrDetail(val type: SeerrMediaType, val tmdbId: Int) : Route
    data class LibraryRoute(val library: Library) : Route
    data class Detail(val itemId: String) : Route
    /** Filmography grid for a cast/crew member. */
    data class Person(val personId: String, val name: String) : Route
}

private val PLAYABLE_KINDS = setOf(MediaKind.Movie, MediaKind.Episode, MediaKind.MusicVideo, MediaKind.Video)

internal fun Route.encode(): String = when (this) {
    Route.Search -> "search"
    Route.Discover -> "discover"
    Route.MyRequests -> "myrequests"
    is Route.SyncPlay -> itemId?.let { listOf("syncplay", it).joinToString(SEPARATOR) } ?: "syncplay"
    Route.Servers -> "servers"
    Route.Calendar -> "calendar"
    Route.About -> "about"
    Route.ArrSettings -> "arrsettings"
    Route.Downloads -> "downloads"
    Route.Offline -> "offline"
    Route.Favorites -> "favorites"
    Route.Bazarr -> "bazarr"
    Route.Prowlarr -> "prowlarr"
    Route.Dashboard -> "dashboard"
    Route.AdminRequests -> "adminrequests"
    Route.AdminUsers -> "adminusers"
    Route.AdminDevices -> "admindevices"
    Route.AdminLibraries -> "adminlibraries"
    Route.AdminTasks -> "admintasks"
    Route.AdminLogs -> "adminlogs"
    Route.AdminSystem -> "adminsystem"
    Route.Sessions -> "sessions"
    Route.MediaManager -> "mediamanager"
    is Route.MediaDetail -> listOf("mediadetail", service.name, itemId).joinToString(SEPARATOR)
    is Route.SeerrDetail -> listOf("seerr", type.name, tmdbId).joinToString(SEPARATOR)
    is Route.LibraryRoute -> listOf("library", library.id, library.type.name, library.name).joinToString(SEPARATOR)
    is Route.Detail -> "detail$SEPARATOR$itemId"
    is Route.Person -> listOf("person", personId, name).joinToString(SEPARATOR)
}

internal fun decodeRoute(value: String): Route? {
    val parts = value.split(SEPARATOR, limit = 4)
    return when (parts[0]) {
        "search" -> Route.Search
        "discover" -> Route.Discover
        "myrequests" -> Route.MyRequests
        "syncplay" -> Route.SyncPlay(parts.getOrNull(1))
        "servers" -> Route.Servers
        "calendar" -> Route.Calendar
        "about" -> Route.About
        "arrsettings" -> Route.ArrSettings
        "downloads" -> Route.Downloads
        "offline" -> Route.Offline
        "favorites" -> Route.Favorites
        "bazarr" -> Route.Bazarr
        "prowlarr" -> Route.Prowlarr
        "dashboard" -> Route.Dashboard
        "adminrequests" -> Route.AdminRequests
        "adminusers" -> Route.AdminUsers
        "admindevices" -> Route.AdminDevices
        "adminlibraries" -> Route.AdminLibraries
        "admintasks" -> Route.AdminTasks
        "adminlogs" -> Route.AdminLogs
        "adminsystem" -> Route.AdminSystem
        "sessions" -> Route.Sessions
        "mediamanager" -> Route.MediaManager
        "mediadetail" -> parts.getOrNull(2)?.toIntOrNull()?.let { itemId ->
            ArrService.entries.firstOrNull { it.name == parts[1] && it.managesMedia }
                ?.let { Route.MediaDetail(it, itemId) }
        }
        "seerr" -> parts.getOrNull(2)?.toIntOrNull()?.let { Route.SeerrDetail(SeerrMediaType.valueOf(parts[1]), it) }
        "library" -> parts.getOrNull(3)?.let { name ->
            Route.LibraryRoute(Library(parts[1], name, LibraryType.valueOf(parts[2]), imageUrl = null))
        }
        "detail" -> parts.getOrNull(1)?.let(Route::Detail)
        "person" -> parts.getOrNull(2)?.let { Route.Person(parts[1], it) }
        else -> null
    }
}

private const val SEPARATOR = "\u001F"

/** The [NavDestination] an admin-only route is gated by, or null for routes everyone may see. */
internal fun Route.adminDestination(): NavDestination? = when (this) {
    Route.Dashboard -> NavDestination.Dashboard
    Route.Sessions -> NavDestination.Sessions
    Route.AdminRequests -> NavDestination.RequestQueue
    Route.AdminUsers -> NavDestination.Users
    Route.AdminDevices -> NavDestination.Devices
    Route.AdminLibraries -> NavDestination.Libraries
    Route.AdminTasks -> NavDestination.Tasks
    Route.AdminLogs -> NavDestination.Logs
    Route.AdminSystem -> NavDestination.SystemInfo
    Route.MediaManager, is Route.MediaDetail -> NavDestination.MediaManager
    Route.Downloads -> NavDestination.Downloads
    Route.Bazarr -> NavDestination.Bazarr
    Route.Prowlarr -> NavDestination.Prowlarr
    Route.ArrSettings -> NavDestination.Services
    else -> null
}

/**
 * Admin-only routes stay off the stack for viewers: even if an admin entry survived in the
 * saved back stack (e.g. an admin signed out and a viewer signed in), it is never rendered.
 */
internal fun Route.isAllowedFor(user: UserProfile?): Boolean =
    adminDestination()?.let { user?.canNavigateTo(it) == true } ?: true

internal fun managementRoute(destination: NavDestination): Route? = when (destination) {
    NavDestination.RequestQueue -> Route.AdminRequests
    NavDestination.MediaManager -> Route.MediaManager
    NavDestination.Downloads -> Route.Downloads
    NavDestination.Bazarr -> Route.Bazarr
    NavDestination.Prowlarr -> Route.Prowlarr
    NavDestination.Services -> Route.ArrSettings
    NavDestination.Users -> Route.AdminUsers
    NavDestination.Devices -> Route.AdminDevices
    NavDestination.Libraries -> Route.AdminLibraries
    NavDestination.Tasks -> Route.AdminTasks
    NavDestination.Logs -> Route.AdminLogs
    NavDestination.SystemInfo -> Route.AdminSystem
    else -> null
}

@Composable
fun SignedInNavigation() {
    val context = LocalContext.current
    val offlineDownloads: OfflineDownloads = koinInject()
    val accountViewModel: HomeViewModel = koinViewModel()
    val accountState by accountViewModel.uiState.collectAsStateWithLifecycle()
    val user = (accountState as? HomeUiState.Loaded)?.user
    var stack by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val routes = stack.mapNotNull(::decodeRoute).filter { it.isAllowedFor(user) }
    val push = { route: Route -> stack = stack + route.encode() }
    val pop = { stack = stack.dropLast(1) }
    val play = { itemId: String -> context.startActivity(PlayerActivity.intent(context, itemId)) }
    val playVersion = { itemId: String, mediaSourceId: String, audioIndex: Int?, subtitleIndex: Int? -> context.startActivity(PlayerActivity.intent(context, itemId, mediaSourceId = mediaSourceId, audioStreamIndex = audioIndex, subtitleStreamIndex = subtitleIndex)) }

    // Always consume Back: pop pushed routes, and stay in the app at Home rather than exiting.
    BackHandler { if (stack.isNotEmpty()) pop() }

    when (val route = routes.lastOrNull()) {
        null -> HomeScreen(
            onOpenLibrary = { push(Route.LibraryRoute(it)) },
            onOpenItem = { push(Route.Detail(it)) },
            onSearch = { push(Route.Search) },
            onDiscover = { push(Route.Discover) },
            onCalendar = { push(Route.Calendar) },
            onDashboard = { push(Route.Dashboard) },
            onOffline = { push(Route.Offline) },
            onFavorites = { push(Route.Favorites) },
        )
        Route.Search -> SearchScreen(
            onBack = pop,
            onOpenItem = { push(Route.Detail(it)) },
            onOpenTitle = { type, tmdbId -> push(Route.SeerrDetail(type, tmdbId)) },
        )
        Route.Discover -> DiscoverScreen(
            onBack = pop,
            onOpenTitle = { type, tmdbId -> push(Route.SeerrDetail(type, tmdbId)) },
            onMyRequests = { push(Route.MyRequests) },
        )
        Route.MyRequests -> MyRequestsScreen(onBack = pop)
        is Route.SyncPlay -> WatchTogetherScreen(onBack = pop, onWatch = play, queueItemId = route.itemId)
        Route.Servers -> ServersScreen(onBack = pop)
        Route.Calendar -> CalendarScreen(
            onBack = pop,
            onSettings = { push(Route.ArrSettings) },
            canConfigureServices = user?.canNavigateTo(NavDestination.Services) == true,
        )
        Route.About -> AboutScreen(onBack = pop)
        Route.ArrSettings -> ArrSettingsScreen(onBack = pop)
        Route.Downloads -> DownloadsScreen(onBack = pop, onSettings = { push(Route.ArrSettings) })
        Route.Offline -> OfflineScreen(onBack = pop, onPlay = { id ->
            offlineDownloads.items.value.firstOrNull { e -> e.itemId == id }
                ?.let { context.startActivity(PlayerActivity.intent(context, localUri = "file://" + it.path, title = it.name)) }
        })
        Route.Bazarr -> BazarrScreen(onBack = pop, onSettings = { push(Route.ArrSettings) })
        Route.Prowlarr -> ProwlarrScreen(onBack = pop, onSettings = { push(Route.ArrSettings) })
        Route.Dashboard -> AdminDashboardScreen(
            onBack = pop,
            onOpenItem = { push(Route.Detail(it)) },
            onOpenManage = { destination -> managementRoute(destination)?.let(push) },
            onOpenSessions = { push(Route.Sessions) },
            onOpenServers = { push(Route.Servers) },
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
        Route.MediaManager -> MediaManagerScreen(
            onBack = pop,
            onOpenItem = { service, itemId -> push(Route.MediaDetail(service, itemId)) },
            onSettings = { push(Route.ArrSettings) },
        )
        is Route.MediaDetail -> MediaDetailScreen(route.service, route.itemId, onBack = pop)
        is Route.SeerrDetail -> SeerrDetailScreen(route.type, route.tmdbId, onBack = pop)
        is Route.LibraryRoute -> if (route.library.type == com.pyoungws.bluewolfplayer.shared.model.LibraryType.LiveTv) {
            com.pyoungws.bluewolfplayer.ui.livetv.LiveTvScreen(onBack = pop, onOpenItem = { push(Route.Detail(it)) })
        } else {
            LibraryScreen(route.library, onBack = pop, onOpenItem = { push(Route.Detail(it)) })
        }
        Route.Favorites -> ItemBrowseScreen(
            title = stringResource(R.string.nav_favorites),
            query = ItemQuery(favoritesOnly = true),
            onBack = pop,
            onOpenItem = { push(Route.Detail(it)) },
            prefsKey = "favorites",
        )
        is Route.Person -> ItemBrowseScreen(
            title = route.name,
            query = ItemQuery(personIds = listOf(route.personId), includeItemKinds = PLAYABLE_KINDS),
            onBack = pop,
            onOpenItem = { push(Route.Detail(it)) },
        )
        is Route.Detail -> DetailScreen(
            route.itemId,
            onBack = pop,
            onPlay = play,
            onPlayVersion = playVersion,
            onPlayFromBeginning = { itemId -> context.startActivity(PlayerActivity.intent(context, itemId, startPositionMs = 0)) },
            onOpenItem = { push(Route.Detail(it)) },
            onWatchTogether = { push(Route.SyncPlay(it)) },
            onOpenPerson = { id, _ -> push(Route.Detail(id)) },
        )
    }
}
