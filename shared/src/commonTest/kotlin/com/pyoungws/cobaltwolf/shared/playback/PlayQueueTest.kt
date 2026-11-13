package com.pyoungws.cobaltwolf.shared.playback

import com.pyoungws.cobaltwolf.shared.model.MediaItem
import com.pyoungws.cobaltwolf.shared.model.MediaKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayQueueTest {
    private fun item(n: Int) = MediaItem(id = "id-$n", name = "Item $n", kind = MediaKind.Song)

    private fun repository(size: Int, start: Int = 0) = PlayQueueRepository().apply {
        playNow((1..size).map(::item), start)
    }

    @Test
    fun `playNow replaces the queue at the start index`() {
        val q = repository(3, 1).queue.value
        assertEquals(3, q.entries.size)
        assertEquals("id-2", q.current?.item?.id)
        assertEquals(listOf("id-3"), q.upcoming.map { it.item.id })
    }

    @Test
    fun `playNext inserts after the current entry`() {
        val r = repository(3)
        r.playNext(item(9))
        assertEquals(listOf("id-1", "id-9", "id-2", "id-3"), r.queue.value.entries.map { it.item.id })
    }

    @Test
    fun `playNext on an empty queue starts one`() {
        val r = PlayQueueRepository()
        r.playNext(item(9))
        assertEquals("id-9", r.queue.value.current?.item?.id)
    }

    @Test
    fun `enqueue appends to the end`() {
        val r = repository(2)
        r.enqueue(item(9))
        assertEquals("id-9", r.queue.value.entries.last().item.id)
        assertEquals("id-1", r.queue.value.current?.item?.id)
    }

    @Test
    fun `advance walks forward and stops at the end`() {
        val r = repository(2)
        assertEquals("id-2", r.advance()?.id)
        assertNull(r.advance())
    }

    @Test
    fun `repeat-all wraps around`() {
        val r = repository(2, start = 1)
        r.setRepeat(RepeatMode.All)
        assertEquals("id-1", r.advance()?.id)
        assertEquals("id-2", r.advance()?.id)
    }

    @Test
    fun `retreat walks back and stops at the start`() {
        val r = repository(2, start = 1)
        assertEquals("id-1", r.retreat()?.id)
        assertNull(r.retreat())
    }

    @Test
    fun `advanceTo jumps to a queued entry`() {
        val r = repository(3)
        assertEquals("id-3", r.advanceTo(2)?.id)
        assertEquals(2, r.queue.value.currentIndex)
    }

    @Test
    fun `remove before the current shifts the index, remove current keeps position`() {
        val r = repository(3, start = 2)
        r.remove(0)
        assertEquals("id-3", r.queue.value.current?.item?.id)
        assertEquals(1, r.queue.value.currentIndex)
        r.remove(1)
        assertEquals("id-2", r.queue.value.current?.item?.id)
    }

    @Test
    fun `shuffle keeps the current entry and restores the original order`() {
        val r = repository(8, start = 2)
        val before = r.queue.value.entries.map { it.item.id }
        r.shuffle()
        val shuffled = r.queue.value
        assertTrue(shuffled.shuffled)
        assertEquals(before[2], shuffled.current?.item?.id)
        assertEquals(before.take(3), shuffled.entries.take(3).map { it.item.id })
        assertEquals(before.size, shuffled.entries.size)
        r.unshuffle()
        assertEquals(before, r.queue.value.entries.map { it.item.id })
        assertEquals(2, r.queue.value.currentIndex)
    }

    @Test
    fun `cycle repeat walks off all one`() {
        val r = repository(1)
        r.cycleRepeat(); assertEquals(RepeatMode.All, r.queue.value.repeat)
        r.cycleRepeat(); assertEquals(RepeatMode.One, r.queue.value.repeat)
        r.cycleRepeat(); assertEquals(RepeatMode.Off, r.queue.value.repeat)
    }
}
