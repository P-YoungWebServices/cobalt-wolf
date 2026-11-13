package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.QuickConnectState
import com.pyoungws.bluewolfplayer.shared.auth.SignInError
import com.pyoungws.bluewolfplayer.shared.auth.SignInResult
import com.pyoungws.bluewolfplayer.shared.model.PublicUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignInUiState(
    val isLoadingUsers: Boolean = true,
    /** Users the server lists on its login screen. */
    val publicUsers: List<PublicUser> = emptyList(),
    /** The tile the user tapped; only the password is asked for. */
    val selectedUser: PublicUser? = null,
    /** The user chose to type a username instead of picking from the list. */
    val manualEntry: Boolean = false,
    val username: String = "",
    val password: String = "",
    val isSigningIn: Boolean = false,
    val error: SignInError? = null,
    /** Null when Quick Connect is not in progress. */
    val quickConnect: QuickConnectState? = null,
    /** True after a password-reset PIN was requested for the current username. */
    val passwordResetSent: Boolean = false,
) {
    val isBusy: Boolean get() = isSigningIn || quickConnect is QuickConnectState.Starting

    /** Show the user picker; otherwise show the credentials form. */
    val showUserList: Boolean get() = !isLoadingUsers && publicUsers.isNotEmpty() && selectedUser == null && !manualEntry
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

    init {
        viewModelScope.launch {
            val users = authRepository.publicUsers(serverAddress)
            _uiState.update { it.copy(isLoadingUsers = false, publicUsers = users) }
        }
    }

    fun onUsernameChange(username: String) = _uiState.update { it.copy(username = username, error = null) }

    fun onPasswordChange(password: String) = _uiState.update { it.copy(password = password, error = null) }

    /** Emails the user a reset PIN through the server's forgot-password flow. */
    fun forgotPassword() {
        val state = _uiState.value
        val username = state.selectedUser?.name ?: state.username
        if (username.isBlank()) return
        viewModelScope.launch {
            val sent = authRepository.forgotPassword(serverAddress, username)
            _uiState.update { it.copy(passwordResetSent = sent) }
        }
    }

    /** Users without a password are signed in straight away, as on the Jellyfin web login screen. */
    fun selectUser(user: PublicUser) {
        if (_uiState.value.isBusy) return
        _uiState.update {
            it.copy(selectedUser = user, manualEntry = false, username = user.name, password = "", error = null)
        }
        if (!user.hasPassword) signIn()
    }

    fun enterUsernameManually() = _uiState.update {
        it.copy(selectedUser = null, manualEntry = true, username = "", password = "", error = null)
    }

    fun backToUserList() = _uiState.update {
        it.copy(selectedUser = null, manualEntry = false, username = "", password = "", error = null)
    }

    fun signIn() {
        val state = _uiState.value
        if (state.isBusy) return
        cancelQuickConnect()
        _uiState.update { it.copy(isSigningIn = true, error = null) }
        viewModelScope.launch {
            val username = state.selectedUser?.name ?: state.username
            when (val result = authRepository.signIn(serverAddress, username, state.password)) {
                // Don't keep credentials in memory; the form starts blank after a sign out.
                is SignInResult.Success -> resetForm()
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
                    resetForm()
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

    private fun resetForm() = _uiState.update {
        SignInUiState(isLoadingUsers = it.isLoadingUsers, publicUsers = it.publicUsers)
    }
}
