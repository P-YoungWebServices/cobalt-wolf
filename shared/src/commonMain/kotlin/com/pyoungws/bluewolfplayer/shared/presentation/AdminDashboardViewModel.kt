package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.admin.ActiveSession
import com.pyoungws.bluewolfplayer.shared.admin.AdminRepository
import com.pyoungws.bluewolfplayer.shared.bazarr.BazarrRepository
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.prowlarr.ProwlarrRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

/** One-line status for a service action card on the dashboard. */
data class ServiceBadge(
    /** False until the service has a usable URL + API key; the card then invites setup. */
    val configured: Boolean = false,
    /** The headline count (wanted subtitles, indexers); null while loading or after a failure. */
    val count: Int? = null,
    /** Secondary count when the service reports problems (Prowlarr health issues). */
    val issues: Int? = null,
)

data class AdminDashboardUiState(
    val isLoading: Boolean = true,
    val sessions: List<ActiveSession> = emptyList(),
    val recentlyAdded: List<MediaItem> = emptyList(),
    /** Set on a failed (re)load; earlier data is kept so the screen does not blank on one bad poll. */
    val error: LoadError? = null,
    val refreshSeconds: Int = AdminDashboardViewModel.DEFAULT_REFRESH_SECONDS,
    val message: MessageStatus? = null,
    val bazarr: ServiceBadge = ServiceBadge(),
    val prowlarr: ServiceBadge = ServiceBadge(),
)

/** Admin dashboard: sessions and recently added media, re-fetched every [AdminDashboardUiState.refreshSeconds]. */
class AdminDashboardViewModel(
    private val repository: AdminRepository,
    private val bazarrRepository: BazarrRepository,
    private val prowlarrRepository: ProwlarrRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminDashboardUiState())
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        restartPolling()
        // The badge counts change slowly; they are fetched once up front and on refresh(),
        // not on every poll like the sessions list.
        viewModelScope.launch { loadBadges() }
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
        viewModelScope.launch {
            loadOnce()
            loadBadges()
        }
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

    /** Bazarr wanted count and Prowlarr indexer/health counts for the action cards. */
    private suspend fun loadBadges() = coroutineScope {
        val bazarr = async {
            if (!bazarrRepository.isConfigured) ServiceBadge()
            else ServiceBadge(
                configured = true,
                count = (bazarrRepository.wanted() as? Outcome.Success)?.value?.total,
            )
        }
        val prowlarr = async {
            if (!prowlarrRepository.isConfigured) ServiceBadge()
            else when (val outcome = prowlarrRepository.overview()) {
                is Outcome.Success -> ServiceBadge(
                    configured = true,
                    count = outcome.value.indexers.size,
                    issues = outcome.value.health.size.takeIf { it > 0 },
                )
                is Outcome.Failure -> ServiceBadge(configured = true)
            }
        }
        _uiState.update { it.copy(bazarr = bazarr.await(), prowlarr = prowlarr.await()) }
    }

    companion object {
        const val DEFAULT_REFRESH_SECONDS = 10
        val REFRESH_OPTIONS_SECONDS = listOf(5, 10, 30, 60)
    }
}
