package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrRepository
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MyRequestsUiState {
    data object Loading : MyRequestsUiState
    data class Loaded(val requests: List<SeerrRequest>) : MyRequestsUiState
    data class Error(val error: LoadError) : MyRequestsUiState
}

/** The user's own Seerr requests with their approval and availability status. */
class MyRequestsViewModel(private val repository: SeerrRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<MyRequestsUiState>(MyRequestsUiState.Loading)
    val uiState: StateFlow<MyRequestsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() = fetch(showLoading = true)

    fun refresh() {
        val loaded = _uiState.value is MyRequestsUiState.Loaded
        if (!loaded && loadJob?.isActive == true) return
        fetch(showLoading = !loaded)
    }

    private fun fetch(showLoading: Boolean) {
        loadJob?.cancel()
        if (showLoading) _uiState.value = MyRequestsUiState.Loading
        loadJob = viewModelScope.launch {
            when (val result = repository.myRequests()) {
                is Outcome.Success -> _uiState.value = MyRequestsUiState.Loaded(result.value)
                is Outcome.Failure ->
                    if (_uiState.value !is MyRequestsUiState.Loaded) _uiState.value = MyRequestsUiState.Error(result.error)
            }
        }
    }
}
