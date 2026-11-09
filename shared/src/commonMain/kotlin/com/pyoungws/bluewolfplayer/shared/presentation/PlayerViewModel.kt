package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackMethod
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackReport
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackRepository
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackRequest
import com.pyoungws.bluewolfplayer.shared.playback.TICKS_PER_MILLISECOND
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Error(val error: LoadError) : PlayerUiState
    /** The player failed even after the transcode fallback. */
    data object PlaybackFailed : PlayerUiState
    /** [generation] increases when the source changes (transcode fallback) so the UI rebuilds the player. */
    data class Ready(val request: PlaybackRequest, val generation: Int) : PlayerUiState
}

/**
 * Prepares playback and reports it to Jellyfin: one start report, progress reports whenever the
 * UI calls [onProgress] (it should do so periodically and on pause/seek), and one stopped report.
 * [reportScope] must outlive the screen so the stopped report is not cancelled by closing it.
 */
class PlayerViewModel(
    private val itemId: String,
    private val repository: PlaybackRepository,
    private val reportScope: CoroutineScope,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var generation = 0
    private var started = false
    private var stopped = false
    private var triedTranscode = false
    private var lastPositionMs = 0L

    init {
        prepare(forceTranscode = false)
    }

    fun retry() {
        _uiState.value = PlayerUiState.Loading
        prepare(forceTranscode = triedTranscode)
    }

    /** The player could not play the stream. Retries once with a server transcode; returns false if that was already tried. */
    fun onPlayerError(positionMs: Long): Boolean {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return false
        endCurrentReporting(positionMs)
        if (triedTranscode || ready.request.source.method == PlaybackMethod.Transcode) {
            _uiState.value = PlayerUiState.PlaybackFailed
            return false
        }
        triedTranscode = true
        _uiState.value = PlayerUiState.Loading
        prepare(forceTranscode = true, resumeAtMs = positionMs)
        return true
    }

    /** Called once the player is actually playing or ready, to send the start report. */
    fun onPlaybackStarted(positionMs: Long, isPaused: Boolean) {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return
        if (started || stopped) return
        started = true
        lastPositionMs = positionMs
        reportScope.launch { repository.reportStart(ready.request.report(positionMs, isPaused)) }
    }

    fun onProgress(positionMs: Long, isPaused: Boolean) {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return
        if (!started || stopped) return
        lastPositionMs = positionMs
        viewModelScope.launch { repository.reportProgress(ready.request.report(positionMs, isPaused)) }
    }

    /** Sends the stopped report (once). Safe to call from several lifecycle callbacks. */
    fun onStopped(positionMs: Long = lastPositionMs) {
        endCurrentReporting(positionMs)
    }

    override fun onCleared() {
        endCurrentReporting(lastPositionMs)
    }

    private fun endCurrentReporting(positionMs: Long) {
        val ready = _uiState.value as? PlayerUiState.Ready
        if (!started || stopped || ready == null) {
            started = false
            return
        }
        stopped = true
        started = false
        val report = ready.request.report(positionMs, isPaused = false)
        reportScope.launch { withContext(NonCancellable) { repository.reportStopped(report) } }
    }

    private fun prepare(forceTranscode: Boolean, resumeAtMs: Long? = null) {
        viewModelScope.launch {
            when (val result = repository.prepare(itemId, forceTranscode)) {
                is Outcome.Success -> {
                    started = false
                    stopped = false
                    val request = result.value.let { if (resumeAtMs != null) it.copy(startPositionMs = resumeAtMs) else it }
                    _uiState.value = PlayerUiState.Ready(request, ++generation)
                }
                is Outcome.Failure -> _uiState.value = PlayerUiState.Error(result.error)
            }
        }
    }

    private fun PlaybackRequest.report(positionMs: Long, isPaused: Boolean) = PlaybackReport(
        itemId = item.id,
        mediaSourceId = source.mediaSourceId,
        playSessionId = source.playSessionId,
        positionTicks = positionMs * TICKS_PER_MILLISECOND,
        isPaused = isPaused,
        method = source.method,
    )
}
