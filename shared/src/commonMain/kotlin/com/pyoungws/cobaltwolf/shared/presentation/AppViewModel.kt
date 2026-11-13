package com.pyoungws.cobaltwolf.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.cobaltwolf.shared.auth.AuthRepository
import com.pyoungws.cobaltwolf.shared.auth.SessionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Root state: restores any stored session on start and exposes whether a user is signed in. */
class AppViewModel(private val authRepository: AuthRepository) : ViewModel() {
    val sessionState: StateFlow<SessionState> = authRepository.sessionState

    init {
        viewModelScope.launch { authRepository.restoreSession() }
    }
}
