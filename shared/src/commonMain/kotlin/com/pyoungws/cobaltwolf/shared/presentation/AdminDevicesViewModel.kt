package com.pyoungws.cobaltwolf.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.cobaltwolf.shared.admin.AdminDevice
import com.pyoungws.cobaltwolf.shared.admin.AdminRepository
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminDevicesUiState(
    val isLoading: Boolean = true,
    val devices: List<AdminDevice> = emptyList(),
    val error: LoadError? = null,
    val action: AdminActionStatus? = null,
)

/** Device management: list devices the server knows, rename them, remove them. */
class AdminDevicesViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminDevicesUiState())
    val uiState: StateFlow<AdminDevicesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { loadDevices() }
    }

    fun renameDevice(deviceId: String, name: String) {
        if (name.isBlank()) return
        runAction("$RENAME_KEY$deviceId") {
            when (val result = repository.renameDevice(deviceId, name)) {
                is Outcome.Success -> null.also { loadDevices() }
                is Outcome.Failure -> result.error
            }
        }
    }

    fun deleteDevice(deviceId: String) {
        runAction("$DELETE_KEY$deviceId") {
            when (val result = repository.deleteDevice(deviceId)) {
                is Outcome.Success -> null.also { loadDevices() }
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

    private suspend fun loadDevices() {
        when (val result = repository.devices()) {
            is Outcome.Success -> _uiState.update {
                it.copy(isLoading = false, devices = result.value, error = null)
            }
            is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
        }
    }

    companion object {
        const val RENAME_KEY = "rename:"
        const val DELETE_KEY = "delete:"
    }
}
