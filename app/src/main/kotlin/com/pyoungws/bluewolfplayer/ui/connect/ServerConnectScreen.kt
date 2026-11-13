package com.pyoungws.bluewolfplayer.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.model.ServerInfo
import com.pyoungws.bluewolfplayer.shared.presentation.ServerConnectViewModel
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ServerConnectScreen(
    onServerValidated: (ServerInfo) -> Unit,
    viewModel: ServerConnectViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.validatedServer) {
        state.validatedServer?.let {
            viewModel.onServerConsumed()
            onServerValidated(it)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            val fieldModifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)
            Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.connect_subtitle), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.address,
                onValueChange = viewModel::onAddressChange,
                modifier = fieldModifier,
                label = { Text(stringResource(R.string.connect_address_label)) },
                placeholder = { Text(stringResource(R.string.connect_address_hint)) },
                singleLine = true,
                enabled = !state.isValidating,
                isError = state.error != null,
                supportingText = state.error?.let { error -> { Text(error.message()) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onGo = { viewModel.connect() }),
            )
            Button(
                onClick = viewModel::connect,
                enabled = !state.isValidating && state.address.isNotBlank(),
                modifier = fieldModifier,
            ) {
                if (state.isValidating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.connect_button))
                }
            }
            val discovered by produceState<List<DiscoveredServer>?>(initialValue = null) {
                value = LanDiscovery.discover()
            }
            if (discovered == null) {
                Text(stringResource(R.string.connect_scanning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (discovered.orEmpty().isNotEmpty()) {
                Text(stringResource(R.string.connect_discovered), style = MaterialTheme.typography.titleSmall)
                discovered.orEmpty().forEach { server ->
                    OutlinedButton(onClick = { viewModel.onAddressChange(server.address) }, modifier = fieldModifier) {
                        Text("${server.name} — ${server.address}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
