package com.pyoungws.bluewolfplayer.shared.playback

import com.pyoungws.bluewolfplayer.shared.library.JellyfinLibraryDataSource
import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.model.Session

class PlaybackRepository(
    private val libraryDataSource: JellyfinLibraryDataSource,
    private val playbackDataSource: JellyfinPlaybackDataSource,
    private val guard: SessionGuard,
    private val capabilityDetector: CapabilityDetector,
    private val settingsRepository: PlaybackSettingsRepository,
) {
    /** The live playback settings — the player offers a bitrate override while playing. */
    val settings = settingsRepository.settings

    /** Persists a new bitrate cap; it applies the next time [prepare] runs. */
    fun setMaxBitrate(bitrate: StreamBitrate) = settingsRepository.update { it.copy(maxBitrate = bitrate) }
    /**
     * Resolves what to play and where to resume. The device is probed each time, because the
     * connected display and receiver decide what can be played directly. [forceTranscode] skips
     * direct play, used as the last fallback after the player failed to decode the original streams.
     */
    suspend fun prepare(target: PlaybackTarget, forceTranscode: Boolean = false): Outcome<PlaybackRequest> {
        val settings = settingsRepository.settings.value
        val capabilities = capabilityDetector.detect()
        return when (target) {
            is PlaybackTarget.DirectUri -> Outcome.Success(
                PlaybackRequest(
                    item = MediaItem(id = "", name = target.title, kind = MediaKind.Other),
                    source = PlaybackSource("", null, target.uri, PlaybackMethod.DirectPlay, emptyList(), emptyMap()),
                    startPositionMs = 0,
                    plan = initialDecodePlan(settings),
                    settings = settings,
                    capabilities = capabilities,
                    isServerItem = false,
                ),
            )
            is PlaybackTarget.Item -> guard.run { session ->
                val item = libraryDataSource.getItem(session, target.itemId)
                val startTicks = target.startPositionMs?.let { it * TICKS_PER_MILLISECOND }
                    ?: if (item.hasProgress) item.positionTicks else 0L
                val serverTranscodesVideo = playbackDataSource.canTranscodeVideo(session)
                val source = playbackDataSource.getPlaybackSource(
                    session, target.itemId, startTicks, buildDeviceProfile(capabilities, settings, serverTranscodesVideo),
                    forceTranscode = forceTranscode || settings.decoding == DecodingPreference.AlwaysTranscode,
                    maxBitrate = settings.maxBitrate.bps,
                    mediaSourceId = target.mediaSourceId,
                )
                PlaybackRequest(
                    item, source, startTicks / TICKS_PER_MILLISECOND, target.startPaused, initialDecodePlan(settings),
                    settings, capabilities,
                    serverTranscodesVideo = serverTranscodesVideo,
                    audioStreamIndex = target.audioStreamIndex,
                    subtitleStreamIndex = target.subtitleStreamIndex,
                )
            }
        }
    }

    /**
     * The episode after [item]: the next one in its season, else the next season's first.
     * Null for non-episodes, series finales and lookup failures — advancing is optional.
     */
    suspend fun nextEpisode(item: MediaItem): MediaItem? {
        if (item.kind != MediaKind.Episode || item.seriesId == null || item.seasonId == null) return null
        val outcome = guard.run { session ->
            val episodes = libraryDataSource.getEpisodes(session, item.seriesId, item.seasonId)
            val index = episodes.indexOfFirst { it.id == item.id }
            val next = if (index >= 0) episodes.getOrNull(index + 1) else null
            next ?: nextSeasonFirstEpisode(session, item)
        }
        return (outcome as? Outcome.Success)?.value
    }

    private suspend fun nextSeasonFirstEpisode(session: Session, item: MediaItem): MediaItem? {
        val seriesId = item.seriesId ?: return null
        val seasons = libraryDataSource.getSeasons(session, seriesId)
        val nextSeason = seasons.getOrNull(seasons.indexOfFirst { it.id == item.seasonId } + 1) ?: return null
        return libraryDataSource.getEpisodes(session, seriesId, nextSeason.id).firstOrNull()
    }

    /** Cinema-mode preroll items for a movie; empty on failure or when the server has none. */
    suspend fun intros(itemId: String): List<MediaItem> =
        (guard.run { libraryDataSource.getIntros(it, itemId) } as? Outcome.Success)?.value.orEmpty()

    /** Intro/outro markers for the item; empty when the server has none or they fail to load. */
    suspend fun mediaSegments(itemId: String): List<MediaSegment> =
        (guard.run { playbackDataSource.getMediaSegments(it, itemId) } as? Outcome.Success)?.value.orEmpty()

    suspend fun reportStart(report: PlaybackReport) = send { session -> playbackDataSource.reportStart(session, report) }

    suspend fun reportProgress(report: PlaybackReport) = send { session -> playbackDataSource.reportProgress(session, report) }

    suspend fun reportStopped(report: PlaybackReport) = send { session -> playbackDataSource.reportStopped(session, report) }

    private suspend fun send(block: suspend (Session) -> Unit): Outcome<Unit> = guard.run { block(it) }
}
