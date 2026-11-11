package com.pyoungws.bluewolfplayer.shared.syncplay

import com.pyoungws.bluewolfplayer.shared.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

fun group(id: String, name: String = id, state: SyncPlayGroupState = SyncPlayGroupState.Idle, participants: List<String> = emptyList()) =
    SyncPlayGroup(id, name, state, participants)

fun queueItem(itemId: String, playlistItemId: String = "pl-$itemId") = SyncPlayQueueItem(itemId, playlistItemId)

class FakeSyncPlayDataSource : SyncPlayDataSource {
    private val _events = MutableSharedFlow<SyncPlayEvent>(extraBufferCapacity = 16)
    val calls = mutableListOf<String>()

    var groups: List<SyncPlayGroup> = emptyList()
    var created: (String) -> SyncPlayGroup = { group("g-new", it) }
    var joinResult: (String) -> Unit = {}
    var lastSeekTicks: Long? = null
    var lastPingTicks: Long? = null
    var lastQueue: List<String>? = null

    /** Pushes a server-side event to the subscriber. */
    suspend fun emit(event: SyncPlayEvent) = _events.emit(event)

    override suspend fun listGroups(session: Session): List<SyncPlayGroup> = groups.also { calls += "groups" }
    override suspend fun createGroup(session: Session, name: String): SyncPlayGroup = created(name).also { calls += "create($name)" }
    override suspend fun joinGroup(session: Session, groupId: String) {
        calls += "join($groupId)"
        joinResult(groupId)
    }
    override suspend fun leaveGroup(session: Session) {
        calls += "leave"
    }
    override suspend fun pause(session: Session) {
        calls += "pause"
    }
    override suspend fun unpause(session: Session) {
        calls += "unpause"
    }
    override suspend fun seek(session: Session, positionTicks: Long) {
        calls += "seek"
        lastSeekTicks = positionTicks
    }
    override suspend fun stop(session: Session) {
        calls += "stop"
    }
    override suspend fun setNewQueue(session: Session, itemIds: List<String>, startIndex: Int, startPositionTicks: Long) {
        calls += "setNewQueue"
        lastQueue = itemIds
    }
    override suspend fun queue(session: Session, itemIds: List<String>) {
        calls += "queue"
    }
    override suspend fun nextItem(session: Session, playlistItemId: String) {
        calls += "next($playlistItemId)"
    }
    override suspend fun previousItem(session: Session, playlistItemId: String) {
        calls += "prev($playlistItemId)"
    }
    override suspend fun buffering(session: Session, positionTicks: Long, isPlaying: Boolean, playlistItemId: String) {
        calls += "buffering($positionTicks)"
    }
    override suspend fun ready(session: Session, positionTicks: Long, isPlaying: Boolean, playlistItemId: String) {
        calls += "ready($positionTicks)"
    }
    override suspend fun ping(session: Session, positionTicks: Long) {
        calls += "ping"
        lastPingTicks = positionTicks
    }
    override fun events(session: Session): Flow<SyncPlayEvent> = _events
}
