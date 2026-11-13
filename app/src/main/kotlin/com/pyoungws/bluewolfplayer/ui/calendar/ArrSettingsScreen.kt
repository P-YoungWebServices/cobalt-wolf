package com.pyoungws.bluewolfplayer.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.presentation.ArrServiceForm
import com.pyoungws.bluewolfplayer.shared.presentation.ArrSettingsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.QBittorrentSettingsViewModel
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel

/** Per-service connection settings: *arr services get URL + API key, qBittorrent gets URL + login. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrSettingsScreen(
    onBack: () -> Unit,
    viewModel: ArrSettingsViewModel = koinViewModel(),
    qbittorrentViewModel: QBittorrentSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val qbittorrent by qbittorrentViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.arr_settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            ArrService.entries.forEach { service ->
                ServiceCard(service, state.form(service), viewModel)
                Spacer(Modifier.height(24.dp))
            }
            QBittorrentCard(qbittorrent, qbittorrentViewModel)
        }
    }
}

@Composable
private fun QBittorrentCard(
    form: com.pyoungws.bluewolfplayer.shared.presentation.QBittorrentSettingsUiState,
    viewModel: QBittorrentSettingsViewModel,
) {
    Text(stringResource(R.string.qbittorrent_title), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = form.serverUrl,
        onValueChange = viewModel::onServerUrlChange,
        label = { Text(stringResource(R.string.arr_field_url)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
    )
    OutlinedTextField(
        value = form.username,
        onValueChange = viewModel::onUsernameChange,
        label = { Text(stringResource(R.string.qbit_field_username)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
    )
    OutlinedTextField(
        value = form.password,
        onValueChange = viewModel::onPasswordChange,
        label = { Text(stringResource(R.string.qbit_field_password)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = viewModel::save) { Text(stringResource(R.string.arr_save)) }
        TextButton(onClick = viewModel::test, enabled = !form.testing) {
            Text(stringResource(R.string.arr_test))
        }
        when {
            form.testing -> CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
            form.testSucceeded == true -> StatusText(stringResource(R.string.arr_connected), MaterialTheme.colorScheme.primary)
            form.testSucceeded == false -> StatusText(form.testError?.message().orEmpty(), MaterialTheme.colorScheme.error)
            form.saved -> StatusText(stringResource(R.string.arr_saved), MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ServiceCard(service: ArrService, form: ArrServiceForm, viewModel: ArrSettingsViewModel) {
    Text(stringResource(service.labelRes()), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = form.serverUrl,
        onValueChange = { viewModel.onServerUrlChange(service, it) },
        label = { Text(stringResource(R.string.arr_field_url)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
    )
    OutlinedTextField(
        value = form.apiKey,
        onValueChange = { viewModel.onApiKeyChange(service, it) },
        label = { Text(stringResource(R.string.arr_field_api_key)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { viewModel.save(service) }) { Text(stringResource(R.string.arr_save)) }
        TextButton(onClick = { viewModel.test(service) }, enabled = !form.testing) {
            Text(stringResource(R.string.arr_test))
        }
        when {
            form.testing -> CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
            form.testSucceeded == true -> StatusText(stringResource(R.string.arr_connected), MaterialTheme.colorScheme.primary)
            form.testSucceeded == false -> StatusText(form.testError?.message().orEmpty(), MaterialTheme.colorScheme.error)
            form.saved -> StatusText(stringResource(R.string.arr_saved), MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StatusText(text: String, color: androidx.compose.ui.graphics.Color) {
    if (text.isNotEmpty()) {
        Text(text, modifier = Modifier.padding(start = 12.dp), color = color, style = MaterialTheme.typography.bodySmall)
    }
}
