package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.SessionState
import com.pyoungws.bluewolfplayer.shared.auth.SignInError
import com.pyoungws.bluewolfplayer.shared.auth.SignInResult
import com.pyoungws.bluewolfplayer.shared.model.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Loaded(val user: UserProfile) : HomeUiState
    data class Error(val error: SignInError) : HomeUiState
}

class HomeViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut: StateFlow<Boolean> = _isSigningOut.asStateFlow()

    private var loadJob: Job? = null

    init {
        // Reload for every new session; drop the previous user's data as soon as they sign out.
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                if (state is SessionState.SignedIn) {
                    load()
                } else {
                    loadJob?.cancel()
                    _uiState.value = HomeUiState.Loading
                }
            }
        }
    }

    fun load() {
        loadJob?.cancel()
        _uiState.value = HomeUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = when (val result = authRepository.currentUser()) {
                is SignInResult.Success -> HomeUiState.Loaded(result.user)
                is SignInResult.Failure -> HomeUiState.Error(result.error)
            }
        }
    }

    fun signOut() {
        if (_isSigningOut.value) return
        _isSigningOut.value = true
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } finally {
                _isSigningOut.value = false
            }
        }
    }
}
