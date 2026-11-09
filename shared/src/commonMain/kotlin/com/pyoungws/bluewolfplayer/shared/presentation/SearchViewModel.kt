package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.library.LibraryRepository
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val isSearching: Boolean = false,
    val error: LoadError? = null,
) {
    val showNoResults: Boolean get() = query.isNotBlank() && !isSearching && error == null && results.isEmpty()
}

/** Search across movies, series and episodes. Typing is debounced and stale requests are cancelled. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: LibraryRepository,
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
            .onEach { term -> _uiState.update { it.copy(isSearching = term.isNotEmpty(), error = null, results = if (term.isEmpty()) emptyList() else it.results) } }
            .mapLatest { term -> if (term.isEmpty()) null else repository.search(term) }
            .onEach { result ->
                when (result) {
                    null -> Unit
                    is Outcome.Success -> _uiState.update { it.copy(results = result.value, isSearching = false) }
                    is Outcome.Failure -> _uiState.update { it.copy(results = emptyList(), isSearching = false, error = result.error) }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        queries.value = query
        _uiState.update { it.copy(query = query) }
    }
}
