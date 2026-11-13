package com.pyoungws.cobaltwolf.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.admin.SystemInformation
import com.pyoungws.cobaltwolf.shared.presentation.AdminSystemInfoViewModel
import com.pyoungws.cobaltwolf.ui.common.ErrorBox
import com.pyoungws.cobaltwolf.ui.common.LoadingBox
import com.pyoungws.cobaltwolf.ui.common.SectionTitle
import com.pyoungws.cobaltwolf.ui.common.tvListScroll
import com.pyoungws.cobaltwolf.ui.common.formatBytes
import org.koin.compose.viewmodel.koinViewModel

/** System information: server identity, update status and storage usage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSystemInfoScreen(
    onBack: () -> Unit,
    viewModel: AdminSystemInfoViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_system_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
                actions = { TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> LoadingBox()
                state.error != null && state.info == null -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
                else -> state.info?.let { InfoContent(it) }
            }
        }
    }
}

@Composable
private fun InfoContent(info: SystemInformation) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().tvListScroll(listState), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "server-title") { SectionTitle(stringResource(R.string.admin_system_server)) }
        item(key = "server-card") {
            InfoCard {
                InfoRow(stringResource(R.string.admin_system_name), info.serverName)
                InfoRow(stringResource(R.string.admin_system_version), info.version)
                InfoRow(stringResource(R.string.admin_system_product), info.productName)
                InfoRow(stringResource(R.string.admin_system_os), info.operatingSystem)
                InfoRow(stringResource(R.string.admin_system_arch), info.architecture)
                InfoRow(stringResource(R.string.admin_system_address), info.localAddress)
                InfoRow(stringResource(R.string.admin_system_package), info.packageName)
                InfoRow(stringResource(R.string.admin_system_websocket_port), info.webSocketPort.takeIf { it > 0 }?.toString())
            }
        }
        item(key = "update-title") { SectionTitle(stringResource(R.string.admin_system_updates)) }
        item(key = "update-card") {
            InfoCard {
                Text(
                    if (info.hasUpdateAvailable) stringResource(R.string.admin_system_update_available)
                    else stringResource(R.string.admin_system_up_to_date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (info.hasUpdateAvailable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                )
                if (info.hasPendingRestart) {
                    Text(
                        stringResource(R.string.admin_system_restart_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (info.storage.isNotEmpty()) {
            item(key = "storage-title") { SectionTitle(stringResource(R.string.admin_system_storage)) }
            item(key = "storage-card") {
                InfoCard {
                    info.storage.forEach { volume ->
                        InfoRow(
                            volume.label,
                            stringResource(
                                R.string.admin_system_storage_free,
                                formatBytes(volume.freeBytes),
                                formatBytes(volume.usedBytes + volume.freeBytes),
                            ),
                        )
                        volume.path?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Text(
        stringResource(R.string.admin_system_field, label, value),
        style = MaterialTheme.typography.bodyMedium,
    )
}
