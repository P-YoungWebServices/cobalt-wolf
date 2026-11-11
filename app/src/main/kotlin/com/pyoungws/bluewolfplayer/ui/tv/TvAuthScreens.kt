package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.auth.QuickConnectState
import com.pyoungws.bluewolfplayer.shared.model.ServerInfo
import com.pyoungws.bluewolfplayer.shared.presentation.ServerConnectViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.SignInUiState
import com.pyoungws.bluewolfplayer.shared.presentation.SignInViewModel
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Server address entry for TVs. The text field opens the on-screen keyboard on select. */
@Composable
fun TvServerConnectScreen(
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

    TvFormColumn {
        Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.connect_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = state.address,
            onValueChange = viewModel::onAddressChange,
            modifier = Modifier.fillMaxWidth(),
            label = { androidx.compose.material3.Text(stringResource(R.string.connect_address_label)) },
            placeholder = { androidx.compose.material3.Text(stringResource(R.string.connect_address_hint)) },
            singleLine = true,
            enabled = !state.isValidating,
            isError = state.error != null,
            supportingText = state.error?.let { error -> { androidx.compose.material3.Text(error.message()) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { viewModel.connect() }),
        )
        Button(
            onClick = viewModel::connect,
            enabled = !state.isValidating && state.address.isNotBlank(),
        ) {
            if (state.isValidating) CircularProgressIndicator() else Text(stringResource(R.string.connect_button))
        }
    }
}

/** Jellyfin sign-in for TVs: user tiles, username/password and Quick Connect. */
@Composable
fun TvSignInScreen(
    server: ServerInfo,
    onChangeServer: () -> Unit,
    viewModel: SignInViewModel = koinViewModel(key = server.address) { parametersOf(server.address) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val leave = {
        viewModel.cancelQuickConnect()
        onChangeServer()
    }
    androidx.activity.compose.BackHandler(onBack = leave)

    TvFormColumn {
        Text(
            stringResource(R.string.sign_in_title, server.name ?: server.address),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(server.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val quickConnect = state.quickConnect
        when {
            quickConnect != null -> TvQuickConnectPanel(quickConnect, viewModel)
            state.isLoadingUsers -> CircularProgressIndicator()
            state.showUserList -> TvUserPicker(state, viewModel)
            else -> TvCredentialsForm(state, viewModel)
        }

        state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }

        OutlinedButton(onClick = leave) { Text(stringResource(R.string.sign_in_change_server)) }
    }
}

@Composable
private fun TvFormColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Column(Modifier.fillMaxWidth().widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
}

@Composable
private fun TvUserPicker(state: SignInUiState, viewModel: SignInViewModel) {
    Text(stringResource(R.string.sign_in_pick_user), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        state.publicUsers.forEach { user ->
            OutlinedButton(onClick = { viewModel.selectUser(user) }, enabled = !state.isBusy) {
                Text(user.name, maxLines = 1)
            }
        }
    }
    if (state.isSigningIn) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = viewModel::enterUsernameManually, enabled = !state.isBusy) {
        Text(stringResource(R.string.sign_in_enter_username))
    }
    OutlinedButton(onClick = viewModel::startQuickConnect, enabled = !state.isBusy) {
        Text(stringResource(R.string.quick_connect_button))
    }
}

@Composable
private fun TvCredentialsForm(state: SignInUiState, viewModel: SignInViewModel) {
    val selected = state.selectedUser
    if (selected != null) {
        Text(selected.name, style = MaterialTheme.typography.titleLarge)
    } else {
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { androidx.compose.material3.Text(stringResource(R.string.sign_in_username)) },
            singleLine = true,
            enabled = !state.isBusy,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
        )
    }
    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        modifier = Modifier.fillMaxWidth(),
        label = { androidx.compose.material3.Text(stringResource(R.string.sign_in_password)) },
        singleLine = true,
        enabled = !state.isBusy,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.signIn() }),
    )
    Button(onClick = viewModel::signIn, enabled = !state.isBusy && (selected != null || state.username.isNotBlank())) {
        if (state.isBusy) CircularProgressIndicator() else Text(stringResource(R.string.sign_in_button))
    }
    if (state.publicUsers.isNotEmpty()) {
        OutlinedButton(onClick = viewModel::backToUserList, enabled = !state.isBusy) {
            Text(stringResource(R.string.sign_in_back_to_users))
        }
    }
    OutlinedButton(onClick = viewModel::startQuickConnect, enabled = !state.isBusy) {
        Text(stringResource(R.string.quick_connect_button))
    }
}

@Composable
private fun TvQuickConnectPanel(state: QuickConnectState, viewModel: SignInViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            QuickConnectState.Starting, is QuickConnectState.SignedIn -> {
                Text(stringResource(R.string.quick_connect_starting))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is QuickConnectState.WaitingForApproval -> {
                Text(stringResource(R.string.quick_connect_instructions), textAlign = TextAlign.Center)
                Text(
                    state.code,
                    style = MaterialTheme.typography.displayMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(stringResource(R.string.quick_connect_waiting), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is QuickConnectState.Failed -> {
                Text(state.error.message(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Button(onClick = viewModel::startQuickConnect) { Text(stringResource(R.string.quick_connect_try_again)) }
            }
        }
        OutlinedButton(onClick = viewModel::cancelQuickConnect) { Text(stringResource(R.string.quick_connect_cancel)) }
    }
}
