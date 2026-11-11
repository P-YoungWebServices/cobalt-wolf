package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.arrs.ArrMediaRepository
import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row in the media manager list: a Radarr movie or a Sonarr series. */
data class MediaRow(
    val id: Int,
    val title: String,
    val subtitle: String?,
    val monitored: Boolean,
    val hasFile: Boolean,
    val posterUrl: String?,
)

data class MediaManagerUiState(
    /** Services with a saved URL + API key; the selected tab is always one of them. */
    val configuredServices: List<ArrService> = emptyList(),
    val service: ArrService = ArrService.Radarr,
    val isLoading: Boolean = false,
    val rows: List<MediaRow> = emptyList(),
    /** A failed (re)load; earlier rows are kept so the list does not blank on one bad try. */
    val error: LoadError? = null,
    val query: String = "",
) {
    val filteredRows: List<MediaRow>
        get() = if (query.isBlank()) rows else rows.filter { it.title.contains(query, ignoreCase = true) }
}

/**
 * The admin media manager: browses the movies/series tracked by the configured
 * Radarr/Sonarr services and is the entry point to the per-item edit screen.
 */
class MediaManagerViewModel(
    private val repository: ArrMediaRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MediaManagerUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val configured = repository.configuredServices
            _uiState.update {
                it.copy(
                    configuredServices = configured,
                    service = configured.firstOrNull() ?: ArrService.Radarr,
                )
            }
            if (configured.isNotEmpty()) load()
            else _uiState.update { it.copy(isLoading = false) }
        }
        viewModelScope.launch { repository.settings.drop(1).collect { onServicesChanged() } }
    }

    fun selectService(service: ArrService) {
        if (service == _uiState.value.service) return
        _uiState.update { it.copy(service = service, rows = emptyList(), query = "", error = null) }
        viewModelScope.launch { load() }
    }

    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun onServicesChanged() {
        val configured = repository.configuredServices
        _uiState.update { state ->
            state.copy(
                configuredServices = configured,
                service = if (state.service in configured) state.service else configured.firstOrNull() ?: ArrService.Radarr,
                rows = if (state.service in configured) state.rows else emptyList(),
            )
        }
        if (configured.isNotEmpty()) load() else _uiState.update { it.copy(isLoading = false, rows = emptyList()) }
    }

    private suspend fun load() {
        val service = _uiState.value.service
        _uiState.update { it.copy(isLoading = it.rows.isEmpty(), error = null) }
        val rows: List<MediaRow> = when (service) {
            ArrService.Radarr -> when (val outcome = repository.movies()) {
                is Outcome.Success -> outcome.value
                    .map { MediaRow(it.id, it.title, it.year?.toString() ?: it.path, it.monitored, it.hasFile, it.posterUrl) }
                    .sortedBy { it.title.lowercase() }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isLoading = false, error = outcome.error) }
                    return
                }
            }
            ArrService.Sonarr -> when (val outcome = repository.seriesList()) {
                is Outcome.Success -> outcome.value
                    .map { MediaRow(it.id, it.title, it.year?.toString() ?: it.path, it.monitored, it.seasons.any { s -> s.episodeFileCount > 0 }, it.posterUrl) }
                    .sortedBy { it.title.lowercase() }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isLoading = false, error = outcome.error) }
                    return
                }
            }
        }
        _uiState.update { it.copy(isLoading = false, rows = rows, error = null) }
    }
}
