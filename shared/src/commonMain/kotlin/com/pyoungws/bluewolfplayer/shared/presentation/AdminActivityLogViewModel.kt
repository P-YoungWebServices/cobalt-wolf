package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.admin.ActivityEntry
import com.pyoungws.bluewolfplayer.shared.admin.AdminRepository
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminActivityLogUiState(
    val isLoading: Boolean = true,
    val entries: List<ActivityEntry> = emptyList(),
    val error: LoadError? = null,
    /** True while a "load more" page is in flight. */
    val isLoadingMore: Boolean = false,
    /** False when the server returned a short page — nothing more to fetch. */
    val hasMore: Boolean = true,
)

/** Server activity log with incremental paging. */
class AdminActivityLogViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminActivityLogUiState())
    val uiState: StateFlow<AdminActivityLogUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = AdminActivityLogUiState()
        load(start = 0)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return
        _uiState.update { it.copy(isLoadingMore = true) }
        load(start = state.entries.size)
    }

    private fun load(start: Int) {
        viewModelScope.launch {
            when (val result = repository.activityLog(startIndex = start, limit = PAGE_SIZE)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        entries = (it.entries + result.value).distinctBy(ActivityEntry::id),
                        error = null,
                        hasMore = result.value.size >= PAGE_SIZE,
                    )
                }
                is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, isLoadingMore = false, error = result.error) }
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
