package com.pyoungws.bluewolfplayer.shared.syncplay

import com.pyoungws.bluewolfplayer.shared.model.Session
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.syncPlayApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BufferRequestDto
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.GroupQueueMode
import org.jellyfin.sdk.model.api.GroupStateType
import org.jellyfin.sdk.model.api.JoinGroupRequestDto
import org.jellyfin.sdk.model.api.NewGroupRequestDto
import org.jellyfin.sdk.model.api.NextItemRequestDto
import org.jellyfin.sdk.model.api.PingRequestDto
import org.jellyfin.sdk.model.api.PlayRequestDto
import org.jellyfin.sdk.model.api.PreviousItemRequestDto
import org.jellyfin.sdk.model.api.QueueRequestDto
import org.jellyfin.sdk.model.api.ReadyRequestDto
import org.jellyfin.sdk.model.api.SeekRequestDto
import org.jellyfin.sdk.model.api.SendCommand
import org.jellyfin.sdk.model.api.SendCommandType
import org.jellyfin.sdk.model.api.SyncPlayCommandMessage
import org.jellyfin.sdk.model.api.SyncPlayGroupDoesNotExistUpdate
import org.jellyfin.sdk.model.api.SyncPlayGroupJoinedUpdate
import org.jellyfin.sdk.model.api.SyncPlayGroupLeftUpdate
import org.jellyfin.sdk.model.api.SyncPlayGroupUpdateMessage
import org.jellyfin.sdk.model.api.SyncPlayLibraryAccessDeniedUpdate
import org.jellyfin.sdk.model.api.SyncPlayNotInGroupUpdate
import org.jellyfin.sdk.model.api.SyncPlayPlayQueueUpdate
import org.jellyfin.sdk.model.api.SyncPlayStateUpdate
import org.jellyfin.sdk.model.api.SyncPlayUserJoinedUpdate
import org.jellyfin.sdk.model.api.SyncPlayUserLeftUpdate

/**
 * SyncPlay endpoints and the session websocket. Implementations throw the SDK's
 * `ApiClientException` subtypes on request failures; [events] surfaces stream problems as
 * [SyncPlayEvent.Error] instead of throwing.
 */
interface SyncPlayDataSource {
    suspend fun listGroups(session: Session): List<SyncPlayGroup>
    suspend fun createGroup(session: Session, name: String): SyncPlayGroup
    suspend fun joinGroup(session: Session, groupId: String)
    suspend fun leaveGroup(session: Session)

    /** Local transport actions reported to the group; only valid while in one. */
    suspend fun pause(session: Session)
    suspend fun unpause(session: Session)
    suspend fun seek(session: Session, positionTicks: Long)
    suspend fun stop(session: Session)

    suspend fun setNewQueue(session: Session, itemIds: List<String>, startIndex: Int, startPositionTicks: Long)
    suspend fun queue(session: Session, itemIds: List<String>)
    suspend fun nextItem(session: Session, playlistItemId: String)
    suspend fun previousItem(session: Session, playlistItemId: String)

    /** Sync reports: the server uses them to compute this client's offset and send corrections. */
    suspend fun buffering(session: Session, positionTicks: Long, isPlaying: Boolean, playlistItemId: String)
    suspend fun ready(session: Session, positionTicks: Long, isPlaying: Boolean, playlistItemId: String)
    suspend fun ping(session: Session, positionTicks: Long)

    /** Group updates and per-client corrections, for as long as the flow is collected. */
    fun events(session: Session): Flow<SyncPlayEvent>
}

class SdkSyncPlayDataSource(
    private val jellyfin: Jellyfin,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SyncPlayDataSource {

    private suspend fun <T> call(session: Session, block: suspend ApiClient.() -> T): T =
        withContext(ioDispatcher) {
            jellyfin.createApi(baseUrl = session.serverAddress, accessToken = session.accessToken).block()
        }

    override suspend fun listGroups(session: Session): List<SyncPlayGroup> = call(session) {
        syncPlayApi.syncPlayGetGroups().content.map(GroupInfoDto::toSyncPlayGroup)
    }

    override suspend fun createGroup(session: Session, name: String): SyncPlayGroup = call(session) {
        syncPlayApi.syncPlayCreateGroup(NewGroupRequestDto(name)).content.toSyncPlayGroup()
    }

    override suspend fun joinGroup(session: Session, groupId: String) = call(session) {
        syncPlayApi.syncPlayJoinGroup(JoinGroupRequestDto(UUID.fromString(groupId)))
        Unit
    }

    override suspend fun leaveGroup(session: Session) = call(session) {
        syncPlayApi.syncPlayLeaveGroup()
        Unit
    }

    override suspend fun pause(session: Session) = call(session) {
        syncPlayApi.syncPlayPause()
        Unit
    }

    override suspend fun unpause(session: Session) = call(session) {
        syncPlayApi.syncPlayUnpause()
        Unit
    }

    override suspend fun seek(session: Session, positionTicks: Long) = call(session) {
        syncPlayApi.syncPlaySeek(SeekRequestDto(positionTicks))
        Unit
    }

    override suspend fun stop(session: Session) = call(session) {
        syncPlayApi.syncPlayStop()
        Unit
    }

    override suspend fun setNewQueue(session: Session, itemIds: List<String>, startIndex: Int, startPositionTicks: Long) =
        call(session) {
            syncPlayApi.syncPlaySetNewQueue(PlayRequestDto(itemIds.map(UUID::fromString), startIndex, startPositionTicks))
            Unit
        }

    override suspend fun queue(session: Session, itemIds: List<String>) = call(session) {
        syncPlayApi.syncPlayQueue(QueueRequestDto(itemIds.map(UUID::fromString), GroupQueueMode.QUEUE))
        Unit
    }

    override suspend fun nextItem(session: Session, playlistItemId: String) = call(session) {
        syncPlayApi.syncPlayNextItem(NextItemRequestDto(UUID.fromString(playlistItemId)))
        Unit
    }

    override suspend fun previousItem(session: Session, playlistItemId: String) = call(session) {
        syncPlayApi.syncPlayPreviousItem(PreviousItemRequestDto(UUID.fromString(playlistItemId)))
        Unit
    }

    override suspend fun buffering(session: Session, positionTicks: Long, isPlaying: Boolean, playlistItemId: String) =
        call(session) {
            syncPlayApi.syncPlayBuffering(BufferRequestDto(utcNow(), positionTicks, isPlaying, UUID.fromString(playlistItemId)))
            Unit
        }

    override suspend fun ready(session: Session, positionTicks: Long, isPlaying: Boolean, playlistItemId: String) =
        call(session) {
            syncPlayApi.syncPlayReady(ReadyRequestDto(utcNow(), positionTicks, isPlaying, UUID.fromString(playlistItemId)))
            Unit
        }

    override suspend fun ping(session: Session, positionTicks: Long) = call(session) {
        syncPlayApi.syncPlayPing(PingRequestDto(positionTicks))
        Unit
    }

    override fun events(session: Session): Flow<SyncPlayEvent> =
        jellyfin.createApi(baseUrl = session.serverAddress, accessToken = session.accessToken)
            .webSocket.subscribeAll()
            .mapNotNull { message ->
                when (message) {
                    is SyncPlayGroupUpdateMessage -> message.data.toEvent()
                    is SyncPlayCommandMessage -> message.data?.toEvent()
                    else -> null
                }
            }
            .flowOn(ioDispatcher)
}

private fun utcNow(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

internal fun GroupInfoDto.toSyncPlayGroup() = SyncPlayGroup(
    id = groupId.toString(),
    name = groupName,
    state = state.toSyncPlayGroupState(),
    participants = participants,
)

private fun GroupStateType.toSyncPlayGroupState() = when (this) {
    GroupStateType.IDLE -> SyncPlayGroupState.Idle
    GroupStateType.WAITING -> SyncPlayGroupState.Waiting
    GroupStateType.PAUSED -> SyncPlayGroupState.Paused
    GroupStateType.PLAYING -> SyncPlayGroupState.Playing
}

private fun org.jellyfin.sdk.model.api.GroupUpdate.toEvent(): SyncPlayEvent = when (this) {
    is SyncPlayGroupJoinedUpdate -> SyncPlayEvent.GroupJoined(data.toSyncPlayGroup())
    is SyncPlayGroupLeftUpdate -> SyncPlayEvent.GroupLeft
    is SyncPlayUserJoinedUpdate -> SyncPlayEvent.UserJoined(data)
    is SyncPlayUserLeftUpdate -> SyncPlayEvent.UserLeft(data)
    is SyncPlayStateUpdate -> SyncPlayEvent.StateChanged(data.state.toSyncPlayGroupState())
    is SyncPlayPlayQueueUpdate -> SyncPlayEvent.QueueChanged(
        items = data.playlist.map { SyncPlayQueueItem(it.itemId.toString(), it.playlistItemId.toString()) },
        playingItemIndex = data.playingItemIndex,
        startPositionTicks = data.startPositionTicks,
        isPlaying = data.isPlaying,
    )
    is SyncPlayNotInGroupUpdate -> SyncPlayEvent.NotInGroup
    is SyncPlayGroupDoesNotExistUpdate -> SyncPlayEvent.GroupDoesNotExist
    is SyncPlayLibraryAccessDeniedUpdate -> SyncPlayEvent.LibraryAccessDenied
}

private fun SendCommand.toEvent(): SyncPlayEvent? = when (command) {
    SendCommandType.UNPAUSE -> SyncPlayEvent.Correction(CorrectionType.Unpause, positionTicks)
    SendCommandType.PAUSE -> SyncPlayEvent.Correction(CorrectionType.Pause, positionTicks)
    SendCommandType.STOP -> SyncPlayEvent.Correction(CorrectionType.Stop, positionTicks)
    SendCommandType.SEEK -> SyncPlayEvent.Correction(CorrectionType.Seek, positionTicks)
}
