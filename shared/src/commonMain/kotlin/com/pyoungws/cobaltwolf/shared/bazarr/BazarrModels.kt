package com.pyoungws.cobaltwolf.shared.bazarr

/** One subtitle language Bazarr is still looking for. */
data class MissingSubtitle(
    val name: String,
    val forced: Boolean = false,
    val hearingImpaired: Boolean = false,
) {
    val label: String
        get() = when {
            forced && hearingImpaired -> "$name (forced, HI)"
            forced -> "$name (forced)"
            hearingImpaired -> "$name (HI)"
            else -> name
        }
}

/** A movie with at least one wanted subtitle still missing. */
data class WantedMovie(
    val title: String,
    val missing: List<MissingSubtitle>,
    /** The Radarr id Bazarr keys movies by; needed for manual provider searches. */
    val radarrId: Int? = null,
)

/** An episode with at least one wanted subtitle still missing. */
data class WantedEpisode(
    val seriesTitle: String,
    /** Episode code formatted as S01E05, or null when Bazarr did not report one. */
    val episodeCode: String?,
    val episodeTitle: String?,
    val missing: List<MissingSubtitle>,
    /** The Sonarr ids Bazarr keys episodes by; needed for manual provider searches/downloads. */
    val sonarrSeriesId: Int? = null,
    val sonarrEpisodeId: Int? = null,
)

/** One provider result from a manual subtitle search. */
data class SubtitleCandidate(
    val provider: String,
    /** Opaque handle Bazarr expects back when downloading this result. */
    val subtitle: String,
    val score: Int = 0,
    /** Language display name (e.g. "English") as Bazarr reports it. */
    val languageName: String? = null,
    val hearingImpaired: Boolean = false,
    val forced: Boolean = false,
    val originalFormat: Boolean = false,
)

/** Everything Bazarr still wants subtitles for. */
data class BazarrWanted(
    val movies: List<WantedMovie>,
    val episodes: List<WantedEpisode>,
) {
    val total: Int get() = movies.size + episodes.size
}
