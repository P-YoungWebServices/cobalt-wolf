package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.playback.FallbackStep
import com.pyoungws.bluewolfplayer.shared.playback.MediaSegment
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackTarget
import com.pyoungws.bluewolfplayer.shared.playback.PlayerErrorKind
import com.pyoungws.bluewolfplayer.shared.playback.nextFallback
import com.pyoungws.bluewolfplayer.shared.playback.PlayQueue
import com.pyoungws.bluewolfplayer.shared.playback.PlayQueueRepository
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackReport
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackRepository
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackRequest
import com.pyoungws.bluewolfplayer.shared.playback.RepeatMode
import com.pyoungws.bluewolfplayer.shared.playback.StreamBitrate
import com.pyoungws.bluewolfplayer.shared.playback.TICKS_PER_MILLISECOND
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayEchoGate
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayMembership
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayPlayerCommand
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Error(val error: LoadError) : PlayerUiState
    /** The player failed and no fallback is left. */
    data object PlaybackFailed : PlayerUiState
    /** [generation] increases when the source or decode plan changes (a fallback) so the UI rebuilds the player. */
    data class Ready(val request: PlaybackRequest, val generation: Int) : PlayerUiState
}

/** Something the SyncPlay group wants the player to do; the UI applies these to the player. */
sealed interface SyncPlayUiCommand {
    data class SeekTo(val positionMs: Long) : SyncPlayUiCommand
    data object Pause : SyncPlayUiCommand
    data class Resume(val positionMs: Long?) : SyncPlayUiCommand
    /** Leave the player: the group went idle or stopped. */
    data object Close : SyncPlayUiCommand
}

/**
 * Prepares playback and reports it to Jellyfin: one start report, progress reports whenever the
 * UI calls [onProgress] (it should do so periodically and on pause/seek), and one stopped report.
 * Player errors walk the fallback order: direct play, local FFmpeg decode, server transcode.
 * [reportScope] must outlive the screen so the stopped report is not cancelled by closing it.
 *
 * While a SyncPlay group membership is active, [syncPlay] drives the player: group commands are
 * re-emitted on [commands] (a different item switches the target entirely), and the local
 * transport events the UI reports through `onUser*` are relayed to the group after filtering out
 * echoes of commands it just applied.
 */
class PlayerViewModel(
    private var target: PlaybackTarget,
    private val repository: PlaybackRepository,
    private val reportScope: CoroutineScope,
    private val syncPlay: SyncPlayRepository,
    private val playQueue: PlayQueueRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** Group commands for the UI to apply to the player. */
    private val _commands = MutableSharedFlow<SyncPlayUiCommand>(extraBufferCapacity = 8)
    val commands: SharedFlow<SyncPlayUiCommand> = _commands.asSharedFlow()

    /** Current SyncPlay membership (the UI shows a badge); null when not in a group. */
    val membership: StateFlow<SyncPlayMembership?> = syncPlay.membership

    /** The episode queued after the current one, null for movies and last episodes. */
    private val _nextItem = MutableStateFlow<MediaItem?>(null)
    val nextItem: StateFlow<MediaItem?> = _nextItem.asStateFlow()

    /** Intro/outro markers of the playing item, for the skip buttons. */
    private val _segments = MutableStateFlow<List<MediaSegment>>(emptyList())
    val segments: StateFlow<List<MediaSegment>> = _segments.asStateFlow()

    private val echoGate = SyncPlayEchoGate()
    private var generation = 0
    private var started = false
    private var stopped = false
    private var triedTranscode = false
    private var audioOutputRetries = 0
    private var lastPositionMs = 0L
    private var pendingStartPaused = false
    private var lastPingAt: TimeSource.Monotonic.ValueTimeMark? = null

    /** The shared play queue; the player follows it for next/previous and repeat. */
    val queue: StateFlow<PlayQueue> = playQueue.queue

    init {
        // A player opened on something outside the queue starts a fresh single-item queue.
        val queued = playQueue.queue.value
        if ((target as? PlaybackTarget.Item)?.itemId != queued.current?.item?.id) playQueue.clear()
        prepare(forceTranscode = false)
        viewModelScope.launch { syncPlay.playerCommands.collect { onGroupCommand(it) } }
        // A fresh player gets the group's current item and position, if any.
        syncPlay.attachPlayer()
    }

    fun retry() {
        _uiState.value = PlayerUiState.Loading
        prepare(forceTranscode = triedTranscode)
    }

    /** Stops reporting this item and starts preparing the queued next or next episode, if there is one. */
    fun playNext(positionMs: Long) {
        val next = playQueue.advance() ?: _nextItem.value ?: return
        retarget(PlaybackTarget.Item(next.id), positionMs)
    }

    /** Jumps to a tapped queue entry. */
    fun playQueueIndex(index: Int, positionMs: Long) {
        val item = playQueue.advanceTo(index) ?: return
        retarget(PlaybackTarget.Item(item.id), positionMs)
    }

    /** Jumps back to the previous queue entry, if any. */
    fun playQueuePrevious(positionMs: Long) {
        val item = playQueue.retreat() ?: return
        retarget(PlaybackTarget.Item(item.id), positionMs)
    }

    fun cycleRepeat() = playQueue.cycleRepeat()
    fun toggleShuffle() = playQueue.toggleShuffle()
    fun removeQueueEntry(index: Int) = playQueue.remove(index)

    private fun retarget(next: PlaybackTarget, positionMs: Long) {
        endCurrentReporting(positionMs)
        triedTranscode = false
        audioOutputRetries = 0
        target = next
        _uiState.value = PlayerUiState.Loading
        prepare(forceTranscode = false)
    }

    /**
     * The stream reached its end. Advances to the next episode when there is one and no SyncPlay
     * group controls the item; returns false when playback is simply over and the UI should close.
     */
    fun onEnded(positionMs: Long): Boolean {
        if (membership.value == null) {
            val q = playQueue.queue.value
            when {
                q.repeat == RepeatMode.One && q.current != null -> {
                    val itemId = (_uiState.value as? PlayerUiState.Ready)?.request?.item?.id ?: q.current?.item?.id ?: return false
                    retarget(PlaybackTarget.Item(itemId, startPositionMs = 0), positionMs)
                    return true
                }
                q.nextIndex() != null || _nextItem.value != null -> {
                    playNext(positionMs)
                    return true
                }
            }
        }
        endCurrentReporting(positionMs)
        return false
    }

    /**
     * Applies a new bitrate cap and re-requests the source, resuming at [positionMs].
     * The cap persists, so the next stream starts with it too.
     */
    fun changeBitrate(bitrate: StreamBitrate, positionMs: Long) {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return
        if (ready.request.settings.maxBitrate == bitrate) return
        repository.setMaxBitrate(bitrate)
        endCurrentReporting(positionMs)
        _uiState.value = PlayerUiState.Loading
        prepare(forceTranscode = triedTranscode, resumeAtMs = positionMs)
    }

    /**
     * The player could not play the stream. Moves to the next fallback (FFmpeg for the decoder that
     * failed, then a server transcode) and resumes at [positionMs]; returns false when none is left.
     */
    fun onPlayerError(positionMs: Long, kind: PlayerErrorKind = PlayerErrorKind.Other): Boolean {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return false
        val request = ready.request
        // A display-mode switch or HDMI renegotiation can kill the passthrough AudioTrack after a
        // few frames. That is transient: retry the exact same plan once before falling back.
        if (kind == PlayerErrorKind.AudioOutput && audioOutputRetries++ == 0) {
            endCurrentReporting(positionMs)
            started = false
            stopped = false
            _uiState.value = PlayerUiState.Ready(request.copy(startPositionMs = positionMs), ++generation)
            return true
        }
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

    // ---- SyncPlay ----------------------------------------------------------

    private suspend fun onGroupCommand(command: SyncPlayPlayerCommand) {
        when (command) {
            is SyncPlayPlayerCommand.LoadItem -> onGroupLoadItem(command)
            SyncPlayPlayerCommand.Pause -> {
                echoGate.remoteApplied(SyncPlayEchoGate.Kind.Pause, lastPositionMs.toTicks())
                _commands.emit(SyncPlayUiCommand.Pause)
            }
            is SyncPlayPlayerCommand.Resume -> {
                echoGate.remoteApplied(SyncPlayEchoGate.Kind.Resume, command.positionTicks ?: lastPositionMs.toTicks())
                _commands.emit(SyncPlayUiCommand.Resume(command.positionTicks?.ticksToMs()))
            }
            is SyncPlayPlayerCommand.Seek -> {
                echoGate.remoteApplied(SyncPlayEchoGate.Kind.Seek, command.positionTicks)
                _commands.emit(SyncPlayUiCommand.SeekTo(command.positionTicks.ticksToMs()))
            }
            SyncPlayPlayerCommand.Stop -> _commands.emit(SyncPlayUiCommand.Close)
        }
    }

    private suspend fun onGroupLoadItem(command: SyncPlayPlayerCommand.LoadItem) {
        val currentItemId = (_uiState.value as? PlayerUiState.Ready)?.request?.item?.id
        if (currentItemId == command.itemId) {
            // Already on the group's item: align the position and the play state.
            echoGate.remoteApplied(SyncPlayEchoGate.Kind.Seek, command.positionTicks)
            _commands.emit(SyncPlayUiCommand.SeekTo(command.positionTicks.ticksToMs()))
            val playGate = if (command.playWhenReady) SyncPlayEchoGate.Kind.Resume else SyncPlayEchoGate.Kind.Pause
            echoGate.remoteApplied(playGate, command.positionTicks)
            _commands.emit(if (command.playWhenReady) SyncPlayUiCommand.Resume(null) else SyncPlayUiCommand.Pause)
            return
        }
        pendingStartPaused = !command.playWhenReady
        triedTranscode = false
        audioOutputRetries = 0
        target = PlaybackTarget.Item(command.itemId, startPositionMs = command.positionTicks.ticksToMs())
        _uiState.value = PlayerUiState.Loading
        prepare(forceTranscode = false)
    }

    /**
     * True while the player shows the item the group is playing. Local transport events are only
     * relayed then — pausing a different video must not pause the group's movie.
     */
    private fun groupControlsCurrentItem(): Boolean {
        val member = membership.value ?: return false
        val current = (_uiState.value as? PlayerUiState.Ready)?.request?.item?.id ?: return false
        return member.playingItem?.itemId == current
    }

    /** The user paused locally: tell the group (unless it is the echo of a group Pause). */
    fun onUserPause(positionMs: Long) {
        if (!groupControlsCurrentItem()) return
        if (echoGate.suppressLocalEcho(SyncPlayEchoGate.Kind.Pause, positionMs.toTicks())) return
        viewModelScope.launch { syncPlay.localPause() }
    }

    fun onUserResume(positionMs: Long) {
        if (!groupControlsCurrentItem()) return
        if (echoGate.suppressLocalEcho(SyncPlayEchoGate.Kind.Resume, positionMs.toTicks())) return
        viewModelScope.launch { syncPlay.localResume() }
    }

    fun onUserSeek(positionMs: Long) {
        if (!groupControlsCurrentItem()) return
        if (echoGate.suppressLocalEcho(SyncPlayEchoGate.Kind.Seek, positionMs.toTicks())) return
        viewModelScope.launch { syncPlay.localSeek(positionMs.toTicks()) }
    }

    /** Buffering started: the group waits for this client before resuming. */
    fun onBuffering(positionMs: Long, isPlaying: Boolean) {
        if (!groupControlsCurrentItem()) return
        viewModelScope.launch { syncPlay.localBuffering(positionMs.toTicks(), isPlaying) }
    }

    /** The player is ready again after buffering: releases the group's wait. */
    fun onReady(positionMs: Long, isPlaying: Boolean) {
        if (!groupControlsCurrentItem()) return
        viewModelScope.launch { syncPlay.localReady(positionMs.toTicks(), isPlaying) }
    }

    /** Periodic position report (every ~[PING_INTERVAL]); the server sends drift corrections. */
    fun onSyncTick(positionMs: Long) {
        if (!groupControlsCurrentItem()) return
        val last = lastPingAt
        if (last != null && last.elapsedNow() < PING_INTERVAL) return
        lastPingAt = TimeSource.Monotonic.markNow()
        viewModelScope.launch { syncPlay.localPing(positionMs.toTicks()) }
    }

    // ------------------------------------------------------------------------

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
        if (groupControlsCurrentItem()) reportScope.launch { syncPlay.localStop() }
    }

    private fun prepare(forceTranscode: Boolean, resumeAtMs: Long? = null) {
        viewModelScope.launch {
            when (val result = repository.prepare(target, forceTranscode)) {
                is Outcome.Success -> {
                    started = false
                    stopped = false
                    val startPaused = pendingStartPaused
                    pendingStartPaused = false
                    val request = result.value.let {
                        it.copy(
                            startPositionMs = resumeAtMs ?: it.startPositionMs,
                            startPaused = startPaused,
                        )
                    }
                    _uiState.value = PlayerUiState.Ready(request, ++generation)
                    _nextItem.value = null
                    _segments.value = emptyList()
                    if (request.isServerItem) {
                        val gen = generation
                        viewModelScope.launch {
                            val q = playQueue.queue.value
                            val queuedNext = q.nextIndex()?.let { q.entries[it].item }
                            val next = queuedNext ?: repository.nextEpisode(request.item)
                            val segs = repository.mediaSegments(request.item.id)
                            if (gen == generation) {
                                _nextItem.value = next
                                _segments.value = segs
                            }
                        }
                    }
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

    private fun Long.toTicks() = this * TICKS_PER_MILLISECOND
    private fun Long.ticksToMs() = this / TICKS_PER_MILLISECOND

    private companion object {
        val PING_INTERVAL = 5.seconds
    }
}
