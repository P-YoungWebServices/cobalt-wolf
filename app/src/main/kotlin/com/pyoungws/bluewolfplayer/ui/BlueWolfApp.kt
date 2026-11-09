package com.pyoungws.bluewolfplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.shared.auth.SessionState
import com.pyoungws.bluewolfplayer.shared.model.ServerInfo
import com.pyoungws.bluewolfplayer.shared.presentation.AppViewModel
import com.pyoungws.bluewolfplayer.ui.connect.ServerConnectScreen
import com.pyoungws.bluewolfplayer.ui.signin.SignInScreen
import com.pyoungws.bluewolfplayer.ui.navigation.SignedInNavigation
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BlueWolfApp(appViewModel: AppViewModel = koinViewModel()) {
    val sessionState by appViewModel.sessionState.collectAsStateWithLifecycle()
    when (val state = sessionState) {
        SessionState.Unknown -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is SessionState.SignedOut -> SignedOutFlow(state.lastServerAddress)
        is SessionState.SignedIn -> SignedInNavigation()
    }
}

private val ServerInfoSaver = listSaver<ServerInfo?, String?>(
    save = { server -> server?.let { listOf(it.address, it.name, it.version, it.id) } ?: emptyList() },
    restore = { values -> values[0]?.let { ServerInfo(address = it, name = values[1], version = values[2], id = values[3]) } },
)

/**
 * Server connection, then sign-in for the validated server. After a sign out the user lands on
 * sign-in for the same server (to switch user), and can still pick another server from there.
 */
@Composable
private fun SignedOutFlow(lastServerAddress: String?) {
    var server by rememberSaveable(stateSaver = ServerInfoSaver) {
        mutableStateOf(lastServerAddress?.let { ServerInfo(address = it, name = null, version = null, id = null) })
    }
    val current = server
    if (current == null) {
        ServerConnectScreen(onServerValidated = { server = it })
    } else {
        SignInScreen(server = current, onChangeServer = { server = null })
    }
}
