package com.pyoungws.cobaltwolf.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.cobaltwolf.shared.library.ItemQuery
import com.pyoungws.cobaltwolf.shared.library.LibraryRepository
import com.pyoungws.cobaltwolf.shared.model.Outcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

/**
 * A paged grid of items for one [ItemQuery] — backs the plain library grid plus the
 * filtered/sorted variants, collection and playlist contents, person filmographies
 * and the by-genre / by-letter views.
 */
class ItemBrowseViewModel(
    private val baseQuery: ItemQuery,
    private val repository: LibraryRepository,
    private val pageSize: Int = PAGE_SIZE,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    /** Active sort/filter overrides; applied on top of [baseQuery]. */
    private val _queryState = MutableStateFlow(baseQuery)
    val queryState: StateFlow<ItemQuery> = _queryState.asStateFlow()

    private var job: Job? = null

    init {
        loadMore()
    }

    fun loadMore() {
        if (job?.isActive == true) return
        val current = _uiState.value
        if (current.items.isNotEmpty() && !current.canLoadMore && current.error == null) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        val query = _queryState.value.copy(startIndex = current.items.size, limit = pageSize)
        job = viewModelScope.launch {
            when (val result = repository.items(query)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(items = it.items + result.value.items, totalCount = result.value.totalCount, isLoading = false)
                }
                is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    /** Applies a new sort/filter and reloads the grid from scratch. */
    fun updateQuery(transform: (ItemQuery) -> ItemQuery) {
        _queryState.value = transform(_queryState.value).copy(startIndex = 0)
        _uiState.value = LibraryUiState()
        job?.cancel()
        loadMore()
    }

    fun setSort(sortBy: ItemSortBy, order: SortOrder) = updateQuery { it.copy(sortBy = sortBy, sortOrder = order) }
    fun toggleUnwatchedOnly() = updateQuery { it.copy(unwatchedOnly = !it.unwatchedOnly) }
    fun toggleFavoritesOnly() = updateQuery { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun retry() = loadMore()

    companion object {
        const val PAGE_SIZE = 60
    }
}
