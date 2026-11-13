package com.pyoungws.cobaltwolf.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.cobaltwolf.shared.model.ServerInfo
import com.pyoungws.cobaltwolf.shared.server.ServerError
import com.pyoungws.cobaltwolf.shared.server.ServerRepository
import com.pyoungws.cobaltwolf.shared.server.ServerValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerConnectUiState(
    val address: String = "",
    val isValidating: Boolean = false,
    val error: ServerError? = null,
    /** Set once validation succeeds; the UI navigates and then calls [ServerConnectViewModel.onServerConsumed]. */
    val validatedServer: ServerInfo? = null,
)

class ServerConnectViewModel(private val serverRepository: ServerRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ServerConnectUiState())
    val uiState: StateFlow<ServerConnectUiState> = _uiState.asStateFlow()

    fun onAddressChange(address: String) = _uiState.update { it.copy(address = address, error = null) }

    fun connect() {
        val state = _uiState.value
        if (state.isValidating) return
        _uiState.update { it.copy(isValidating = true, error = null) }
        viewModelScope.launch {
            val result = serverRepository.validate(state.address)
            _uiState.update {
                when (result) {
                    is ServerValidationResult.Valid -> it.copy(isValidating = false, validatedServer = result.server)
                    is ServerValidationResult.Invalid -> it.copy(isValidating = false, error = result.error)
                }
            }
        }
    }

    fun onServerConsumed() = _uiState.update { it.copy(validatedServer = null) }
}
