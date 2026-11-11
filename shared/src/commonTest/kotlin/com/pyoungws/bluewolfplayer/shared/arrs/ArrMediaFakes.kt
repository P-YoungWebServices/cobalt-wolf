package com.pyoungws.bluewolfplayer.shared.arrs

/** Records every call and returns scripted results; [error] makes every call throw. */
class FakeArrMediaDataSource : ArrMediaDataSource {
    val calls = mutableListOf<String>()
    var error: Throwable? = null

    var movieItems: List<ArrMovie> = emptyList()
    var moviesById = mutableMapOf<Int, ArrMovie>()
    var seriesItems: List<ArrSeries> = emptyList()
    var seriesById = mutableMapOf<Int, ArrSeries>()
    var episodeItems: List<ArrEpisode> = emptyList()
    var fileItems: List<ArrMediaFile> = emptyList()
    var profiles: List<ArrQualityProfile> = listOf(ArrQualityProfile(7, "HD-1080p"))
    var roots: List<ArrRootFolder> = listOf(ArrRootFolder(1, "/media/library"))
    var tagItems: List<ArrTag> = emptyList()
    var nextTagId = 10
    var renameItems: List<ArrRenamePreview> = emptyList()
    var releaseItems: List<ArrRelease> = emptyList()
    var importItems: List<ManualImportItem> = emptyList()

    private fun fail() {
        error?.let { throw it }
    }

    override suspend fun movies(settings: ArrServiceSettings): List<ArrMovie> {
        calls += "movies()"
        fail()
        return movieItems
    }

    override suspend fun movie(settings: ArrServiceSettings, movieId: Int): ArrMovie {
        calls += "movie($movieId)"
        fail()
        return moviesById[movieId] ?: throw NoSuchElementException("movie $movieId")
    }

    override suspend fun seriesList(settings: ArrServiceSettings): List<ArrSeries> {
        calls += "seriesList()"
        fail()
        return seriesItems
    }

    override suspend fun series(settings: ArrServiceSettings, seriesId: Int): ArrSeries {
        calls += "series($seriesId)"
        fail()
        return seriesById[seriesId] ?: throw NoSuchElementException("series $seriesId")
    }

    override suspend fun episodes(settings: ArrServiceSettings, seriesId: Int): List<ArrEpisode> {
        calls += "episodes($seriesId)"
        fail()
        return episodeItems
    }

    override suspend fun qualityProfiles(settings: ArrServiceSettings): List<ArrQualityProfile> {
        calls += "qualityProfiles()"
        fail()
        return profiles
    }

    override suspend fun rootFolders(settings: ArrServiceSettings): List<ArrRootFolder> {
        calls += "rootFolders()"
        fail()
        return roots
    }

    override suspend fun tags(settings: ArrServiceSettings): List<ArrTag> {
        calls += "tags()"
        fail()
        return tagItems
    }

    override suspend fun createTag(settings: ArrServiceSettings, label: String): ArrTag {
        calls += "createTag($label)"
        fail()
        return ArrTag(nextTagId++, label).also { tagItems = tagItems + it }
    }

    override suspend fun updateMovie(settings: ArrServiceSettings, edit: ArrMovieEdit): ArrMovie {
        calls += "updateMovie($edit)"
        fail()
        return moviesById[edit.id] ?: ArrMovie(edit.id, "Movie", null, edit.monitored, edit.qualityProfileId, edit.rootFolderPath, "", edit.tagIds, false, null, null)
    }

    override suspend fun updateSeries(settings: ArrServiceSettings, edit: ArrSeriesEdit): ArrSeries {
        calls += "updateSeries($edit)"
        fail()
        val current = seriesById[edit.id]
        return current?.copy(monitored = edit.monitored, qualityProfileId = edit.qualityProfileId, rootFolderPath = edit.rootFolderPath, tagIds = edit.tagIds, seasons = edit.seasons)
            ?: ArrSeries(edit.id, "Series", null, edit.monitored, edit.qualityProfileId, edit.rootFolderPath, "", edit.tagIds, edit.seasons, null)
    }

    override suspend fun setEpisodesMonitored(settings: ArrServiceSettings, episodeIds: List<Int>, monitored: Boolean) {
        calls += "setEpisodesMonitored($episodeIds,$monitored)"
        fail()
        episodeItems = episodeItems.map { if (it.id in episodeIds) it.copy(monitored = monitored) else it }
    }

    override suspend fun movieFiles(settings: ArrServiceSettings, movieId: Int): List<ArrMediaFile> {
        calls += "movieFiles($movieId)"
        fail()
        return fileItems
    }

    override suspend fun episodeFiles(settings: ArrServiceSettings, seriesId: Int): List<ArrMediaFile> {
        calls += "episodeFiles($seriesId)"
        fail()
        return fileItems
    }

    override suspend fun deleteFile(settings: ArrServiceSettings, service: ArrService, fileId: Int) {
        calls += "deleteFile($service,$fileId)"
        fail()
        fileItems = fileItems.filterNot { it.id == fileId }
    }

    override suspend fun deleteItem(settings: ArrServiceSettings, service: ArrService, itemId: Int, deleteFiles: Boolean) {
        calls += "deleteItem($service,$itemId,$deleteFiles)"
        fail()
    }

    override suspend fun renamePreview(settings: ArrServiceSettings, service: ArrService, itemId: Int): List<ArrRenamePreview> {
        calls += "renamePreview($service,$itemId)"
        fail()
        return renameItems
    }

    override suspend fun applyRename(settings: ArrServiceSettings, service: ArrService, itemId: Int, fileIds: List<Int>) {
        calls += "applyRename($service,$itemId,$fileIds)"
        fail()
    }

    override suspend fun automaticSearch(settings: ArrServiceSettings, target: SearchTarget) {
        calls += "automaticSearch($target)"
        fail()
    }

    override suspend fun releases(settings: ArrServiceSettings, query: ReleaseQuery): List<ArrRelease> {
        calls += "releases($query)"
        fail()
        return releaseItems
    }

    override suspend fun grabRelease(settings: ArrServiceSettings, guid: String, indexerId: Int) {
        calls += "grabRelease($guid,$indexerId)"
        fail()
    }

    override suspend fun manualImportList(
        settings: ArrServiceSettings,
        service: ArrService,
        itemId: Int,
        folder: String,
    ): List<ManualImportItem> {
        calls += "manualImportList($service,$itemId,$folder)"
        fail()
        return importItems
    }

    override suspend fun manualImport(
        settings: ArrServiceSettings,
        service: ArrService,
        itemId: Int,
        files: List<ManualImportItem>,
    ) {
        calls += "manualImport($service,$itemId,${files.map { it.path }})"
        fail()
    }
}

fun arrMovie(
    id: Int = 1,
    title: String = "Test Movie",
    year: Int? = 2026,
    monitored: Boolean = true,
    qualityProfileId: Int = 7,
    rootFolderPath: String? = "/media/movies",
    path: String = "/media/movies/Test Movie (2026)",
    tagIds: List<Int> = emptyList(),
    hasFile: Boolean = true,
) = ArrMovie(id, title, year, monitored, qualityProfileId, rootFolderPath, path, tagIds, hasFile, sizeOnDisk = 4_000_000_000, posterUrl = null)

fun arrSeason(seasonNumber: Int, monitored: Boolean = true, episodeFileCount: Int = 5, episodeCount: Int = 8) =
    ArrSeason(seasonNumber, monitored, episodeFileCount, episodeCount, episodeCount)

fun arrSeries(
    id: Int = 2,
    title: String = "Test Series",
    year: Int? = 2025,
    monitored: Boolean = true,
    qualityProfileId: Int = 7,
    rootFolderPath: String? = "/media/tv",
    path: String = "/media/tv/Test Series",
    tagIds: List<Int> = emptyList(),
    seasons: List<ArrSeason> = listOf(arrSeason(1, true), arrSeason(2, false)),
) = ArrSeries(id, title, year, monitored, qualityProfileId, rootFolderPath, path, tagIds, seasons, posterUrl = null)

fun arrEpisode(
    id: Int,
    seasonNumber: Int = 1,
    episodeNumber: Int = id,
    title: String = "Episode $episodeNumber",
    monitored: Boolean = true,
    hasFile: Boolean = true,
    episodeFileId: Int? = 100 + id,
) = ArrEpisode(id, seasonNumber, episodeNumber, title, monitored, hasFile, episodeFileId)

fun arrFile(id: Int = 1, relativePath: String = "file-$id.mkv") =
    ArrMediaFile(id, relativePath, size = 1_000_000_000L * id, qualityName = "Bluray-1080p", dateAdded = "2026-01-01T10:00:00Z")

fun arrRelease(guid: String, title: String = "Release $guid", seeders: Int? = 10, rejected: Boolean = false) =
    ArrRelease(guid, indexerId = 3, title, qualityName = "WEBDL-1080p", size = 2_000_000_000, indexer = "indexer", seeders, leechers = 2, protocol = "torrent", ageHours = 5.0, rejected, rejections = emptyList())
