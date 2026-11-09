package com.pyoungws.bluewolfplayer.ui.signin

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.auth.QuickConnectState
import com.pyoungws.bluewolfplayer.shared.model.ServerInfo
import com.pyoungws.bluewolfplayer.shared.presentation.SignInViewModel
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SignInScreen(
    server: ServerInfo,
    onChangeServer: () -> Unit,
    viewModel: SignInViewModel = koinViewModel(key = server.address) { parametersOf(server.address) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val leave = {
        viewModel.cancelQuickConnect()
        onChangeServer()
    }
    BackHandler(onBack = leave)

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
            Text(
                stringResource(R.string.sign_in_title, server.name ?: server.address),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(server.address, style = MaterialTheme.typography.bodySmall)

            when (val quickConnect = state.quickConnect) {
                null -> PasswordForm(state.username, state.password, state.isBusy, viewModel, fieldModifier)
                else -> QuickConnectPanel(quickConnect, viewModel, fieldModifier)
            }

            state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }

            TextButton(onClick = leave) { Text(stringResource(R.string.sign_in_change_server)) }
        }
    }
}

@Composable
private fun PasswordForm(
    username: String,
    password: String,
    isBusy: Boolean,
    viewModel: SignInViewModel,
    fieldModifier: Modifier,
) {
    OutlinedTextField(
        value = username,
        onValueChange = viewModel::onUsernameChange,
        modifier = fieldModifier,
        label = { Text(stringResource(R.string.sign_in_username)) },
        singleLine = true,
        enabled = !isBusy,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
    )
    OutlinedTextField(
        value = password,
        onValueChange = viewModel::onPasswordChange,
        modifier = fieldModifier,
        label = { Text(stringResource(R.string.sign_in_password)) },
        singleLine = true,
        enabled = !isBusy,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.signIn() }),
    )
    Button(onClick = viewModel::signIn, enabled = !isBusy && username.isNotBlank(), modifier = fieldModifier) {
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(R.string.sign_in_button))
        }
    }
    Text(stringResource(R.string.sign_in_or), style = MaterialTheme.typography.labelMedium)
    OutlinedButton(onClick = viewModel::startQuickConnect, enabled = !isBusy, modifier = fieldModifier) {
        Text(stringResource(R.string.quick_connect_button))
    }
}

@Composable
private fun QuickConnectPanel(state: QuickConnectState, viewModel: SignInViewModel, fieldModifier: Modifier) {
    Card(modifier = fieldModifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
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
}
