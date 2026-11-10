package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.playback.FallbackStep
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackTarget
import com.pyoungws.bluewolfplayer.shared.playback.PlayerErrorKind
import com.pyoungws.bluewolfplayer.shared.playback.nextFallback
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
    /** The player failed and no fallback is left. */
    data object PlaybackFailed : PlayerUiState
    /** [generation] increases when the source or decode plan changes (a fallback) so the UI rebuilds the player. */
    data class Ready(val request: PlaybackRequest, val generation: Int) : PlayerUiState
}

/**
 * Prepares playback and reports it to Jellyfin: one start report, progress reports whenever the
 * UI calls [onProgress] (it should do so periodically and on pause/seek), and one stopped report.
 * Player errors walk the fallback order: direct play, local FFmpeg decode, server transcode.
 * [reportScope] must outlive the screen so the stopped report is not cancelled by closing it.
 */
class PlayerViewModel(
    private val target: PlaybackTarget,
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

    /**
     * The player could not play the stream. Moves to the next fallback (FFmpeg for the decoder that
     * failed, then a server transcode) and resumes at [positionMs]; returns false when none is left.
     */
    fun onPlayerError(positionMs: Long, kind: PlayerErrorKind = PlayerErrorKind.Other): Boolean {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return false
        val request = ready.request
        endCurrentReporting(positionMs)
        val step = nextFallback(
            request.source.method, request.plan, kind, request.source.info, request.capabilities,
            canTranscode = request.isServerItem && !triedTranscode,
            serverTranscodesVideo = request.serverTranscodesVideo,
        )
        return when (step) {
            is FallbackStep.LocalDecode -> {
                started = false
                stopped = false
                _uiState.value = PlayerUiState.Ready(request.copy(plan = step.plan, startPositionMs = positionMs), ++generation)
                true
            }
            FallbackStep.Transcode -> {
                triedTranscode = true
                _uiState.value = PlayerUiState.Loading
                prepare(forceTranscode = true, resumeAtMs = positionMs)
                true
            }
            FallbackStep.Fail -> {
                _uiState.value = PlayerUiState.PlaybackFailed
                false
            }
        }
    }

    /** Called once the player is actually playing or ready, to send the start report. */
    fun onPlaybackStarted(positionMs: Long, isPaused: Boolean) {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return
        if (started || stopped) return
        started = true
        if (!ready.request.isServerItem) return
        lastPositionMs = positionMs
        reportScope.launch { repository.reportStart(ready.request.report(positionMs, isPaused)) }
    }

    fun onProgress(positionMs: Long, isPaused: Boolean) {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return
        if (!started || stopped) return
        lastPositionMs = positionMs
        if (!ready.request.isServerItem) return
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
        if (!ready.request.isServerItem) return
        val report = ready.request.report(positionMs, isPaused = false)
        reportScope.launch { withContext(NonCancellable) { repository.reportStopped(report) } }
    }

    private fun prepare(forceTranscode: Boolean, resumeAtMs: Long? = null) {
        viewModelScope.launch {
            when (val result = repository.prepare(target, forceTranscode)) {
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
