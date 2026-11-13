package com.pyoungws.bluewolfplayer.shared.playback

import com.pyoungws.bluewolfplayer.shared.model.MediaItem

enum class PlaybackMethod { DirectPlay, DirectStream, Transcode }

/** A text subtitle the server delivers as a separate file. Embedded tracks are handled by the player. */
data class ExternalSubtitle(
    val url: String,
    val mimeType: String,
    val language: String?,
    val label: String?,
    val isDefault: Boolean,
    val isForced: Boolean,
)

/** The video stream of a source, as probed by the server. */
data class VideoStreamInfo(
    val codec: String? = null,
    val profile: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val bitDepth: Int? = null,
    val bitrate: Int? = null,
    val range: VideoRange? = null,
    val dolbyVisionProfile: Int? = null,
)

/** The default audio stream of a source, as probed by the server. */
data class AudioStreamInfo(
    val codec: String? = null,
    val profile: String? = null,
    val channels: Int? = null,
    val sampleRate: Int? = null,
    val bitrate: Int? = null,
)

/** What the server knows about the original file. Used for fallback decisions and the stats overlay. */
data class SourceMediaInfo(
    val container: String? = null,
    val bitrate: Int? = null,
    val video: VideoStreamInfo? = null,
    val audio: AudioStreamInfo? = null,
)

/** What to play: a library item, or (debug builds) a file or URL played without the server. */
sealed interface PlaybackTarget {
    /** [startPositionMs] overrides the resume point (e.g. joining a SyncPlay group mid-playback). */
    data class Item(val itemId: String, val startPositionMs: Long? = null, val startPaused: Boolean = false, val mediaSourceId: String? = null) : PlaybackTarget
    data class DirectUri(val uri: String, val title: String = uri.substringAfterLast('/')) : PlaybackTarget
}

/** Where and how to stream one media source, as decided by the server's playback info. */
data class PlaybackSource(
    val mediaSourceId: String,
    val playSessionId: String?,
    val url: String,
    val method: PlaybackMethod,
    val subtitles: List<ExternalSubtitle>,
    /** Request headers (authorization) for [url] and [subtitles]. */
    val headers: Map<String, String>,
    val info: SourceMediaInfo? = null,
    /**
     * Matroska cue index probed for progressive streams whose own SeekHead never names the Cues
     * element — the player injects it when the extractor reports the stream unseekable.
     */
    val cuePoints: List<MkvCuePoint>? = null,
)

/** Everything the player needs to start: the item, its source and where to resume. */
data class PlaybackRequest(
    val item: MediaItem,
    val source: PlaybackSource,
    val startPositionMs: Long,
    /** True when the player must start paused (a paused SyncPlay group was joined mid-playback). */
    val startPaused: Boolean = false,
    /** Which decoders to use for the streams; moves to FFmpeg after a decoder failure. */
    val plan: DecodePlan = DecodePlan(),
    val settings: PlaybackSettings = PlaybackSettings(),
    val capabilities: DeviceCapabilities = DeviceCapabilities(),
    /** False for streams the server does not know about, which are not reported or transcoded. */
    val isServerItem: Boolean = true,
    /** False when the user's server policy forbids video transcoding: a "transcode" then keeps the original video. */
    val serverTranscodesVideo: Boolean = true,
) {
    val level: PlaybackLevel
        get() = when {
            source.method == PlaybackMethod.Transcode -> PlaybackLevel.Transcode
            plan.isLocal -> PlaybackLevel.LocalDecode
            else -> PlaybackLevel.DirectPlay
        }
}

/** The three fallback levels, best first. */
enum class PlaybackLevel { DirectPlay, LocalDecode, Transcode }

/** A marked region of a stream the player can offer to skip; positions are milliseconds. */
enum class MediaSegmentKind { Intro, Outro }

data class MediaSegment(val kind: MediaSegmentKind, val startMs: Long, val endMs: Long) {
    fun contains(positionMs: Long) = positionMs in startMs until endMs
}

/** A snapshot of the player sent to Jellyfin as a start, progress or stopped report. */
data class PlaybackReport(
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String?,
    val positionTicks: Long,
    val isPaused: Boolean,
    val method: PlaybackMethod,
)

const val TICKS_PER_MILLISECOND = 10_000L
