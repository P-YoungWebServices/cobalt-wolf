package com.pyoungws.cobaltwolf.shared.arrs

/**
 * The Radarr/Sonarr management API: browsing the tracked library, editing media, files,
 * rename, searching and manual import. Everything is a suspending call that throws on
 * failure; [ArrMediaRepository] maps errors to user-facing outcomes.
 */
interface ArrMediaDataSource {

    /** All movies tracked by Radarr (`GET /api/v3/movie`). */
    suspend fun movies(settings: ArrServiceSettings): List<ArrMovie>

    /** One Radarr movie (`GET /api/v3/movie/{id}`). */
    suspend fun movie(settings: ArrServiceSettings, movieId: Int): ArrMovie

    /** All series tracked by Sonarr (`GET /api/v3/series`). */
    suspend fun seriesList(settings: ArrServiceSettings): List<ArrSeries>

    /** One Sonarr series (`GET /api/v3/series/{id}`). */
    suspend fun series(settings: ArrServiceSettings, seriesId: Int): ArrSeries

    /** All episodes of a series (`GET /api/v3/episode?seriesId=`). */
    suspend fun episodes(settings: ArrServiceSettings, seriesId: Int): List<ArrEpisode>

    suspend fun qualityProfiles(settings: ArrServiceSettings): List<ArrQualityProfile>
    suspend fun rootFolders(settings: ArrServiceSettings): List<ArrRootFolder>
    suspend fun tags(settings: ArrServiceSettings): List<ArrTag>

    /** Creates a tag and returns it (it already exists when the service rejects duplicates; callers then pick by label). */
    suspend fun createTag(settings: ArrServiceSettings, label: String): ArrTag

    /**
     * Applies [edit] to the stored movie: the current resource is fetched, the edited
     * fields are replaced and the resource is PUT back, so fields this model does not
     * know are preserved. A changed root folder moves the files (`moveFiles=true`).
     */
    suspend fun updateMovie(settings: ArrServiceSettings, edit: ArrMovieEdit): ArrMovie

    /** Like [updateMovie] for a series; [ArrSeriesEdit.seasons] carries per-season monitoring. */
    suspend fun updateSeries(settings: ArrServiceSettings, edit: ArrSeriesEdit): ArrSeries

    /** Bulk per-episode monitoring (`PUT /api/v3/episode/monitor`, Sonarr only). */
    suspend fun setEpisodesMonitored(settings: ArrServiceSettings, episodeIds: List<Int>, monitored: Boolean)

    /** Files of a movie (`GET /api/v3/moviefile?movieId=`). */
    suspend fun movieFiles(settings: ArrServiceSettings, movieId: Int): List<ArrMediaFile>

    /** Files of a series (`GET /api/v3/episodefile?seriesId=`). */
    suspend fun episodeFiles(settings: ArrServiceSettings, seriesId: Int): List<ArrMediaFile>

    /** Deletes one tracked file (`DELETE /api/v3/moviefile/{id}` or `episodefile/{id}`). */
    suspend fun deleteFile(settings: ArrServiceSettings, service: ArrService, fileId: Int)

    /**
     * Removes the movie or series itself (`DELETE /api/v3/movie/{id}` / `series/{id}`).
     * [deleteFiles] controls whether the files are also deleted from disk.
     */
    suspend fun deleteItem(settings: ArrServiceSettings, service: ArrService, itemId: Int, deleteFiles: Boolean)

    /** Proposed file renames (`GET /api/v3/rename?seriesId=` / `?movieId=`). */
    suspend fun renamePreview(settings: ArrServiceSettings, service: ArrService, itemId: Int): List<ArrRenamePreview>

    /** Applies a rename for the given file ids (`RenameFiles` command). */
    suspend fun applyRename(settings: ArrServiceSettings, service: ArrService, itemId: Int, fileIds: List<Int>)

    /** Queues an automatic search (`MoviesSearch`, `SeriesSearch`, `SeasonSearch` or `EpisodeSearch`). */
    suspend fun automaticSearch(settings: ArrServiceSettings, target: SearchTarget)

    /** Interactive search results (`GET /api/v3/release`). */
    suspend fun releases(settings: ArrServiceSettings, query: ReleaseQuery): List<ArrRelease>

    /** Grabs a release (`POST /api/v3/release`). */
    suspend fun grabRelease(settings: ArrServiceSettings, guid: String, indexerId: Int)

    /** Files under [folder] the service can import for [itemId] (`GET /api/v3/manualimport`). */
    suspend fun manualImportList(
        settings: ArrServiceSettings,
        service: ArrService,
        itemId: Int,
        folder: String,
    ): List<ManualImportItem>

    /** Queues a manual import of the selected files (`ManualImport` command, copy mode). */
    suspend fun manualImport(
        settings: ArrServiceSettings,
        service: ArrService,
        itemId: Int,
        files: List<ManualImportItem>,
    )
}
