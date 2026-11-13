package com.pyoungws.bluewolfplayer.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.admin.AdminDevice
import com.pyoungws.bluewolfplayer.shared.presentation.AdminActionStatus
import com.pyoungws.bluewolfplayer.shared.presentation.AdminDevicesUiState
import com.pyoungws.bluewolfplayer.shared.presentation.AdminDevicesViewModel
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.formatTimestamp
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel

/** Device management: devices the server has seen, rename and remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDevicesScreen(
    onBack: () -> Unit,
    viewModel: AdminDevicesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<AdminDevice?>(null) }
    var deleting by remember { mutableStateOf<AdminDevice?>(null) }

    LaunchedEffect(state.action) {
        when (val action = state.action) {
            is AdminActionStatus.Succeeded -> when {
                action.key == "${AdminDevicesViewModel.RENAME_KEY}${renaming?.id}" -> renaming = null
                action.key == "${AdminDevicesViewModel.DELETE_KEY}${deleting?.id}" -> deleting = null
            }
            else -> Unit
        }
        if (state.action is AdminActionStatus.Succeeded) viewModel.consumeAction()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_devices_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
                actions = { TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> LoadingBox()
                state.error != null && state.devices.isEmpty() -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
                state.devices.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.admin_devices_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> DeviceList(state, onRename = { renaming = it }, onDelete = { deleting = it })
            }
        }
    }

    renaming?.let { device ->
        var name by rememberSaveable(device.id) { mutableStateOf(device.displayName) }
        val key = "${AdminDevicesViewModel.RENAME_KEY}${device.id}"
        val busy = (state.action as? AdminActionStatus.Running)?.key == key
        val failed = (state.action as? AdminActionStatus.Failed)?.takeIf { it.key == key }
        AlertDialog(
            onDismissRequest = { if (!busy) renaming = null },
            title = { Text(stringResource(R.string.admin_device_rename_title, device.displayName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.admin_device_name_hint)) },
                        enabled = !busy,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    failed?.let {
                        Text(it.error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.renameDevice(device.id, name) }, enabled = name.isNotBlank() && !busy) {
                    if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.admin_save))
                }
            },
            dismissButton = { TextButton(onClick = { renaming = null }, enabled = !busy) { Text(stringResource(R.string.home_sign_out_cancel)) } },
        )
    }
    deleting?.let { device ->
        val key = "${AdminDevicesViewModel.DELETE_KEY}${device.id}"
        val busy = (state.action as? AdminActionStatus.Running)?.key == key
        val failed = (state.action as? AdminActionStatus.Failed)?.takeIf { it.key == key }
        AlertDialog(
            onDismissRequest = { if (!busy) deleting = null },
            title = { Text(stringResource(R.string.admin_device_delete_title, device.displayName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.admin_device_delete_message))
                    failed?.let {
                        Text(it.error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteDevice(device.id) }, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.admin_delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }, enabled = !busy) { Text(stringResource(R.string.home_sign_out_cancel)) } },
        )
    }
}

@Composable
private fun DeviceList(state: AdminDevicesUiState, onRename: (AdminDevice) -> Unit, onDelete: (AdminDevice) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.devices, key = { it.id }) { device ->
            DeviceCard(device, onRename = { onRename(device) }, onDelete = { onDelete(device) })
        }
    }
}

@Composable
private fun DeviceCard(device: AdminDevice, onRename: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(device.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    listOfNotNull(device.appName, device.appVersion).joinToString(" ").takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onRename) { Text(stringResource(R.string.admin_device_rename)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.admin_delete)) }
            }
            device.lastUserName?.let {
                Text(
                    stringResource(R.string.admin_device_last_user, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            device.lastActivity?.let {
                Text(
                    stringResource(R.string.admin_last_activity, formatTimestamp(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
