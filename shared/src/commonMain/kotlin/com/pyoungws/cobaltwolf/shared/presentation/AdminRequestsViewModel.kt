package com.pyoungws.cobaltwolf.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import com.pyoungws.cobaltwolf.shared.seerr.SeerrRepository
import com.pyoungws.cobaltwolf.shared.seerr.SeerrRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** The details sheet for one request; [requestId] is always known even while loading. */
sealed interface AdminRequestDetails {
    val requestId: Int

    data class Loading(override val requestId: Int) : AdminRequestDetails
    data class Loaded(override val requestId: Int, val request: SeerrRequest) : AdminRequestDetails
    data class Failed(override val requestId: Int, val error: LoadError) : AdminRequestDetails
}

data class AdminRequestsUiState(
    val isLoading: Boolean = true,
    val requests: List<SeerrRequest> = emptyList(),
    /** Set on a failed (re)load; earlier data is kept so the list does not blank on one bad poll. */
    val error: LoadError? = null,
    val action: AdminActionStatus? = null,
    /** Non-null while the details sheet is open. */
    val details: AdminRequestDetails? = null,
)

/**
 * The admin request approval queue: pending Seerr requests from every user, with approve,
 * decline and a details sheet. Re-fetches every [POLL_SECONDS] while the screen is open.
 */
class AdminRequestsViewModel(private val repository: SeerrRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminRequestsUiState())
    val uiState: StateFlow<AdminRequestsUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private var detailsJob: Job? = null

    init {
        pollJob = viewModelScope.launch {
            while (isActive) {
                loadRequests()
                delay(POLL_SECONDS.seconds)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { loadRequests() }
    }

    fun approve(requestId: Int) = runAction("$APPROVE_KEY$requestId") { repository.approveRequest(requestId) }

    fun decline(requestId: Int) = runAction("$DECLINE_KEY$requestId") { repository.declineRequest(requestId) }

    fun openDetails(requestId: Int) {
        detailsJob?.cancel()
        _uiState.update { it.copy(details = AdminRequestDetails.Loading(requestId)) }
        detailsJob = viewModelScope.launch {
            val details = when (val result = repository.requestDetails(requestId)) {
                is Outcome.Success -> AdminRequestDetails.Loaded(requestId, result.value)
                is Outcome.Failure -> AdminRequestDetails.Failed(requestId, result.error)
            }
            _uiState.update { it.copy(details = details) }
        }
    }

    fun closeDetails() {
        detailsJob?.cancel()
        _uiState.update { it.copy(details = null) }
    }

    fun consumeAction() = _uiState.update { it.copy(action = null) }

    private fun runAction(key: String, block: suspend () -> Outcome<SeerrRequest>) {
        if (_uiState.value.action is AdminActionStatus.Running) return
        _uiState.update { it.copy(action = AdminActionStatus.Running(key)) }
        viewModelScope.launch {
            val error = when (val result = block()) {
                is Outcome.Success -> null
                is Outcome.Failure -> result.error
            }
            if (error == null) {
                loadRequests()
                // Keep an open details sheet on the acted-on request in sync with its new state.
                _uiState.value.details?.requestId?.let(::openDetails)
            }
            _uiState.update {
                it.copy(action = error?.let { e -> AdminActionStatus.Failed(key, e) } ?: AdminActionStatus.Succeeded(key))
            }
        }
    }

    private suspend fun loadRequests() {
        when (val result = repository.pendingRequests()) {
            is Outcome.Success -> _uiState.update {
                it.copy(isLoading = false, requests = result.value, error = null)
            }
            is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
        }
    }

    companion object {
        const val POLL_SECONDS = 30
        const val APPROVE_KEY = "approve:"
        const val DECLINE_KEY = "decline:"
    }
}
