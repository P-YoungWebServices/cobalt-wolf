package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrMediaStatus
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrMediaType
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrRepository
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrRequest
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrRequestStatus
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrTitleDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RequestActionState {
    data object Idle : RequestActionState
    data object InProgress : RequestActionState
    data class Done(val request: SeerrRequest) : RequestActionState
    data class Failed(val error: LoadError) : RequestActionState
}

sealed interface SeerrDetailUiState {
    data object Loading : SeerrDetailUiState
    data class Error(val error: LoadError) : SeerrDetailUiState
    data class Loaded(
        val details: SeerrTitleDetails,
        val selectedSeasons: Set<Int>,
        val requestState: RequestActionState = RequestActionState.Idle,
    ) : SeerrDetailUiState {
        /** Seasons that can still be requested (not already on the server). */
        val requestableSeasons get() = details.seasons.filter { !it.isAvailable }
    }
}

/** Title details and the request flow: season selection for series, a single button for movies. */
class SeerrDetailViewModel(
    private val type: SeerrMediaType,
    private val tmdbId: Int,
    private val repository: SeerrRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SeerrDetailUiState>(SeerrDetailUiState.Loading)
    val uiState: StateFlow<SeerrDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = SeerrDetailUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.details(type, tmdbId)) {
                is Outcome.Failure -> SeerrDetailUiState.Error(result.error)
                is Outcome.Success -> {
                    val details = result.value
                    SeerrDetailUiState.Loaded(
                        details = details,
                        selectedSeasons = details.seasons.filter { !it.isAvailable }.map { it.seasonNumber }.toSet(),
                    )
                }
            }
        }
    }

    fun toggleSeason(seasonNumber: Int) = updateLoaded { state ->
        state.copy(
            selectedSeasons = if (seasonNumber in state.selectedSeasons) {
                state.selectedSeasons - seasonNumber
            } else {
                state.selectedSeasons + seasonNumber
            },
        )
    }

    fun selectAllSeasons() = updateLoaded { state ->
        state.copy(selectedSeasons = state.requestableSeasons.map { it.seasonNumber }.toSet())
    }

    fun request() {
        val state = _uiState.value as? SeerrDetailUiState.Loaded ?: return
        if (state.requestState == RequestActionState.InProgress) return
        if (type == SeerrMediaType.Series && state.selectedSeasons.isEmpty()) return
        _uiState.value = state.copy(requestState = RequestActionState.InProgress)
        viewModelScope.launch {
            val seasons = if (type == SeerrMediaType.Series) state.selectedSeasons.sorted() else null
            _uiState.value = when (val result = repository.request(type, tmdbId, seasons)) {
                is Outcome.Failure -> state.copy(requestState = RequestActionState.Failed(result.error))
                is Outcome.Success -> {
                    // Reflect the fresh request immediately, even before a re-fetch.
                    val title = state.details.title.copy(
                        mediaStatus = result.value.mediaStatus.takeUnless { it == SeerrMediaStatus.NotRequested }
                            ?: SeerrMediaStatus.Pending,
                        requestStatus = SeerrRequestStatus.Pending,
                    )
                    state.copy(
                        details = state.details.copy(title = title),
                        requestState = RequestActionState.Done(result.value),
                    )
                }
            }
        }
    }

    /** Dismisses a Done/Failed banner so the user can request more seasons. */
    fun acknowledgeRequest() = updateLoaded { state ->
        if (state.requestState is RequestActionState.InProgress) state else state.copy(requestState = RequestActionState.Idle)
    }

    private inline fun updateLoaded(crossinline transform: (SeerrDetailUiState.Loaded) -> SeerrDetailUiState) {
        (_uiState.value as? SeerrDetailUiState.Loaded)?.let { _uiState.value = transform(it) }
    }
}
