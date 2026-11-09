package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.QuickConnectState
import com.pyoungws.bluewolfplayer.shared.auth.SignInError
import com.pyoungws.bluewolfplayer.shared.auth.SignInResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignInUiState(
    val username: String = "",
    val password: String = "",
    val isSigningIn: Boolean = false,
    val error: SignInError? = null,
    /** Null when Quick Connect is not in progress. */
    val quickConnect: QuickConnectState? = null,
) {
    val isBusy: Boolean get() = isSigningIn || quickConnect is QuickConnectState.Starting
}

/**
 * Sign-in for a validated server. Success is observed through [AuthRepository.sessionState],
 * which switches the app to the signed-in UI.
 */
class SignInViewModel(
    private val serverAddress: String,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    private var quickConnectJob: Job? = null

    fun onUsernameChange(username: String) = _uiState.update { it.copy(username = username, error = null) }

    fun onPasswordChange(password: String) = _uiState.update { it.copy(password = password, error = null) }

    fun signIn() {
        val state = _uiState.value
        if (state.isBusy) return
        cancelQuickConnect()
        _uiState.update { it.copy(isSigningIn = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.signIn(serverAddress, state.username, state.password)) {
                // Don't keep credentials in memory; the form starts blank after a sign out.
                is SignInResult.Success -> _uiState.value = SignInUiState()
                is SignInResult.Failure -> _uiState.update {
                    it.copy(
                        isSigningIn = false,
                        error = result.error,
                        password = if (result.error == SignInError.InvalidCredentials) "" else it.password,
                    )
                }
            }
        }
    }

    fun startQuickConnect() {
        if (_uiState.value.isBusy) return
        quickConnectJob?.cancel()
        _uiState.update { it.copy(error = null) }
        quickConnectJob = viewModelScope.launch {
            authRepository.quickConnect(serverAddress).collect { state ->
                if (state is QuickConnectState.SignedIn) {
                    _uiState.value = SignInUiState()
                } else {
                    _uiState.update { it.copy(quickConnect = state) }
                }
            }
        }
    }

    fun cancelQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectJob = null
        _uiState.update { it.copy(quickConnect = null) }
    }
}
