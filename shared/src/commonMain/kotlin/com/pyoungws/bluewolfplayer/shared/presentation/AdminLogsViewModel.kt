package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.admin.AdminRepository
import com.pyoungws.bluewolfplayer.shared.admin.LogSeverity
import com.pyoungws.bluewolfplayer.shared.admin.ServerLogFile
import com.pyoungws.bluewolfplayer.shared.admin.ServerLogLine
import com.pyoungws.bluewolfplayer.shared.admin.filteredBy
import com.pyoungws.bluewolfplayer.shared.admin.parseServerLog
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class AdminLogsUiState(
    /** True until the file list (and the first file's content) has loaded once. */
    val isLoading: Boolean = true,
    val files: List<ServerLogFile> = emptyList(),
    val selectedFile: String? = null,
    /** Parsed lines of the selected file, oldest first, capped at [AdminLogsViewModel.MAX_LINES]. */
    val lines: List<ServerLogLine> = emptyList(),
    val minSeverity: LogSeverity? = null,
    val query: String = "",
    val error: LoadError? = null,
) {
    /** The lines the screen should render after the severity and text filters. */
    val visibleLines: List<ServerLogLine> get() = lines.filteredBy(minSeverity, query)
}

/**
 * Server logs: newest files first, the selected file re-fetched every [POLL_SECONDS] so it
 * stays live. Filtering is done locally over the parsed lines.
 */
class AdminLogsViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminLogsUiState())
    val uiState: StateFlow<AdminLogsUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            loadFiles()
            loadContent()
            startPolling()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadFiles()
            loadContent()
        }
    }

    fun selectFile(name: String) {
        if (name == _uiState.value.selectedFile) return
        _uiState.update { it.copy(selectedFile = name, lines = emptyList()) }
        viewModelScope.launch { loadContent() }
    }

    fun setMinSeverity(severity: LogSeverity?) = _uiState.update { it.copy(minSeverity = severity) }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_SECONDS.seconds)
                loadContent()
            }
        }
    }

    private suspend fun loadFiles() {
        when (val result = repository.logFiles()) {
            is Outcome.Success -> _uiState.update { state ->
                val files = result.value
                state.copy(
                    isLoading = false,
                    files = files,
                    selectedFile = state.selectedFile?.takeIf { sel -> files.any { it.name == sel } }
                        ?: files.firstOrNull()?.name,
                    error = null,
                )
            }
            is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
        }
    }

    private suspend fun loadContent() {
        val file = _uiState.value.selectedFile ?: return
        when (val result = repository.logContent(file)) {
            is Outcome.Success -> _uiState.update {
                it.copy(lines = parseServerLog(result.value).takeLast(MAX_LINES), error = null)
            }
            is Outcome.Failure -> _uiState.update { it.copy(error = result.error) }
        }
    }

    companion object {
        const val POLL_SECONDS = 5
        const val MAX_LINES = 2_000
    }
}
