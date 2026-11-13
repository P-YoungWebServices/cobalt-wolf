package com.pyoungws.cobaltwolf.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.cobaltwolf.shared.arrs.ArrEpisode
import com.pyoungws.cobaltwolf.shared.arrs.ArrMediaFile
import com.pyoungws.cobaltwolf.shared.arrs.ArrMediaRepository
import com.pyoungws.cobaltwolf.shared.arrs.ArrMovie
import com.pyoungws.cobaltwolf.shared.arrs.ArrMovieEdit
import com.pyoungws.cobaltwolf.shared.arrs.ArrQualityProfile
import com.pyoungws.cobaltwolf.shared.arrs.ArrRelease
import com.pyoungws.cobaltwolf.shared.arrs.ArrRenamePreview
import com.pyoungws.cobaltwolf.shared.arrs.ArrRootFolder
import com.pyoungws.cobaltwolf.shared.arrs.ArrSeason
import com.pyoungws.cobaltwolf.shared.arrs.ArrSeries
import com.pyoungws.cobaltwolf.shared.arrs.ArrSeriesEdit
import com.pyoungws.cobaltwolf.shared.arrs.ArrService
import com.pyoungws.cobaltwolf.shared.arrs.ArrTag
import com.pyoungws.cobaltwolf.shared.arrs.ManualImportItem
import com.pyoungws.cobaltwolf.shared.arrs.ReleaseQuery
import com.pyoungws.cobaltwolf.shared.arrs.SearchTarget
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The editable fields shown in the form; [tagIds] references [ArrTag.id]. */
data class MediaEditForm(
    val monitored: Boolean = false,
    val qualityProfileId: Int = 0,
    val rootFolderPath: String = "",
    val tagIds: Set<Int> = emptySet(),
)

/** A series season with its episodes and UI expansion state. */
data class SeasonState(
    val season: ArrSeason,
    val expanded: Boolean = false,
    val episodes: List<ArrEpisode> = emptyList(),
)

/** The rename preview dialog; only [Preview] can be confirmed. */
sealed interface RenameState {
    data object Loading : RenameState
    data class Preview(val items: List<ArrRenamePreview>) : RenameState
    data class Failed(val error: LoadError) : RenameState
}

/** The interactive search dialog: indexer releases with a per-release grab action. */
data class ReleaseSearchState(
    val title: String,
    val query: ReleaseQuery,
    val loading: Boolean = true,
    val releases: List<ArrRelease> = emptyList(),
    val error: LoadError? = null,
    /** Guid currently being grabbed. */
    val grabbing: String? = null,
    /** Guids grabbed in this session of the dialog. */
    val grabbed: Set<String> = emptySet(),
)

/** The manual-import dialog: a folder field plus the selectable files the service listed. */
data class ManualImportState(
    val folder: String = "",
    val loading: Boolean = false,
    /** Null until the first successful listing; keeps the last list after a failed reload. */
    val files: List<ManualImportItem>? = null,
    val selectedPaths: Set<String> = emptySet(),
    val error: LoadError? = null,
    val importing: Boolean = false,
    val done: Boolean = false,
)

data class MediaDetailUiState(
    val loading: Boolean = true,
    val error: LoadError? = null,
    val title: String = "",
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val path: String = "",
    val sizeOnDisk: Long? = null,
    val hasFile: Boolean = false,
    /** The form as last loaded or saved; [form] is what the user edits. */
    val savedForm: MediaEditForm? = null,
    val form: MediaEditForm? = null,
    val qualityProfiles: List<ArrQualityProfile> = emptyList(),
    val rootFolders: List<ArrRootFolder> = emptyList(),
    val allTags: List<ArrTag> = emptyList(),
    val seasons: List<SeasonState> = emptyList(),
    val files: List<ArrMediaFile> = emptyList(),
    /** Set once the item itself was deleted; the screen navigates back. */
    val deleted: Boolean = false,
    val action: AdminActionStatus? = null,
    val rename: RenameState? = null,
    val releaseSearch: ReleaseSearchState? = null,
    val manualImport: ManualImportState? = null,
) {
    val dirty: Boolean get() = form != null && form != savedForm
}

/**
 * The admin edit/manage screen for one Radarr movie or Sonarr series: monitored flag,
 * quality profile, root folder and tags; per-season and per-episode monitoring for
 * series; rename preview/apply; automatic and interactive searches; manual import;
 * file and item deletion. All destructive calls run through [action] so the UI can
 * show progress and errors — the screen gates them behind confirmation dialogs.
 */
class MediaDetailViewModel(
    private val service: ArrService,
    private val itemId: Int,
    private val repository: ArrMediaRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MediaDetailUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    // Form editing

    fun onMonitoredChange(monitored: Boolean) = editForm { it.copy(monitored = monitored) }

    fun onQualityProfileChange(profileId: Int) = editForm { it.copy(qualityProfileId = profileId) }

    fun onRootFolderChange(path: String) = editForm { it.copy(rootFolderPath = path) }

    fun onTagToggled(tagId: Int) = editForm { form ->
        form.copy(tagIds = if (tagId in form.tagIds) form.tagIds - tagId else form.tagIds + tagId)
    }

    /** Creates a new tag on the service and selects it in the form on success. */
    fun createTag(label: String) = runAction(TAG_KEY, { tag: ArrTag ->
        _uiState.update { state ->
            state.copy(
                allTags = (state.allTags + tag).distinctBy { it.id },
                form = state.form?.let { it.copy(tagIds = it.tagIds + tag.id) },
            )
        }
    }) {
        repository.createTag(service, label.trim())
    }

    /** Saves the form fields (and nothing else) to the service. */
    fun save() {
        val form = _uiState.value.form ?: return
        runAction(SAVE_KEY, ::applySaved) {
            when (service) {
                ArrService.Radarr -> repository.updateMovie(
                    ArrMovieEdit(itemId, form.monitored, form.qualityProfileId, rootFolderOrNull(form), form.tagIds.toList()),
                )
                ArrService.Sonarr -> repository.updateSeries(
                    ArrSeriesEdit(itemId, form.monitored, form.qualityProfileId, rootFolderOrNull(form), form.tagIds.toList(), currentSeasons()),
                )
                else -> error("Media management is not available for $service")
            }
        }
    }

    private fun applySaved(model: Any) {
        _uiState.update { state ->
            val (title, subtitle) = titleSubtitle(model)
            state.copy(
                title = title,
                subtitle = subtitle,
                savedForm = state.form,
                seasons = (model as? ArrSeries)?.let { refreshedSeasons(it.seasons, state.seasons) } ?: state.seasons,
            )
        }
    }

    // Season and episode monitoring (series)

    /** Opens or closes a season's episode list. */
    fun toggleSeasonExpanded(seasonNumber: Int) = _uiState.update { state ->
        state.copy(seasons = state.seasons.map {
            if (it.season.seasonNumber == seasonNumber) it.copy(expanded = !it.expanded) else it
        })
    }

    /** Applies immediately; season monitoring is a property of the stored series. */
    fun setSeasonMonitored(seasonNumber: Int, monitored: Boolean) {
        val form = _uiState.value.savedForm ?: return
        val seasons = currentSeasons().map {
            if (it.seasonNumber == seasonNumber) it.copy(monitored = monitored) else it
        }
        runAction("$SEASON_KEY$seasonNumber", { updated: ArrSeries ->
            _uiState.update { it.copy(seasons = refreshedSeasons(updated.seasons, it.seasons)) }
        }) {
            repository.updateSeries(
                ArrSeriesEdit(itemId, form.monitored, form.qualityProfileId, rootFolderOrNull(form), form.tagIds.toList(), seasons),
            )
        }
    }

    fun setEpisodeMonitored(episode: ArrEpisode) {
        runAction("$EPISODE_KEY${episode.id}", { _ ->
            _uiState.update { state ->
                state.copy(seasons = state.seasons.map { season ->
                    season.copy(episodes = season.episodes.map {
                        if (it.id == episode.id) it.copy(monitored = !episode.monitored) else it
                    })
                })
            }
        }) {
            repository.setEpisodesMonitored(listOf(episode.id), !episode.monitored)
        }
    }

    // Files

    /** Deletes one tracked file; the UI asks for confirmation first. */
    fun deleteFile(fileId: Int) = runAction("$DELETE_FILE_KEY$fileId", { _ ->
        _uiState.update { state ->
            state.copy(files = state.files.filterNot { it.id == fileId })
        }
        refreshFiles()
    }) {
        repository.deleteFile(service, fileId)
    }

    /** Deletes the movie/series; [deleteFiles] also removes the files from disk. */
    fun deleteItem(deleteFiles: Boolean) = runAction(DELETE_KEY, { _ ->
        _uiState.update { it.copy(deleted = true) }
    }) {
        repository.deleteItem(service, itemId, deleteFiles)
    }

    // Rename

    /** Fetches the rename proposals and opens the preview dialog. */
    fun previewRename() {
        _uiState.update { it.copy(rename = RenameState.Loading) }
        viewModelScope.launch {
            val next = when (val outcome = repository.renamePreview(service, itemId)) {
                is Outcome.Success -> RenameState.Preview(outcome.value)
                is Outcome.Failure -> RenameState.Failed(outcome.error)
            }
            _uiState.update { it.copy(rename = next) }
        }
    }

    /** Applies every proposed rename shown in the preview. */
    fun confirmRename() {
        val items = (_uiState.value.rename as? RenameState.Preview)?.items ?: return
        runAction(RENAME_APPLY_KEY, { _ ->
            _uiState.update { it.copy(rename = null) }
            refreshFiles()
        }) {
            repository.applyRename(service, itemId, items.map { it.fileId })
        }
    }

    fun dismissRename() {
        if (_uiState.value.action is AdminActionStatus.Running) return
        _uiState.update { it.copy(rename = null) }
    }

    // Searches

    /** Queues an automatic search on the service. */
    fun automaticSearch(target: SearchTarget) = runAction("$SEARCH_KEY$target") {
        repository.automaticSearch(target)
    }

    /** Opens the interactive search dialog and loads the releases for [query]. */
    fun openReleaseSearch(query: ReleaseQuery, title: String) {
        _uiState.update { it.copy(releaseSearch = ReleaseSearchState(title, query)) }
        viewModelScope.launch {
            val outcome = repository.releases(query)
            _uiState.update { state ->
                state.copy(releaseSearch = state.releaseSearch?.let { search ->
                    when (outcome) {
                        is Outcome.Success -> search.copy(
                            loading = false,
                            releases = outcome.value.sortedByDescending { it.seeders ?: 0 },
                            error = null,
                        )
                        is Outcome.Failure -> search.copy(loading = false, error = outcome.error)
                    }
                })
            }
        }
    }

    /** Grabs one release listed in the interactive search. */
    fun grab(release: ArrRelease) {
        val search = _uiState.value.releaseSearch ?: return
        runAction("$GRAB_KEY${release.guid}", { _ ->
            _uiState.update { state ->
                state.copy(releaseSearch = state.releaseSearch?.let {
                    it.copy(grabbing = null, grabbed = it.grabbed + release.guid)
                })
            }
        }) {
            _uiState.update { state ->
                state.copy(releaseSearch = state.releaseSearch?.copy(grabbing = release.guid))
            }
            repository.grabRelease(search.query, release.guid, release.indexerId).also { outcome ->
                if (outcome is Outcome.Failure) {
                    _uiState.update { state ->
                        state.copy(releaseSearch = state.releaseSearch?.copy(grabbing = null))
                    }
                }
            }
        }
    }

    fun closeReleaseSearch() {
        _uiState.update { it.copy(releaseSearch = null) }
    }

    // Manual import

    fun openManualImport() {
        _uiState.update { it.copy(manualImport = ManualImportState(folder = it.form?.rootFolderPath ?: it.path)) }
    }

    fun onImportFolderChange(folder: String) = _uiState.update { state ->
        state.copy(manualImport = state.manualImport?.copy(folder = folder, error = null))
    }

    /** Lists the files the service can import out of the entered folder. */
    fun loadImportFiles() {
        val folder = _uiState.value.manualImport?.folder?.trim().orEmpty()
        if (folder.isEmpty()) return
        _uiState.update { state -> state.copy(manualImport = state.manualImport?.copy(loading = true, error = null)) }
        viewModelScope.launch {
            val outcome = repository.manualImportList(service, itemId, folder)
            _uiState.update { state ->
                state.copy(manualImport = state.manualImport?.let { current ->
                    when (outcome) {
                        is Outcome.Success -> current.copy(
                            loading = false,
                            files = outcome.value,
                            selectedPaths = outcome.value.filter { it.rejections.isEmpty() }.map { it.path }.toSet(),
                            error = null,
                        )
                        is Outcome.Failure -> current.copy(loading = false, error = outcome.error)
                    }
                })
            }
        }
    }

    fun toggleImportSelection(path: String) = _uiState.update { state ->
        state.copy(manualImport = state.manualImport?.let { current ->
            current.copy(selectedPaths = if (path in current.selectedPaths) current.selectedPaths - path else current.selectedPaths + path)
        })
    }

    /** Queues the manual import of the selected files (copied, not moved). */
    fun confirmImport() {
        val current = _uiState.value.manualImport ?: return
        val selected = current.files.orEmpty().filter { it.path in current.selectedPaths }
        if (selected.isEmpty()) return
        _uiState.update { it.copy(manualImport = it.manualImport?.copy(importing = true)) }
        viewModelScope.launch {
            val outcome = repository.manualImport(service, itemId, selected)
            _uiState.update { state ->
                state.copy(manualImport = state.manualImport?.let {
                    when (outcome) {
                        is Outcome.Success -> it.copy(importing = false, done = true, error = null)
                        is Outcome.Failure -> it.copy(importing = false, error = outcome.error)
                    }
                })
            }
        }
    }

    fun closeManualImport() {
        if (_uiState.value.manualImport?.importing == true) return
        _uiState.update { it.copy(manualImport = null) }
    }

    fun consumeAction() = _uiState.update { it.copy(action = null) }

    // Internals

    private fun editForm(transform: (MediaEditForm) -> MediaEditForm) = _uiState.update { state ->
        state.copy(form = state.form?.let(transform))
    }

    private fun currentSeasons(): List<ArrSeason> = _uiState.value.seasons.map { it.season }

    /** The saved root folder, or null when unchanged (so the update leaves paths alone). */
    private fun rootFolderOrNull(form: MediaEditForm): String? =
        form.rootFolderPath.takeIf { it.isNotBlank() }

    private fun refreshedSeasons(updated: List<ArrSeason>, previous: List<SeasonState>): List<SeasonState> =
        updated.map { season ->
            val old = previous.firstOrNull { it.season.seasonNumber == season.seasonNumber }
            SeasonState(season, expanded = old?.expanded ?: false, episodes = old?.episodes.orEmpty())
        }

    private fun titleSubtitle(model: Any): Pair<String, String?> = when (model) {
        is ArrMovie -> model.title to model.year?.toString()
        is ArrSeries -> model.title to model.year?.toString()
        else -> "" to null
    }

    private fun refreshFiles() {
        viewModelScope.launch {
            when (val outcome = repository.files(service, itemId)) {
                is Outcome.Success -> _uiState.update { it.copy(files = outcome.value) }
                is Outcome.Failure -> Unit
            }
        }
    }

    private suspend fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        when (service) {
            ArrService.Radarr -> loadMovie()
            ArrService.Sonarr -> loadSeries()
            else -> Unit
        }
    }

    private suspend fun loadMovie() {
        val movie = fetch { repository.movie(itemId) } ?: return
        val profiles = fetch { repository.qualityProfiles(service) } ?: return
        val roots = fetch { repository.rootFolders(service) } ?: return
        val tags = fetch { repository.tags(service) } ?: return
        val files = fetch { repository.files(service, itemId) } ?: return
        val form = MediaEditForm(movie.monitored, movie.qualityProfileId, movie.rootFolder(), movie.tagIds.toSet())
        _uiState.update {
            it.copy(
                loading = false, error = null,
                title = movie.title, subtitle = movie.year?.toString(), posterUrl = movie.posterUrl,
                path = movie.path, sizeOnDisk = movie.sizeOnDisk, hasFile = movie.hasFile,
                form = form, savedForm = form,
                qualityProfiles = profiles, rootFolders = roots, allTags = tags,
                files = files,
            )
        }
    }

    private suspend fun loadSeries() {
        val series = fetch { repository.series(itemId) } ?: return
        val profiles = fetch { repository.qualityProfiles(service) } ?: return
        val roots = fetch { repository.rootFolders(service) } ?: return
        val tags = fetch { repository.tags(service) } ?: return
        val episodes = fetch { repository.episodes(itemId) } ?: return
        val files = fetch { repository.files(service, itemId) } ?: return
        val form = MediaEditForm(series.monitored, series.qualityProfileId, series.rootFolder(), series.tagIds.toSet())
        val expanded = _uiState.value.seasons.mapNotNull { if (it.expanded) it.season.seasonNumber else null }.toSet()
        _uiState.update {
            it.copy(
                loading = false, error = null,
                title = series.title, subtitle = series.year?.toString(), posterUrl = series.posterUrl,
                path = series.path, sizeOnDisk = null, hasFile = series.seasons.any { s -> s.episodeFileCount > 0 },
                form = form, savedForm = form,
                qualityProfiles = profiles, rootFolders = roots, allTags = tags,
                seasons = refreshedSeasons(series.seasons, emptyList()).map { season ->
                    season.copy(
                        expanded = season.season.seasonNumber in expanded,
                        episodes = episodes.filter { e -> e.seasonNumber == season.season.seasonNumber }
                            .sortedBy { e -> e.episodeNumber },
                    )
                },
                files = files,
            )
        }
    }

    private fun ArrMovie.rootFolder(): String = rootFolderPath ?: path.substringBeforeLast('/')
    private fun ArrSeries.rootFolder(): String = rootFolderPath ?: path.substringBeforeLast('/')

    /** Runs [block], mapping the outcome to an error state; returns null on failure. */
    private suspend fun <T> fetch(block: suspend () -> Outcome<T>): T? = when (val outcome = block()) {
        is Outcome.Success -> outcome.value
        is Outcome.Failure -> {
            _uiState.update { it.copy(loading = false, error = outcome.error) }
            null
        }
    }

    private fun <T> runAction(key: String, onSuccess: (T) -> Unit = {}, block: suspend () -> Outcome<T>) {
        if (_uiState.value.action is AdminActionStatus.Running) return
        _uiState.update { it.copy(action = AdminActionStatus.Running(key)) }
        viewModelScope.launch {
            val status = when (val outcome = block()) {
                is Outcome.Success -> {
                    onSuccess(outcome.value)
                    AdminActionStatus.Succeeded(key)
                }
                is Outcome.Failure -> AdminActionStatus.Failed(key, outcome.error)
            }
            _uiState.update { it.copy(action = status) }
        }
    }

    companion object {
        const val SAVE_KEY = "save"
        const val DELETE_KEY = "delete-item"
        const val DELETE_FILE_KEY = "delete-file:"
        const val RENAME_APPLY_KEY = "rename:apply"
        const val SEARCH_KEY = "search:"
        const val GRAB_KEY = "grab:"
        const val SEASON_KEY = "season:"
        const val EPISODE_KEY = "episode:"
        const val TAG_KEY = "tag:create"
    }
}
