package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.prowlarr.ProwlarrIndexer
import com.pyoungws.bluewolfplayer.shared.prowlarr.ProwlarrOverview
import com.pyoungws.bluewolfplayer.shared.prowlarr.ProwlarrRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

sealed interface ProwlarrUiState {
    data object Loading : ProwlarrUiState
    /** No usable URL + API key yet. */
    data object NotConfigured : ProwlarrUiState
    data class Ready(
        val overview: ProwlarrOverview,
        /** The last load failed; the list still shows the previous data. */
        val stale: Boolean = false,
    ) : ProwlarrUiState
    data class Error(val error: LoadError) : ProwlarrUiState
}

/** Status of an indexer connectivity test. */
sealed interface ProwlarrTestStatus {
    data object Testing : ProwlarrTestStatus
    data object Passed : ProwlarrTestStatus
    data class Failed(val error: LoadError) : ProwlarrTestStatus
}

/** The Prowlarr indexer/health view behind the dashboard "Indexers" card. */
class ProwlarrViewModel(
    private val repository: ProwlarrRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProwlarrUiState>(ProwlarrUiState.Loading)
    val uiState = _uiState.asStateFlow()

    /** In-flight/finished per-indexer tests, keyed by indexer id. */
    private val _tests = MutableStateFlow<Map<Int, ProwlarrTestStatus>>(emptyMap())
    val tests = _tests.asStateFlow()

    /** True while the test-all sweep is running. */
    private val _testingAll = MutableStateFlow(false)
    val testingAll = _testingAll.asStateFlow()

    init {
        viewModelScope.launch { loadOnce() }
        viewModelScope.launch { repository.settings.drop(1).collect { loadOnce() } }
    }

    fun refresh() {
        viewModelScope.launch { loadOnce() }
    }

    /** Re-runs the connectivity test for one indexer, then refreshes its status line. */
    fun test(indexer: ProwlarrIndexer) {
        if (_tests.value[indexer.id] is ProwlarrTestStatus.Testing) return
        _tests.value += indexer.id to ProwlarrTestStatus.Testing
        viewModelScope.launch {
            _tests.value += indexer.id to when (val outcome = repository.testIndexer(indexer.id)) {
                is Outcome.Success -> ProwlarrTestStatus.Passed
                is Outcome.Failure -> ProwlarrTestStatus.Failed(outcome.error)
            }
            loadOnce()
        }
    }

    /** Tests every indexer; the health/disable lines update when the refresh lands. */
    fun testAll() {
        if (_testingAll.value) return
        viewModelScope.launch {
            _testingAll.value = true
            when (repository.testAllIndexers()) {
                is Outcome.Success -> loadOnce()
                is Outcome.Failure -> { /* surfaced through the refresh/error state */ }
            }
            _testingAll.value = false
        }
    }

    private suspend fun loadOnce() {
        if (!repository.isConfigured) {
            _uiState.value = ProwlarrUiState.NotConfigured
            return
        }
        if (_uiState.value !is ProwlarrUiState.Ready) _uiState.value = ProwlarrUiState.Loading
        _uiState.value = when (val outcome = repository.overview()) {
            is Outcome.Success -> ProwlarrUiState.Ready(outcome.value)
            is Outcome.Failure -> when (val current = _uiState.value) {
                is ProwlarrUiState.Ready -> current.copy(stale = true)
                else -> ProwlarrUiState.Error(outcome.error)
            }
        }
    }
}
