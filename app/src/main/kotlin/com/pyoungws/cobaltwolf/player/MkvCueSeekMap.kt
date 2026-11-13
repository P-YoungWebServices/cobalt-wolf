package com.pyoungws.cobaltwolf.player

import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import com.pyoungws.cobaltwolf.shared.playback.MkvCuePoint

/**
 * SeekMap built from the file's own Cues index, parsed beforehand over HTTP ranges. Media3 only
 * locates Matroska cues when the first SeekHead names them; ffmpeg-style files put that entry in a
 * trailing SeekHead and end up reported as unseekable — this restores real, keyframe-aligned seeks.
 */
internal class MkvCueSeekMap(
    points: List<MkvCuePoint>,
    private val durationUs: Long,
) : SeekMap {

    private val timesUs: LongArray = LongArray(points.size) { points[it].timeMs * 1_000 }
    private val positions: LongArray = LongArray(points.size) { points[it].position }

    override fun isSeekable() = true

    override fun getDurationUs() = durationUs

    override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
        val found = timesUs.binarySearch(timeUs)
        val afterIdx = if (found >= 0) found else -(found + 1)
        val lower = (if (found >= 0) found else afterIdx - 1).coerceAtLeast(0)
        val upper = afterIdx.coerceAtMost(timesUs.lastIndex)
        return SeekMap.SeekPoints(
            SeekPoint(timesUs[lower], positions[lower]),
            SeekPoint(timesUs[upper], positions[upper]),
        )
    }
}

/**
 * Wraps an extractor so that a reported [SeekMap.Unseekable] is replaced by [fallback] (built with
 * the duration the extractor reports). Seekable maps pass through untouched.
 */
private class SeekMapPatchingExtractor(
    private val delegate: Extractor,
    private val fallback: (durationUs: Long) -> SeekMap,
) : Extractor {

    override fun sniff(input: ExtractorInput) = delegate.sniff(input)

    override fun init(output: ExtractorOutput) {
        delegate.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput = output.track(id, type)
            override fun endTracks() = output.endTracks()
            override fun seekMap(seekMap: SeekMap) =
                output.seekMap(if (seekMap.isSeekable) seekMap else fallback(seekMap.durationUs))
        })
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = delegate.read(input, seekPosition)

    override fun seek(position: Long, timeUs: Long) = delegate.seek(position, timeUs)

    override fun release() = delegate.release()
}

/**
 * Replaces every Matroska extractor (including libass's subclass) with a wrapper that swaps an
 * unseekable SeekMap for one built from [cuePoints]. Wrap AFTER `withAssMkvSupport`.
 */
internal fun ExtractorsFactory.withMkvCueIndex(cuePoints: List<MkvCuePoint>): ExtractorsFactory = ExtractorsFactory {
    createExtractors()
        .map { extractor ->
            if (extractor is MatroskaExtractor) {
                SeekMapPatchingExtractor(extractor) { durationUs -> MkvCueSeekMap(cuePoints, durationUs) }
            } else extractor
        }
        .toTypedArray()
}
