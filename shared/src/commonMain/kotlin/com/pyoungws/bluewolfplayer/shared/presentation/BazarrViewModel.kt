package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.bazarr.BazarrRepository
import com.pyoungws.bluewolfplayer.shared.bazarr.BazarrWanted
import com.pyoungws.bluewolfplayer.shared.bazarr.WantedEpisode
import com.pyoungws.bluewolfplayer.shared.bazarr.WantedMovie
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

sealed interface BazarrUiState {
    data object Loading : BazarrUiState
    /** No usable URL + API key yet. */
    data object NotConfigured : BazarrUiState
    data class Ready(
        val wanted: BazarrWanted,
        /** The last load failed; the list still shows the previous data. */
        val stale: Boolean = false,
    ) : BazarrUiState
    data class Error(val error: LoadError) : BazarrUiState
}

/** Status of a per-item manual search, keyed by [WantedMovie]/[WantedEpisode] identity. */
sealed interface BazarrSearchStatus {
    data object Searching : BazarrSearchStatus
    /** Subtitles were found and downloaded; the item drops off the wanted list on refresh. */
    data class Downloaded(val count: Int) : BazarrSearchStatus
    /** The providers returned nothing usable for the wanted languages. */
    data object NothingFound : BazarrSearchStatus
    data class Failed(val error: LoadError) : BazarrSearchStatus
}

/** The Bazarr wanted-subtitles list behind the dashboard "Subtitles" card. */
class BazarrViewModel(
    private val repository: BazarrRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<BazarrUiState>(BazarrUiState.Loading)
    val uiState = _uiState.asStateFlow()

    /** In-flight/finished manual searches, keyed by the wanted item's stable identity. */
    private val _searches = MutableStateFlow<Map<String, BazarrSearchStatus>>(emptyMap())
    val searches = _searches.asStateFlow()

    init {
        viewModelScope.launch { loadOnce() }
        viewModelScope.launch { repository.settings.drop(1).collect { loadOnce() } }
    }

    fun refresh() {
        viewModelScope.launch { loadOnce() }
    }

    /** Runs a provider search for [movie] and grabs the best result per missing language. */
    fun search(movie: WantedMovie) = search("m:${movie.radarrId}:${movie.title}") { repository.searchAndDownload(movie) }

    /** Runs a provider search for [episode] and grabs the best result per missing language. */
    fun search(episode: WantedEpisode) = search("e:${episode.sonarrEpisodeId}:${episode.seriesTitle}:${episode.episodeCode}") {
        repository.searchAndDownload(episode)
    }

    /** Stable key a row uses to look up its search status. */
    fun searchKey(movie: WantedMovie) = "m:${movie.radarrId}:${movie.title}"
    fun searchKey(episode: WantedEpisode) = "e:${episode.sonarrEpisodeId}:${episode.seriesTitle}:${episode.episodeCode}"

    private fun search(key: String, block: suspend () -> Outcome<Int>) {
        if (_searches.value[key] is BazarrSearchStatus.Searching) return
        _searches.value += key to BazarrSearchStatus.Searching
        viewModelScope.launch {
            val status = when (val outcome = block()) {
                is Outcome.Success ->
                    if (outcome.value > 0) BazarrSearchStatus.Downloaded(outcome.value) else BazarrSearchStatus.NothingFound
                is Outcome.Failure -> BazarrSearchStatus.Failed(outcome.error)
            }
            _searches.value += key to status
            if (status is BazarrSearchStatus.Downloaded) loadOnce()
        }
    }

    private suspend fun loadOnce() {
        if (!repository.isConfigured) {
            _uiState.value = BazarrUiState.NotConfigured
            return
        }
        if (_uiState.value !is BazarrUiState.Ready) _uiState.value = BazarrUiState.Loading
        _uiState.value = when (val outcome = repository.wanted()) {
            is Outcome.Success -> BazarrUiState.Ready(outcome.value)
            is Outcome.Failure -> when (val current = _uiState.value) {
                is BazarrUiState.Ready -> current.copy(stale = true)
                else -> BazarrUiState.Error(outcome.error)
            }
        }
    }
}
