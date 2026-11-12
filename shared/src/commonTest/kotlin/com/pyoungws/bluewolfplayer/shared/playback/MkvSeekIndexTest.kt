package com.pyoungws.bluewolfplayer.shared.playback

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MkvSeekIndexTest {

    // --- Minimal EBML writer for fixtures ---------------------------------

    private fun vint(value: Long): ByteArray {
        var length = 1
        var v = value
        while (length < 8 && v >= (1L shl (7 * length))) length++
        val out = ByteArray(length)
        for (i in length - 1 downTo 1) {
            out[i] = (v and 0xFF).toByte()
            v = v shr 8
        }
        out[0] = (v or (0x80L shr (length - 1))).toByte()
        return out
    }

    private fun idBytes(id: Long): ByteArray {
        val bytes = mutableListOf<Byte>()
        var v = id
        while (v > 0) { bytes.add(0, (v and 0xFF).toByte()); v = v shr 8 }
        return bytes.toByteArray()
    }

    private fun element(id: Long, payload: ByteArray) = idBytes(id) + vint(payload.size.toLong()) + payload

    private fun uintElement(id: Long, value: Long): ByteArray {
        val bytes = mutableListOf<Byte>()
        var v = value
        while (v > 0) { bytes.add(0, (v and 0xFF).toByte()); v = v shr 8 }
        if (bytes.isEmpty()) bytes.add(0)
        return element(id, bytes.toByteArray())
    }

    private fun seekEntry(id: Long, position: Long) =
        element(0x4DBB, element(0x53AB, idBytes(id)) + uintElement(0x53AC, position))

    private fun cuePoint(timeTicks: Long, clusterPosition: Long) =
        element(0xBB, uintElement(0xB3, timeTicks) + element(0xB7, uintElement(0xF1, clusterPosition)))

    // EBML header (8B) + Segment id (4B) + unknown-size vint (1B) => segment data starts at 13.
    private val ebmlHeader = element(0x1A45DFA3, byteArrayOf(1, 2, 3))
    private val segmentDataOffset = (ebmlHeader.size + 5).toLong()

    /** Builds a file whose segment holds the given top-level elements, sized to [totalSize]. */
    private fun mkv(totalSize: Int, vararg topLevel: ByteArray): ByteArray {
        val head = ebmlHeader + idBytes(0x18538067) + byteArrayOf(0xFF.toByte())
        val combined = head + topLevel.fold(byteArrayOf()) { acc, b -> acc + b }
        val file = ByteArray(maxOf(totalSize, combined.size))
        combined.copyInto(file)
        return file
    }

    /** Fake file served through range requests like an HTTP server would. */
    private fun fakeFetch(file: ByteArray): ByteRangeFetch = { start, end ->
        val to = (end + 1).coerceAtMost(file.size.toLong()).toInt()
        file.copyOfRange(start.toInt(), to)
    }

    // --- Tests --------------------------------------------------------------

    @Test
    fun `reads cues pointed directly by the head SeekHead`() = runTest {
        val cuesOffset = 4_000L
        val cues = element(0x1C53BB6B, cuePoint(1_000, 10_000) + cuePoint(2_000, 20_000))
        val seekHead = element(0x114D9B74, seekEntry(0x1C53BB6B, cuesOffset - segmentDataOffset))
        val info = element(0x1549A966, uintElement(0x2AD7B1, 1_000_000))
        val file = mkv((cuesOffset + cues.size).toInt(), seekHead, info)
        cues.copyInto(file, cuesOffset.toInt())

        val points = probeMkvCueIndex(fakeFetch(file))
        assertEquals(
            listOf(MkvCuePoint(1_000, segmentDataOffset + 10_000), MkvCuePoint(2_000, segmentDataOffset + 20_000)),
            points,
        )
    }

    @Test
    fun `follows a trailing SeekHead to find the Cues element`() = runTest {
        val cuesOffset = 6_000L
        val trailingSeekHeadOffset = 5_000L
        val cues = element(0x1C53BB6B, cuePoint(500, 7_000) + cuePoint(9_000, 8_000))
        val headSeekHead = element(0x114D9B74, seekEntry(0x114D9B74, trailingSeekHeadOffset - segmentDataOffset))
        val trailingSeekHead = element(0x114D9B74, seekEntry(0x1C53BB6B, cuesOffset - segmentDataOffset) + seekEntry(0x1654AE6B, 200))

        val file = mkv((cuesOffset + cues.size).toInt(), headSeekHead)
        trailingSeekHead.copyInto(file, trailingSeekHeadOffset.toInt())
        cues.copyInto(file, cuesOffset.toInt())

        val points = probeMkvCueIndex(fakeFetch(file))
        assertEquals(
            listOf(MkvCuePoint(500, segmentDataOffset + 7_000), MkvCuePoint(9_000, segmentDataOffset + 8_000)),
            points,
        )
    }

    @Test
    fun `returns null when no SeekHead references the Cues element`() = runTest {
        val seekHead = element(0x114D9B74, seekEntry(0x1654AE6B, 300))
        assertNull(probeMkvCueIndex(fakeFetch(mkv(0, seekHead))))
    }

    @Test
    fun `applies TimecodeScale to cue times`() = runTest {
        val cuesOffset = 3_000L
        val cues = element(0x1C53BB6B, cuePoint(1_000, 5_000))
        val seekHead = element(0x114D9B74, seekEntry(0x1C53BB6B, cuesOffset - segmentDataOffset))
        val info = element(0x1549A966, uintElement(0x2AD7B1, 2_000_000)) // 2 ms per tick
        val file = mkv((cuesOffset + cues.size).toInt(), seekHead, info)
        cues.copyInto(file, cuesOffset.toInt())

        val points = probeMkvCueIndex(fakeFetch(file))
        assertEquals(listOf(MkvCuePoint(2_000, segmentDataOffset + 5_000)), points)
    }
}
