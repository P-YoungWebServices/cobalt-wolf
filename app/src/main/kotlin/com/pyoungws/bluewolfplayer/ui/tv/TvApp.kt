package com.pyoungws.bluewolfplayer.ui.tv

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.isTelevision
import com.pyoungws.bluewolfplayer.player.PlayerActivity
import com.pyoungws.bluewolfplayer.shared.auth.SessionState
import com.pyoungws.bluewolfplayer.shared.model.ServerInfo
import com.pyoungws.bluewolfplayer.shared.presentation.AppViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.HomeUiState
import com.pyoungws.bluewolfplayer.shared.presentation.HomeViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.LibraryHomeUiState
import com.pyoungws.bluewolfplayer.shared.presentation.LibraryHomeViewModel
import com.pyoungws.bluewolfplayer.supportsHomeScreenChannels
import com.pyoungws.bluewolfplayer.ui.navigation.decodeRoute
import com.pyoungws.bluewolfplayer.ui.navigation.encode
import com.pyoungws.bluewolfplayer.ui.navigation.Route
import com.pyoungws.bluewolfplayer.ui.settings.PlaybackSettingsActivity
import com.pyoungws.bluewolfplayer.ui.tv.channels.TvHomeChannels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

/** Root of the TV layout: session flow, then the 10-foot signed-in experience. */
@Composable
fun TvApp(initialItemId: String?, appViewModel: AppViewModel = koinViewModel()) {
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
        is SessionState.SignedIn -> TvSignedInNavigation(initialItemId)
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

private enum class TvSection { Home, Search, Requests, MyRequests }

/** Routes that can be pushed over the current section. */
private fun decodeTvRoute(value: String): Route? = when (val route = decodeRoute(value)) {
    is Route.LibraryRoute, is Route.Detail, is Route.SeerrDetail -> route
    else -> null
}

@Composable
private fun TvSignedInNavigation(
    initialItemId: String?,
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
    val routes = stack.mapNotNull(::decodeTvRoute)
    val push = { route: Route -> stack = stack + route.encode() }
    val pop = { stack = stack.dropLast(1) }
    val play = { itemId: String -> context.startActivity(PlayerActivity.intent(context, itemId)) }

    // A channel or deep link opens the matching item on top of Home.
    LaunchedEffect(initialItemId) {
        initialItemId?.let { push(Route.Detail(it)) }
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

    BackHandler(enabled = routes.isNotEmpty()) { pop() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TvTopBar(
            section = TvSection.valueOf(section),
            userName = user?.name,
            isSigningOut = isSigningOut,
            onSelect = { selected ->
                if (routes.isNotEmpty()) stack = emptyList()
                section = selected.name
            },
            onSettings = { context.startActivity(PlaybackSettingsActivity.intent(context)) },
            onSignOut = { confirmSignOut = true },
        )
        Box(Modifier.weight(1f)) {
            when (val route = routes.lastOrNull()) {
                is Route.LibraryRoute -> TvLibraryScreen(route.library, onOpenItem = { push(Route.Detail(it)) })
                is Route.Detail -> TvDetailScreen(route.itemId, onPlay = play, onOpenItem = { push(Route.Detail(it)) })
                is Route.SeerrDetail -> TvSeerrDetailScreen(route.type, route.tmdbId)
                null -> when (TvSection.valueOf(section)) {
                    TvSection.Home -> TvHomeScreen(
                        feedViewModel = feedViewModel,
                        onOpenLibrary = { push(Route.LibraryRoute(it)) },
                        onOpenItem = { push(Route.Detail(it)) },
                    )
                    TvSection.Search -> TvSearchScreen(onOpenItem = { push(Route.Detail(it)) })
                    TvSection.Requests -> TvDiscoverScreen(onOpenTitle = { type, tmdbId -> push(Route.SeerrDetail(type, tmdbId)) })
                    TvSection.MyRequests -> TvMyRequestsScreen(onOpenTitle = { type, tmdbId -> push(Route.SeerrDetail(type, tmdbId)) })
                }
                else -> Unit
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
    isSigningOut: Boolean,
    onSelect: (TvSection) -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = TvContentPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp),
        )
        TvNavButton(stringResource(R.string.tv_nav_home), selected = section == TvSection.Home) { onSelect(TvSection.Home) }
        TvNavButton(stringResource(R.string.home_search), selected = section == TvSection.Search) { onSelect(TvSection.Search) }
        TvNavButton(stringResource(R.string.home_requests), selected = section == TvSection.Requests) { onSelect(TvSection.Requests) }
        TvNavButton(stringResource(R.string.discover_my_requests), selected = section == TvSection.MyRequests) { onSelect(TvSection.MyRequests) }
        Box(Modifier.weight(1f))
        if (userName != null) {
            Text(userName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TvNavButton(stringResource(R.string.tv_nav_settings), selected = false, onClick = onSettings)
        TvNavButton(stringResource(R.string.home_sign_out), selected = false, onClick = onSignOut)
    }
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
