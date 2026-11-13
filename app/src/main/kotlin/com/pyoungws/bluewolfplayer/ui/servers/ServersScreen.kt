package com.pyoungws.bluewolfplayer.ui.servers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.auth.ServerProfile
import com.pyoungws.bluewolfplayer.shared.auth.SessionState
import com.pyoungws.bluewolfplayer.shared.model.ServerInfo
import com.pyoungws.bluewolfplayer.shared.presentation.AppViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.ServersViewModel
import com.pyoungws.bluewolfplayer.ui.connect.ServerConnectScreen
import com.pyoungws.bluewolfplayer.ui.signin.SignInScreen
import org.koin.compose.viewmodel.koinViewModel

private val ServerInfoSaver = listSaver<ServerInfo?, String?>(
    save = { server -> server?.let { listOf(it.address, it.name, it.version, it.id) } ?: emptyList() },
    restore = { values -> values[0]?.let { ServerInfo(address = it, name = values[1], version = values[2], id = values[3]) } },
)

/**
 * Saved servers and accounts. Tapping a profile moves the app to that server; "Add server or
 * account" runs the normal connect + sign-in flow and lands back here with the new profile saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    onBack: () -> Unit,
    viewModel: ServersViewModel = koinViewModel(),
    appViewModel: AppViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by appViewModel.sessionState.collectAsStateWithLifecycle()
    val entrySession = remember { (sessionState as? SessionState.SignedIn)?.session }
    var adding by rememberSaveable { mutableStateOf(false) }
    var addServer by rememberSaveable(stateSaver = ServerInfoSaver) { mutableStateOf<ServerInfo?>(null) }
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
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.servers_add_title)) },
                    navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val server = addServer
                if (server == null) {
                    ServerConnectScreen(onServerValidated = { addServer = it })
                } else {
                    SignInScreen(server = server, onChangeServer = { addServer = null })
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.servers_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.profiles.isEmpty()) {
                Text(
                    stringResource(R.string.servers_empty),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.profiles, key = { it.id }) { profile ->
                    val isActive = profile.id == state.activeProfileId
                    Card(
                        onClick = { if (!isActive && !state.busy) viewModel.switchTo(profile.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
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
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }
                            TextButton(onClick = { removing = profile }) {
                                Text(stringResource(R.string.servers_remove))
                            }
                        }
                    }
                }
                item(key = "add") {
                    TextButton(onClick = { adding = true }, modifier = Modifier.padding(8.dp)) {
                        Text(stringResource(R.string.servers_add))
                    }
                }
            }
        }
    }

    removing?.let { profile ->
        val isActive = profile.id == state.activeProfileId
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.servers_remove_title, profile.userName)) },
            text = {
                Text(
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
                ) { Text(stringResource(R.string.servers_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(R.string.home_sign_out_cancel)) }
            },
        )
    }
}
