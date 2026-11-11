package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.admin.AdminRepository
import com.pyoungws.bluewolfplayer.shared.admin.AdminUser
import com.pyoungws.bluewolfplayer.shared.admin.AdminUserEdit
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUsersUiState(
    val isLoading: Boolean = true,
    val users: List<AdminUser> = emptyList(),
    /** Set on a failed (re)load; earlier data is kept so the list does not blank on one bad fetch. */
    val error: LoadError? = null,
    val action: AdminActionStatus? = null,
)

/** User management: list accounts, create new ones, edit name/policy/password and delete. */
class AdminUsersViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminUsersUiState())
    val uiState: StateFlow<AdminUsersUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { loadUsers() }
    }

    /** Ignored when [name] is blank; [password] may be left empty for a passwordless account. */
    fun createUser(name: String, password: String) {
        if (name.isBlank()) return
        runAction(CREATE_KEY) {
            when (val result = repository.createUser(name, password)) {
                is Outcome.Success -> null.also { loadUsers() }
                is Outcome.Failure -> result.error
            }
        }
    }

    fun updateUser(userId: String, edit: AdminUserEdit) {
        val name = edit.name
        if (name != null && name.isBlank()) return
        runAction("$EDIT_KEY$userId") {
            when (val result = repository.updateUser(userId, edit)) {
                is Outcome.Success -> null.also { loadUsers() }
                is Outcome.Failure -> result.error
            }
        }
    }

    fun deleteUser(userId: String) {
        runAction("$DELETE_KEY$userId") {
            when (val result = repository.deleteUser(userId)) {
                is Outcome.Success -> null.also { loadUsers() }
                is Outcome.Failure -> result.error
            }
        }
    }

    /** Clears the last action status after the UI has reacted to it. */
    fun consumeAction() = _uiState.update { it.copy(action = null) }

    private fun runAction(key: String, block: suspend () -> LoadError?) {
        if (_uiState.value.action is AdminActionStatus.Running) return
        _uiState.update { it.copy(action = AdminActionStatus.Running(key)) }
        viewModelScope.launch {
            val error = block()
            _uiState.update {
                it.copy(action = error?.let { e -> AdminActionStatus.Failed(key, e) } ?: AdminActionStatus.Succeeded(key))
            }
        }
    }

    private suspend fun loadUsers() {
        when (val result = repository.users()) {
            is Outcome.Success -> _uiState.update {
                it.copy(isLoading = false, users = result.value, error = null)
            }
            is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
        }
    }

    companion object {
        const val CREATE_KEY = "create"
        const val EDIT_KEY = "edit:"
        const val DELETE_KEY = "delete:"
    }
}
