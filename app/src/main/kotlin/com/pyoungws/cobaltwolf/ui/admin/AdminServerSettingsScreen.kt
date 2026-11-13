package com.pyoungws.cobaltwolf.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.presentation.AdminActionStatus
import com.pyoungws.cobaltwolf.shared.presentation.AdminServerSettingsViewModel
import com.pyoungws.cobaltwolf.ui.common.ErrorBox
import com.pyoungws.cobaltwolf.ui.common.LoadingBox
import com.pyoungws.cobaltwolf.ui.message
import org.koin.compose.viewmodel.koinViewModel

/** Server-level settings: display name plus restart/shutdown controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminServerSettingsScreen(
    onBack: () -> Unit,
    viewModel: AdminServerSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmAction by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_server_settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> LoadingBox()
                state.error != null && state.settings == null -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                var name by rememberSaveable(state.settings?.serverName) {
                                    mutableStateOf(state.settings?.serverName.orEmpty())
                                }
                                Text(stringResource(R.string.admin_server_name), style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                                Button(
                                    onClick = { viewModel.saveServerName(name) },
                                    enabled = name.isNotBlank() && name != state.settings?.serverName,
                                ) { Text(stringResource(R.string.admin_save)) }
                            }
                        }
                    }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.admin_server_power), style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { confirmAction = "restart" }) {
                                        Text(stringResource(R.string.admin_restart))
                                    }
                                    Button(onClick = { confirmAction = "shutdown" }) {
                                        Text(stringResource(R.string.admin_shutdown))
                                    }
                                }
                            }
                        }
                    }
                    (state.action as? AdminActionStatus.Failed)?.let { failed ->
                        item {
                            Text(failed.error.message(), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    (state.action as? AdminActionStatus.Succeeded)?.let { done ->
                        item {
                            Text(
                                stringResource(if (done.key == "save") R.string.admin_saved else R.string.admin_action_sent),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(stringResource(if (action == "restart") R.string.admin_restart else R.string.admin_shutdown)) },
            text = { Text(stringResource(if (action == "restart") R.string.admin_restart_confirm else R.string.admin_shutdown_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmAction = null
                    if (action == "restart") viewModel.restart() else viewModel.shutdown()
                }) { Text(stringResource(R.string.admin_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
