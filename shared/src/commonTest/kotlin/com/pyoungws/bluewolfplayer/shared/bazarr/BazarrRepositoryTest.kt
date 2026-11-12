package com.pyoungws.bluewolfplayer.shared.bazarr

import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.arrs.ArrSettingsRepository
import com.pyoungws.bluewolfplayer.shared.arrs.InMemoryArrSettingsStore
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue


class BazarrRepositoryTest {

    private val dataSource = FakeBazarrDataSource()
    private val settingsRepository = ArrSettingsRepository(InMemoryArrSettingsStore())

    private fun repository() = BazarrRepository(dataSource, settingsRepository)

    private fun configure() = settingsRepository.update(ArrService.Bazarr, "https://bazarr.example.com", "key")

    @Test
    fun `unconfigured until url and api key are set`() {
        assertFalse(repository().isConfigured)
        settingsRepository.update(ArrService.Bazarr, "https://bazarr.example.com", "")
        assertFalse(repository().isConfigured)
        configure()
        assertTrue(repository().isConfigured)
    }

    @Test
    fun `wanted fails cleanly when the service is not configured`() = runTest {
        val result = repository().wanted()

        val failure = assertIs<Outcome.Failure>(result)
        assertIs<LoadError.Unexpected>(failure.error)
        assertEquals(emptyList(), dataSource.calls)
    }

    @Test
    fun `wanted merges the movie and episode lists`() = runTest {
        configure()
        dataSource.movies = listOf(WantedMovie("Film", listOf(MissingSubtitle("English"))))
        dataSource.episodes = listOf(WantedEpisode("Show", "S02E03", "The One", listOf(MissingSubtitle("French", forced = true))))

        val result = repository().wanted()

        val wanted = assertIs<Outcome.Success<BazarrWanted>>(result).value
        assertEquals(2, wanted.total)
        assertEquals("Film", wanted.movies.single().title)
        assertEquals("Show", wanted.episodes.single().seriesTitle)
        assertEquals("S02E03", wanted.episodes.single().episodeCode)
        assertEquals(setOf("wantedMovies", "wantedEpisodes"), dataSource.calls.toSet())
    }

    @Test
    fun `a rejected api key surfaces as a failure`() = runTest {
        configure()
        dataSource.error = InvalidStatusException(401)

        val result = repository().wanted()

        assertIs<Outcome.Failure>(result)
    }

    @Test
    fun `search and download grabs the best candidate per missing language`() = runTest {
        configure()
        dataSource.movieCandidates = listOf(
            SubtitleCandidate("opensubtitles", "sub-1", score = 55, languageName = "English"),
            SubtitleCandidate("addic7ed", "sub-2", score = 80, languageName = "English"),
            SubtitleCandidate("opensubtitles", "sub-3", score = 70, languageName = "French", forced = true),
            SubtitleCandidate("opensubtitles", "sub-4", score = 99, languageName = "German"),
        )
        val movie = WantedMovie("Film", listOf(MissingSubtitle("English"), MissingSubtitle("French", forced = true)), radarrId = 42)

        val result = repository().searchAndDownload(movie)

        assertEquals(Outcome.Success(2), result)
        assertEquals(listOf("m:42:sub-2", "m:42:sub-3"), dataSource.downloads)
    }

    @Test
    fun `search and download prefers candidates matching the forced flag`() = runTest {
        configure()
        dataSource.movieCandidates = listOf(
            SubtitleCandidate("opensubtitles", "plain", score = 99, languageName = "English"),
            SubtitleCandidate("opensubtitles", "forced", score = 10, languageName = "English", forced = true),
        )
        val movie = WantedMovie("Film", listOf(MissingSubtitle("English", forced = true)), radarrId = 7)

        repository().searchAndDownload(movie)

        assertEquals(listOf("m:7:forced"), dataSource.downloads)
    }

    @Test
    fun `episode searches use the sonarr ids`() = runTest {
        configure()
        dataSource.episodeCandidates = listOf(SubtitleCandidate("opensubtitles", "ep-sub", score = 60, languageName = "English"))
        val episode = WantedEpisode("Show", "S01E01", null, listOf(MissingSubtitle("English")), sonarrSeriesId = 3, sonarrEpisodeId = 44)

        val result = repository().searchAndDownload(episode)

        assertEquals(Outcome.Success(1), result)
        assertEquals(listOf("e:44:ep-sub"), dataSource.downloads)
        assertTrue(dataSource.calls.contains("searchEpisodes(44)"))
    }

    @Test
    fun `search without ids fails cleanly`() = runTest {
        configure()
        val movie = WantedMovie("Film", listOf(MissingSubtitle("English")))
        val episode = WantedEpisode("Show", null, null, listOf(MissingSubtitle("English")))

        assertIs<Outcome.Failure>(repository().searchAndDownload(movie))
        assertIs<Outcome.Failure>(repository().searchAndDownload(episode))
        assertTrue(dataSource.calls.none { it.startsWith("search") })
    }

    @Test
    fun `search with no matching candidates downloads nothing`() = runTest {
        configure()
        dataSource.movieCandidates = listOf(SubtitleCandidate("opensubtitles", "x", score = 10, languageName = "German"))
        val movie = WantedMovie("Film", listOf(MissingSubtitle("English")), radarrId = 42)

        assertEquals(Outcome.Success(0), repository().searchAndDownload(movie))
        assertEquals(emptyList(), dataSource.downloads)
    }
}
