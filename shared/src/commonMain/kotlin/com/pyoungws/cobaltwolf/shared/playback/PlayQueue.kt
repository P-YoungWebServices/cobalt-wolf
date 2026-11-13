package com.pyoungws.cobaltwolf.shared.playback

import com.pyoungws.cobaltwolf.shared.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

enum class RepeatMode { Off, All, One }

/** One queued item; [startPositionMs] overrides the resume point when it becomes current. */
data class QueueEntry(val item: MediaItem, val startPositionMs: Long? = null)

/**
 * The ordered play queue backing play-next/add-to-queue, the visible queue UI, shuffle and repeat.
 * [currentIndex] points at the playing entry; entries after it are "up next".
 * [unshuffled] keeps the original order so shuffle can be undone.
 */
data class PlayQueue(
    val entries: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val repeat: RepeatMode = RepeatMode.Off,
    val unshuffled: List<QueueEntry>? = null,
) {
    val current: QueueEntry? get() = entries.getOrNull(currentIndex)
    val upcoming: List<QueueEntry> get() = entries.drop(currentIndex + 1)
    val shuffled: Boolean get() = unshuffled != null

    /** Index [advance] would land on: next entry, wrapping on [RepeatMode.All]; null at the end. */
    fun nextIndex(): Int? = when {
        entries.isEmpty() -> null
        currentIndex + 1 < entries.size -> currentIndex + 1
        repeat == RepeatMode.All -> 0
        else -> null
    }

    fun previousIndex(): Int? = when {
        entries.isEmpty() -> null
        currentIndex > 0 -> currentIndex - 1
        repeat == RepeatMode.All -> entries.lastIndex
        else -> null
    }
}

/**
 * App-wide play queue singleton. The video player reads the next entry on end-of-stream, the audio
 * player treats the whole queue as its track list, and item menus mutate it from anywhere.
 */
class PlayQueueRepository {
    private val _queue = MutableStateFlow(PlayQueue())
    val queue: StateFlow<PlayQueue> = _queue.asStateFlow()

    /** Replaces the queue and starts at [startIndex]. */
    fun playNow(items: List<MediaItem>, startIndex: Int = 0) {
        _queue.value = PlayQueue(
            entries = items.map { QueueEntry(it) },
            currentIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
            repeat = _queue.value.repeat,
        )
    }

    /** Inserts [item] directly after the current entry — it plays next. */
    fun playNext(item: MediaItem) = _queue.update { q ->
        if (q.entries.isEmpty()) PlayQueue(listOf(QueueEntry(item)), 0, q.repeat)
        else q.copy(entries = q.entries.toMutableList().apply { add(q.currentIndex + 1, QueueEntry(item)) })
    }

    /** Appends [item] to the end of the queue. */
    fun enqueue(item: MediaItem) = _queue.update { q ->
        if (q.entries.isEmpty()) PlayQueue(listOf(QueueEntry(item)), 0, q.repeat)
        else q.copy(entries = q.entries + QueueEntry(item))
    }

    fun enqueueAll(items: List<MediaItem>) = _queue.update { q ->
        if (q.entries.isEmpty()) PlayQueue(items.map { QueueEntry(it) }, 0, q.repeat)
        else q.copy(entries = q.entries + items.map { QueueEntry(it) })
    }

    fun remove(index: Int) = _queue.update { q ->
        if (index !in q.entries.indices) q
        else {
            val entries = q.entries.toMutableList().apply { removeAt(index) }
            q.copy(
                entries = entries,
                currentIndex = when {
                    index < q.currentIndex -> q.currentIndex - 1
                    index == q.currentIndex -> q.currentIndex.coerceAtMost(entries.lastIndex)
                    else -> q.currentIndex
                },
            )
        }
    }

    fun clear() = _queue.update { it.copy(entries = emptyList(), currentIndex = -1, unshuffled = null) }

    /** Jumps to [index]; the returned entry is what should be prepared next. */
    fun advanceTo(index: Int): MediaItem? {
        val q = _queue.value
        if (index !in q.entries.indices) return null
        _queue.value = q.copy(currentIndex = index)
        return q.entries[index].item
    }

    /** Moves to [PlayQueue.nextIndex]; null when the queue is exhausted. */
    fun advance(): MediaItem? = _queue.value.nextIndex()?.let { advanceTo(it) }

    /** Moves to [PlayQueue.previousIndex]; null when there is no previous entry. */
    fun retreat(): MediaItem? = _queue.value.previousIndex()?.let { advanceTo(it) }

    fun setRepeat(mode: RepeatMode) = _queue.update { it.copy(repeat = mode) }

    /** Cycles Off → All → One, the single repeat button's behaviour. */
    fun cycleRepeat() = setRepeat(when (_queue.value.repeat) {
        RepeatMode.Off -> RepeatMode.All
        RepeatMode.All -> RepeatMode.One
        RepeatMode.One -> RepeatMode.Off
    })

    /** Reorders the unplayed entries randomly, keeping the current one in place; stores the original order. */
    fun shuffle() = _queue.update { q ->
        if (q.unshuffled != null || q.upcoming.size < 2) q
        else q.copy(
            entries = q.entries.take(q.currentIndex + 1) + q.upcoming.shuffled(Random),
            unshuffled = q.entries,
        )
    }

    /** Restores the pre-shuffle order, keeping the current entry's position. */
    fun unshuffle() = _queue.update { q ->
        val original = q.unshuffled ?: return@update q
        val current = q.current ?: return@update q.copy(entries = original, unshuffled = null)
        val remaining = original.filter { it.item.id != current.item.id }
        val playedIds = q.entries.take(q.currentIndex).map { it.item.id }.toSet()
        val entries = original.filter { it.item.id in playedIds } +
            current +
            remaining.filter { it.item.id !in playedIds }
        q.copy(entries = entries, currentIndex = entries.indexOfFirst { it.item.id == current.item.id }.coerceAtLeast(0), unshuffled = null)
    }

    fun toggleShuffle() = if (_queue.value.shuffled) unshuffle() else shuffle()
}
