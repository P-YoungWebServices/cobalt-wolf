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

/** Where and how to stream one media source, as decided by the server's playback info. */
data class PlaybackSource(
    val mediaSourceId: String,
    val playSessionId: String?,
    val url: String,
    val method: PlaybackMethod,
    val subtitles: List<ExternalSubtitle>,
    /** Request headers (authorization) for [url] and [subtitles]. */
    val headers: Map<String, String>,
)

/** Everything the player needs to start: the item, its source and where to resume. */
data class PlaybackRequest(
    val item: MediaItem,
    val source: PlaybackSource,
    val startPositionMs: Long,
)

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
