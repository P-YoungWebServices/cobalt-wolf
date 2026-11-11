package com.pyoungws.bluewolfplayer.shared.syncplay

/** Playback state of a SyncPlay group, as the server reports it. */
enum class SyncPlayGroupState { Idle, Waiting, Paused, Playing }

/** A SyncPlay group as listed by the server. */
data class SyncPlayGroup(
    val id: String,
    val name: String,
    val state: SyncPlayGroupState,
    val participants: List<String>,
)

/** One entry of a group's play queue. [playlistItemId] identifies the queue slot, [itemId] the media. */
data class SyncPlayQueueItem(val itemId: String, val playlistItemId: String)

/**
 * What the group is doing, kept while the user is a member. The position is the queue's start
 * position, extrapolated by the repository while the group is playing.
 */
data class SyncPlayMembership(
    val groupId: String,
    val groupName: String,
    val participants: List<String>,
    val groupState: SyncPlayGroupState,
    val queue: List<SyncPlayQueueItem>,
    val playingItemIndex: Int,
    /** Server-reported position of the playing item when [queueReceivedAtMs] was recorded. */
    val startPositionTicks: Long,
    val isPlaying: Boolean,
    /** Client monotonic timestamp (ms) at which [startPositionTicks]/[isPlaying] were last updated. */
    val queueReceivedAtMs: Long,
) {
    val playingItem: SyncPlayQueueItem? get() = queue.getOrNull(playingItemIndex)
}

/**
 * A command the local player must apply to stay in sync. Emitted by [SyncPlayRepository] whenever
 * the group changes something the player cares about.
 */
sealed interface SyncPlayPlayerCommand {
    /** Play [itemId] from [positionTicks]; starts paused when [playWhenReady] is false. */
    data class LoadItem(val itemId: String, val positionTicks: Long, val playWhenReady: Boolean) : SyncPlayPlayerCommand
    data object Pause : SyncPlayPlayerCommand
    /** Resume playback, seeking to [positionTicks] first when the local position drifted. */
    data class Resume(val positionTicks: Long?) : SyncPlayPlayerCommand
    data class Seek(val positionTicks: Long) : SyncPlayPlayerCommand
    /** The group stopped playback or switched to idle. */
    data object Stop : SyncPlayPlayerCommand
}

/**
 * Server → client events, already mapped from the SDK's websocket messages. The repository
 * consumes these; the player never sees SDK types.
 */
sealed interface SyncPlayEvent {
    /** This client joined a group. */
    data class GroupJoined(val group: SyncPlayGroup) : SyncPlayEvent
    /** This client left (or was removed from) the group. */
    data object GroupLeft : SyncPlayEvent
    data class UserJoined(val userName: String) : SyncPlayEvent
    data class UserLeft(val userName: String) : SyncPlayEvent
    /** The group's state changed (paused, resumed, started waiting for a buffer, went idle). */
    data class StateChanged(val state: SyncPlayGroupState) : SyncPlayEvent
    /** The play queue or the currently playing item changed. */
    data class QueueChanged(
        val items: List<SyncPlayQueueItem>,
        val playingItemIndex: Int,
        val startPositionTicks: Long,
        val isPlaying: Boolean,
    ) : SyncPlayEvent
    /** A per-client correction: seek to [positionTicks], pause, unpause or stop. */
    data class Correction(val type: CorrectionType, val positionTicks: Long?) : SyncPlayEvent
    /** The last request was rejected because this client is not in a group. */
    data object NotInGroup : SyncPlayEvent
    /** A join target disappeared before we could get in. */
    data object GroupDoesNotExist : SyncPlayEvent
    /** The group's library is not shared with this user. */
    data object LibraryAccessDenied : SyncPlayEvent
    /** The event stream failed (e.g. the websocket dropped and could not reconnect). */
    data class Error(val message: String?) : SyncPlayEvent
}

enum class CorrectionType { Unpause, Pause, Stop, Seek }

/** One-way messages surfaced to the UI as toasts/banners. */
enum class SyncPlayNotice {
    GroupClosed,
    GroupGone,
    LibraryAccessDenied,
    EventsLost,
}
