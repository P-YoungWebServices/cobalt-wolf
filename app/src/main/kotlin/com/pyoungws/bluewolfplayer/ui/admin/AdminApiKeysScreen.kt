package com.pyoungws.bluewolfplayer.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.admin.ServerApiKey
import com.pyoungws.bluewolfplayer.shared.presentation.AdminActionStatus
import com.pyoungws.bluewolfplayer.shared.presentation.AdminApiKeysViewModel
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.tvListScroll
import org.koin.compose.viewmodel.koinViewModel

/** API keys registered on the server — create new ones for integrations, revoke old ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminApiKeysScreen(
    onBack: () -> Unit,
    viewModel: AdminApiKeysViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var creating by remember { mutableStateOf(false) }
    var revoking by remember { mutableStateOf<ServerApiKey?>(null) }

    state.action?.let { action ->
        when (action) {
            is AdminActionStatus.Running -> {}
            is AdminActionStatus.Succeeded -> {
                creating = false
                revoking = null
                viewModel.consumeAction()
            }
            is AdminActionStatus.Failed -> viewModel.consumeAction()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_apikeys_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
                actions = { TextButton(onClick = { creating = true }) { Text(stringResource(R.string.admin_apikeys_new)) } },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingBox(Modifier.fillMaxSize().padding(padding))
            state.error != null -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).tvListScroll(listState)) {
                items(state.keys, key = { it.id }) { key ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(key.appName ?: key.accessToken, style = MaterialTheme.typography.bodyMedium)
                            Text(key.accessToken, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        TextButton(onClick = { revoking = key }) { Text(stringResource(R.string.admin_apikeys_revoke)) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(stringResource(R.string.admin_apikeys_new)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.admin_apikeys_name)) })
            },
            confirmButton = { TextButton(onClick = { viewModel.createKey(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.admin_apikeys_create)) } },
            dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.player_close)) } },
        )
    }
    revoking?.let { key ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text(stringResource(R.string.admin_apikeys_revoke)) },
            text = { Text(stringResource(R.string.admin_apikeys_revoke_confirm, key.appName ?: key.accessToken)) },
            confirmButton = { TextButton(onClick = { viewModel.revokeKey(key) }) { Text(stringResource(R.string.admin_apikeys_revoke)) } },
            dismissButton = { TextButton(onClick = { revoking = null }) { Text(stringResource(R.string.player_close)) } },
        )
    }
}
