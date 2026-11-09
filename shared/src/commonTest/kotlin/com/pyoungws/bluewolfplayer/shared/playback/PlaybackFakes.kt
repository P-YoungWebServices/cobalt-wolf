package com.pyoungws.bluewolfplayer.shared.playback

import com.pyoungws.bluewolfplayer.shared.model.Session

val DIRECT_SOURCE = PlaybackSource(
    mediaSourceId = "ms-1",
    playSessionId = "ps-1",
    url = "https://jellyfin.example.com/Videos/m1/stream?static=true",
    method = PlaybackMethod.DirectPlay,
    subtitles = emptyList(),
    headers = emptyMap(),
)

class FakePlaybackDataSource : JellyfinPlaybackDataSource {
    val sourceRequests = mutableListOf<Triple<String, Long, Boolean>>()
    val reports = mutableListOf<String>()
    val reported = mutableListOf<PlaybackReport>()

    var source: (forceTranscode: Boolean) -> PlaybackSource = { force ->
        if (force) DIRECT_SOURCE.copy(method = PlaybackMethod.Transcode, url = "https://jellyfin.example.com/hls") else DIRECT_SOURCE
    }

    override suspend fun getPlaybackSource(session: Session, itemId: String, startPositionTicks: Long, forceTranscode: Boolean): PlaybackSource {
        sourceRequests += Triple(itemId, startPositionTicks, forceTranscode)
        return source(forceTranscode)
    }

    override suspend fun reportStart(session: Session, report: PlaybackReport) {
        reports += "start"
        reported += report
    }

    override suspend fun reportProgress(session: Session, report: PlaybackReport) {
        reports += "progress"
        reported += report
    }

    override suspend fun reportStopped(session: Session, report: PlaybackReport) {
        reports += "stopped"
        reported += report
    }
}
