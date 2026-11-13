package com.pyoungws.cobaltwolf.player

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

private const val SCAN_WIDTH = 160
private const val BLACK_LUMINANCE = 20
private const val MIN_BAR_FRACTION = 0.04f
private const val MAX_CROP_SCALE = 2.0f

/**
 * Samples a few frames of [url] and works out the uniform zoom that removes black bars from the
 * image (auto-crop): the scale the video needs so the active picture covers the whole frame.
 * Returns 1 when nothing should be cropped — letterboxing under [MIN_BAR_FRACTION], a stream the
 * retriever cannot read (HLS transcodes, encrypted content), or genuinely full-frame video.
 */
internal suspend fun detectCropScale(url: String, headers: Map<String, String>, durationTicks: Long?, startPositionMs: Long): Float =
    withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            if (headers.isEmpty()) retriever.setDataSource(url) else retriever.setDataSource(url, headers)
            val durationMs = durationTicks?.div(10_000) ?: 0L
            // A frame well into the video; starting mid-credits or a black intro would lie.
            val samplePoints = if (durationMs > 120_000) {
                listOf(startPositionMs + durationMs / 4, startPositionMs + durationMs / 2)
            } else {
                listOf(startPositionMs + durationMs / 3)
            }
            samplePoints.firstNotNullOfOrNull { timeMs ->
                retriever.getFrameAtTime(timeMs * 1_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let(::cropScaleOf)
            } ?: 1f
        } catch (_: Exception) {
            1f
        } finally {
            retriever.release()
        }
    }

/** Uniform zoom covering the frame with the non-black region, or null when there are no bars. */
internal fun cropScaleOf(frame: Bitmap): Float? {
    val scaled = Bitmap.createScaledBitmap(
        frame,
        SCAN_WIDTH,
        (SCAN_WIDTH * frame.height / frame.width).coerceAtLeast(1),
        false,
    )
    val w = scaled.width
    val h = scaled.height
    val rowSums = IntArray(h)
    val colSums = IntArray(w)
    val px = IntArray(w)
    for (y in 0 until h) {
        scaled.getPixels(px, 0, w, 0, y, w, 1)
        for (x in 0 until w) {
            val p = px[x]
            val lum = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            if (lum > BLACK_LUMINANCE) {
                rowSums[y]++
                colSums[x]++
            }
        }
    }
    // A row/column is picture when a decent share of its pixels is above black, so a bright
    // spec or the subtitle line does not mark a bar as content.
    val rowLimit = (w * 0.02f).toInt()
    val colLimit = (h * 0.02f).toInt()
    val top = rowSums.indexOfFirst { it > rowLimit }.takeIf { it >= 0 } ?: return null
    val bottom = h - 1 - rowSums.reversedArray().indexOfFirst { it > rowLimit }
    val left = colSums.indexOfFirst { it > colLimit }.takeIf { it >= 0 } ?: return null
    val right = w - 1 - colSums.reversedArray().indexOfFirst { it > colLimit }

    val barV = top + (h - 1 - bottom)
    val barH = left + (w - 1 - right)
    if (barV < h * MIN_BAR_FRACTION && barH < w * MIN_BAR_FRACTION) return null

    val activeW = (right - left + 1).coerceAtLeast(1)
    val activeH = (bottom - top + 1).coerceAtLeast(1)
    return max(w.toFloat() / activeW, h.toFloat() / activeH).coerceAtMost(MAX_CROP_SCALE)
}
