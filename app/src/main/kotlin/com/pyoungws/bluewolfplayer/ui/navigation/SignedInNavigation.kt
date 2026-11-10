package com.pyoungws.bluewolfplayer.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pyoungws.bluewolfplayer.player.PlayerActivity
import com.pyoungws.bluewolfplayer.shared.model.Library
import com.pyoungws.bluewolfplayer.shared.model.LibraryType
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrMediaType
import com.pyoungws.bluewolfplayer.ui.detail.DetailScreen
import com.pyoungws.bluewolfplayer.ui.discover.DiscoverScreen
import com.pyoungws.bluewolfplayer.ui.discover.MyRequestsScreen
import com.pyoungws.bluewolfplayer.ui.discover.SeerrDetailScreen
import com.pyoungws.bluewolfplayer.ui.home.HomeScreen
import com.pyoungws.bluewolfplayer.ui.library.LibraryScreen
import com.pyoungws.bluewolfplayer.ui.search.SearchScreen
import com.pyoungws.bluewolfplayer.ui.settings.PlaybackSettingsActivity

/** Screens above the home screen. Encoded as strings so the back stack survives process death. */
internal sealed interface Route {
    data object Search : Route
    data object Discover : Route
    data object MyRequests : Route
    data class SeerrDetail(val type: SeerrMediaType, val tmdbId: Int) : Route
    data class LibraryRoute(val library: Library) : Route
    data class Detail(val itemId: String) : Route
}

internal fun Route.encode(): String = when (this) {
    Route.Search -> "search"
    Route.Discover -> "discover"
    Route.MyRequests -> "myrequests"
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
        "seerr" -> parts.getOrNull(2)?.toIntOrNull()?.let { Route.SeerrDetail(SeerrMediaType.valueOf(parts[1]), it) }
        "library" -> parts.getOrNull(3)?.let { name ->
            Route.LibraryRoute(Library(parts[1], name, LibraryType.valueOf(parts[2]), imageUrl = null))
        }
        "detail" -> parts.getOrNull(1)?.let(Route::Detail)
        else -> null
    }
}

private const val SEPARATOR = "\u001F"

@Composable
fun SignedInNavigation() {
    val context = LocalContext.current
    var stack by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val routes = stack.mapNotNull(::decodeRoute)
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
            onPlaybackSettings = { context.startActivity(PlaybackSettingsActivity.intent(context)) },
        )
        Route.Search -> SearchScreen(onBack = pop, onOpenItem = { push(Route.Detail(it)) })
        Route.Discover -> DiscoverScreen(
            onBack = pop,
            onOpenTitle = { type, tmdbId -> push(Route.SeerrDetail(type, tmdbId)) },
            onMyRequests = { push(Route.MyRequests) },
        )
        Route.MyRequests -> MyRequestsScreen(onBack = pop)
        is Route.SeerrDetail -> SeerrDetailScreen(route.type, route.tmdbId, onBack = pop)
        is Route.LibraryRoute -> LibraryScreen(route.library, onBack = pop, onOpenItem = { push(Route.Detail(it)) })
        is Route.Detail -> DetailScreen(route.itemId, onBack = pop, onPlay = play, onOpenItem = { push(Route.Detail(it)) })
    }
}
