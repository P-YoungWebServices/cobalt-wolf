package com.pyoungws.bluewolfplayer.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.auth.ServerProfile
import com.pyoungws.bluewolfplayer.shared.auth.SessionState
import com.pyoungws.bluewolfplayer.shared.model.ServerInfo
import com.pyoungws.bluewolfplayer.shared.presentation.AppViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.ServersViewModel
import org.koin.compose.viewmodel.koinViewModel

private val TvServerInfoSaver = listSaver<ServerInfo?, String?>(
    save = { server -> server?.let { listOf(it.address, it.name, it.version, it.id) } ?: emptyList() },
    restore = { values -> values[0]?.let { ServerInfo(address = it, name = values[1], version = values[2], id = values[3]) } },
)

/** The server/account switcher on TV, with the connect + sign-in flow for adding one. */
@Composable
fun TvServersScreen(
    viewModel: ServersViewModel = koinViewModel(),
    appViewModel: AppViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by appViewModel.sessionState.collectAsStateWithLifecycle()
    val entrySession = remember { (sessionState as? SessionState.SignedIn)?.session }
    var adding by rememberSaveable { mutableStateOf(false) }
    var addServer by rememberSaveable(stateSaver = TvServerInfoSaver) { mutableStateOf<ServerInfo?>(null) }
    var removing by remember { mutableStateOf<ServerProfile?>(null) }

    // A successful add signs the app into the new server — return to the list.
    LaunchedEffect(sessionState) {
        val session = (sessionState as? SessionState.SignedIn)?.session
        if (adding && session != null && session != entrySession) {
            adding = false
            addServer = null
        }
    }

    if (adding) {
        val leave = {
            if (addServer != null) addServer = null else adding = false
        }
        BackHandler(onBack = leave)
        val server = addServer
        if (server == null) {
            TvServerConnectScreen(onServerValidated = { addServer = it })
        } else {
            TvSignInScreen(server = server, onChangeServer = { addServer = null })
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TvContentPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") { TvSectionTitle(stringResource(R.string.servers_title)) }
        if (state.profiles.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.servers_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.profiles, key = { it.id }) { profile ->
            val isActive = profile.id == state.activeProfileId
            Card(
                onClick = { if (!isActive && !state.busy) viewModel.switchTo(profile.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile.serverName ?: profile.serverAddress,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.servers_user_at, profile.userName, profile.serverAddress),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isActive) {
                        Text(
                            stringResource(R.string.servers_current),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    OutlinedButton(onClick = { removing = profile }) { Text(stringResource(R.string.servers_remove)) }
                }
            }
        }
        item(key = "add") {
            Button(onClick = { adding = true }) { Text(stringResource(R.string.servers_add)) }
        }
    }

    removing?.let { profile ->
        val isActive = profile.id == state.activeProfileId
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { androidx.compose.material3.Text(stringResource(R.string.servers_remove_title, profile.userName)) },
            text = {
                androidx.compose.material3.Text(
                    stringResource(
                        if (isActive) R.string.servers_remove_body_active else R.string.servers_remove_body,
                        profile.userName,
                        profile.serverName ?: profile.serverAddress,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = null
                        viewModel.remove(profile.id)
                    },
                ) { androidx.compose.material3.Text(stringResource(R.string.servers_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) {
                    androidx.compose.material3.Text(stringResource(R.string.home_sign_out_cancel))
                }
            },
        )
    }
}
