package com.pyoungws.cobaltwolf.shared.arrs

import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import com.pyoungws.cobaltwolf.shared.net.toLoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

/**
 * Admin-side media management through the Radarr/Sonarr APIs. Every call resolves the
 * saved connection for the relevant service and reports [Outcome] results; a missing
 * configuration is an [Outcome.Failure], not a crash.
 */
class ArrMediaRepository(
    private val dataSource: ArrMediaDataSource,
    private val settingsRepository: ArrSettingsRepository,
) {
    val settings: StateFlow<ArrSettings> = settingsRepository.settings

    /** The media services with a usable configuration; Bazarr/Prowlarr never appear here. */
    val configuredServices: List<ArrService>
        get() = settingsRepository.configuredServices.filter { it.managesMedia }

    fun isConfigured(service: ArrService): Boolean = settingsRepository.serviceSettings(service) != null

    suspend fun movies(): Outcome<List<ArrMovie>> = call(ArrService.Radarr) { dataSource.movies(it) }

    suspend fun movie(movieId: Int): Outcome<ArrMovie> =
        call(ArrService.Radarr) { dataSource.movie(it, movieId) }

    suspend fun seriesList(): Outcome<List<ArrSeries>> = call(ArrService.Sonarr) { dataSource.seriesList(it) }

    suspend fun series(seriesId: Int): Outcome<ArrSeries> =
        call(ArrService.Sonarr) { dataSource.series(it, seriesId) }

    suspend fun episodes(seriesId: Int): Outcome<List<ArrEpisode>> =
        call(ArrService.Sonarr) { dataSource.episodes(it, seriesId) }

    suspend fun qualityProfiles(service: ArrService): Outcome<List<ArrQualityProfile>> =
        call(service) { dataSource.qualityProfiles(it) }

    suspend fun rootFolders(service: ArrService): Outcome<List<ArrRootFolder>> =
        call(service) { dataSource.rootFolders(it) }

    suspend fun tags(service: ArrService): Outcome<List<ArrTag>> = call(service) { dataSource.tags(it) }

    suspend fun createTag(service: ArrService, label: String): Outcome<ArrTag> =
        call(service) { dataSource.createTag(it, label) }

    suspend fun updateMovie(edit: ArrMovieEdit): Outcome<ArrMovie> =
        call(ArrService.Radarr) { dataSource.updateMovie(it, edit) }

    suspend fun updateSeries(edit: ArrSeriesEdit): Outcome<ArrSeries> =
        call(ArrService.Sonarr) { dataSource.updateSeries(it, edit) }

    suspend fun setEpisodesMonitored(episodeIds: List<Int>, monitored: Boolean): Outcome<Unit> =
        call(ArrService.Sonarr) { dataSource.setEpisodesMonitored(it, episodeIds, monitored) }

    suspend fun files(service: ArrService, itemId: Int): Outcome<List<ArrMediaFile>> = call(service) {
        when (service) {
            ArrService.Radarr -> dataSource.movieFiles(it, itemId)
            ArrService.Sonarr -> dataSource.episodeFiles(it, itemId)
            else -> emptyList()
        }
    }

    suspend fun deleteFile(service: ArrService, fileId: Int): Outcome<Unit> =
        call(service) { dataSource.deleteFile(it, service, fileId) }

    suspend fun deleteItem(service: ArrService, itemId: Int, deleteFiles: Boolean): Outcome<Unit> =
        call(service) { dataSource.deleteItem(it, service, itemId, deleteFiles) }

    suspend fun renamePreview(service: ArrService, itemId: Int): Outcome<List<ArrRenamePreview>> =
        call(service) { dataSource.renamePreview(it, service, itemId) }

    suspend fun applyRename(service: ArrService, itemId: Int, fileIds: List<Int>): Outcome<Unit> =
        call(service) { dataSource.applyRename(it, service, itemId, fileIds) }

    suspend fun automaticSearch(target: SearchTarget): Outcome<Unit> =
        call(target.service) { dataSource.automaticSearch(it, target) }

    suspend fun releases(query: ReleaseQuery): Outcome<List<ArrRelease>> =
        call(query.service) { dataSource.releases(it, query) }

    suspend fun grabRelease(query: ReleaseQuery, guid: String, indexerId: Int): Outcome<Unit> =
        call(query.service) { dataSource.grabRelease(it, guid, indexerId) }

    suspend fun manualImportList(
        service: ArrService,
        itemId: Int,
        folder: String,
    ): Outcome<List<ManualImportItem>> =
        call(service) { dataSource.manualImportList(it, service, itemId, folder) }

    suspend fun manualImport(
        service: ArrService,
        itemId: Int,
        files: List<ManualImportItem>,
    ): Outcome<Unit> = call(service) { dataSource.manualImport(it, service, itemId, files) }

    private val SearchTarget.service: ArrService
        get() = when (this) {
            is SearchTarget.Movie -> ArrService.Radarr
            is SearchTarget.Series, is SearchTarget.Season, is SearchTarget.Episodes -> ArrService.Sonarr
        }

    private val ReleaseQuery.service: ArrService
        get() = when (this) {
            is ReleaseQuery.Movie -> ArrService.Radarr
            is ReleaseQuery.Season, is ReleaseQuery.Episode -> ArrService.Sonarr
        }

    private suspend fun <T> call(
        service: ArrService,
        block: suspend (ArrServiceSettings) -> T,
    ): Outcome<T> {
        val settings = settingsRepository.serviceSettings(service)
            ?: return Outcome.Failure(LoadError.Unexpected("${service.name} is not configured"))
        return try {
            Outcome.Success(block(settings))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Failure(e.toLoadError())
        }
    }
}
