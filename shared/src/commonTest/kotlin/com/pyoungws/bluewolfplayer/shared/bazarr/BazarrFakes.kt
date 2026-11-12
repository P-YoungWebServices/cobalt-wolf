package com.pyoungws.bluewolfplayer.shared.bazarr

import com.pyoungws.bluewolfplayer.shared.arrs.ArrServiceSettings

class FakeBazarrDataSource : BazarrDataSource {
    var movies: List<WantedMovie> = emptyList()
    var episodes: List<WantedEpisode> = emptyList()
    var error: Throwable? = null
    val calls = mutableListOf<String>()

    override suspend fun wantedMovies(settings: ArrServiceSettings): List<WantedMovie> {
        calls += "wantedMovies"
        error?.let { throw it }
        return movies
    }

    override suspend fun wantedEpisodes(settings: ArrServiceSettings): List<WantedEpisode> {
        calls += "wantedEpisodes"
        error?.let { throw it }
        return episodes
    }

    var movieCandidates: List<SubtitleCandidate> = emptyList()
    var episodeCandidates: List<SubtitleCandidate> = emptyList()
    val downloads = mutableListOf<String>()

    override suspend fun searchMovieSubtitles(settings: ArrServiceSettings, radarrId: Int): List<SubtitleCandidate> {
        calls += "searchMovies($radarrId)"
        error?.let { throw it }
        return movieCandidates
    }

    override suspend fun searchEpisodeSubtitles(settings: ArrServiceSettings, sonarrEpisodeId: Int): List<SubtitleCandidate> {
        calls += "searchEpisodes($sonarrEpisodeId)"
        error?.let { throw it }
        return episodeCandidates
    }

    override suspend fun downloadMovieSubtitle(settings: ArrServiceSettings, radarrId: Int, candidate: SubtitleCandidate) {
        calls += "downloadMovie($radarrId,${candidate.provider})"
        downloads += "m:$radarrId:${candidate.subtitle}"
        error?.let { throw it }
    }

    override suspend fun downloadEpisodeSubtitle(
        settings: ArrServiceSettings,
        sonarrSeriesId: Int,
        sonarrEpisodeId: Int,
        candidate: SubtitleCandidate,
    ) {
        calls += "downloadEpisode($sonarrEpisodeId,${candidate.provider})"
        downloads += "e:$sonarrEpisodeId:${candidate.subtitle}"
        error?.let { throw it }
    }
}
