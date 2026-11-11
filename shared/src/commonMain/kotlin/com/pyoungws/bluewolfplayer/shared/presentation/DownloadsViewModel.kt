package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.qbittorrent.DownloadState
import com.pyoungws.bluewolfplayer.shared.qbittorrent.QBittorrentRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

sealed interface DownloadsUiState {
    data object Loading : DownloadsUiState
    /** No usable URL/username/password yet. */
    data object NotConfigured : DownloadsUiState
    data class Ready(
        val downloads: DownloadState,
        /** The last poll failed; the list still shows the previous data. */
        val stale: Boolean = false,
    ) : DownloadsUiState
    data class Error(val error: LoadError) : DownloadsUiState
}

/** qBittorrent monitoring: polls the torrent list every few seconds while the screen is open. */
class DownloadsViewModel(
    private val repository: QBittorrentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DownloadsUiState>(DownloadsUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                loadOnce()
                delay(POLL_INTERVAL_SECONDS.seconds)
            }
        }
        viewModelScope.launch { repository.settings.drop(1).collect { loadOnce() } }
    }

    fun refresh() {
        viewModelScope.launch { loadOnce() }
    }

    private suspend fun loadOnce() {
        if (!repository.isConfigured) {
            _uiState.value = DownloadsUiState.NotConfigured
            return
        }
        if (_uiState.value !is DownloadsUiState.Ready) _uiState.value = DownloadsUiState.Loading
        _uiState.value = when (val outcome = repository.downloads()) {
            is Outcome.Success -> DownloadsUiState.Ready(outcome.value)
            is Outcome.Failure -> when (val current = _uiState.value) {
                is DownloadsUiState.Ready -> current.copy(stale = true)
                else -> DownloadsUiState.Error(outcome.error)
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_SECONDS = 3L
    }
}
