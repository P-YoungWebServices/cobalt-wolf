package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.admin.AdminRepository
import com.pyoungws.bluewolfplayer.shared.admin.SystemInformation
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminSystemInfoUiState(
    val isLoading: Boolean = true,
    val info: SystemInformation? = null,
    val error: LoadError? = null,
)

/** System information: server identity, update status and storage usage. */
class AdminSystemInfoViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminSystemInfoUiState())
    val uiState: StateFlow<AdminSystemInfoUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = repository.systemInfo()) {
                is Outcome.Success -> _uiState.update {
                    it.copy(isLoading = false, info = result.value, error = null)
                }
                is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }
}
