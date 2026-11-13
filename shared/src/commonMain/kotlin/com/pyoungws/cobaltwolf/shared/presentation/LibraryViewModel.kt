package com.pyoungws.cobaltwolf.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.cobaltwolf.shared.library.LibraryRepository
import com.pyoungws.cobaltwolf.shared.model.Library
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.MediaItem
import com.pyoungws.cobaltwolf.shared.model.Outcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val items: List<MediaItem> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val error: LoadError? = null,
) {
    val canLoadMore: Boolean get() = !isLoading && error == null && items.size < totalCount
}

/** A paged grid of the movies or series in one library. */
class LibraryViewModel(
    val library: Library,
    private val repository: LibraryRepository,
    private val pageSize: Int = PAGE_SIZE,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        loadMore()
    }

    fun loadMore() {
        if (job?.isActive == true) return
        val current = _uiState.value
        if (current.items.isNotEmpty() && !current.canLoadMore && current.error == null) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            when (val result = repository.libraryItems(library, current.items.size, pageSize)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(items = it.items + result.value.items, totalCount = result.value.totalCount, isLoading = false)
                }
                is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    fun retry() = loadMore()

    companion object {
        const val PAGE_SIZE = 60
    }
}
