package com.pyoungws.bluewolfplayer.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.admin.AdminPlugin
import com.pyoungws.bluewolfplayer.shared.presentation.AdminActionStatus
import com.pyoungws.bluewolfplayer.shared.presentation.AdminPluginsUiState
import com.pyoungws.bluewolfplayer.shared.presentation.AdminPluginsViewModel
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel

/** Installed plugin management, mirroring Dashboard → Plugins in jellyfin-web. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPluginsScreen(
    onBack: () -> Unit,
    viewModel: AdminPluginsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_plugins_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
                actions = { TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> LoadingBox()
                state.error != null && state.plugins.isEmpty() -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
                state.plugins.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.admin_plugins_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> PluginList(state, onToggle = viewModel::setEnabled, onUninstall = viewModel::uninstall)
            }
            (state.action as? AdminActionStatus.Failed)?.let { failed ->
                Text(
                    failed.error.message(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun PluginList(
    state: AdminPluginsUiState,
    onToggle: (AdminPlugin, Boolean) -> Unit,
    onUninstall: (AdminPlugin) -> Unit,
) {
    val running = (state.action as? AdminActionStatus.Running)?.key
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.plugins, key = { it.id }) { plugin ->
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(plugin.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(plugin.version, plugin.status).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        plugin.description?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (plugin.canUninstall) {
                            TextButton(onClick = { onUninstall(plugin) }, enabled = running == null) {
                                Text(stringResource(R.string.admin_plugin_uninstall))
                            }
                        }
                    }
                    Switch(
                        checked = plugin.status == "Active" || plugin.status == "Restart",
                        onCheckedChange = { onToggle(plugin, it) },
                        enabled = running == null,
                    )
                }
            }
        }
    }
}
