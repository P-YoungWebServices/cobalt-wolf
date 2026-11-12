package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.arrs.ArrRepository
import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.arrs.UpcomingItem
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
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

    /** Which layout the screen is showing; drives the fetched date range. */
    private val _mode = MutableStateFlow(CalendarMode.List)
    val mode = _mode.asStateFlow()

    /** The week/month currently on screen. */
    private val _anchor = MutableStateFlow(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    val anchor = _anchor.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { repository.settings.drop(1).collect { refresh() } }
    }

    /** Switches view (or steps to another week/month) and reloads the matching date range. */
    fun show(mode: CalendarMode, anchor: LocalDate) {
        _mode.value = mode
        _anchor.value = anchor
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (repository.configuredServices.none { it.managesMedia }) {
                _uiState.value = CalendarUiState.NotConfigured
                return@launch
            }
            _uiState.value = when (val current = _uiState.value) {
                is CalendarUiState.Ready -> current.copy(refreshing = true)
                else -> CalendarUiState.Loading
            }
            val range = _mode.value.rangeAround(_anchor.value)
            _uiState.value = when (val outcome = repository.calendar(range?.first, range?.second)) {
                is Outcome.Success -> CalendarUiState.Ready(outcome.value.items, outcome.value.failedServices)
                is Outcome.Failure -> CalendarUiState.Error(outcome.error)
            }
        }
    }
}
