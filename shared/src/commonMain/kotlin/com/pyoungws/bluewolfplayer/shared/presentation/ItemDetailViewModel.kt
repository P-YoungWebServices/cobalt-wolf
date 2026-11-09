package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.library.LibraryRepository
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ItemDetailUiState {
    data object Loading : ItemDetailUiState
    data class Error(val error: LoadError) : ItemDetailUiState
    data class Loaded(
        val item: MediaItem,
        val seasons: List<MediaItem> = emptyList(),
        val selectedSeasonId: String? = null,
        val episodes: List<MediaItem> = emptyList(),
        val isLoadingEpisodes: Boolean = false,
        val episodesError: LoadError? = null,
    ) : ItemDetailUiState {
        /** What the main Play button starts: the item itself, or for a series the first unwatched (else first) episode. */
        val playTarget: MediaItem?
            get() = when {
                item.isPlayable -> item
                item.kind == MediaKind.Series -> episodes.firstOrNull { it.hasProgress } ?: episodes.firstOrNull { !it.isPlayed } ?: episodes.firstOrNull()
                else -> null
            }
    }
}

/** Detail screen for a movie, a series (with seasons and episodes) or an episode. */
class ItemDetailViewModel(
    private val itemId: String,
    private val repository: LibraryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ItemDetailUiState>(ItemDetailUiState.Loading)
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    private var episodesJob: Job? = null

    init {
        load()
    }

    fun load() {
        _uiState.value = ItemDetailUiState.Loading
        viewModelScope.launch { fetch() }
    }

    /** Reloads in place (watched state and progress change after playing). */
    fun refresh() {
        if (_uiState.value is ItemDetailUiState.Loaded) viewModelScope.launch { fetch(keepSelection = true) }
    }

    fun selectSeason(seasonId: String) {
        val state = _uiState.value as? ItemDetailUiState.Loaded ?: return
        if (state.selectedSeasonId == seasonId) return
        loadEpisodes(state.item, seasonId)
    }

    private suspend fun fetch(keepSelection: Boolean = false) {
        val item = when (val result = repository.item(itemId)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> {
                if (!keepSelection) _uiState.value = ItemDetailUiState.Error(result.error)
                return
            }
        }
        if (item.kind != MediaKind.Series) {
            _uiState.value = ItemDetailUiState.Loaded(item)
            return
        }
        val seasons = when (val result = repository.seasons(item.id)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> {
                if (!keepSelection) _uiState.value = ItemDetailUiState.Error(result.error)
                return
            }
        }
        val previous = (_uiState.value as? ItemDetailUiState.Loaded)?.selectedSeasonId.takeIf { keepSelection }
        val selected = seasons.firstOrNull { it.id == previous } ?: seasons.firstOrNull { it.seasonNumber != 0 } ?: seasons.firstOrNull()
        _uiState.value = ItemDetailUiState.Loaded(item, seasons = seasons, selectedSeasonId = selected?.id)
        if (selected != null) loadEpisodes(item, selected.id)
    }

    private fun loadEpisodes(series: MediaItem, seasonId: String) {
        episodesJob?.cancel()
        _uiState.update { (it as? ItemDetailUiState.Loaded)?.copy(selectedSeasonId = seasonId, isLoadingEpisodes = true, episodesError = null, episodes = emptyList()) ?: it }
        episodesJob = viewModelScope.launch {
            val result = repository.episodes(series.id, seasonId)
            _uiState.update { state ->
                if (state !is ItemDetailUiState.Loaded || state.selectedSeasonId != seasonId) state
                else when (result) {
                    is Outcome.Success -> state.copy(episodes = result.value, isLoadingEpisodes = false)
                    is Outcome.Failure -> state.copy(isLoadingEpisodes = false, episodesError = result.error)
                }
            }
        }
    }

    fun retryEpisodes() {
        val state = _uiState.value as? ItemDetailUiState.Loaded ?: return
        state.selectedSeasonId?.let { loadEpisodes(state.item, it) }
    }
}
