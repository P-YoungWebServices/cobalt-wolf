package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.library.LibraryRepository
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrAuthState
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrRepository
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrTitle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class SearchUiState(
    val query: String = "",
    val results: List<MediaItem> = emptyList(),
    val seerrResults: List<SeerrTitle> = emptyList(),
    val isSearching: Boolean = false,
    val error: LoadError? = null,
    val seerrError: LoadError? = null,
) {
    val showNoResults: Boolean
        get() = query.isNotBlank() && !isSearching && error == null && results.isEmpty() && seerrResults.isEmpty() && seerrError == null
}

/** Search across movies, series and episodes plus the Seerr catalog. Typing is debounced and stale requests are cancelled. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: LibraryRepository,
    private val seerrRepository: SeerrRepository,
    debounceTime: Duration = 300.milliseconds,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queries = MutableStateFlow("")

    init {
        queries
            .debounce(debounceTime)
            .map { it.trim() }
            .distinctUntilChanged()
            .onEach { term ->
                _uiState.update {
                    it.copy(
                        isSearching = term.isNotEmpty(),
                        error = null,
                        seerrError = null,
                        results = if (term.isEmpty()) emptyList() else it.results,
                        seerrResults = if (term.isEmpty()) emptyList() else it.seerrResults,
                    )
                }
            }
            .mapLatest { term -> if (term.isEmpty()) null else search(term) }
            .onEach { result ->
                val (local, remote) = result ?: return@onEach
                _uiState.update {
                    it.copy(
                        results = (local as? Outcome.Success)?.value.orEmpty(),
                        error = (local as? Outcome.Failure)?.error,
                        seerrResults = (remote as? Outcome.Success)?.value.orEmpty(),
                        seerrError = (remote as? Outcome.Failure)?.error,
                        isSearching = false,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun search(term: String): Pair<Outcome<List<MediaItem>>, Outcome<List<SeerrTitle>>> = coroutineScope {
        val local = async { repository.search(term) }
        val remote = async {
            // Wait for the auth restore so a search typed just after launch is not skipped.
            val auth = seerrRepository.authState.first { it !is SeerrAuthState.Checking }
            if (auth is SeerrAuthState.SignedIn) {
                seerrRepository.search(term)
            } else {
                Outcome.Success(emptyList())
            }
        }
        local.await() to remote.await()
    }

    fun onQueryChange(query: String) {
        queries.value = query
        _uiState.update { it.copy(query = query) }
    }
}
