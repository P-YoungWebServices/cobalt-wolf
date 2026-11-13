package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.admin.AdminPlugin
import com.pyoungws.bluewolfplayer.shared.admin.AdminRepository
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminPluginsUiState(
    val isLoading: Boolean = true,
    val plugins: List<AdminPlugin> = emptyList(),
    val error: LoadError? = null,
    val action: AdminActionStatus? = null,
)

/** Installed plugin management: list, enable/disable and uninstall. */
class AdminPluginsViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminPluginsUiState())
    val uiState: StateFlow<AdminPluginsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { loadPlugins() }
    }

    fun setEnabled(plugin: AdminPlugin, enabled: Boolean) {
        runAction("toggle:${plugin.id}") {
            when (val result = repository.setPluginEnabled(plugin.id, plugin.version, enabled)) {
                is Outcome.Success -> null.also { loadPlugins() }
                is Outcome.Failure -> result.error
            }
        }
    }

    fun uninstall(plugin: AdminPlugin) {
        runAction("uninstall:${plugin.id}") {
            when (val result = repository.uninstallPlugin(plugin.id)) {
                is Outcome.Success -> null.also { loadPlugins() }
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

    private suspend fun loadPlugins() {
        when (val result = repository.plugins()) {
            is Outcome.Success -> _uiState.update { it.copy(isLoading = false, plugins = result.value, error = null) }
            is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
        }
    }
}
