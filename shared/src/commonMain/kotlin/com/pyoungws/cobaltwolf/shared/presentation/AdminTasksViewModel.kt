package com.pyoungws.cobaltwolf.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.cobaltwolf.shared.admin.AdminRepository
import com.pyoungws.cobaltwolf.shared.admin.ScheduledTask
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class AdminTasksUiState(
    val isLoading: Boolean = true,
    val tasks: List<ScheduledTask> = emptyList(),
    /** Set on a failed poll; earlier data is kept so the list does not blank on one bad fetch. */
    val error: LoadError? = null,
    val action: AdminActionStatus? = null,
)

/** Scheduled tasks: live state with start/stop, re-fetched every [POLL_SECONDS] while the screen is open. */
class AdminTasksViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminTasksUiState())
    val uiState: StateFlow<AdminTasksUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        pollJob = viewModelScope.launch {
            while (isActive) {
                loadTasks()
                delay(POLL_SECONDS.seconds)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { loadTasks() }
    }

    fun startTask(taskId: String) = runTaskAction("$START_KEY$taskId") { repository.startTask(taskId) }

    fun stopTask(taskId: String) = runTaskAction("$STOP_KEY$taskId") { repository.stopTask(taskId) }

    fun consumeAction() = _uiState.update { it.copy(action = null) }

    private fun runTaskAction(key: String, block: suspend () -> Outcome<Unit>) {
        if (_uiState.value.action is AdminActionStatus.Running) return
        _uiState.update { it.copy(action = AdminActionStatus.Running(key)) }
        viewModelScope.launch {
            val error = when (val result = block()) {
                is Outcome.Success -> null
                is Outcome.Failure -> result.error
            }
            // The server takes a moment to flip a task's state; reload so the row reflects it.
            if (error == null) loadTasks()
            _uiState.update {
                it.copy(action = error?.let { e -> AdminActionStatus.Failed(key, e) } ?: AdminActionStatus.Succeeded(key))
            }
        }
    }

    private suspend fun loadTasks() {
        when (val result = repository.scheduledTasks()) {
            is Outcome.Success -> _uiState.update {
                it.copy(isLoading = false, tasks = result.value, error = null)
            }
            is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
        }
    }

    companion object {
        const val POLL_SECONDS = 4
        const val START_KEY = "start:"
        const val STOP_KEY = "stop:"
    }
}
