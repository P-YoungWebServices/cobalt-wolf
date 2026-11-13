package com.pyoungws.cobaltwolf.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import com.pyoungws.cobaltwolf.shared.qbittorrent.DownloadState
import com.pyoungws.cobaltwolf.shared.qbittorrent.QBittorrentRepository
import com.pyoungws.cobaltwolf.shared.qbittorrent.Torrent
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

    /** Set when a start/stop/toggle action fails; cleared once the UI has shown it. */
    private val _actionError = MutableStateFlow<LoadError?>(null)
    val actionError = _actionError.asStateFlow()

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

    /** Starts a paused torrent; pauses a running one. The 3 s poll picks up the new state. */
    fun toggle(torrent: Torrent) = act {
        if (torrent.isPaused) repository.startTorrent(torrent.hash) else repository.stopTorrent(torrent.hash)
    }

    /** Flips the client's alternative speed limits on/off. */
    fun toggleAlternativeSpeeds() = act { repository.toggleAlternativeSpeeds() }

    fun consumeActionError() {
        _actionError.value = null
    }

    private fun act(block: suspend () -> Outcome<Unit>) {
        viewModelScope.launch {
            when (val outcome = block()) {
                is Outcome.Success -> loadOnce()
                is Outcome.Failure -> _actionError.value = outcome.error
            }
        }
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
