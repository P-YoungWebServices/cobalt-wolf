package com.pyoungws.bluewolfplayer.shared.playback

/**
 * Matroska/WebM seek data parsed out of the file's own Cues index.
 *
 * Many Matroska files (ffmpeg remuxes especially) only reference their Cues element from a second
 * SeekHead at the end of the file. Media3's `MatroskaExtractor` never follows that indirection, so
 * the stream reports itself unseekable and every seek control dies. We probe the file over HTTP
 * Range requests before playback and hand the index to the player, which injects it as the stream's
 * `SeekMap` when the extractor comes up empty.
 */

/** One cue entry: a point in time mapped to the absolute byte offset of its cluster. */
data class MkvCuePoint(val timeMs: Long, val position: Long)

private const val ID_SEGMENT = 0x18538067
private const val ID_INFO = 0x1549A966
private const val ID_SEEK_HEAD = 0x114D9B74
private const val ID_CUES = 0x1C53BB6B
private const val ID_SEEK = 0x4DBB
private const val ID_SEEK_ID = 0x53AB
private const val ID_SEEK_POSITION = 0x53AC
private const val ID_TIMECODE_SCALE = 0x2AD7B1
private const val ID_CUE_POINT = 0xBB
private const val ID_CUE_TIME = 0xB3
private const val ID_CUE_TRACK_POSITIONS = 0xB7
private const val ID_CUE_CLUSTER_POSITION = 0xF1

private const val HEAD_READ_BYTES = 1L shl 20      // 1 MiB covers EBML/SeekHead/Info/Tracks
private const val SEEK_HEAD_READ_BYTES = 64L shl 10
private const val ELEMENT_HEADER_BYTES = 16L
private const val MAX_CUES_BYTES = 8L shl 20       // absurdly large indexes are not worth fetching
private const val DEFAULT_TIMECODE_SCALE_NS = 1_000_000L
private const val MAX_SEEK_HEADS = 4

/**
 * Fetches the range `[start, end]` (inclusive) of the media file. Implemented by the data source
 * over HTTP Range requests in production and by synthetic fixtures in tests.
 */
internal typealias ByteRangeFetch = suspend (start: Long, end: Long) -> ByteArray

/** Minimal EBML element reader over a fixed buffer. */
private class Ebml(private val bytes: ByteArray) {

    /** Reads an element id at [offset]: 1–4 bytes, marker bits kept. Null when truncated. */
    fun idAt(offset: Int): Pair<Int, Int>? {
        if (offset >= bytes.size) return null
        var length = 1
        while (length <= 4 && bytes[offset].toInt() and (0x80 shr (length - 1)) == 0) length++
        if (length > 4 || offset + length > bytes.size) return null
        var value = 0
        for (i in 0 until length) value = (value shl 8) or (bytes[offset + i].toInt() and 0xFF)
        return value to length
    }

    /** Reads a variable-length integer at [offset]: 1–8 bytes, marker bit stripped. Null when truncated. */
    fun vintAt(offset: Int): Pair<Long, Int>? {
        if (offset >= bytes.size) return null
        var length = 1
        while (length <= 8 && bytes[offset].toInt() and (0x80 shr (length - 1)) == 0) length++
        if (length > 8 || offset + length > bytes.size) return null
        var value = 0L
        for (i in 0 until length) value = (value shl 8) or (bytes[offset + i].toInt() and 0xFF).toLong()
        // Strip the length marker bit so the value is payload only (7 usable bits per declared byte).
        value = value and ((1L shl (7 * length)) - 1)
        return value to length
    }

    fun uintAt(offset: Int, size: Int): Long {
        var value = 0L
        for (i in 0 until size) value = (value shl 8) or (bytes[offset + i].toInt() and 0xFF).toLong()
        return value
    }

    /** Iterates children between [start] (inclusive) and [end] (exclusive) as (id, dataOffset, dataSize). */
    fun children(start: Int, end: Int): Sequence<Triple<Int, Int, Int>> = sequence {
        var position = start
        while (position < end) {
            val (id, idLength) = idAt(position) ?: break
            val (size, sizeLength) = vintAt(position + idLength) ?: break
            val dataOffset = position + idLength + sizeLength
            val dataEnd = (dataOffset.toLong() + size).coerceAtMost(end.toLong()).toInt()
            yield(Triple(id, dataOffset, dataEnd - dataOffset))
            position = (dataOffset.toLong() + size).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }
}

/**
 * Result of scanning a buffer for the pieces the seek index needs: where the Segment's content
 * starts, SeekHead targets indexed by the element id they point at, and the track TimecodeScale.
 */
private class MkvHead(
    val segmentDataOffset: Long,
    val seekTargets: MutableMap<Int, Long> = mutableMapOf(),
    var timecodeScaleNs: Long = DEFAULT_TIMECODE_SCALE_NS,
)

private fun Ebml.parseSeekEntries(dataOffset: Int, dataSize: Int, segmentDataOffset: Long, out: MutableMap<Int, Long>) {
    for ((seekId, seekData, seekSize) in children(dataOffset, dataOffset + dataSize)) {
        if (seekId != ID_SEEK) continue
        var targetId: Int? = null
        var targetPosition: Long? = null
        for ((childId, childData, childSize) in children(seekData, seekData + seekSize)) {
            when (childId) {
                ID_SEEK_ID -> targetId = uintAt(childData, childSize).toInt()
                ID_SEEK_POSITION -> targetPosition = uintAt(childData, childSize)
            }
        }
        if (targetId != null && targetPosition != null) out[targetId] = segmentDataOffset + targetPosition
    }
}

/** Probes a Matroska/WebM stream for its Cues index. Returns null when the file does not offer one. */
internal suspend fun probeMkvCueIndex(fetch: ByteRangeFetch): List<MkvCuePoint>? {
    val headBytes = fetch(0, HEAD_READ_BYTES - 1)
    val ebml = Ebml(headBytes)

    // Locate the Segment element (it follows the EBML doc header) and read its top-level children.
    var position = 0
    var segmentDataOffset = -1L
    while (position < headBytes.size) {
        val (id, idLength) = ebml.idAt(position) ?: break
        val sizePair = ebml.vintAt(position + idLength) ?: break
        val dataOffset = position + idLength + sizePair.second
        if (id == ID_SEGMENT) {
            segmentDataOffset = dataOffset.toLong()
            break
        }
        position = (dataOffset.toLong() + sizePair.first).toInt()
    }
    if (segmentDataOffset < 0) return null

    val head = MkvHead(segmentDataOffset)
    // Walk top-level children available in the head buffer: collect SeekHead targets + TimecodeScale.
    var cursor = segmentDataOffset.toInt()
    while (cursor < headBytes.size) {
        val (id, idLength) = ebml.idAt(cursor) ?: break
        val (size, sizeLength) = ebml.vintAt(cursor + idLength) ?: break
        val dataOffset = cursor + idLength + sizeLength
        when (id) {
            ID_SEEK_HEAD -> ebml.parseSeekEntries(dataOffset, size.toInt().coerceAtMost(headBytes.size - dataOffset), segmentDataOffset, head.seekTargets)
            ID_INFO -> for ((childId, childData, childSize) in ebml.children(dataOffset, dataOffset + size.toInt().coerceAtMost(headBytes.size - dataOffset))) {
                if (childId == ID_TIMECODE_SCALE) head.timecodeScaleNs = ebml.uintAt(childData, childSize)
            }
        }
        cursor = (dataOffset.toLong() + size).toInt()
    }

    // ffmpeg-style files point the head SeekHead at a trailing SeekHead which holds the real entries.
    var cuesOffset = head.seekTargets[ID_CUES]
    val visitedSeekHeads = mutableSetOf<Long>()
    var chase = 0
    while (cuesOffset == null && chase < MAX_SEEK_HEADS) {
        val next = head.seekTargets[ID_SEEK_HEAD]?.takeIf { it !in visitedSeekHeads } ?: break
        visitedSeekHeads += next
        val bytes = fetch(next, next + SEEK_HEAD_READ_BYTES - 1)
        val trailing = Ebml(bytes)
        val (id, idLength) = trailing.idAt(0) ?: break
        val (size, sizeLength) = trailing.vintAt(idLength) ?: break
        if (id != ID_SEEK_HEAD) break
        trailing.parseSeekEntries(idLength + sizeLength, size.toInt().coerceAtMost(bytes.size - idLength - sizeLength), segmentDataOffset, head.seekTargets)
        cuesOffset = head.seekTargets[ID_CUES]
        chase++
    }
    if (cuesOffset == null) return null

    // Read the Cues element header first so the payload fetch is sized exactly.
    val cueHeader = Ebml(fetch(cuesOffset, cuesOffset + ELEMENT_HEADER_BYTES - 1))
    val (cuesId, cuesIdLength) = cueHeader.idAt(0) ?: return null
    if (cuesId != ID_CUES) return null
    val (cuesSize, cuesSizeLength) = cueHeader.vintAt(cuesIdLength) ?: return null
    if (cuesSize > MAX_CUES_BYTES) return null

    val cuesBytes = fetch(cuesOffset, cuesOffset + cuesIdLength + cuesSizeLength + cuesSize - 1)
    val cues = Ebml(cuesBytes)
    val points = mutableListOf<MkvCuePoint>()
    val cuesData = cuesIdLength + cuesSizeLength
    for ((pointId, pointData, pointSize) in cues.children(cuesData, cuesBytes.size)) {
        if (pointId != ID_CUE_POINT) continue
        var cueTime: Long? = null
        var clusterPosition: Long? = null
        for ((childId, childData, childSize) in cues.children(pointData, pointData + pointSize)) {
            when (childId) {
                ID_CUE_TIME -> cueTime = cues.uintAt(childData, childSize)
                ID_CUE_TRACK_POSITIONS -> for ((posId, posData, posSize) in cues.children(childData, childData + childSize)) {
                    if (posId == ID_CUE_CLUSTER_POSITION) clusterPosition = cues.uintAt(posData, posSize)
                }
            }
        }
        if (cueTime != null && clusterPosition != null) {
            points += MkvCuePoint(
                timeMs = cueTime * head.timecodeScaleNs / 1_000_000,
                position = segmentDataOffset + clusterPosition,
            )
        }
    }
    return points.sortedBy { it.timeMs }.takeIf { it.isNotEmpty() }
}
