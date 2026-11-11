package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.admin.AdminLibrary
import com.pyoungws.bluewolfplayer.shared.admin.AdminRepository
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminLibrariesUiState(
    val isLoading: Boolean = true,
    val libraries: List<AdminLibrary> = emptyList(),
    val error: LoadError? = null,
)

/** Library details: the virtual folders configured on the server and their scan state. */
class AdminLibrariesViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminLibrariesUiState())
    val uiState: StateFlow<AdminLibrariesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = repository.libraries()) {
                is Outcome.Success -> _uiState.update {
                    it.copy(isLoading = false, libraries = result.value, error = null)
                }
                is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }
}
