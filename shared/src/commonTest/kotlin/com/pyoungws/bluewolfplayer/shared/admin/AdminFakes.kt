package com.pyoungws.bluewolfplayer.shared.admin

import com.pyoungws.bluewolfplayer.shared.library.movie
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.Session

fun playingSession(
    id: String,
    user: String = "alice",
    device: String = "Shield TV",
    method: SessionPlayMethod? = SessionPlayMethod.DirectPlay,
    item: MediaItem? = movie("item-$id"),
) = ActiveSession(
    id = id,
    userName = user,
    deviceName = device,
    client = "Blue Wolf Player",
    item = item,
    progressFraction = 0.4f,
    isPaused = false,
    playMethod = method,
    videoCodec = "hevc",
    audioCodec = "truehd",
    transcoding = if (method == SessionPlayMethod.Transcode) {
        TranscodeDetails("hls", "h264", "aac", 8_000_000, 1920, 1080, 2, false, false, listOf("video codec not supported"))
    } else {
        null
    },
)

class FakeAdminDataSource : JellyfinAdminDataSource {
    val calls = mutableListOf<String>()
    var dashboardCalls = 0
        private set

    var sessions: () -> List<ActiveSession> = { emptyList() }
    var recentlyAdded: () -> List<MediaItem> = { emptyList() }
    var onSendMessage: (sessionId: String, text: String) -> Unit = { _, _ -> }

    override suspend fun getSessions(session: Session): List<ActiveSession> {
        calls += "sessions"
        dashboardCalls++
        return sessions()
    }

    override suspend fun getRecentlyAdded(session: Session, limit: Int): List<MediaItem> {
        calls += "latest($limit)"
        return recentlyAdded()
    }

    override suspend fun sendMessage(session: Session, sessionId: String, text: String) {
        calls += "message($sessionId, $text)"
        onSendMessage(sessionId, text)
    }
}
