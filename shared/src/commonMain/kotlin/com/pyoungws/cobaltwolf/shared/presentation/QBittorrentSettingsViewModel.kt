package com.pyoungws.cobaltwolf.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import com.pyoungws.cobaltwolf.shared.qbittorrent.QBittorrentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Editable qBittorrent connection form: URL + username + password with save and test. */
data class QBittorrentSettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val testing: Boolean = false,
    val testSucceeded: Boolean? = null,
    val testError: LoadError? = null,
    val saved: Boolean = false,
)

class QBittorrentSettingsViewModel(
    private val repository: QBittorrentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(repository.settings.value.let {
        QBittorrentSettingsUiState(
            serverUrl = it.serverUrl.orEmpty(),
            username = it.username.orEmpty(),
            password = it.password.orEmpty(),
        )
    })
    val uiState = _uiState.asStateFlow()

    fun onServerUrlChange(value: String) = update { it.copy(serverUrl = value, testSucceeded = null, testError = null, saved = false) }
    fun onUsernameChange(value: String) = update { it.copy(username = value, testSucceeded = null, testError = null, saved = false) }
    fun onPasswordChange(value: String) = update { it.copy(password = value, testSucceeded = null, testError = null, saved = false) }

    fun save() {
        val form = _uiState.value
        repository.saveSettings(form.serverUrl, form.username, form.password)
        update { it.copy(saved = true) }
    }

    fun test() {
        val form = _uiState.value
        update { it.copy(testing = true, testSucceeded = null, testError = null) }
        viewModelScope.launch {
            when (val outcome = repository.testConnection(form.serverUrl, form.username, form.password)) {
                is Outcome.Success -> update { it.copy(testing = false, testSucceeded = true) }
                is Outcome.Failure -> update { it.copy(testing = false, testSucceeded = false, testError = outcome.error) }
            }
        }
    }

    private fun update(transform: (QBittorrentSettingsUiState) -> QBittorrentSettingsUiState) {
        _uiState.update(transform)
    }
}
