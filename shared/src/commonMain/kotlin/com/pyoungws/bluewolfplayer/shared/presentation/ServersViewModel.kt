package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.ServerProfile
import com.pyoungws.bluewolfplayer.shared.auth.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ServersUiState(
    val profiles: List<ServerProfile> = emptyList(),
    /** The profile matching the live session — which server+account the app is using right now. */
    val activeProfileId: String? = null,
    /** A switch is in flight. */
    val busy: Boolean = false,
)

/**
 * The saved-server/account switcher. Profiles come from [AuthRepository.profiles]; switching only
 * swaps the stored session, every other part of the app follows `sessionState` on its own.
 */
class ServersViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val busy = MutableStateFlow(false)

    val uiState: StateFlow<ServersUiState> =
        combine(authRepository.profiles, authRepository.sessionState, busy) { profiles, sessionState, busy ->
            val session = (sessionState as? SessionState.SignedIn)?.session
            val active = profiles.find { it.serverAddress == session?.serverAddress && it.userId == session.userId }
            ServersUiState(profiles = profiles, activeProfileId = active?.id, busy = busy)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ServersUiState())

    fun switchTo(profileId: String) {
        viewModelScope.launch {
            busy.value = true
            try {
                authRepository.switchTo(profileId)
            } finally {
                busy.value = false
            }
        }
    }

    fun remove(profileId: String) {
        viewModelScope.launch { authRepository.removeProfile(profileId) }
    }
}
