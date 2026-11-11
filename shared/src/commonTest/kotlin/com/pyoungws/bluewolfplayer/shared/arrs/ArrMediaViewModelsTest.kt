package com.pyoungws.bluewolfplayer.shared.arrs

import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.presentation.AdminActionStatus
import com.pyoungws.bluewolfplayer.shared.presentation.ManualImportState
import com.pyoungws.bluewolfplayer.shared.presentation.MediaDetailUiState
import com.pyoungws.bluewolfplayer.shared.presentation.MediaDetailViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.MediaManagerUiState
import com.pyoungws.bluewolfplayer.shared.presentation.MediaManagerViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.RenameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArrMediaViewModelsTest {

    private val settingsStore = InMemoryArrSettingsStore()
    private lateinit var settingsRepository: ArrSettingsRepository
    private lateinit var dataSource: FakeArrMediaDataSource
    private lateinit var repository: ArrMediaRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        settingsRepository = ArrSettingsRepository(settingsStore)
        dataSource = FakeArrMediaDataSource()
        repository = ArrMediaRepository(dataSource, settingsRepository)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun configure(service: ArrService = ArrService.Radarr) =
        settingsRepository.update(service, "${service.name.lowercase()}.example.com", "key")

    // MediaManagerViewModel

    @Test
    fun `the manager shows nothing without a configured service`() = runTest {
        val viewModel = MediaManagerViewModel(repository)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(emptyList(), state.configuredServices)
        assertFalse(state.isLoading)
        assertEquals(emptyList(), state.rows)
    }

    @Test
    fun `the manager lists movies for the first configured service`() = runTest {
        configure()
        dataSource.movieItems = listOf(arrMovie(id = 5, title = "Beta"), arrMovie(id = 6, title = "Alpha"))

        val viewModel = MediaManagerViewModel(repository)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf("Alpha", "Beta"), state.rows.map { it.title })
        assertEquals(listOf(ArrService.Radarr), state.configuredServices)
    }

    @Test
    fun `switching the service tab loads its library`() = runTest {
        configure(ArrService.Radarr)
        configure(ArrService.Sonarr)
        dataSource.seriesItems = listOf(arrSeries(id = 9, title = "The Show"))

        val viewModel = MediaManagerViewModel(repository)
        runCurrent()
        viewModel.selectService(ArrService.Sonarr)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(ArrService.Sonarr, state.service)
        assertEquals(listOf("The Show"), state.rows.map { it.title })
        assertTrue("seriesList()" in dataSource.calls)
    }

    @Test
    fun `the query filters the listed rows`() = runTest {
        configure()
        dataSource.movieItems = listOf(arrMovie(id = 5, title = "Alpha"), arrMovie(id = 6, title = "Beta"))

        val viewModel = MediaManagerViewModel(repository)
        runCurrent()
        viewModel.onQueryChange("alp")

        assertEquals(listOf("Alpha"), viewModel.uiState.value.filteredRows.map { it.title })
    }

    @Test
    fun `a failed load surfaces the error`() = runTest {
        configure()
        dataSource.error = IOException("down")

        val viewModel = MediaManagerViewModel(repository)
        runCurrent()

        assertEquals(LoadError.ServerUnreachable, viewModel.uiState.value.error)
    }

    // MediaDetailViewModel: loading and the edit form

    private fun movieDetail(dataSource: FakeArrMediaDataSource): MediaDetailViewModel {
        dataSource.moviesById[5] = arrMovie(id = 5, tagIds = listOf(1))
        dataSource.tagItems = listOf(ArrTag(1, "kids"), ArrTag(2, "anime"))
        return MediaDetailViewModel(ArrService.Radarr, 5, repository)
    }

    @Test
    fun `the movie form loads with the stored values`() = runTest {
        configure()
        val viewModel = movieDetail(dataSource)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals("Test Movie", state.title)
        val form = state.form!!
        assertEquals(true, form.monitored)
        assertEquals(7, form.qualityProfileId)
        assertEquals("/media/movies", form.rootFolderPath)
        assertEquals(setOf(1), form.tagIds)
        assertFalse(state.dirty)
    }

    @Test
    fun `editing the form marks it dirty and save persists it`() = runTest {
        configure()
        val viewModel = movieDetail(dataSource)
        runCurrent()

        viewModel.onMonitoredChange(false)
        viewModel.onQualityProfileChange(2)
        viewModel.onTagToggled(2)
        assertTrue(viewModel.uiState.value.dirty)

        viewModel.save()
        runCurrent()

        val call = dataSource.calls.filterIsInstance<String>().first { it.startsWith("updateMovie") }
        assertTrue("monitored=false" in call)
        assertTrue("qualityProfileId=2" in call)
        assertTrue("tagIds=[1, 2]" in call)
        assertFalse(viewModel.uiState.value.dirty)
        assertIs<AdminActionStatus.Succeeded>(viewModel.uiState.value.action)
    }

    @Test
    fun `a failed save reports the api error`() = runTest {
        configure()
        val viewModel = movieDetail(dataSource)
        runCurrent()

        dataSource.error = IOException("down")
        viewModel.onMonitoredChange(false)
        viewModel.save()
        runCurrent()

        val action = viewModel.uiState.value.action
        assertIs<AdminActionStatus.Failed>(action)
        assertEquals(LoadError.ServerUnreachable, action.error)
        assertTrue(viewModel.uiState.value.dirty)
    }

    // MediaDetailViewModel: series seasons and episodes

    private fun seriesDetail(dataSource: FakeArrMediaDataSource): MediaDetailViewModel {
        dataSource.seriesById[9] = arrSeries(id = 9)
        dataSource.episodeItems = listOf(
            arrEpisode(21, seasonNumber = 1, episodeNumber = 1),
            arrEpisode(22, seasonNumber = 1, episodeNumber = 2, monitored = false),
            arrEpisode(23, seasonNumber = 2, episodeNumber = 1),
        )
        return MediaDetailViewModel(ArrService.Sonarr, 9, repository)
    }

    @Test
    fun `the series screen groups episodes under their seasons`() = runTest {
        configure(ArrService.Sonarr)
        val viewModel = seriesDetail(dataSource)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf(1, 2), state.seasons.map { it.season.seasonNumber })
        val season1 = state.seasons[0]
        assertEquals(listOf(21, 22), season1.episodes.map { it.id })
        assertEquals(listOf(23), state.seasons[1].episodes.map { it.id })
    }

    @Test
    fun `toggling a season updates monitoring on the service`() = runTest {
        configure(ArrService.Sonarr)
        val viewModel = seriesDetail(dataSource)
        runCurrent()

        viewModel.setSeasonMonitored(2, true)
        runCurrent()

        val update = dataSource.calls.first { it.startsWith("updateSeries") }
        assertTrue("seasonNumber=2, monitored=true" in update)
        assertEquals(true, viewModel.uiState.value.seasons[1].season.monitored)
    }

    @Test
    fun `toggling an episode uses the bulk monitor endpoint`() = runTest {
        configure(ArrService.Sonarr)
        val viewModel = seriesDetail(dataSource)
        runCurrent()

        val episode = viewModel.uiState.value.seasons[0].episodes[1]
        viewModel.setEpisodeMonitored(episode)
        runCurrent()

        assertTrue("setEpisodesMonitored([22],true)" in dataSource.calls)
        assertEquals(true, viewModel.uiState.value.seasons[0].episodes[1].monitored)
    }

    // Files and deletion

    @Test
    fun `deleting a file removes it from the list`() = runTest {
        configure()
        dataSource.fileItems = listOf(arrFile(31), arrFile(32))
        val viewModel = movieDetail(dataSource)
        runCurrent()

        viewModel.deleteFile(31)
        runCurrent()

        assertTrue("deleteFile(Radarr,31)" in dataSource.calls)
        assertEquals(listOf(32), viewModel.uiState.value.files.map { it.id })
    }

    @Test
    fun `deleting the item with files marks the screen deleted`() = runTest {
        configure()
        val viewModel = movieDetail(dataSource)
        runCurrent()

        viewModel.deleteItem(deleteFiles = true)
        runCurrent()

        assertTrue("deleteItem(Radarr,5,true)" in dataSource.calls)
        assertTrue(viewModel.uiState.value.deleted)
    }

    // Rename

    @Test
    fun `rename preview then apply sends the file ids`() = runTest {
        configure()
        dataSource.renameItems = listOf(ArrRenamePreview(31, "old1", "new1"), ArrRenamePreview(32, "old2", "new2"))
        val viewModel = movieDetail(dataSource)
        runCurrent()

        viewModel.previewRename()
        runCurrent()
        val preview = viewModel.uiState.value.rename
        assertIs<RenameState.Preview>(preview)
        assertEquals(listOf("old1", "old2"), preview.items.map { it.existingPath })

        viewModel.confirmRename()
        runCurrent()

        assertTrue("applyRename(Radarr,5,[31, 32])" in dataSource.calls)
        assertNull(viewModel.uiState.value.rename)
    }

    @Test
    fun `a failed rename preview surfaces the error`() = runTest {
        configure()
        val viewModel = movieDetail(dataSource)
        runCurrent()

        dataSource.error = IOException("down")
        viewModel.previewRename()
        runCurrent()

        assertIs<RenameState.Failed>(viewModel.uiState.value.rename)
    }

    // Searches

    @Test
    fun `automatic search runs the movie command`() = runTest {
        configure()
        val viewModel = movieDetail(dataSource)
        runCurrent()

        viewModel.automaticSearch(SearchTarget.Movie(5))
        runCurrent()

        assertTrue("automaticSearch(Movie(movieId=5))" in dataSource.calls)
        assertIs<AdminActionStatus.Succeeded>(viewModel.uiState.value.action)
    }

    @Test
    fun `interactive search loads releases sorted by seeders and grab marks it`() = runTest {
        configure()
        dataSource.releaseItems = listOf(arrRelease("g1", seeders = 5), arrRelease("g2", seeders = 99))
        val viewModel = movieDetail(dataSource)
        runCurrent()

        viewModel.openReleaseSearch(ReleaseQuery.Movie(5), "Releases")
        runCurrent()

        val search = viewModel.uiState.value.releaseSearch!!
        assertFalse(search.loading)
        assertEquals(listOf("g2", "g1"), search.releases.map { it.guid })

        viewModel.grab(search.releases[0])
        runCurrent()

        val updated = viewModel.uiState.value.releaseSearch!!
        assertEquals(setOf("g2"), updated.grabbed)
        assertTrue("grabRelease(g2,3)" in dataSource.calls)
    }

    @Test
    fun `a failed release grab leaves the release ungrabs`() = runTest {
        configure()
        dataSource.releaseItems = listOf(arrRelease("g1"))
        val viewModel = movieDetail(dataSource)
        runCurrent()
        viewModel.openReleaseSearch(ReleaseQuery.Movie(5), "Releases")
        runCurrent()

        dataSource.error = IOException("down")
        viewModel.grab(viewModel.uiState.value.releaseSearch!!.releases.single())
        runCurrent()

        assertNull(viewModel.uiState.value.releaseSearch?.grabbing)
        assertEquals(emptySet(), viewModel.uiState.value.releaseSearch?.grabbed)
        assertIs<AdminActionStatus.Failed>(viewModel.uiState.value.action)
    }

    // Manual import

    @Test
    fun `manual import lists files and imports the selected ones`() = runTest {
        configure(ArrService.Sonarr)
        dataSource.importItems = listOf(
            ManualImportItem("/dl/a.mkv", "a.mkv", 9, 9, listOf(21), 1, "HDTV-720p", null, emptyList()),
            ManualImportItem("/dl/b.mkv", "b.mkv", 9, null, emptyList(), null, null, null, listOf("Unknown episode")),
        )
        val viewModel = seriesDetail(dataSource)
        runCurrent()

        viewModel.openManualImport()
        assertEquals("/media/tv", viewModel.uiState.value.manualImport?.folder)

        viewModel.onImportFolderChange("/dl")
        viewModel.loadImportFiles()
        runCurrent()

        val import = viewModel.uiState.value.manualImport!!
        assertEquals(2, import.files!!.size)
        // The importable row is pre-selected, the rejected row is not.
        assertEquals(setOf("/dl/a.mkv"), import.selectedPaths)

        viewModel.confirmImport()
        runCurrent()

        assertTrue("manualImport(Sonarr,9,[/dl/a.mkv])" in dataSource.calls)
        assertTrue(viewModel.uiState.value.manualImport!!.done)
    }

    @Test
    fun `manual import reports listing errors`() = runTest {
        configure(ArrService.Sonarr)
        val viewModel = seriesDetail(dataSource)
        runCurrent()

        viewModel.openManualImport()
        viewModel.onImportFolderChange("/dl")
        dataSource.error = IOException("down")
        viewModel.loadImportFiles()
        runCurrent()

        assertEquals(LoadError.ServerUnreachable, viewModel.uiState.value.manualImport?.error)
        assertNull(viewModel.uiState.value.manualImport?.files)
    }
}
