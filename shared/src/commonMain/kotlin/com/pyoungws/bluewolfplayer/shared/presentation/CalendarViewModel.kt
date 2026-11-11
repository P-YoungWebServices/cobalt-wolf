package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.arrs.ArrRepository
import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.arrs.UpcomingItem
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

sealed interface CalendarUiState {
    data object Loading : CalendarUiState
    /** No service has both a URL and an API key yet. */
    data object NotConfigured : CalendarUiState
    data class Ready(
        val items: List<UpcomingItem>,
        val failedServices: List<ArrService>,
        val refreshing: Boolean = false,
    ) : CalendarUiState
    data class Error(val error: LoadError) : CalendarUiState
}

/** The merged Radarr/Sonarr upcoming calendar; reloads when the service settings change. */
class CalendarViewModel(
    private val repository: ArrRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { repository.settings.drop(1).collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            if (repository.configuredServices.isEmpty()) {
                _uiState.value = CalendarUiState.NotConfigured
                return@launch
            }
            _uiState.value = when (val current = _uiState.value) {
                is CalendarUiState.Ready -> current.copy(refreshing = true)
                else -> CalendarUiState.Loading
            }
            _uiState.value = when (val outcome = repository.calendar()) {
                is Outcome.Success -> CalendarUiState.Ready(outcome.value.items, outcome.value.failedServices)
                is Outcome.Failure -> CalendarUiState.Error(outcome.error)
            }
        }
    }
}
