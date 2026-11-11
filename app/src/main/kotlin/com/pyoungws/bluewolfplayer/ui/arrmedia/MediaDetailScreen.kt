package com.pyoungws.bluewolfplayer.ui.arrmedia

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.arrs.ArrEpisode
import com.pyoungws.bluewolfplayer.shared.arrs.ArrMediaFile
import com.pyoungws.bluewolfplayer.shared.arrs.ArrRelease
import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.arrs.ArrTag
import com.pyoungws.bluewolfplayer.shared.arrs.ManualImportItem
import com.pyoungws.bluewolfplayer.shared.arrs.ReleaseQuery
import com.pyoungws.bluewolfplayer.shared.arrs.SearchTarget
import com.pyoungws.bluewolfplayer.shared.presentation.AdminActionStatus
import com.pyoungws.bluewolfplayer.shared.presentation.ManualImportState
import com.pyoungws.bluewolfplayer.shared.presentation.MediaDetailUiState
import com.pyoungws.bluewolfplayer.shared.presentation.MediaDetailViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.ReleaseSearchState
import com.pyoungws.bluewolfplayer.shared.presentation.RenameState
import com.pyoungws.bluewolfplayer.shared.presentation.SeasonState
import com.pyoungws.bluewolfplayer.ui.common.Artwork
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.SectionTitle
import com.pyoungws.bluewolfplayer.ui.common.formatBytes
import com.pyoungws.bluewolfplayer.ui.common.formatTimestamp
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The admin edit screen for one Radarr movie or Sonarr series: monitoring, quality
 * profile, root folder and tags; per-season/episode monitoring for series; rename,
 * searches, manual import and deletion — every destructive step behind a dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(
    service: ArrService,
    itemId: Int,
    onBack: () -> Unit,
    viewModel: MediaDetailViewModel = koinViewModel(key = "media-$service-$itemId", parameters = { parametersOf(service, itemId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var deleteFileTarget by remember { mutableStateOf<Int?>(null) }
    var deleteItemOpen by remember { mutableStateOf(false) }

    // Report action outcomes; successes also carry a message to the snackbar.
    val actionSuccess = (state.action as? AdminActionStatus.Succeeded)?.let { successMessage(it.key) }
    val actionFailure = (state.action as? AdminActionStatus.Failed)?.error?.message()
    LaunchedEffect(state.action) {
        when {
            actionSuccess != null -> snackbar.showSnackbar(actionSuccess)
            actionFailure != null -> snackbar.showSnackbar(actionFailure)
            else -> null
        }?.let { viewModel.consumeAction() }
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.title.ifBlank { stringResource(R.string.media_detail_title) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
                actions = { TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingBox()
                state.error != null -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
                else -> DetailContent(
                    state = state,
                    service = service,
                    itemId = itemId,
                    viewModel = viewModel,
                    onDeleteFile = { deleteFileTarget = it },
                    onDeleteItem = { deleteItemOpen = true },
                )
            }
        }
    }

    // Rename preview
    state.rename?.let { rename ->
        RenameDialog(
            rename = rename,
            applying = (state.action as? AdminActionStatus.Running)?.key == MediaDetailViewModel.RENAME_APPLY_KEY,
            onConfirm = viewModel::confirmRename,
            onDismiss = viewModel::dismissRename,
        )
    }

    // Interactive search
    state.releaseSearch?.let { search ->
        ReleasesDialog(
            search = search,
            onGrab = viewModel::grab,
            onDismiss = viewModel::closeReleaseSearch,
        )
    }

    // Manual import
    state.manualImport?.let { import ->
        ManualImportDialog(
            import = import,
            onFolderChange = viewModel::onImportFolderChange,
            onLoad = viewModel::loadImportFiles,
            onToggle = viewModel::toggleImportSelection,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::closeManualImport,
        )
    }

    // Single-file delete confirmation
    deleteFileTarget?.let { fileId ->
        ConfirmDialog(
            title = stringResource(R.string.media_delete_file_title),
            message = stringResource(R.string.media_delete_file_message),
            confirm = stringResource(R.string.admin_delete),
            busy = (state.action as? AdminActionStatus.Running)?.key == "${MediaDetailViewModel.DELETE_FILE_KEY}$fileId",
            onConfirm = { viewModel.deleteFile(fileId); deleteFileTarget = null },
            onDismiss = { deleteFileTarget = null },
        )
    }

    // Item delete confirmation with the explicit "delete files too" choice
    if (deleteItemOpen) {
        DeleteItemDialog(
            service = service,
            busy = (state.action as? AdminActionStatus.Running)?.key == MediaDetailViewModel.DELETE_KEY,
            onConfirm = { deleteFiles -> viewModel.deleteItem(deleteFiles) },
            onDismiss = { deleteItemOpen = false },
        )
    }
}

@Composable
private fun successMessage(key: String): String = when {
    key == MediaDetailViewModel.SAVE_KEY -> stringResource(R.string.media_saved)
    key == MediaDetailViewModel.DELETE_KEY -> stringResource(R.string.media_deleted)
    key.startsWith(MediaDetailViewModel.DELETE_FILE_KEY) -> stringResource(R.string.media_file_deleted)
    key == MediaDetailViewModel.RENAME_APPLY_KEY -> stringResource(R.string.media_rename_applied)
    key.startsWith(MediaDetailViewModel.SEARCH_KEY) -> stringResource(R.string.media_search_started)
    key.startsWith(MediaDetailViewModel.GRAB_KEY) -> stringResource(R.string.media_release_grabbed)
    key.startsWith(MediaDetailViewModel.SEASON_KEY) || key.startsWith(MediaDetailViewModel.EPISODE_KEY) ->
        stringResource(R.string.media_monitoring_updated)
    key == MediaDetailViewModel.TAG_KEY -> stringResource(R.string.media_tag_created)
    else -> stringResource(R.string.media_action_done)
}

@Composable
private fun DetailContent(
    state: MediaDetailUiState,
    service: ArrService,
    itemId: Int,
    viewModel: MediaDetailViewModel,
    onDeleteFile: (Int) -> Unit,
    onDeleteItem: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "header") { Header(state) }
        item(key = "edit") { EditSection(state, viewModel) }
        item(key = "actions") { ActionsSection(state, service, itemId, viewModel) }
        if (service == ArrService.Sonarr) {
            item(key = "seasons-title") { SectionTitle(stringResource(R.string.media_seasons)) }
            items(state.seasons, key = { "season-${it.season.seasonNumber}" }) { season ->
                SeasonCard(season, state, itemId, viewModel)
            }
        }
        item(key = "files-title") { SectionTitle(stringResource(R.string.media_files)) }
        if (state.files.isEmpty()) {
            item(key = "files-empty") {
                Text(
                    stringResource(R.string.media_no_files),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            items(state.files, key = { "file-${it.id}" }) { file ->
                FileRow(file, onDelete = { onDeleteFile(file.id) })
            }
        }
        item(key = "danger") {
            TextButton(onClick = onDeleteItem, modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(if (service == ArrService.Radarr) R.string.media_delete_movie else R.string.media_delete_series),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Header(state: MediaDetailUiState) {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Artwork(state.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.width(90.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(state.title, style = MaterialTheme.typography.headlineSmall)
            state.subtitle?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(state.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.sizeOnDisk?.let { Text(formatBytes(it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun EditSection(state: MediaDetailUiState, viewModel: MediaDetailViewModel) {
    val form = state.form ?: return
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.media_monitored), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = form.monitored, onCheckedChange = viewModel::onMonitoredChange)
            }
            SelectorRow(
                label = stringResource(R.string.media_quality_profile),
                options = state.qualityProfiles.map { it.id to it.name },
                selected = form.qualityProfileId,
                onSelect = viewModel::onQualityProfileChange,
            )
            SelectorRow(
                label = stringResource(R.string.media_root_folder),
                options = state.rootFolders.map { it.path to it.path },
                selected = form.rootFolderPath,
                onSelect = viewModel::onRootFolderChange,
            )
            Text(stringResource(R.string.media_tags), style = MaterialTheme.typography.titleMedium)
            TagChips(state.allTags, selected = form.tagIds, onToggle = viewModel::onTagToggled, onCreate = viewModel::createTag)
            Button(
                onClick = viewModel::save,
                enabled = state.dirty && state.action !is AdminActionStatus.Running,
                modifier = Modifier.align(Alignment.End),
            ) {
                if ((state.action as? AdminActionStatus.Running)?.key == MediaDetailViewModel.SAVE_KEY) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                }
                Text(stringResource(R.string.admin_save))
            }
        }
    }
}

@Composable
private fun <T> SelectorRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(options.firstOrNull { it.first == selected }?.second ?: selected.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (value, name) ->
                    DropdownMenuItem(text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onSelect(value); open = false })
                }
            }
        }
    }
}

@Composable
private fun TagChips(allTags: List<ArrTag>, selected: Set<Int>, onToggle: (Int) -> Unit, onCreate: (String) -> Unit) {
    var newTag by rememberSaveable { mutableStateOf("") }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        allTags.forEach { tag ->
            FilterChip(selected = tag.id in selected, onClick = { onToggle(tag.id) }, label = { Text(tag.label) })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = newTag,
            onValueChange = { newTag = it },
            label = { Text(stringResource(R.string.media_new_tag)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onCreate(newTag); newTag = "" }, enabled = newTag.isNotBlank()) {
            Text(stringResource(R.string.admin_create))
        }
    }
}

@Composable
private fun ActionsSection(state: MediaDetailUiState, service: ArrService, itemId: Int, viewModel: MediaDetailViewModel) {
    val running = (state.action as? AdminActionStatus.Running)?.key
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (service == ArrService.Radarr) {
                ActionButton(
                    label = stringResource(R.string.media_auto_search),
                    busy = running?.startsWith(MediaDetailViewModel.SEARCH_KEY) == true,
                    enabled = running == null,
                ) { viewModel.automaticSearch(SearchTarget.Movie(itemId)) }
                ActionButton(
                    label = stringResource(R.string.media_interactive_search),
                    busy = false,
                    enabled = true,
                ) { viewModel.openReleaseSearch(ReleaseQuery.Movie(itemId), state.title) }
            } else {
                ActionButton(
                    label = stringResource(R.string.media_auto_search_series),
                    busy = running?.startsWith(MediaDetailViewModel.SEARCH_KEY) == true,
                    enabled = running == null,
                ) { viewModel.automaticSearch(SearchTarget.Series(itemId)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                label = stringResource(R.string.media_rename_files),
                busy = state.rename is RenameState.Loading,
                enabled = state.files.isNotEmpty(),
            ) { viewModel.previewRename() }
            ActionButton(
                label = stringResource(R.string.media_manual_import),
                busy = false,
                enabled = true,
            ) { viewModel.openManualImport() }
        }
    }
}

@Composable
private fun ActionButton(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled && !busy) {
        if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(14.dp), strokeWidth = 2.dp)
        Text(label)
    }
}

@Composable
private fun SeasonCard(season: SeasonState, state: MediaDetailUiState, itemId: Int, viewModel: MediaDetailViewModel) {
    val running = (state.action as? AdminActionStatus.Running)?.key
    val seasonReleasesTitle = stringResource(R.string.media_releases_season, season.season.seasonNumber)
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (season.season.seasonNumber == 0) stringResource(R.string.detail_season_specials)
                        else stringResource(R.string.request_season_label, season.season.seasonNumber),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.media_season_counts, season.season.episodeFileCount, season.season.episodeCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = season.season.monitored,
                    onCheckedChange = { viewModel.setSeasonMonitored(season.season.seasonNumber, it) },
                    enabled = running == null,
                )
                TextButton(onClick = { viewModel.toggleSeasonExpanded(season.season.seasonNumber) }) {
                    Text(stringResource(if (season.expanded) R.string.media_hide_episodes else R.string.media_show_episodes))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { viewModel.automaticSearch(SearchTarget.Season(itemId, season.season.seasonNumber)) },
                    enabled = running == null,
                ) { Text(stringResource(R.string.media_search_season)) }
                TextButton(
                    onClick = {
                        viewModel.openReleaseSearch(
                            ReleaseQuery.Season(itemId, season.season.seasonNumber),
                            seasonReleasesTitle,
                        )
                    },
                    enabled = running == null,
                ) { Text(stringResource(R.string.media_search_releases)) }
            }
            if (season.expanded) {
                season.episodes.forEach { episode ->
                    EpisodeRow(episode, running, viewModel)
                }
                if (season.episodes.isEmpty()) {
                    Text(
                        stringResource(R.string.media_no_episodes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: ArrEpisode, running: String?, viewModel: MediaDetailViewModel) {
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${episode.label} · ${episode.title}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(if (episode.hasFile) R.string.media_has_file else R.string.media_no_file),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { viewModel.openReleaseSearch(ReleaseQuery.Episode(episode.id), episode.label) },
            enabled = running == null,
        ) { Text(stringResource(R.string.media_search)) }
        Switch(
            checked = episode.monitored,
            onCheckedChange = { viewModel.setEpisodeMonitored(episode) },
            enabled = running == null,
        )
    }
}

@Composable
private fun FileRow(file: ArrMediaFile, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(file.relativePath, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        formatBytes(file.size),
                        file.qualityName,
                        file.dateAdded?.let(::formatTimestamp),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.admin_delete), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun RenameDialog(rename: RenameState, applying: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!applying) onDismiss() },
        title = { Text(stringResource(R.string.media_rename_title)) },
        text = {
            when (rename) {
                RenameState.Loading -> LoadingBox(Modifier.fillMaxWidth().heightIn(min = 80.dp))
                is RenameState.Failed -> Text(rename.error.message(), color = MaterialTheme.colorScheme.error)
                is RenameState.Preview -> if (rename.items.isEmpty()) {
                    Text(stringResource(R.string.media_rename_nothing))
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(rename.items, key = { it.fileId }) { item ->
                            Column {
                                Text(item.existingPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("→ ${item.newPath}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (rename is RenameState.Preview && rename.items.isNotEmpty()) {
                TextButton(onClick = onConfirm, enabled = !applying) {
                    if (applying) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.media_rename_apply, rename.items.size))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !applying) { Text(stringResource(R.string.home_sign_out_cancel)) } },
    )
}

@Composable
private fun ReleasesDialog(
    search: ReleaseSearchState,
    onGrab: (ArrRelease) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(search.title) },
        text = {
            when {
                search.loading -> LoadingBox(Modifier.fillMaxWidth().heightIn(min = 120.dp))
                search.error != null -> Text(search.error!!.message(), color = MaterialTheme.colorScheme.error)
                search.releases.isEmpty() -> Text(stringResource(R.string.media_no_releases))
                else -> LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(search.releases, key = { it.guid }) { release ->
                        ReleaseRow(release, grabbing = search.grabbing == release.guid, grabbed = release.guid in search.grabbed, onGrab = { onGrab(release) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_close)) } },
    )
}

@Composable
private fun ReleaseRow(release: ArrRelease, grabbing: Boolean, grabbed: Boolean, onGrab: () -> Unit) {
    Column {
        Text(release.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(
                release.qualityName,
                formatBytes(release.size),
                release.indexer,
                release.seeders?.let { stringResource(R.string.media_seeders, it) },
                release.ageHours?.let { stringResource(R.string.media_age_hours, it.toInt()) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (release.rejected && release.rejections.isNotEmpty()) {
            Text(
                release.rejections.joinToString("; "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.align(Alignment.End)) {
            when {
                grabbed -> Text(stringResource(R.string.media_grabbed), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                else -> TextButton(onClick = onGrab, enabled = !grabbing && !release.rejected) {
                    if (grabbing) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(14.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.media_grab))
                }
            }
        }
    }
}

@Composable
private fun ManualImportDialog(
    import: ManualImportState,
    onFolderChange: (String) -> Unit,
    onLoad: () -> Unit,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!import.importing) onDismiss() },
        title = { Text(stringResource(R.string.media_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (import.done) {
                    Text(stringResource(R.string.media_import_started), color = MaterialTheme.colorScheme.primary)
                } else {
                    OutlinedTextField(
                        value = import.folder,
                        onValueChange = onFolderChange,
                        label = { Text(stringResource(R.string.media_import_folder)) },
                        singleLine = true,
                        enabled = !import.loading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = onLoad, enabled = import.folder.isNotBlank() && !import.loading) {
                        if (import.loading) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(14.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.media_import_scan))
                    }
                    import.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    import.files?.let { files ->
                        if (files.isEmpty()) {
                            Text(stringResource(R.string.media_import_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(files, key = { it.path }) { file ->
                                    ImportRow(file, selected = file.path in import.selectedPaths, onToggle = { onToggle(file.path) })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (import.done) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_close)) }
            } else {
                TextButton(onClick = onConfirm, enabled = import.selectedPaths.isNotEmpty() && !import.importing) {
                    if (import.importing) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.media_import_run, import.selectedPaths.size))
                }
            }
        },
        dismissButton = {
            if (!import.done) TextButton(onClick = onDismiss, enabled = !import.importing) { Text(stringResource(R.string.home_sign_out_cancel)) }
        },
    )
}

@Composable
private fun ImportRow(file: ManualImportItem, selected: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.clickable(enabled = file.rejections.isEmpty(), onClick = onToggle)) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            enabled = file.rejections.isEmpty(),
        )
        Column {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(formatBytes(file.size), file.qualityName, file.releaseGroup)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (file.rejections.isNotEmpty()) {
                Text(
                    file.rejections.joinToString("; "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DeleteItemDialog(service: ArrService, busy: Boolean, onConfirm: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var deleteFiles by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(stringResource(if (service == ArrService.Radarr) R.string.media_delete_movie_title else R.string.media_delete_series_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.media_delete_item_message))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { deleteFiles = !deleteFiles }) {
                    Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it }, enabled = !busy)
                    Text(stringResource(R.string.media_delete_files_too))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteFiles) }, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.admin_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.home_sign_out_cancel)) } },
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirm: String, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                Text(confirm, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.home_sign_out_cancel)) } },
    )
}
