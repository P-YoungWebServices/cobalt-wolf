package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.admin.AdminRepository
import com.pyoungws.bluewolfplayer.shared.admin.ServerSettings
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminServerSettingsUiState(
    val isLoading: Boolean = true,
    val settings: ServerSettings? = null,
    val error: LoadError? = null,
    val action: AdminActionStatus? = null,
)

/** Server-level settings: display name plus restart/shutdown controls. */
class AdminServerSettingsViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminServerSettingsUiState())
    val uiState: StateFlow<AdminServerSettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun saveServerName(name: String) {
        if (name.isBlank()) return
        runAction("save") {
            when (val result = repository.updateServerSettings(ServerSettings(name.trim()))) {
                is Outcome.Success -> null.also { load() }
                is Outcome.Failure -> result.error
            }
        }
    }

    fun restart() = runAction("restart") {
        when (val result = repository.restartServer()) {
            is Outcome.Success -> null
            is Outcome.Failure -> result.error
        }
    }

    fun shutdown() = runAction("shutdown") {
        when (val result = repository.shutdownServer()) {
            is Outcome.Success -> null
            is Outcome.Failure -> result.error
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

    private suspend fun load() {
        when (val result = repository.serverSettings()) {
            is Outcome.Success -> _uiState.update { it.copy(isLoading = false, settings = result.value, error = null) }
            is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
        }
    }
}
