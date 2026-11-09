package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.library.LibraryRepository
import com.pyoungws.bluewolfplayer.shared.model.HomeFeed
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LibraryHomeUiState {
    data object Loading : LibraryHomeUiState
    data class Loaded(val feed: HomeFeed) : LibraryHomeUiState
    data class Error(val error: LoadError) : LibraryHomeUiState
}

/** Home screen content: the user's libraries, Continue Watching and Recently Added rows. */
class LibraryHomeViewModel(private val repository: LibraryRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<LibraryHomeUiState>(LibraryHomeUiState.Loading)
    val uiState: StateFlow<LibraryHomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /** Full reload with a spinner, e.g. after an error. */
    fun load() = fetch(showLoading = true)

    /** Reload in the background, keeping current content visible (e.g. when returning from the player). */
    fun refresh() {
        val loaded = _uiState.value is LibraryHomeUiState.Loaded
        if (!loaded && loadJob?.isActive == true) return
        fetch(showLoading = !loaded)
    }

    private fun fetch(showLoading: Boolean) {
        loadJob?.cancel()
        if (showLoading) _uiState.value = LibraryHomeUiState.Loading
        loadJob = viewModelScope.launch {
            when (val result = repository.loadHome()) {
                is Outcome.Success -> _uiState.value = LibraryHomeUiState.Loaded(result.value)
                is Outcome.Failure ->
                    // A failed background refresh keeps the stale content rather than replacing it with an error.
                    if (_uiState.value !is LibraryHomeUiState.Loaded) _uiState.value = LibraryHomeUiState.Error(result.error)
            }
        }
    }
}
