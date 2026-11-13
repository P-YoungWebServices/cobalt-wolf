package com.pyoungws.cobaltwolf.shared.arrs

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Ktor client for the Radarr/Sonarr management endpoints. Edits are applied as
 * fetch-modify-PUT on the raw resource so fields this client does not model are
 * preserved; commands are `POST /api/v3/command` bodies.
 */
class KtorArrMediaDataSource(
    private val client: HttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ArrMediaDataSource {

    override suspend fun movies(settings: ArrServiceSettings): List<ArrMovie> = call {
        client.get("${settings.serverUrl}/api/v3/movie") { apiKey(settings) }
            .body<List<RadarrMovieDto>>().map { it.toModel(settings) }
    }

    override suspend fun movie(settings: ArrServiceSettings, movieId: Int): ArrMovie = call {
        client.get("${settings.serverUrl}/api/v3/movie/$movieId") { apiKey(settings) }
            .body<RadarrMovieDto>().toModel(settings)
    }

    override suspend fun seriesList(settings: ArrServiceSettings): List<ArrSeries> = call {
        client.get("${settings.serverUrl}/api/v3/series") { apiKey(settings) }
            .body<List<SonarrSeriesResourceDto>>().map { it.toModel(settings) }
    }

    override suspend fun series(settings: ArrServiceSettings, seriesId: Int): ArrSeries = call {
        client.get("${settings.serverUrl}/api/v3/series/$seriesId") { apiKey(settings) }
            .body<SonarrSeriesResourceDto>().toModel(settings)
    }

    override suspend fun episodes(settings: ArrServiceSettings, seriesId: Int): List<ArrEpisode> = call {
        client.get("${settings.serverUrl}/api/v3/episode") {
            apiKey(settings)
            parameter("seriesId", seriesId)
        }.body<List<SonarrEpisodeDto>>().map { it.toModel() }
    }

    override suspend fun qualityProfiles(settings: ArrServiceSettings): List<ArrQualityProfile> = call {
        client.get("${settings.serverUrl}/api/v3/qualityprofile") { apiKey(settings) }
            .body<List<ArrQualityProfileDto>>().map { ArrQualityProfile(it.id, it.name) }
    }

    override suspend fun rootFolders(settings: ArrServiceSettings): List<ArrRootFolder> = call {
        client.get("${settings.serverUrl}/api/v3/rootfolder") { apiKey(settings) }
            .body<List<ArrRootFolderDto>>().map { ArrRootFolder(it.id, it.path, it.freeSpace) }
    }

    override suspend fun tags(settings: ArrServiceSettings): List<ArrTag> = call {
        client.get("${settings.serverUrl}/api/v3/tag") { apiKey(settings) }
            .body<List<ArrTagDto>>().map { ArrTag(it.id, it.label) }
    }

    override suspend fun createTag(settings: ArrServiceSettings, label: String): ArrTag = call {
        client.post("${settings.serverUrl}/api/v3/tag") {
            apiKey(settings)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("label", label) })
        }.body<ArrTagDto>().let { ArrTag(it.id, it.label) }
    }

    override suspend fun updateMovie(settings: ArrServiceSettings, edit: ArrMovieEdit): ArrMovie = call {
        val updated = updateResource(settings, "/api/v3/movie/${edit.id}", moveFiles = true) { current ->
            current.applyEdits {
                put("monitored", JsonPrimitive(edit.monitored))
                put("qualityProfileId", JsonPrimitive(edit.qualityProfileId))
                put("tags", JsonArray(edit.tagIds.map(::JsonPrimitive)))
                edit.rootFolderPath?.let { root -> moveToRoot(current, root) }
            }
        }
        arrJson.decodeFromJsonElement<RadarrMovieDto>(updated).toModel(settings)
    }

    override suspend fun updateSeries(settings: ArrServiceSettings, edit: ArrSeriesEdit): ArrSeries = call {
        val updated = updateResource(settings, "/api/v3/series/${edit.id}", moveFiles = true) { current ->
            current.applyEdits {
                put("monitored", JsonPrimitive(edit.monitored))
                put("qualityProfileId", JsonPrimitive(edit.qualityProfileId))
                put("tags", JsonArray(edit.tagIds.map(::JsonPrimitive)))
                edit.rootFolderPath?.let { root -> moveToRoot(current, root) }
                val edits = edit.seasons.associateBy { it.seasonNumber }
                put("seasons", JsonArray(current.seasons().map { season ->
                    val number = season["seasonNumber"]?.jsonPrimitive?.int ?: return@map season
                    val seasonEdit = edits[number] ?: return@map season
                    season.applyEdits { put("monitored", JsonPrimitive(seasonEdit.monitored)) }
                }))
            }
        }
        arrJson.decodeFromJsonElement<SonarrSeriesResourceDto>(updated).toModel(settings)
    }

    override suspend fun setEpisodesMonitored(
        settings: ArrServiceSettings,
        episodeIds: List<Int>,
        monitored: Boolean,
    ) = call {
        client.put("${settings.serverUrl}/api/v3/episode/monitor") {
            apiKey(settings)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                putJsonArray("episodeIds") { episodeIds.forEach(::add) }
                put("monitored", monitored)
            })
        }.bodyAsText()
        Unit
    }

    override suspend fun movieFiles(settings: ArrServiceSettings, movieId: Int): List<ArrMediaFile> = call {
        client.get("${settings.serverUrl}/api/v3/moviefile") {
            apiKey(settings)
            parameter("movieId", movieId)
        }.body<List<ArrFileDto>>().map { it.toModel() }
    }

    override suspend fun episodeFiles(settings: ArrServiceSettings, seriesId: Int): List<ArrMediaFile> = call {
        client.get("${settings.serverUrl}/api/v3/episodefile") {
            apiKey(settings)
            parameter("seriesId", seriesId)
        }.body<List<ArrFileDto>>().map { it.toModel() }
    }

    override suspend fun deleteFile(settings: ArrServiceSettings, service: ArrService, fileId: Int) = call {
        val resource = when (service) {
            ArrService.Radarr -> "moviefile"
            ArrService.Sonarr -> "episodefile"
            else -> error("$service has no file resources")
        }
        client.delete("${settings.serverUrl}/api/v3/$resource/$fileId") { apiKey(settings) }.bodyAsText()
        Unit
    }

    override suspend fun deleteItem(
        settings: ArrServiceSettings,
        service: ArrService,
        itemId: Int,
        deleteFiles: Boolean,
    ) = call {
        val resource = when (service) {
            ArrService.Radarr -> "movie"
            ArrService.Sonarr -> "series"
            else -> error("$service has no media items")
        }
        client.delete("${settings.serverUrl}/api/v3/$resource/$itemId") {
            apiKey(settings)
            parameter("deleteFiles", deleteFiles)
        }.bodyAsText()
        Unit
    }

    override suspend fun renamePreview(
        settings: ArrServiceSettings,
        service: ArrService,
        itemId: Int,
    ): List<ArrRenamePreview> = call {
        client.get("${settings.serverUrl}/api/v3/rename") {
            apiKey(settings)
            when (service) {
                ArrService.Radarr -> parameter("movieId", itemId)
                ArrService.Sonarr -> parameter("seriesId", itemId)
                else -> error("$service has no media items")
            }
        }.body<List<RenamePreviewDto>>().map {
            ArrRenamePreview(it.movieFileId ?: it.episodeFileId ?: 0, it.existingPath, it.newPath)
        }
    }

    override suspend fun applyRename(
        settings: ArrServiceSettings,
        service: ArrService,
        itemId: Int,
        fileIds: List<Int>,
    ) = call {
        command(settings, buildJsonObject {
            put("name", "RenameFiles")
            when (service) {
                ArrService.Radarr -> put("movieId", itemId)
                ArrService.Sonarr -> put("seriesId", itemId)
                else -> error("$service has no media items")
            }
            putJsonArray("files") { fileIds.forEach(::add) }
        })
    }

    override suspend fun automaticSearch(settings: ArrServiceSettings, target: SearchTarget) = call {
        command(settings, buildJsonObject {
            when (target) {
                is SearchTarget.Movie -> {
                    put("name", "MoviesSearch")
                    putJsonArray("movieIds") { add(target.movieId) }
                }
                is SearchTarget.Series -> {
                    put("name", "SeriesSearch")
                    put("seriesId", target.seriesId)
                }
                is SearchTarget.Season -> {
                    put("name", "SeasonSearch")
                    put("seriesId", target.seriesId)
                    put("seasonNumber", target.seasonNumber)
                }
                is SearchTarget.Episodes -> {
                    put("name", "EpisodeSearch")
                    putJsonArray("episodeIds") { target.episodeIds.forEach(::add) }
                }
            }
        })
    }

    override suspend fun releases(settings: ArrServiceSettings, query: ReleaseQuery): List<ArrRelease> = call {
        client.get("${settings.serverUrl}/api/v3/release") {
            apiKey(settings)
            // Release search fans out to every indexer; the server holds the response
            // open silently meanwhile, so the socket (read) timeout must grow too.
            timeout {
                requestTimeoutMillis = LONG_TIMEOUT_MS
                socketTimeoutMillis = LONG_TIMEOUT_MS
            }
            when (query) {
                is ReleaseQuery.Movie -> parameter("movieId", query.movieId)
                is ReleaseQuery.Season -> {
                    parameter("seriesId", query.seriesId)
                    parameter("seasonNumber", query.seasonNumber)
                }
                is ReleaseQuery.Episode -> parameter("episodeId", query.episodeId)
            }
        }.body<List<ArrReleaseDto>>().map { it.toModel() }
    }

    override suspend fun grabRelease(settings: ArrServiceSettings, guid: String, indexerId: Int) = call {
        client.post("${settings.serverUrl}/api/v3/release") {
            apiKey(settings)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("guid", guid)
                put("indexerId", indexerId)
            })
        }.bodyAsText()
        Unit
    }

    override suspend fun manualImportList(
        settings: ArrServiceSettings,
        service: ArrService,
        itemId: Int,
        folder: String,
    ): List<ManualImportItem> = call {
        client.get("${settings.serverUrl}/api/v3/manualimport") {
            apiKey(settings)
            // Scanning and matching a folder's files is slow on large folders.
            timeout {
                requestTimeoutMillis = LONG_TIMEOUT_MS
                socketTimeoutMillis = LONG_TIMEOUT_MS
            }
            parameter("folder", folder)
            parameter("filterExistingFiles", false)
            when (service) {
                ArrService.Radarr -> parameter("movieId", itemId)
                ArrService.Sonarr -> parameter("seriesId", itemId)
                else -> error("$service has no media items")
            }
        }.body<List<ManualImportRowDto>>().map { it.toModel() }
    }

    override suspend fun manualImport(
        settings: ArrServiceSettings,
        service: ArrService,
        itemId: Int,
        files: List<ManualImportItem>,
    ) = call {
        command(settings, buildJsonObject {
            put("name", "ManualImport")
            put("importMode", "copy")
            putJsonArray("files") {
                files.forEach { file ->
                    add(buildJsonObject {
                        put("path", file.path)
                        put("folderName", file.path.substringBeforeLast('/'))
                        when (service) {
                            ArrService.Radarr -> put("movieId", file.matchedItemId ?: itemId)
                            ArrService.Sonarr -> {
                                put("seriesId", file.matchedItemId ?: itemId)
                                putJsonArray("episodeIds") { file.episodeIds.forEach(::add) }
                            }
                            else -> error("$service has no media items")
                        }
                        file.qualityId?.let { id ->
                            putJsonObject("quality") {
                                putJsonObject("quality") {
                                    put("id", id)
                                    file.qualityName?.let { put("name", it) }
                                }
                            }
                        }
                        file.releaseGroup?.let { put("releaseGroup", it) }
                    })
                }
            }
        })
    }

    /** POSTs a command resource; the queue response body is consumed but unused. */
    private suspend fun command(settings: ArrServiceSettings, body: JsonObject) {
        client.post("${settings.serverUrl}/api/v3/command") {
            apiKey(settings)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()
    }

    /** Fetches the raw resource, applies [mutate] and PUTs the result back. */
    private suspend fun updateResource(
        settings: ArrServiceSettings,
        path: String,
        moveFiles: Boolean,
        mutate: (JsonObject) -> JsonObject,
    ): JsonObject {
        val url = "${settings.serverUrl}$path"
        val current = client.get(url) { apiKey(settings) }.body<JsonObject>()
        return client.put(url) {
            apiKey(settings)
            contentType(ContentType.Application.Json)
            parameter("moveFiles", moveFiles)
            setBody(mutate(current))
        }.body()
    }

    /** Returns a copy of this resource with [edits] applied. */
    private inline fun JsonObject.applyEdits(edits: MutableMap<String, kotlinx.serialization.json.JsonElement>.() -> Unit): JsonObject =
        JsonObject(toMutableMap().apply(edits))

    private fun JsonObject.seasons(): List<JsonObject> =
        (this["seasons"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    /** Sets `rootFolderPath` and points `path` at the same folder name under the new root. */
    private fun MutableMap<String, kotlinx.serialization.json.JsonElement>.moveToRoot(current: JsonObject, root: String) {
        put("rootFolderPath", JsonPrimitive(root))
        val folderName = current["path"]?.jsonPrimitive?.content?.trimEnd('/')?.substringAfterLast('/')
        if (!folderName.isNullOrBlank()) put("path", JsonPrimitive("$root/$folderName"))
    }

    private fun HttpRequestBuilder.apiKey(settings: ArrServiceSettings) {
        header("X-Api-Key", settings.apiKey)
    }

    private suspend fun <T> call(block: suspend () -> T): T = withContext(ioDispatcher) { block() }

    private companion object {
        const val LONG_TIMEOUT_MS = 120_000L
    }
}

@Serializable
internal data class RadarrMovieDto(
    val id: Int = 0,
    val title: String = "",
    val year: Int? = null,
    val monitored: Boolean = false,
    val qualityProfileId: Int = 0,
    val rootFolderPath: String? = null,
    val path: String = "",
    val tags: List<Int> = emptyList(),
    val hasFile: Boolean = false,
    val sizeOnDisk: Long? = null,
    val images: List<ArrImageDto> = emptyList(),
) {
    fun toModel(settings: ArrServiceSettings) = ArrMovie(
        id = id,
        title = title,
        year = year,
        monitored = monitored,
        qualityProfileId = qualityProfileId,
        rootFolderPath = rootFolderPath,
        path = path,
        tagIds = tags,
        hasFile = hasFile,
        sizeOnDisk = sizeOnDisk,
        posterUrl = images.posterUrl(settings.serverUrl.orEmpty()),
    )
}

@Serializable
internal data class SonarrSeasonStatsDto(
    val episodeFileCount: Int = 0,
    val episodeCount: Int = 0,
    val totalEpisodeCount: Int = 0,
)

@Serializable
internal data class SonarrSeasonDto(
    val seasonNumber: Int = 0,
    val monitored: Boolean = false,
    val statistics: SonarrSeasonStatsDto? = null,
) {
    fun toModel() = ArrSeason(
        seasonNumber = seasonNumber,
        monitored = monitored,
        episodeFileCount = statistics?.episodeFileCount ?: 0,
        episodeCount = statistics?.episodeCount ?: 0,
        totalEpisodeCount = statistics?.totalEpisodeCount ?: 0,
    )
}

@Serializable
internal data class SonarrSeriesResourceDto(
    val id: Int = 0,
    val title: String = "",
    val year: Int? = null,
    val monitored: Boolean = false,
    val qualityProfileId: Int = 0,
    val rootFolderPath: String? = null,
    val path: String = "",
    val tags: List<Int> = emptyList(),
    val seasons: List<SonarrSeasonDto> = emptyList(),
    val images: List<ArrImageDto> = emptyList(),
) {
    fun toModel(settings: ArrServiceSettings) = ArrSeries(
        id = id,
        title = title,
        year = year,
        monitored = monitored,
        qualityProfileId = qualityProfileId,
        rootFolderPath = rootFolderPath,
        path = path,
        tagIds = tags,
        seasons = seasons.map { it.toModel() },
        posterUrl = images.posterUrl(settings.serverUrl.orEmpty()),
    )
}

@Serializable
internal data class SonarrEpisodeDto(
    val id: Int = 0,
    val episodeFileId: Int? = null,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val title: String = "",
    val monitored: Boolean = false,
    val hasFile: Boolean = false,
) {
    fun toModel() = ArrEpisode(
        id = id,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        title = title,
        monitored = monitored,
        hasFile = hasFile,
        episodeFileId = episodeFileId,
    )
}

@Serializable
internal data class QualityNameDto(val id: Int? = null, val name: String? = null)

@Serializable
internal data class QualityModelDto(val quality: QualityNameDto? = null)

@Serializable
internal data class ArrFileDto(
    val id: Int = 0,
    val relativePath: String = "",
    val size: Long = 0,
    val quality: QualityModelDto? = null,
    val dateAdded: String? = null,
) {
    fun toModel() = ArrMediaFile(
        id = id,
        relativePath = relativePath,
        size = size,
        qualityName = quality?.quality?.name,
        dateAdded = dateAdded,
    )
}

@Serializable
internal data class ArrReleaseDto(
    val guid: String = "",
    val indexerId: Int = 0,
    val title: String = "",
    val size: Long = 0,
    val indexer: String? = null,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val protocol: String? = null,
    val ageHours: Double? = null,
    val rejected: Boolean = false,
    val rejections: List<String> = emptyList(),
    val quality: QualityModelDto? = null,
) {
    fun toModel() = ArrRelease(
        guid = guid,
        indexerId = indexerId,
        title = title,
        qualityName = quality?.quality?.name,
        size = size,
        indexer = indexer,
        seeders = seeders,
        leechers = leechers,
        protocol = protocol,
        ageHours = ageHours,
        rejected = rejected,
        rejections = rejections,
    )
}

@Serializable
internal data class RenamePreviewDto(
    val movieFileId: Int? = null,
    val episodeFileId: Int? = null,
    val existingPath: String = "",
    val newPath: String = "",
)

@Serializable
internal data class ArrTagDto(val id: Int = 0, val label: String = "")

@Serializable
internal data class ArrRootFolderDto(val id: Int = 0, val path: String = "", val freeSpace: Long? = null)

@Serializable
internal data class ArrQualityProfileDto(val id: Int = 0, val name: String = "")

@Serializable
internal data class IdRefDto(val id: Int = 0)

@Serializable
internal data class RejectionDto(val reason: String? = null)

@Serializable
internal data class ManualImportRowDto(
    val path: String = "",
    val name: String = "",
    val size: Long = 0,
    val movie: IdRefDto? = null,
    val series: IdRefDto? = null,
    val episodes: List<IdRefDto> = emptyList(),
    val quality: QualityModelDto? = null,
    val releaseGroup: String? = null,
    val rejections: List<RejectionDto> = emptyList(),
) {
    fun toModel() = ManualImportItem(
        path = path,
        name = name.ifBlank { path.substringAfterLast('/') },
        size = size,
        matchedItemId = movie?.id ?: series?.id,
        episodeIds = episodes.map { it.id },
        qualityId = quality?.quality?.id,
        qualityName = quality?.quality?.name,
        releaseGroup = releaseGroup,
        rejections = rejections.mapNotNull { it.reason },
    )
}
