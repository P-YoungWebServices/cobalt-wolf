package com.pyoungws.cobaltwolf.shared.bazarr

import com.pyoungws.cobaltwolf.shared.arrs.ArrServiceSettings

interface BazarrDataSource {
    /** Movies with at least one wanted subtitle; throws when Bazarr is unreachable or the key is rejected. */
    suspend fun wantedMovies(settings: ArrServiceSettings): List<WantedMovie>

    /** Episodes with at least one wanted subtitle; throws when Bazarr is unreachable or the key is rejected. */
    suspend fun wantedEpisodes(settings: ArrServiceSettings): List<WantedEpisode>

    /** Scored provider results for a movie (`GET /api/providers/movies`); a slow, live provider search. */
    suspend fun searchMovieSubtitles(settings: ArrServiceSettings, radarrId: Int): List<SubtitleCandidate>

    /** Scored provider results for an episode (`GET /api/providers/episodes`). */
    suspend fun searchEpisodeSubtitles(settings: ArrServiceSettings, sonarrEpisodeId: Int): List<SubtitleCandidate>

    /** Downloads one search result for a movie (`POST /api/providers/movies`). */
    suspend fun downloadMovieSubtitle(settings: ArrServiceSettings, radarrId: Int, candidate: SubtitleCandidate)

    /** Downloads one search result for an episode (`POST /api/providers/episodes`). */
    suspend fun downloadEpisodeSubtitle(settings: ArrServiceSettings, sonarrSeriesId: Int, sonarrEpisodeId: Int, candidate: SubtitleCandidate)
}
