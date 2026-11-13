package com.pyoungws.cobaltwolf.shared.arrs

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The media-management API client against mocked HTTP responses: request paths, methods,
 * query params and command bodies, plus DTO parsing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KtorArrMediaDataSourceTest {
    private val settings = ArrServiceSettings("https://arr.example.com", "secret-key")
    private val sent = mutableListOf<HttpRequestData>()
    private var handler: (HttpRequestData) -> Pair<HttpStatusCode, String> = { HttpStatusCode.OK to "[]" }

    private fun TestScope.dataSource(): KtorArrMediaDataSource {
        val engine = MockEngine { request ->
            sent += request
            val (status, body) = handler(request)
            if (status.value >= 400) return@MockEngine respondError(status)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return KtorArrMediaDataSource(HttpClient(engine) { arrDefaults() }, StandardTestDispatcher(testScheduler))
    }

    private fun HttpRequestData.bodyJson() = Json.parseToJsonElement((body as TextContent).text).jsonObject

    @Test
    fun `movies lists Radarr movies`() = runTest {
        handler = {
            HttpStatusCode.OK to """[{"id":5,"title":"Film","year":2024,"monitored":true,"qualityProfileId":2,
                "rootFolderPath":"/movies","path":"/movies/Film (2024)","tags":[1,3],"hasFile":true,"sizeOnDisk":100,
                "images":[{"coverType":"poster","url":"/MediaCover/5/poster.jpg"}]}]"""
        }

        val movies = dataSource().movies(settings)

        val request = sent.single()
        assertEquals("/api/v3/movie", request.url.encodedPath)
        assertEquals("secret-key", request.headers["X-Api-Key"])
        val movie = movies.single()
        assertEquals(5, movie.id)
        assertEquals("Film", movie.title)
        assertEquals(2, movie.qualityProfileId)
        assertEquals("/movies", movie.rootFolderPath)
        assertEquals(listOf(1, 3), movie.tagIds)
        assertEquals("https://arr.example.com/MediaCover/5/poster.jpg", movie.posterUrl)
    }

    @Test
    fun `seriesList maps seasons and their statistics`() = runTest {
        handler = {
            HttpStatusCode.OK to """[{"id":9,"title":"Show","year":2023,"monitored":false,"qualityProfileId":4,
                "path":"/tv/Show","tags":[],"seasons":[{"seasonNumber":1,"monitored":true,
                "statistics":{"episodeFileCount":3,"episodeCount":8,"totalEpisodeCount":10}}]}]"""
        }

        val series = dataSource().seriesList(settings).single()

        assertEquals(9, series.id)
        val season = series.seasons.single()
        assertEquals(1, season.seasonNumber)
        assertEquals(true, season.monitored)
        assertEquals(3, season.episodeFileCount)
        assertEquals(10, season.totalEpisodeCount)
    }

    @Test
    fun `episodes requests the series id`() = runTest {
        handler = {
            HttpStatusCode.OK to """[{"id":21,"episodeFileId":7,"seasonNumber":2,"episodeNumber":4,
                "title":"The Episode","monitored":true,"hasFile":true}]"""
        }

        val episodes = dataSource().episodes(settings, 9)

        assertEquals("9", sent.single().url.parameters["seriesId"])
        val episode = episodes.single()
        assertEquals(2, episode.seasonNumber)
        assertEquals(4, episode.episodeNumber)
        assertEquals(7, episode.episodeFileId)
        assertEquals("S02E04", episode.label)
    }

    @Test
    fun `form metadata comes from the profile root-folder and tag endpoints`() = runTest {
        handler = { request ->
            when (request.url.encodedPath) {
                "/api/v3/qualityprofile" -> HttpStatusCode.OK to """[{"id":1,"name":"HD-1080p"},{"id":2,"name":"Ultra-HD"}]"""
                "/api/v3/rootfolder" -> HttpStatusCode.OK to """[{"id":3,"path":"/media/movies","freeSpace":50}]"""
                "/api/v3/tag" -> HttpStatusCode.OK to """[{"id":8,"label":"kids"}]"""
                "/api/v3/tag/9" -> HttpStatusCode.OK to ""
                else -> HttpStatusCode.OK to "[]"
            }
        }
        val ds = dataSource()

        assertEquals(listOf("HD-1080p", "Ultra-HD"), ds.qualityProfiles(settings).map { it.name })
        assertEquals(listOf(ArrRootFolder(3, "/media/movies", 50)), ds.rootFolders(settings))
        assertEquals(listOf(ArrTag(8, "kids")), ds.tags(settings))
    }

    @Test
    fun `createTag posts the label`() = runTest {
        handler = { HttpStatusCode.OK to """{"id":11,"label":"anime"}""" }

        val tag = dataSource().createTag(settings, "anime")

        val request = sent.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v3/tag", request.url.encodedPath)
        assertEquals("anime", request.bodyJson()["label"]?.jsonPrimitive?.content)
        assertEquals(ArrTag(11, "anime"), tag)
    }

    @Test
    fun `updateMovie fetches the resource then PUTs only the edited fields`() = runTest {
        handler = { request ->
            when (request.method) {
                HttpMethod.Get -> HttpStatusCode.OK to """{"id":5,"title":"Film","year":2024,"monitored":false,
                    "qualityProfileId":2,"rootFolderPath":"/old","path":"/old/Film (2024)","tags":[1],
                    "minimumAvailability":"released","customField":"keep-me"}"""
                else -> HttpStatusCode.OK to """{"id":5,"title":"Film","monitored":true,"qualityProfileId":9,
                    "rootFolderPath":"/new","path":"/new/Film (2024)","tags":[2]}"""
            }
        }
        val edit = ArrMovieEdit(5, monitored = true, qualityProfileId = 9, rootFolderPath = "/new", tagIds = listOf(2))

        val updated = dataSource().updateMovie(settings, edit)

        assertEquals(2, sent.size)
        val put = sent[1]
        assertEquals(HttpMethod.Put, put.method)
        assertEquals("/api/v3/movie/5", put.url.encodedPath)
        assertEquals("true", put.url.parameters["moveFiles"])
        val body = put.bodyJson()
        assertEquals(true, body["monitored"]?.jsonPrimitive?.content?.toBooleanStrict())
        assertEquals("9", body["qualityProfileId"]?.jsonPrimitive?.content)
        assertEquals(listOf("2"), body["tags"]?.jsonArray?.map { it.jsonPrimitive.content })
        assertEquals("/new", body["rootFolderPath"]?.jsonPrimitive?.content)
        // The folder name is recomputed under the new root and unknown fields survive.
        assertEquals("/new/Film (2024)", body["path"]?.jsonPrimitive?.content)
        assertEquals("keep-me", body["customField"]?.jsonPrimitive?.content)
        assertEquals(true, updated.monitored)
    }

    @Test
    fun `updateSeries mutates only the matching season monitored flags`() = runTest {
        handler = { request ->
            when (request.method) {
                HttpMethod.Get -> HttpStatusCode.OK to """{"id":9,"title":"Show","monitored":true,"qualityProfileId":4,
                    "path":"/tv/Show","tags":[],
                    "seasons":[{"seasonNumber":0,"monitored":false},{"seasonNumber":1,"monitored":false},{"seasonNumber":2,"monitored":true}]}"""
                else -> HttpStatusCode.OK to """{"id":9,"title":"Show"}"""
            }
        }
        val edit = ArrSeriesEdit(9, true, 4, null, emptyList(), listOf(arrSeason(1, true), arrSeason(2, true)))

        dataSource().updateSeries(settings, edit)

        val body = sent[1].bodyJson()
        val seasons = body["seasons"]!!.jsonArray.map { it.jsonObject }
        assertEquals(3, seasons.size)
        assertEquals(false, seasons[0]["monitored"]!!.jsonPrimitive.boolean)
        assertEquals(true, seasons[1]["monitored"]!!.jsonPrimitive.boolean)
        assertEquals(true, seasons[2]["monitored"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `setEpisodesMonitored PUTs the bulk monitor resource`() = runTest {
        handler = { HttpStatusCode.Accepted to "[]" }

        dataSource().setEpisodesMonitored(settings, listOf(21, 22), false)

        val request = sent.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/api/v3/episode/monitor", request.url.encodedPath)
        val body = request.bodyJson()
        assertEquals(listOf("21", "22"), body["episodeIds"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(false, body["monitored"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `files and file deletion use the service-specific resource`() = runTest {
        handler = { request ->
            if (request.method == HttpMethod.Delete) HttpStatusCode.OK to ""
            else HttpStatusCode.OK to """[{"id":31,"relativePath":"Movie.mkv","size":42,
                "quality":{"quality":{"id":1,"name":"Bluray-1080p"}},"dateAdded":"2026-01-02T00:00:00Z"}]"""
        }
        val ds = dataSource()

        val movieFiles = ds.movieFiles(settings, 5)
        ds.deleteFile(settings, ArrService.Sonarr, 31)

        assertEquals("5", sent[0].url.parameters["movieId"])
        assertEquals("Bluray-1080p", movieFiles.single().qualityName)
        assertEquals(HttpMethod.Delete, sent[1].method)
        assertEquals("/api/v3/episodefile/31", sent[1].url.encodedPath)
    }

    @Test
    fun `deleteItem passes the deleteFiles choice`() = runTest {
        handler = { HttpStatusCode.OK to "" }

        dataSource().deleteItem(settings, ArrService.Radarr, 5, deleteFiles = true)
        dataSource().deleteItem(settings, ArrService.Sonarr, 9, deleteFiles = false)

        assertEquals("/api/v3/movie/5", sent[0].url.encodedPath)
        assertEquals("true", sent[0].url.parameters["deleteFiles"])
        assertEquals("/api/v3/series/9", sent[1].url.encodedPath)
        assertEquals("false", sent[1].url.parameters["deleteFiles"])
    }

    @Test
    fun `renamePreview uses the item id for the right service`() = runTest {
        handler = { HttpStatusCode.OK to """[{"seriesId":9,"episodeFileId":31,"existingPath":"old.mkv","newPath":"new.mkv"}]""" }

        val previews = dataSource().renamePreview(settings, ArrService.Sonarr, 9)

        assertEquals("9", sent.single().url.parameters["seriesId"])
        assertEquals(listOf(ArrRenamePreview(31, "old.mkv", "new.mkv")), previews)
    }

    @Test
    fun `applyRename posts the RenameFiles command`() = runTest {
        handler = { HttpStatusCode.Created to """{"id":1,"name":"RenameFiles"}""" }

        dataSource().applyRename(settings, ArrService.Radarr, 5, listOf(31, 32))

        val request = sent.single()
        assertEquals("/api/v3/command", request.url.encodedPath)
        val body = request.bodyJson()
        assertEquals("RenameFiles", body["name"]?.jsonPrimitive?.content)
        assertEquals("5", body["movieId"]?.jsonPrimitive?.content)
        assertEquals(listOf("31", "32"), body["files"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `automaticSearch posts the right command per target`() = runTest {
        handler = { HttpStatusCode.Created to """{"id":1}""" }
        val ds = dataSource()

        ds.automaticSearch(settings, SearchTarget.Movie(5))
        ds.automaticSearch(settings, SearchTarget.Series(9))
        ds.automaticSearch(settings, SearchTarget.Season(9, 2))
        ds.automaticSearch(settings, SearchTarget.Episodes(listOf(21, 22)))

        val bodies = sent.map { it.bodyJson() }
        assertEquals("MoviesSearch", bodies[0]["name"]?.jsonPrimitive?.content)
        assertEquals(listOf("5"), bodies[0]["movieIds"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("SeriesSearch", bodies[1]["name"]?.jsonPrimitive?.content)
        assertEquals("9", bodies[1]["seriesId"]?.jsonPrimitive?.content)
        assertEquals("SeasonSearch", bodies[2]["name"]?.jsonPrimitive?.content)
        assertEquals("2", bodies[2]["seasonNumber"]?.jsonPrimitive?.content)
        assertEquals("EpisodeSearch", bodies[3]["name"]?.jsonPrimitive?.content)
        assertEquals(listOf("21", "22"), bodies[3]["episodeIds"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `releases queries by query type and parses the results`() = runTest {
        handler = { HttpStatusCode.OK to """[{"guid":"g1","indexerId":2,"title":"Movie.2024.1080p","size":1500,
            "indexer":"RARBG","seeders":50,"leechers":3,"protocol":"torrent","ageHours":12.5,
            "quality":{"quality":{"name":"WEBDL-1080p"}},
            "rejected":false,"rejections":[]}]""" }
        val ds = dataSource()

        val releases = ds.releases(settings, ReleaseQuery.Movie(5))
        ds.releases(settings, ReleaseQuery.Season(9, 2))
        ds.releases(settings, ReleaseQuery.Episode(21))

        assertEquals("5", sent[0].url.parameters["movieId"])
        assertEquals("9", sent[1].url.parameters["seriesId"])
        assertEquals("2", sent[1].url.parameters["seasonNumber"])
        assertEquals("21", sent[2].url.parameters["episodeId"])
        val release = releases.single()
        assertEquals("g1", release.guid)
        assertEquals("WEBDL-1080p", release.qualityName)
        assertEquals(50, release.seeders)
        assertEquals("RARBG", release.indexer)
    }

    @Test
    fun `grabRelease posts guid and indexer`() = runTest {
        handler = { HttpStatusCode.OK to """{"guid":"g1"}""" }

        dataSource().grabRelease(settings, "g1", 4)

        val request = sent.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v3/release", request.url.encodedPath)
        assertEquals("g1", request.bodyJson()["guid"]?.jsonPrimitive?.content)
        assertEquals("4", request.bodyJson()["indexerId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `manualImportList passes the folder and item and parses rows`() = runTest {
        handler = { HttpStatusCode.OK to """[{"path":"/downloads/a.mkv","name":"a.mkv","size":9,
            "series":{"id":9},"episodes":[{"id":21}],"quality":{"quality":{"id":1,"name":"HDTV-720p"}},
            "releaseGroup":"GRP","rejections":[]},
            {"path":"/downloads/b.mkv","name":"b.mkv","size":9,"rejections":[{"reason":"Unknown episode"}]}]""" }

        val rows = dataSource().manualImportList(settings, ArrService.Sonarr, 9, "/downloads")

        val request = sent.single()
        assertEquals("/downloads", request.url.parameters["folder"])
        assertEquals("9", request.url.parameters["seriesId"])
        assertEquals(2, rows.size)
        assertEquals(9, rows[0].matchedItemId)
        assertEquals(listOf(21), rows[0].episodeIds)
        assertEquals(1, rows[0].qualityId)
        assertEquals(listOf("Unknown episode"), rows[1].rejections)
    }

    @Test
    fun `manualImport posts the ManualImport command with the selected files`() = runTest {
        handler = { HttpStatusCode.Created to """{"id":1}""" }
        val files = listOf(
            ManualImportItem("/d/a.mkv", "a.mkv", 9, 9, listOf(21), 1, "HDTV-720p", "GRP", emptyList()),
            ManualImportItem("/d/b.mkv", "b.mkv", 9, null, listOf(22), null, null, null, emptyList()),
        )

        dataSource().manualImport(settings, ArrService.Sonarr, 9, files)

        val body = sent.single().bodyJson()
        assertEquals("ManualImport", body["name"]?.jsonPrimitive?.content)
        val sentFiles = body["files"]!!.jsonArray.map { it.jsonObject }
        assertEquals("9", sentFiles[0]["seriesId"]?.jsonPrimitive?.content)
        assertEquals(listOf("21"), sentFiles[0]["episodeIds"]!!.jsonArray.map { it.jsonPrimitive.content })
        // An unmatched row still imports against the open item.
        assertEquals("9", sentFiles[1]["seriesId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a rejected call throws`() = runTest {
        handler = { HttpStatusCode.Unauthorized to "" }

        assertFailsWith<Exception> {
            dataSource().movies(settings)
        }
    }
}
