package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.admin.AdminRepository
import com.pyoungws.bluewolfplayer.shared.admin.ServerApiKey
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminApiKeysUiState(
    val isLoading: Boolean = true,
    val keys: List<ServerApiKey> = emptyList(),
    val error: LoadError? = null,
    val action: AdminActionStatus? = null,
)

/** API key management: list registered keys, create and revoke them. */
class AdminApiKeysViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminApiKeysUiState())
    val uiState: StateFlow<AdminApiKeysUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { loadKeys() }
    }

    fun createKey(appName: String) {
        if (appName.isBlank()) return
        runAction("$CREATE_KEY$appName") {
            when (val result = repository.createApiKey(appName.trim())) {
                is Outcome.Success -> null.also { loadKeys() }
                is Outcome.Failure -> result.error
            }
        }
    }

    fun revokeKey(key: ServerApiKey) {
        runAction("$REVOKE_KEY${key.id}") {
            when (val result = repository.revokeApiKey(key.accessToken)) {
                is Outcome.Success -> null.also { loadKeys() }
                is Outcome.Failure -> result.error
            }
        }
    }

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

    private suspend fun loadKeys() {
        when (val result = repository.apiKeys()) {
            is Outcome.Success -> _uiState.update { it.copy(isLoading = false, keys = result.value, error = null) }
            is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
        }
    }

    companion object {
        const val CREATE_KEY = "create:"
        const val REVOKE_KEY = "revoke:"
    }
}
