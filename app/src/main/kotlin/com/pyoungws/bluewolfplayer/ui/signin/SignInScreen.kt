package com.pyoungws.bluewolfplayer.ui.signin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.auth.QuickConnectState
import com.pyoungws.bluewolfplayer.shared.model.ServerInfo
import com.pyoungws.bluewolfplayer.shared.presentation.SignInUiState
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

            val quickConnect = state.quickConnect
            when {
                quickConnect != null -> QuickConnectPanel(quickConnect, viewModel, fieldModifier)
                state.isLoadingUsers -> CircularProgressIndicator()
                state.showUserList -> UserPicker(state, viewModel, fieldModifier)
                else -> CredentialsForm(state, viewModel, fieldModifier)
            }

            state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }

            TextButton(onClick = leave) { Text(stringResource(R.string.sign_in_change_server)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserPicker(state: SignInUiState, viewModel: SignInViewModel, fieldModifier: Modifier) {
    Text(stringResource(R.string.sign_in_pick_user), style = MaterialTheme.typography.titleMedium)
    FlowRow(
        modifier = fieldModifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.publicUsers.forEach { user ->
            Column(
                modifier = Modifier
                    .width(96.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(enabled = !state.isBusy) { viewModel.selectUser(user) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                UserAvatar(user.name, size = 64.dp)
                Text(user.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (state.isSigningIn) LinearProgressIndicator(modifier = fieldModifier)
    OutlinedButton(onClick = viewModel::enterUsernameManually, enabled = !state.isBusy, modifier = fieldModifier) {
        Text(stringResource(R.string.sign_in_enter_username))
    }
    OutlinedButton(onClick = viewModel::startQuickConnect, enabled = !state.isBusy, modifier = fieldModifier) {
        Text(stringResource(R.string.quick_connect_button))
    }
}

/** Initial-letter avatar; profile pictures can replace this once an image loader is added. */
@Composable
private fun UserAvatar(name: String, size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun CredentialsForm(state: SignInUiState, viewModel: SignInViewModel, fieldModifier: Modifier) {
    val selected = state.selectedUser
    if (selected != null) {
        UserAvatar(selected.name, size = 72.dp)
        Text(selected.name, style = MaterialTheme.typography.titleLarge)
    } else {
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            modifier = fieldModifier,
            label = { Text(stringResource(R.string.sign_in_username)) },
            singleLine = true,
            enabled = !state.isBusy,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
        )
    }
    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        modifier = fieldModifier,
        label = { Text(stringResource(R.string.sign_in_password)) },
        singleLine = true,
        enabled = !state.isBusy,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.signIn() }),
    )
    val canSignIn = selected != null || state.username.isNotBlank()
    Button(onClick = viewModel::signIn, enabled = !state.isBusy && canSignIn, modifier = fieldModifier) {
        if (state.isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(R.string.sign_in_button))
        }
    }
    if (state.publicUsers.isNotEmpty()) {
        TextButton(onClick = viewModel::backToUserList, enabled = !state.isBusy) {
            Text(stringResource(R.string.sign_in_back_to_users))
        }
    }
    Text(stringResource(R.string.sign_in_or), style = MaterialTheme.typography.labelMedium)
    OutlinedButton(onClick = viewModel::startQuickConnect, enabled = !state.isBusy, modifier = fieldModifier) {
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
