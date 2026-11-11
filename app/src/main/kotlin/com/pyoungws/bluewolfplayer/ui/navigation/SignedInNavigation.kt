package com.pyoungws.bluewolfplayer.ui.navigation

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
import com.pyoungws.bluewolfplayer.ui.admin.AdminSystemInfoScreen
import com.pyoungws.bluewolfplayer.ui.admin.AdminTasksScreen
import com.pyoungws.bluewolfplayer.ui.admin.AdminUsersScreen
import com.pyoungws.bluewolfplayer.ui.detail.DetailScreen
import com.pyoungws.bluewolfplayer.ui.discover.DiscoverScreen
import com.pyoungws.bluewolfplayer.ui.discover.MyRequestsScreen
import com.pyoungws.bluewolfplayer.ui.discover.SeerrDetailScreen
import com.pyoungws.bluewolfplayer.ui.home.HomeScreen
import com.pyoungws.bluewolfplayer.ui.library.LibraryScreen
import com.pyoungws.bluewolfplayer.ui.search.SearchScreen
import com.pyoungws.bluewolfplayer.ui.calendar.ArrSettingsScreen
import com.pyoungws.bluewolfplayer.ui.calendar.CalendarScreen
import com.pyoungws.bluewolfplayer.ui.servers.ServersScreen
import com.pyoungws.bluewolfplayer.ui.settings.PlaybackSettingsActivity
import com.pyoungws.bluewolfplayer.ui.syncplay.WatchTogetherScreen
import org.koin.compose.viewmodel.koinViewModel

/** Screens above the home screen. Encoded as strings so the back stack survives process death. */
internal sealed interface Route {
    data object Search : Route
    data object Discover : Route
    data object MyRequests : Route
    data object SyncPlay : Route
    data object Servers : Route
    data object Calendar : Route
    data object ArrSettings : Route
    data object Dashboard : Route
    data object AdminRequests : Route
    data object AdminUsers : Route
    data object AdminDevices : Route
    data object AdminLibraries : Route
    data object AdminTasks : Route
    data object AdminLogs : Route
    data object AdminSystem : Route
    data class SeerrDetail(val type: SeerrMediaType, val tmdbId: Int) : Route
    data class LibraryRoute(val library: Library) : Route
    data class Detail(val itemId: String) : Route
}

internal fun Route.encode(): String = when (this) {
    Route.Search -> "search"
    Route.Discover -> "discover"
    Route.MyRequests -> "myrequests"
    Route.SyncPlay -> "syncplay"
    Route.Servers -> "servers"
    Route.Calendar -> "calendar"
    Route.ArrSettings -> "arrsettings"
    Route.Dashboard -> "dashboard"
    Route.AdminRequests -> "adminrequests"
    Route.AdminUsers -> "adminusers"
    Route.AdminDevices -> "admindevices"
    Route.AdminLibraries -> "adminlibraries"
    Route.AdminTasks -> "admintasks"
    Route.AdminLogs -> "adminlogs"
    Route.AdminSystem -> "adminsystem"
    is Route.SeerrDetail -> listOf("seerr", type.name, tmdbId).joinToString(SEPARATOR)
    is Route.LibraryRoute -> listOf("library", library.id, library.type.name, library.name).joinToString(SEPARATOR)
    is Route.Detail -> "detail$SEPARATOR$itemId"
}

internal fun decodeRoute(value: String): Route? {
    val parts = value.split(SEPARATOR, limit = 4)
    return when (parts[0]) {
        "search" -> Route.Search
        "discover" -> Route.Discover
        "myrequests" -> Route.MyRequests
        "syncplay" -> Route.SyncPlay
        "servers" -> Route.Servers
        "calendar" -> Route.Calendar
        "arrsettings" -> Route.ArrSettings
        "dashboard" -> Route.Dashboard
        "adminrequests" -> Route.AdminRequests
        "adminusers" -> Route.AdminUsers
        "admindevices" -> Route.AdminDevices
        "adminlibraries" -> Route.AdminLibraries
        "admintasks" -> Route.AdminTasks
        "adminlogs" -> Route.AdminLogs
        "adminsystem" -> Route.AdminSystem
        "seerr" -> parts.getOrNull(2)?.toIntOrNull()?.let { Route.SeerrDetail(SeerrMediaType.valueOf(parts[1]), it) }
        "library" -> parts.getOrNull(3)?.let { name ->
            Route.LibraryRoute(Library(parts[1], name, LibraryType.valueOf(parts[2]), imageUrl = null))
        }
        "detail" -> parts.getOrNull(1)?.let(Route::Detail)
        else -> null
    }
}

private const val SEPARATOR = "\u001F"

/** The [NavDestination] an admin-only route is gated by, or null for routes everyone may see. */
private fun Route.adminDestination(): NavDestination? = when (this) {
    Route.Dashboard -> NavDestination.Dashboard
    Route.AdminRequests -> NavDestination.RequestQueue
    Route.AdminUsers -> NavDestination.Users
    Route.AdminDevices -> NavDestination.Devices
    Route.AdminLibraries -> NavDestination.Libraries
    Route.AdminTasks -> NavDestination.Tasks
    Route.AdminLogs -> NavDestination.Logs
    Route.AdminSystem -> NavDestination.SystemInfo
    else -> null
}

/**
 * Admin-only routes stay off the stack for viewers: even if an admin entry survived in the
 * saved back stack (e.g. an admin signed out and a viewer signed in), it is never rendered.
 */
private fun Route.isAllowedFor(user: UserProfile?): Boolean =
    adminDestination()?.let { user?.canNavigateTo(it) == true } ?: true

private fun managementRoute(destination: NavDestination): Route? = when (destination) {
    NavDestination.RequestQueue -> Route.AdminRequests
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
    val accountViewModel: HomeViewModel = koinViewModel()
    val accountState by accountViewModel.uiState.collectAsStateWithLifecycle()
    val user = (accountState as? HomeUiState.Loaded)?.user
    var stack by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val routes = stack.mapNotNull(::decodeRoute).filter { it.isAllowedFor(user) }
    val push = { route: Route -> stack = stack + route.encode() }
    val pop = { stack = stack.dropLast(1) }
    val play = { itemId: String -> context.startActivity(PlayerActivity.intent(context, itemId)) }

    BackHandler(enabled = routes.isNotEmpty()) { pop() }

    when (val route = routes.lastOrNull()) {
        null -> HomeScreen(
            onOpenLibrary = { push(Route.LibraryRoute(it)) },
            onOpenItem = { push(Route.Detail(it)) },
            onSearch = { push(Route.Search) },
            onDiscover = { push(Route.Discover) },
            onWatchTogether = { push(Route.SyncPlay) },
            onServers = { push(Route.Servers) },
            onCalendar = { push(Route.Calendar) },
            onPlaybackSettings = { context.startActivity(PlaybackSettingsActivity.intent(context)) },
            isAdmin = user?.canNavigateTo(NavDestination.Dashboard) == true,
            onDashboard = { push(Route.Dashboard) },
        )
        Route.Search -> SearchScreen(onBack = pop, onOpenItem = { push(Route.Detail(it)) })
        Route.Discover -> DiscoverScreen(
            onBack = pop,
            onOpenTitle = { type, tmdbId -> push(Route.SeerrDetail(type, tmdbId)) },
            onMyRequests = { push(Route.MyRequests) },
        )
        Route.MyRequests -> MyRequestsScreen(onBack = pop)
        Route.SyncPlay -> WatchTogetherScreen(onBack = pop, onWatch = play)
        Route.Servers -> ServersScreen(onBack = pop)
        Route.Calendar -> CalendarScreen(onBack = pop, onSettings = { push(Route.ArrSettings) })
        Route.ArrSettings -> ArrSettingsScreen(onBack = pop)
        Route.Dashboard -> AdminDashboardScreen(
            onBack = pop,
            onOpenItem = { push(Route.Detail(it)) },
            onOpenManage = { destination -> managementRoute(destination)?.let(push) },
        )
        Route.AdminRequests -> AdminRequestsScreen(onBack = pop)
        Route.AdminUsers -> AdminUsersScreen(onBack = pop)
        Route.AdminDevices -> AdminDevicesScreen(onBack = pop)
        Route.AdminLibraries -> AdminLibrariesScreen(onBack = pop)
        Route.AdminTasks -> AdminTasksScreen(onBack = pop)
        Route.AdminLogs -> AdminLogsScreen(onBack = pop)
        Route.AdminSystem -> AdminSystemInfoScreen(onBack = pop)
        is Route.SeerrDetail -> SeerrDetailScreen(route.type, route.tmdbId, onBack = pop)
        is Route.LibraryRoute -> LibraryScreen(route.library, onBack = pop, onOpenItem = { push(Route.Detail(it)) })
        is Route.Detail -> DetailScreen(route.itemId, onBack = pop, onPlay = play, onOpenItem = { push(Route.Detail(it)) })
    }
}
