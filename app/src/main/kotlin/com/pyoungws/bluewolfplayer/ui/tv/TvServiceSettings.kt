package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.presentation.ArrServiceForm
import com.pyoungws.bluewolfplayer.shared.presentation.ArrSettingsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.QBittorrentSettingsUiState
import com.pyoungws.bluewolfplayer.shared.presentation.QBittorrentSettingsViewModel
import com.pyoungws.bluewolfplayer.ui.calendar.labelRes
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel

/** The shared services settings form on TV: Radarr, Sonarr and qBittorrent connection details. */
@Composable
internal fun TvServiceSettings(
    onDone: () -> Unit,
    viewModel: ArrSettingsViewModel = koinViewModel(),
    qbittorrentViewModel: QBittorrentSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val qbittorrent by qbittorrentViewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TvContentPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") { TvSectionTitle(stringResource(R.string.arr_settings_title)) }
        ArrService.entries.forEach { service ->
            item(key = "card-${service.name}") {
                TvServiceCard(service, state.form(service), viewModel)
            }
        }
        item(key = "card-qbittorrent") {
            TvQBittorrentCard(qbittorrent, qbittorrentViewModel)
        }
        item(key = "done") {
            Button(onClick = onDone) { Text(stringResource(R.string.nav_back)) }
        }
    }
}

@Composable
private fun TvServiceCard(service: ArrService, form: ArrServiceForm, viewModel: ArrSettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(service.labelRes()), style = MaterialTheme.typography.titleMedium)
        TvField(form.serverUrl, { viewModel.onServerUrlChange(service, it) }, R.string.arr_field_url)
        TvField(form.apiKey, { viewModel.onApiKeyChange(service, it) }, R.string.arr_field_api_key, masked = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.save(service) }) { Text(stringResource(R.string.arr_save)) }
            OutlinedButton(onClick = { viewModel.test(service) }, enabled = !form.testing) {
                Text(stringResource(R.string.arr_test))
            }
        }
        when {
            form.testSucceeded == true ->
                Text(stringResource(R.string.arr_connected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            form.testSucceeded == false ->
                Text(form.testError?.message().orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            form.saved ->
                Text(stringResource(R.string.arr_saved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TvQBittorrentCard(form: QBittorrentSettingsUiState, viewModel: QBittorrentSettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.qbittorrent_title), style = MaterialTheme.typography.titleMedium)
        TvField(form.serverUrl, viewModel::onServerUrlChange, R.string.arr_field_url)
        TvField(form.username, viewModel::onUsernameChange, R.string.qbit_field_username)
        TvField(form.password, viewModel::onPasswordChange, R.string.qbit_field_password, masked = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::save) { Text(stringResource(R.string.arr_save)) }
            OutlinedButton(onClick = viewModel::test, enabled = !form.testing) {
                Text(stringResource(R.string.arr_test))
            }
        }
        when {
            form.testSucceeded == true ->
                Text(stringResource(R.string.arr_connected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            form.testSucceeded == false ->
                Text(form.testError?.message().orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            form.saved ->
                Text(stringResource(R.string.arr_saved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TvField(value: String, onChange: (String) -> Unit, labelRes: Int, masked: Boolean = false) {
    TvTextField(
        value = value,
        onValueChange = onChange,
        label = { androidx.compose.material3.Text(stringResource(labelRes)) },
        modifier = Modifier.fillMaxWidth(0.6f),
        visualTransformation = if (masked) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}
