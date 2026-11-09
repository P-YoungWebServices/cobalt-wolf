package com.pyoungws.bluewolfplayer.shared.playback

import com.pyoungws.bluewolfplayer.shared.library.JellyfinLibraryDataSource
import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.model.Session

class PlaybackRepository(
    private val libraryDataSource: JellyfinLibraryDataSource,
    private val playbackDataSource: JellyfinPlaybackDataSource,
    private val guard: SessionGuard,
) {
    /**
     * Resolves what to play and where to resume. [forceTranscode] skips direct play, used as a
     * fallback after the player failed to decode the original streams.
     */
    suspend fun prepare(itemId: String, forceTranscode: Boolean = false): Outcome<PlaybackRequest> = guard.run { session ->
        val item = libraryDataSource.getItem(session, itemId)
        val startTicks = if (item.hasProgress) item.positionTicks else 0L
        val source = playbackDataSource.getPlaybackSource(session, itemId, startTicks, forceTranscode)
        PlaybackRequest(item, source, startTicks / TICKS_PER_MILLISECOND)
    }

    suspend fun reportStart(report: PlaybackReport) = send { session -> playbackDataSource.reportStart(session, report) }

    suspend fun reportProgress(report: PlaybackReport) = send { session -> playbackDataSource.reportProgress(session, report) }

    suspend fun reportStopped(report: PlaybackReport) = send { session -> playbackDataSource.reportStopped(session, report) }

    private suspend fun send(block: suspend (Session) -> Unit): Outcome<Unit> = guard.run { block(it) }
}
