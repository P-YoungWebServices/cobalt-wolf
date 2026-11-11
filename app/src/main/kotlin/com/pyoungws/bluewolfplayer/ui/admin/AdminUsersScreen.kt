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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.pyoungws.bluewolfplayer.shared.admin.AdminUser
import com.pyoungws.bluewolfplayer.shared.admin.AdminUserEdit
import com.pyoungws.bluewolfplayer.shared.presentation.AdminActionStatus
import com.pyoungws.bluewolfplayer.shared.presentation.AdminUsersUiState
import com.pyoungws.bluewolfplayer.shared.presentation.AdminUsersViewModel
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.formatTimestamp
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel

/** User management: list accounts, create new ones, edit them and delete them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(
    onBack: () -> Unit,
    viewModel: AdminUsersViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AdminUser?>(null) }
    var deleting by remember { mutableStateOf<AdminUser?>(null) }

    // Close the create/edit/delete dialogs once their action succeeded.
    LaunchedEffect(state.action) {
        when (val action = state.action) {
            is AdminActionStatus.Succeeded -> when {
                action.key == AdminUsersViewModel.CREATE_KEY -> creating = false
                action.key == "${AdminUsersViewModel.EDIT_KEY}${editing?.id}" -> editing = null
                action.key == "${AdminUsersViewModel.DELETE_KEY}${deleting?.id}" -> deleting = null
            }
            else -> Unit
        }
        if (state.action is AdminActionStatus.Succeeded) viewModel.consumeAction()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_users_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
                actions = {
                    TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) }
                    TextButton(onClick = { creating = true }) { Text(stringResource(R.string.admin_users_new)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> LoadingBox()
                state.error != null && state.users.isEmpty() -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
                else -> UserList(state, onEdit = { editing = it }, onDelete = { deleting = it })
            }
        }
    }

    if (creating) {
        UserDialog(
            title = stringResource(R.string.admin_user_create_title),
            action = state.action,
            actionKey = AdminUsersViewModel.CREATE_KEY,
            onSubmit = { name, password, _, _, _, _ -> viewModel.createUser(name.orEmpty(), password.orEmpty()) },
            onDismiss = { if (state.action !is AdminActionStatus.Running) creating = false },
        )
    }
    editing?.let { user ->
        UserDialog(
            title = stringResource(R.string.admin_user_edit_title, user.name),
            action = state.action,
            actionKey = "${AdminUsersViewModel.EDIT_KEY}${user.id}",
            user = user,
            onSubmit = { name, password, clearPassword, admin, disabled, hidden ->
                viewModel.updateUser(
                    user.id,
                    AdminUserEdit(
                        name = name?.takeIf { it != user.name },
                        isAdministrator = admin?.takeIf { it != user.isAdministrator },
                        isDisabled = disabled?.takeIf { it != user.isDisabled },
                        isHidden = hidden?.takeIf { it != user.isHidden },
                        password = if (clearPassword) "" else password,
                    ),
                )
            },
            onDismiss = { if (state.action !is AdminActionStatus.Running) editing = null },
        )
    }
    deleting?.let { user ->
        val key = "${AdminUsersViewModel.DELETE_KEY}${user.id}"
        val busy = (state.action as? AdminActionStatus.Running)?.key == key
        val failed = (state.action as? AdminActionStatus.Failed)?.takeIf { it.key == key }
        AlertDialog(
            onDismissRequest = { if (!busy) deleting = null },
            title = { Text(stringResource(R.string.admin_user_delete_title, user.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.admin_user_delete_message))
                    failed?.let {
                        Text(it.error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteUser(user.id) }, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.admin_delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }, enabled = !busy) { Text(stringResource(R.string.home_sign_out_cancel)) } },
        )
    }
}

@Composable
private fun UserList(state: AdminUsersUiState, onEdit: (AdminUser) -> Unit, onDelete: (AdminUser) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.users, key = { it.id }) { user ->
            UserCard(user, onEdit = { onEdit(user) }, onDelete = { onDelete(user) })
        }
    }
}

@Composable
private fun UserCard(user: AdminUser, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(user.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        userBadges(user).ifEmpty { stringResource(R.string.admin_user_no_flags) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.admin_edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.admin_delete)) }
            }
            user.lastLogin?.let {
                Text(
                    stringResource(R.string.admin_last_login, formatTimestamp(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun userBadges(user: AdminUser): String = buildList {
    if (user.isAdministrator) add(stringResource(R.string.admin_user_admin))
    if (user.isDisabled) add(stringResource(R.string.admin_user_disabled))
    if (user.isHidden) add(stringResource(R.string.admin_user_hidden))
    if (!user.hasPassword) add(stringResource(R.string.admin_user_no_password))
}.joinToString(" · ")

/** Shared form for creating and editing a user. [user] is null when creating. */
@Composable
private fun UserDialog(
    title: String,
    action: AdminActionStatus?,
    actionKey: String,
    user: AdminUser? = null,
    onSubmit: (name: String?, password: String?, clearPassword: Boolean, admin: Boolean?, disabled: Boolean?, hidden: Boolean?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(user?.id) { mutableStateOf(user?.name.orEmpty()) }
    var password by rememberSaveable(user?.id) { mutableStateOf("") }
    var clearPassword by rememberSaveable(user?.id) { mutableStateOf(false) }
    var admin by rememberSaveable(user?.id) { mutableStateOf(user?.isAdministrator == true) }
    var disabled by rememberSaveable(user?.id) { mutableStateOf(user?.isDisabled == true) }
    var hidden by rememberSaveable(user?.id) { mutableStateOf(user?.isHidden == true) }

    val busy = (action as? AdminActionStatus.Running)?.key == actionKey
    val failed = (action as? AdminActionStatus.Failed)?.takeIf { it.key == actionKey }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.admin_user_name)) },
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = {
                        Text(
                            stringResource(
                                if (user == null) R.string.admin_user_password
                                else R.string.admin_user_new_password,
                            ),
                        )
                    },
                    enabled = !busy && !clearPassword,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (user != null) {
                    FlagCheckbox(stringResource(R.string.admin_user_clear_password), clearPassword, !busy) { clearPassword = it }
                }
                FlagCheckbox(stringResource(R.string.admin_user_admin), admin, !busy) { admin = it }
                FlagCheckbox(stringResource(R.string.admin_user_disabled), disabled, !busy) { disabled = it }
                FlagCheckbox(stringResource(R.string.admin_user_hidden), hidden, !busy) { hidden = it }
                failed?.let {
                    Text(it.error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (user == null) {
                        onSubmit(name, password, false, admin, disabled, hidden)
                    } else {
                        onSubmit(name, password.ifBlank { null }, clearPassword, admin, disabled, hidden)
                    }
                },
                enabled = name.isNotBlank() && !busy,
            ) {
                if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(if (user == null) R.string.admin_create else R.string.admin_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.home_sign_out_cancel)) } },
    )
}

@Composable
private fun FlagCheckbox(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
