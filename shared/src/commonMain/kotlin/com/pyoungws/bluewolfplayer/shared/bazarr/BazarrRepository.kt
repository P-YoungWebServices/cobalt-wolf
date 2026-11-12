package com.pyoungws.bluewolfplayer.shared.bazarr

import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.arrs.ArrServiceSettings
import com.pyoungws.bluewolfplayer.shared.arrs.ArrSettings
import com.pyoungws.bluewolfplayer.shared.arrs.ArrSettingsRepository
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.net.toLoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Bazarr wanted-subtitles reporting. Shares the *arr settings store: URL + API key are saved
 * through the same Services screen as Radarr and Sonarr.
 */
class BazarrRepository(
    private val dataSource: BazarrDataSource,
    private val settingsRepository: ArrSettingsRepository,
) {
    val settings: StateFlow<ArrSettings> = settingsRepository.settings

    val isConfigured: Boolean get() = settingsRepository.serviceSettings(ArrService.Bazarr) != null

    /** Every movie and episode Bazarr is still missing a subtitle for. */
    suspend fun wanted(): Outcome<BazarrWanted> = call { settings ->
        coroutineScope {
            val movies = async { dataSource.wantedMovies(settings) }
            val episodes = async { dataSource.wantedEpisodes(settings) }
            BazarrWanted(movies.await(), episodes.await())
        }
    }

    /**
     * One-tap search for [movie]: queries the providers live, picks the best-scored result per
     * missing language and downloads it. Returns the number of subtitles grabbed.
     */
    suspend fun searchAndDownload(movie: WantedMovie): Outcome<Int> {
        val id = movie.radarrId
            ?: return Outcome.Failure(LoadError.Unexpected("Bazarr did not report a Radarr id for this movie"))
        return call { settings ->
            val candidates = dataSource.searchMovieSubtitles(settings, id)
            bestPerLanguage(candidates, movie.missing).also { best ->
                best.forEach { dataSource.downloadMovieSubtitle(settings, id, it) }
            }.size
        }
    }

    /** Same as [searchAndDownload] for an episode. */
    suspend fun searchAndDownload(episode: WantedEpisode): Outcome<Int> {
        val seriesId = episode.sonarrSeriesId
        val episodeId = episode.sonarrEpisodeId
        if (seriesId == null || episodeId == null) {
            return Outcome.Failure(LoadError.Unexpected("Bazarr did not report Sonarr ids for this episode"))
        }
        return call { settings ->
            val candidates = dataSource.searchEpisodeSubtitles(settings, episodeId)
            bestPerLanguage(candidates, episode.missing).also { best ->
                best.forEach { dataSource.downloadEpisodeSubtitle(settings, seriesId, episodeId, it) }
            }.size
        }
    }

    /**
     * The highest-scored candidate per wanted language. Candidates matching the missing
     * forced/HI flags win over same-language results with different flags.
     */
    internal fun bestPerLanguage(candidates: List<SubtitleCandidate>, missing: List<MissingSubtitle>): List<SubtitleCandidate> =
        missing.mapNotNull { wanted ->
            val sameLanguage = candidates.filter { it.languageName.equals(wanted.name, ignoreCase = true) }
            val exact = sameLanguage.filter { it.forced == wanted.forced && it.hearingImpaired == wanted.hearingImpaired }
            (exact.ifEmpty { sameLanguage }).maxByOrNull { it.score }
        }

    private suspend fun <T> call(block: suspend (ArrServiceSettings) -> T): Outcome<T> {
        val settings = settingsRepository.serviceSettings(ArrService.Bazarr)
            ?: return Outcome.Failure(LoadError.Unexpected("Bazarr is not configured"))
        return try {
            Outcome.Success(block(settings))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Failure(e.toLoadError())
        }
    }
}
