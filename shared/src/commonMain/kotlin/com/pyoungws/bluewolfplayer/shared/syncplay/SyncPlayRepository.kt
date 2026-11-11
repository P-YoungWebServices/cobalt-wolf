package com.pyoungws.bluewolfplayer.shared.syncplay

import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.SessionState
import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.model.Session
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayPlayerCommand.LoadItem
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayPlayerCommand.Pause
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayPlayerCommand.Resume
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayPlayerCommand.Seek
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayPlayerCommand.Stop
import kotlin.math.abs
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SyncPlay ("watch together") state. While a membership is active the repository collects the
 * server's websocket events, keeps [membership] up to date and translates group changes into
 * [playerCommands] for whichever player is attached. Local transport actions are reported back
 * through the `local*` functions so the group follows this device; corrections the server sends
 * back that only echo the position this client just reported are suppressed.
 *
 * Everything runs on [scope], which must outlive screens: a group survives the player closing.
 */
class SyncPlayRepository(
    private val dataSource: SyncPlayDataSource,
    private val guard: SessionGuard,
    private val authRepository: AuthRepository,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = { TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds },
) {
    /** The group this device is in, or null. */
    private val _membership = MutableStateFlow<SyncPlayMembership?>(null)
    val membership: StateFlow<SyncPlayMembership?> = _membership.asStateFlow()

    /** Commands the attached player applies (load/pause/resume/seek/stop). */
    private val _playerCommands = MutableSharedFlow<SyncPlayPlayerCommand>(extraBufferCapacity = COMMAND_BUFFER)
    val playerCommands: SharedFlow<SyncPlayPlayerCommand> = _playerCommands.asSharedFlow()

    /** User-facing messages such as "the group was closed". */
    private val _notices = MutableSharedFlow<SyncPlayNotice>(extraBufferCapacity = COMMAND_BUFFER)
    val notices: SharedFlow<SyncPlayNotice> = _notices.asSharedFlow()

    private var eventsJob: Job? = null
    /** The seek this client just asked for, so its echo is not applied twice. */
    private var echoedSeekTicks: Long? = null
    private var echoedSeekAtMs: Long = 0

    /** The session the group was joined under; the membership is dropped the moment it changes. */
    private var membershipSession: Session? = null

    init {
        scope.launch {
            authRepository.sessionState.collect { state ->
                val session = (state as? SessionState.SignedIn)?.session
                // Signing out — or switching to another server/account while signed in — leaves the group.
                if (_membership.value != null && membershipSession != session) leaveLocally()
            }
        }
    }

    suspend fun listGroups(): Outcome<List<SyncPlayGroup>> =
        guard.run { dataSource.listGroups(it) }

    /** Creates a group and joins it. [name] falls back to a default when blank. */
    suspend fun createAndJoin(name: String): Outcome<Unit> =
        guard.run { session ->
            val group = dataSource.createGroup(session, name.ifBlank { DEFAULT_GROUP_NAME })
            join(session, group)
        }

    suspend fun join(group: SyncPlayGroup): Outcome<Unit> =
        guard.run { session -> join(session, group) }

    suspend fun leave(): Outcome<Unit> {
        // Leave locally first so any GroupLeft echo finds nothing to do.
        val wasMember = _membership.value != null
        leaveLocally()
        if (!wasMember) return Outcome.Success(Unit)
        return guard.run { dataSource.leaveGroup(it) }
    }

    private suspend fun join(session: Session, group: SyncPlayGroup) {
        // The socket must be up before the join call, or the GroupJoined/UserJoined/queue events
        // sent on arrival could be missed.
        ensureEvents(session)
        try {
            dataSource.joinGroup(session, group.id)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (_membership.value == null) {
                eventsJob?.cancel()
                eventsJob = null
            }
            throw e
        }
        membershipSession = session
        enterMembership(group)
    }

    // ---- Player attachment -------------------------------------------------

    /**
     * Called when a player screen opens so it immediately receives the group's current state
     * (usually a [LoadItem] for the playing item). Safe to call outside a group: no-op.
     */
    fun attachPlayer() {
        val member = _membership.value ?: return
        val item = member.playingItem ?: return
        emit(LoadItem(item.itemId, estimatedTicks(member), member.isPlaying))
    }

    // ---- Local player reports (no-ops outside a group) ---------------------

    suspend fun localPause() = send {
        if (inGroup()) dataSource.pause(it)
    }

    suspend fun localResume() = send {
        if (inGroup()) dataSource.unpause(it)
    }

    suspend fun localSeek(positionTicks: Long) = send {
        if (inGroup()) {
            echoedSeekTicks = positionTicks
            echoedSeekAtMs = nowMs()
            dataSource.seek(it, positionTicks)
        }
    }

    suspend fun localStop() = send {
        if (inGroup()) dataSource.stop(it)
    }

    suspend fun localBuffering(positionTicks: Long, isPlaying: Boolean) = send {
        val playlistItemId = _membership.value?.playingItem?.playlistItemId ?: return@send
        dataSource.buffering(it, positionTicks, isPlaying, playlistItemId)
    }

    suspend fun localReady(positionTicks: Long, isPlaying: Boolean) = send {
        val playlistItemId = _membership.value?.playingItem?.playlistItemId ?: return@send
        dataSource.ready(it, positionTicks, isPlaying, playlistItemId)
    }

    suspend fun localPing(positionTicks: Long) = send {
        if (inGroup()) dataSource.ping(it, positionTicks)
    }

    /** Replaces the group queue and starts [startIndex]; requires control permission on the server. */
    suspend fun playInGroup(itemIds: List<String>, startIndex: Int = 0, startPositionTicks: Long = 0): Outcome<Unit> {
        if (_membership.value == null) return Outcome.Failure(LoadError.Unexpected("Not in a SyncPlay group"))
        return guard.run { session ->
            dataSource.setNewQueue(session, itemIds, startIndex, startPositionTicks)
        }
    }

    suspend fun nextItem(): Outcome<Unit> = playlistMove { session, playlistItemId ->
        dataSource.nextItem(session, playlistItemId)
    }

    suspend fun previousItem(): Outcome<Unit> = playlistMove { session, playlistItemId ->
        dataSource.previousItem(session, playlistItemId)
    }

    private suspend fun playlistMove(block: suspend (Session, String) -> Unit): Outcome<Unit> {
        val playlistItemId = _membership.value?.playingItem?.playlistItemId
            ?: return Outcome.Failure(LoadError.Unexpected("Not in a SyncPlay group"))
        return guard.run { session -> block(session, playlistItemId) }
    }

    private fun inGroup() = _membership.value != null

    /** Sends [block] through the session guard; failures are ignored — the group keeps moving. */
    private suspend fun send(block: suspend (Session) -> Unit) {
        guard.run { block(it) }
    }

    // ---- Event handling ----------------------------------------------------

    private fun ensureEvents(session: Session) {
        if (eventsJob?.isActive == true) return
        eventsJob = scope.launch {
            try {
                dataSource.events(session).collect { event -> onEvent(event) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onEvent(SyncPlayEvent.Error(e.message))
            }
        }
    }

    private suspend fun onEvent(event: SyncPlayEvent) {
        when (event) {
            is SyncPlayEvent.GroupJoined -> enterMembership(event.group)
            is SyncPlayEvent.GroupLeft -> leaveLocally()
            is SyncPlayEvent.UserJoined -> updateMember {
                it.copy(participants = (it.participants + event.userName).distinct())
            }
            is SyncPlayEvent.UserLeft -> updateMember { it.copy(participants = it.participants - event.userName) }
            is SyncPlayEvent.StateChanged -> onStateChanged(event.state)
            is SyncPlayEvent.QueueChanged -> onQueueChanged(event)
            is SyncPlayEvent.Correction -> onCorrection(event)
            is SyncPlayEvent.NotInGroup -> dropMembership(SyncPlayNotice.GroupClosed)
            is SyncPlayEvent.GroupDoesNotExist -> dropMembership(SyncPlayNotice.GroupGone)
            is SyncPlayEvent.LibraryAccessDenied -> dropMembership(SyncPlayNotice.LibraryAccessDenied)
            is SyncPlayEvent.Error -> dropMembership(SyncPlayNotice.EventsLost, leaveOnServer = true)
        }
    }

    private fun onStateChanged(state: SyncPlayGroupState) {
        val member = _membership.value ?: return
        when (state) {
            // While paused, or while the group waits for someone to finish buffering, the
            // group's position does not advance: freeze the extrapolation first.
            SyncPlayGroupState.Waiting, SyncPlayGroupState.Paused -> {
                _membership.value = member.copy(
                    groupState = state,
                    startPositionTicks = estimatedTicks(member),
                    isPlaying = false,
                    queueReceivedAtMs = nowMs(),
                )
                emit(Pause)
            }
            SyncPlayGroupState.Playing -> {
                _membership.value = member.copy(groupState = state, isPlaying = true, queueReceivedAtMs = nowMs())
                emit(Resume(estimatedTicks(member)))
            }
            SyncPlayGroupState.Idle -> {
                _membership.value = member.copy(groupState = state, isPlaying = false, queueReceivedAtMs = nowMs())
                emit(Stop)
            }
        }
    }

    private fun onQueueChanged(event: SyncPlayEvent.QueueChanged) {
        val member = _membership.value ?: return
        val previousItemId = member.playingItem?.itemId
        // What the position should be if the queue only kept running since the last update:
        // a routine queue edit does not move it, a real seek does.
        val expectedTicks = estimatedTicks(member)
        val updated = member.copy(
            queue = event.items,
            playingItemIndex = event.playingItemIndex,
            startPositionTicks = event.startPositionTicks,
            isPlaying = event.isPlaying,
            queueReceivedAtMs = nowMs(),
        )
        _membership.value = updated
        val newItem = event.items.getOrNull(event.playingItemIndex)
        when {
            newItem == null -> if (previousItemId != null) emit(Stop)
            newItem.itemId != previousItemId ->
                emit(LoadItem(newItem.itemId, estimatedTicks(updated), event.isPlaying))
            abs(event.startPositionTicks - expectedTicks) > SEEK_TOLERANCE_TICKS ->
                if (!isEchoedSeek(event.startPositionTicks)) emit(Seek(event.startPositionTicks))
        }
    }

    private fun onCorrection(correction: SyncPlayEvent.Correction) {
        if (_membership.value == null) return
        when (correction.type) {
            CorrectionType.Seek -> {
                val ticks = correction.positionTicks ?: return
                if (!isEchoedSeek(ticks)) emit(Seek(ticks))
            }
            CorrectionType.Pause -> emit(Pause)
            CorrectionType.Unpause -> {
                _membership.value = _membership.value?.copy(isPlaying = true, queueReceivedAtMs = nowMs())
                emit(Resume(correction.positionTicks))
            }
            CorrectionType.Stop -> emit(Stop)
        }
    }

    /** True when [ticks] matches the seek this client just sent — the echo must not be applied. */
    private fun isEchoedSeek(ticks: Long): Boolean {
        val echoed = echoedSeekTicks ?: return false
        return abs(ticks - echoed) < SEEK_TOLERANCE_TICKS && nowMs() - echoedSeekAtMs < ECHO_WINDOW_MS
    }

    private fun enterMembership(group: SyncPlayGroup) {
        val current = _membership.value
        _membership.value = if (current != null && current.groupId == group.id) {
            // Already inside: refresh the listed fields, keep the queue we already know.
            current.copy(groupName = group.name, participants = group.participants, groupState = group.state)
        } else {
            SyncPlayMembership(
                groupId = group.id,
                groupName = group.name,
                participants = group.participants,
                groupState = group.state,
                queue = emptyList(),
                playingItemIndex = -1,
                startPositionTicks = 0,
                isPlaying = group.state == SyncPlayGroupState.Playing,
                queueReceivedAtMs = nowMs(),
            )
        }
    }

    private fun updateMember(transform: (SyncPlayMembership) -> SyncPlayMembership) {
        _membership.value = _membership.value?.let(transform) ?: return
    }

    /** The group's position right now: the last reported position plus elapsed time while playing. */
    private fun estimatedTicks(member: SyncPlayMembership): Long =
        member.startPositionTicks + if (member.isPlaying) (nowMs() - member.queueReceivedAtMs).coerceAtLeast(0) * TICKS_PER_MS else 0

    private fun emit(command: SyncPlayPlayerCommand) {
        scope.launch { _playerCommands.emit(command) }
    }

    private fun dropMembership(notice: SyncPlayNotice, leaveOnServer: Boolean = false) {
        if (_membership.value == null) return
        leaveLocally()
        scope.launch { _notices.emit(notice) }
        // The event stream is gone but the server still counts this session as a member.
        if (leaveOnServer) scope.launch { guard.run { dataSource.leaveGroup(it) } }
    }

    private fun leaveLocally() {
        eventsJob?.cancel()
        eventsJob = null
        membershipSession = null
        _membership.value = null
        echoedSeekTicks = null
    }

    private companion object {
        const val DEFAULT_GROUP_NAME = "Cobalt Wolf"
        const val COMMAND_BUFFER = 32
        /** How long a locally sent action can still explain an incoming echo. */
        const val ECHO_WINDOW_MS = 5_000L
        /** Position differences below this are treated as equal (~1.5 s). */
        const val SEEK_TOLERANCE_TICKS = 15_000_000L
        const val TICKS_PER_MS = 10_000L
    }
}
