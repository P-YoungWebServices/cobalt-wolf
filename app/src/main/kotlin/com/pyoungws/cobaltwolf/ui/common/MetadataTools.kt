package com.pyoungws.cobaltwolf.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.auth.AuthRepository
import com.pyoungws.cobaltwolf.shared.auth.SignInResult
import com.pyoungws.cobaltwolf.shared.library.LibraryRepository
import com.pyoungws.cobaltwolf.shared.model.IdentifyMatch
import com.pyoungws.cobaltwolf.shared.model.MediaItem
import com.pyoungws.cobaltwolf.shared.model.MetadataRefresh
import com.pyoungws.cobaltwolf.shared.model.Outcome
import com.pyoungws.cobaltwolf.shared.model.SubtitleMatch
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private sealed interface ManageDialog {
    data object Refresh : ManageDialog
    data object Identify : ManageDialog
    data object Edit : ManageDialog
    data object Subtitles : ManageDialog
    data object Artwork : ManageDialog
}

/**
 * Admin-only "Manage" menu for detail pages: refresh metadata, identify against
 * remote providers, edit metadata fields and download remote subtitles.
 * Renders nothing for non-administrators.
 */
@Composable
fun ManageMediaButton(item: MediaItem, onChanged: () -> Unit = {}) {
    val auth: AuthRepository = koinInject()
    val repository: LibraryRepository = koinInject()
    val scope = rememberCoroutineScope()
    val isAdmin by produceState(false) {
        value = (auth.currentUser() as? SignInResult.Success)?.user?.isAdministrator == true
    }
    if (!isAdmin) return

    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ManageDialog?>(null) }

    OutlinedButton(onClick = { menuOpen = true }) { Text(stringResource(R.string.manage_title)) }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(text = { Text(stringResource(R.string.manage_refresh)) }, onClick = { menuOpen = false; dialog = ManageDialog.Refresh })
        DropdownMenuItem(text = { Text(stringResource(R.string.manage_identify)) }, onClick = { menuOpen = false; dialog = ManageDialog.Identify })
        DropdownMenuItem(text = { Text(stringResource(R.string.manage_edit)) }, onClick = { menuOpen = false; dialog = ManageDialog.Edit })
        DropdownMenuItem(text = { Text(stringResource(R.string.manage_artwork)) }, onClick = { menuOpen = false; dialog = ManageDialog.Artwork })
        if (item.isPlayable) {
            DropdownMenuItem(text = { Text(stringResource(R.string.manage_subtitles)) }, onClick = { menuOpen = false; dialog = ManageDialog.Subtitles })
        }
    }

    when (dialog) {
        ManageDialog.Refresh -> RefreshDialog(item, onDone = { dialog = null; onChanged() }, onDismiss = { dialog = null })
        ManageDialog.Identify -> IdentifyDialog(item, onDone = { dialog = null; onChanged() }, onDismiss = { dialog = null })
        ManageDialog.Edit -> EditMetadataDialog(item, onDone = { dialog = null; onChanged() }, onDismiss = { dialog = null })
        ManageDialog.Subtitles -> SubtitleSearchDialog(item, onDone = { dialog = null }, onDismiss = { dialog = null })
        ManageDialog.Artwork -> ArtworkEditorDialog(item, onChanged = onChanged, onDismiss = { dialog = null })
        null -> Unit
    }
}

/**
 * Same admin manage tools as [ManageMediaButton] but as a dialog for hosts
 * (like the TV detail menu) that open it themselves.
 */
@Composable
fun ManageMediaDialog(item: MediaItem, onChanged: () -> Unit = {}, onDismiss: () -> Unit) {
    val auth: AuthRepository = koinInject()
    val isAdmin by produceState(false) {
        value = (auth.currentUser() as? SignInResult.Success)?.user?.isAdministrator == true
    }
    var dialog by remember { mutableStateOf<ManageDialog?>(null) }
    if (!isAdmin) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    if (dialog == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.manage_title)) },
            text = {
                Column {
                    TextButton(onClick = { dialog = ManageDialog.Refresh }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.manage_refresh), modifier = Modifier.fillMaxWidth()) }
                    TextButton(onClick = { dialog = ManageDialog.Identify }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.manage_identify), modifier = Modifier.fillMaxWidth()) }
                    TextButton(onClick = { dialog = ManageDialog.Edit }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.manage_edit), modifier = Modifier.fillMaxWidth()) }
                    TextButton(onClick = { dialog = ManageDialog.Artwork }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.manage_artwork), modifier = Modifier.fillMaxWidth()) }
                    if (item.isPlayable) {
                        TextButton(onClick = { dialog = ManageDialog.Subtitles }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.manage_subtitles), modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        return
    }
    when (dialog) {
        ManageDialog.Refresh -> RefreshDialog(item, onDone = { dialog = null; onChanged(); onDismiss() }, onDismiss = { dialog = null })
        ManageDialog.Identify -> IdentifyDialog(item, onDone = { dialog = null; onChanged(); onDismiss() }, onDismiss = { dialog = null })
        ManageDialog.Edit -> EditMetadataDialog(item, onDone = { dialog = null; onChanged(); onDismiss() }, onDismiss = { dialog = null })
        ManageDialog.Subtitles -> SubtitleSearchDialog(item, onDone = { dialog = null }, onDismiss = { dialog = null })
        ManageDialog.Artwork -> ArtworkEditorDialog(item, onChanged = { dialog = null; onChanged(); onDismiss() }, onDismiss = { dialog = null })
        null -> Unit
    }
}

@Composable
private fun RefreshDialog(item: MediaItem, onDone: () -> Unit, onDismiss: () -> Unit) {
    val repository: LibraryRepository = koinInject()
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(MetadataRefresh.ScanMissing) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_refresh)) },
        text = {
            Column {
                listOf(
                    MetadataRefresh.ScanMissing to R.string.manage_refresh_scan,
                    MetadataRefresh.ReplaceMetadata to R.string.manage_refresh_replace_meta,
                    MetadataRefresh.ReplaceAll to R.string.manage_refresh_replace_all,
                ).forEach { (m, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == m, onClick = { mode = m })
                        Text(stringResource(label))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { repository.refreshItem(item.id, mode); onDone() } }) {
                Text(stringResource(R.string.manage_refresh))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun IdentifyDialog(item: MediaItem, onDone: () -> Unit, onDismiss: () -> Unit) {
    val repository: LibraryRepository = koinInject()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(item.name) }
    var year by remember { mutableStateOf(item.year?.toString().orEmpty()) }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<IdentifyMatch>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_identify)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.manage_identify_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = year, onValueChange = { year = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.year_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                when {
                    searching -> Text(stringResource(R.string.loading), modifier = Modifier.padding(top = 12.dp))
                    results != null && results!!.isEmpty() -> Text(stringResource(R.string.library_empty), modifier = Modifier.padding(top = 12.dp))
                }
                results.orEmpty().forEach { match ->
                    TextButton(
                        onClick = { scope.launch { repository.applyIdentify(item.id, match); onDone() } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(listOfNotNull(match.name, match.year?.toString(), match.provider).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            match.overview?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        searching = true
                        results = (repository.identifySearch(item, name.trim(), year.toIntOrNull()) as? Outcome.Success)?.value.orEmpty()
                        searching = false
                    }
                },
                enabled = !searching && name.isNotBlank(),
            ) { Text(stringResource(R.string.media_manager_search_hint)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun EditMetadataDialog(item: MediaItem, onDone: () -> Unit, onDismiss: () -> Unit) {
    val repository: LibraryRepository = koinInject()
    val scope = rememberCoroutineScope()
    val dto by produceState<org.jellyfin.sdk.model.api.BaseItemDto?>(null, item.id) {
        value = (repository.itemDto(item.id) as? Outcome.Success)?.value
    }
    var name by remember(dto) { mutableStateOf(dto?.name ?: item.name) }
    var sortName by remember(dto) { mutableStateOf(dto?.sortName.orEmpty()) }
    var overview by remember(dto) { mutableStateOf(dto?.overview ?: item.overview.orEmpty()) }
    var tagline by remember(dto) { mutableStateOf(dto?.taglines?.firstOrNull() ?: item.tagline.orEmpty()) }
    var yearText by remember(dto) { mutableStateOf((dto?.productionYear ?: item.year)?.toString().orEmpty()) }
    var rating by remember(dto) { mutableStateOf(dto?.communityRating?.toString().orEmpty()) }
    var parental by remember(dto) { mutableStateOf(dto?.officialRating.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_edit)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (dto == null) {
                    Text(stringResource(R.string.loading))
                } else {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.manage_identify_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sortName, onValueChange = { sortName = it }, label = { Text(stringResource(R.string.manage_sort_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(value = tagline, onValueChange = { tagline = it }, label = { Text(stringResource(R.string.manage_tagline)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(value = overview, onValueChange = { overview = it }, label = { Text(stringResource(R.string.manage_overview)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3)
                    Row(Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(value = yearText, onValueChange = { yearText = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.year_label)) }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = rating, onValueChange = { rating = it }, label = { Text(stringResource(R.string.manage_rating)) }, singleLine = true, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    }
                    OutlinedTextField(value = parental, onValueChange = { parental = it }, label = { Text(stringResource(R.string.manage_parental)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val base = dto ?: return@TextButton
                    scope.launch {
                        repository.updateItemDto(item.id, base.copy(
                            name = name.ifBlank { null },
                            sortName = sortName.ifBlank { null },
                            overview = overview.ifBlank { null },
                            taglines = listOf(tagline).filter { it.isNotBlank() },
                            productionYear = yearText.toIntOrNull(),
                            communityRating = rating.toFloatOrNull(),
                            officialRating = parental.ifBlank { null },
                            lockedFields = (base.lockedFields.orEmpty() + org.jellyfin.sdk.model.api.MetadataField.NAME).distinct(),
                        ))
                        onDone()
                    }
                },
                enabled = dto != null,
            ) { Text(stringResource(R.string.manage_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SubtitleSearchDialog(item: MediaItem, onDone: () -> Unit, onDismiss: () -> Unit) {
    val repository: LibraryRepository = koinInject()
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf("eng") }
    var searching by remember { mutableStateOf(false) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<SubtitleMatch>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_subtitles)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = language, onValueChange = { language = it }, label = { Text(stringResource(R.string.manage_sub_language)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                when {
                    searching -> Text(stringResource(R.string.loading), modifier = Modifier.padding(top = 12.dp))
                    results != null && results!!.isEmpty() -> Text(stringResource(R.string.library_empty), modifier = Modifier.padding(top = 12.dp))
                }
                results.orEmpty().forEach { match ->
                    TextButton(
                        onClick = {
                            scope.launch {
                                downloadingId = match.id
                                repository.downloadRemoteSubtitle(item.id, match.id)
                                downloadingId = null
                            }
                        },
                        enabled = downloadingId == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val flags = listOfNotNull(
                            match.format,
                            if (match.forced) "forced" else null,
                            if (match.hearingImpaired) "SDH" else null,
                            match.provider,
                        ).joinToString(" · ")
                        Text(listOf(match.name, match.language ?: "", flags).filter { it.isNotBlank() }.joinToString("  "), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        searching = true
                        results = (repository.searchRemoteSubtitles(item.id, language.trim()) as? Outcome.Success)?.value.orEmpty()
                        searching = false
                    }
                },
                enabled = !searching && language.isNotBlank(),
            ) { Text(stringResource(R.string.media_manager_search_hint)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
