package com.pyoungws.cobaltwolf.shared.arrs

/** A quality profile configured on the service, offered in the edit form. */
data class ArrQualityProfile(val id: Int, val name: String)

/** A root folder configured on the service. */
data class ArrRootFolder(val id: Int, val path: String, val freeSpace: Long? = null)

/** A tag defined on the service; media reference tags by id. */
data class ArrTag(val id: Int, val label: String)

/** A movie tracked by Radarr. */
data class ArrMovie(
    val id: Int,
    val title: String,
    val year: Int?,
    val monitored: Boolean,
    val qualityProfileId: Int,
    /** The configured root folder the movie lives under, when known. */
    val rootFolderPath: String?,
    /** The full current path of the movie folder. */
    val path: String,
    val tagIds: List<Int>,
    val hasFile: Boolean,
    val sizeOnDisk: Long?,
    val posterUrl: String?,
)

/** One season of a tracked series, as stored inside the series resource. */
data class ArrSeason(
    val seasonNumber: Int,
    val monitored: Boolean,
    val episodeFileCount: Int = 0,
    val episodeCount: Int = 0,
    val totalEpisodeCount: Int = 0,
)

/** A series tracked by Sonarr. */
data class ArrSeries(
    val id: Int,
    val title: String,
    val year: Int?,
    val monitored: Boolean,
    val qualityProfileId: Int,
    val rootFolderPath: String?,
    val path: String,
    val tagIds: List<Int>,
    val seasons: List<ArrSeason>,
    val posterUrl: String?,
)

/** One episode of a series, with its monitoring and file state. */
data class ArrEpisode(
    val id: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val monitored: Boolean,
    val hasFile: Boolean,
    /** The episode file id, or null when there is no file. */
    val episodeFileId: Int?,
) {
    val label: String get() = "S%02dE%02d".format(seasonNumber, episodeNumber)
}

/** A media file tracked by the service (a movie file or an episode file). */
data class ArrMediaFile(
    val id: Int,
    /** Path relative to the movie/series folder. */
    val relativePath: String,
    val size: Long,
    val qualityName: String?,
    val dateAdded: String?,
)

/** A rename proposed by `GET /api/v3/rename` for one tracked file. */
data class ArrRenamePreview(
    val fileId: Int,
    val existingPath: String,
    val newPath: String,
)

/** One indexer result of an interactive release search. */
data class ArrRelease(
    val guid: String,
    val indexerId: Int,
    val title: String,
    val qualityName: String?,
    val size: Long,
    val indexer: String?,
    val seeders: Int?,
    val leechers: Int?,
    val protocol: String?,
    val ageHours: Double?,
    val rejected: Boolean,
    val rejections: List<String>,
)

/** What an interactive release search looks up. */
sealed interface ReleaseQuery {
    data class Movie(val movieId: Int) : ReleaseQuery
    data class Season(val seriesId: Int, val seasonNumber: Int) : ReleaseQuery
    data class Episode(val episodeId: Int) : ReleaseQuery
}

/** What an automatic search command targets. */
sealed interface SearchTarget {
    data class Movie(val movieId: Int) : SearchTarget
    data class Series(val seriesId: Int) : SearchTarget
    data class Season(val seriesId: Int, val seasonNumber: Int) : SearchTarget
    data class Episodes(val episodeIds: List<Int>) : SearchTarget
}

/** One file listed by `GET /api/v3/manualimport`, matched (or not) to the open item. */
data class ManualImportItem(
    val path: String,
    val name: String,
    val size: Long,
    /** The movie or series id the row was matched to, when the service matched one. */
    val matchedItemId: Int?,
    /** Episode ids the row resolves to (Sonarr); empty when unmatched. */
    val episodeIds: List<Int>,
    val qualityId: Int?,
    val qualityName: String?,
    val releaseGroup: String?,
    /** Reasons the service refuses to import the row; empty means importable. */
    val rejections: List<String>,
)

/** Field edits for a movie. Only these fields are touched; everything else is left as stored. */
data class ArrMovieEdit(
    val id: Int,
    val monitored: Boolean,
    val qualityProfileId: Int,
    /** New root folder; the movie folder is moved there (files move on the service). */
    val rootFolderPath: String?,
    val tagIds: List<Int>,
)

/** Field edits for a series, including per-season monitoring. */
data class ArrSeriesEdit(
    val id: Int,
    val monitored: Boolean,
    val qualityProfileId: Int,
    val rootFolderPath: String?,
    val tagIds: List<Int>,
    val seasons: List<ArrSeason>,
)
