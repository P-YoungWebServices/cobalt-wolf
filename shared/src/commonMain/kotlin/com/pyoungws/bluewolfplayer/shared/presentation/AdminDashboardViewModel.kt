package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.admin.ActiveSession
import com.pyoungws.bluewolfplayer.shared.admin.AdminRepository
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** Status of the "message this session" action; null when nothing is in flight or just happened. */
sealed interface MessageStatus {
    data class Sending(val sessionId: String) : MessageStatus
    data class Sent(val sessionId: String) : MessageStatus
    data class Failed(val sessionId: String, val error: LoadError) : MessageStatus
}

data class AdminDashboardUiState(
    val isLoading: Boolean = true,
    val sessions: List<ActiveSession> = emptyList(),
    val recentlyAdded: List<MediaItem> = emptyList(),
    /** Set on a failed (re)load; earlier data is kept so the screen does not blank on one bad poll. */
    val error: LoadError? = null,
    val refreshSeconds: Int = AdminDashboardViewModel.DEFAULT_REFRESH_SECONDS,
    val message: MessageStatus? = null,
)

/** Admin dashboard: sessions and recently added media, re-fetched every [AdminDashboardUiState.refreshSeconds]. */
class AdminDashboardViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminDashboardUiState())
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        restartPolling()
    }

    /** Changes the auto-refresh interval and applies it right away (the next poll uses it). */
    fun setRefreshSeconds(seconds: Int) {
        require(seconds > 0) { "refresh interval must be positive" }
        if (seconds == _uiState.value.refreshSeconds) return
        _uiState.update { it.copy(refreshSeconds = seconds) }
        restartPolling()
    }

    /** One-off reload, e.g. pull-to-refresh; the poll loop keeps its own schedule. */
    fun refresh() {
        viewModelScope.launch { loadOnce() }
    }

    fun sendMessage(sessionId: String, text: String) {
        if (text.isBlank() || _uiState.value.message is MessageStatus.Sending) return
        _uiState.update { it.copy(message = MessageStatus.Sending(sessionId)) }
        viewModelScope.launch {
            val status = when (val result = repository.sendMessage(sessionId, text)) {
                is Outcome.Success -> MessageStatus.Sent(sessionId)
                is Outcome.Failure -> MessageStatus.Failed(sessionId, result.error)
            }
            _uiState.update { it.copy(message = status) }
        }
    }

    /** Clears the last message status after the UI has reacted to it. */
    fun consumeMessageStatus() = _uiState.update { it.copy(message = null) }

    private fun restartPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                loadOnce()
                delay(_uiState.value.refreshSeconds.seconds)
            }
        }
    }

    private suspend fun loadOnce() {
        when (val result = repository.dashboard()) {
            is Outcome.Success -> _uiState.update {
                it.copy(
                    isLoading = false,
                    sessions = result.value.sessions,
                    recentlyAdded = result.value.recentlyAdded,
                    error = null,
                )
            }
            is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
        }
    }

    companion object {
        const val DEFAULT_REFRESH_SECONDS = 10
        val REFRESH_OPTIONS_SECONDS = listOf(5, 10, 30, 60)
    }
}
