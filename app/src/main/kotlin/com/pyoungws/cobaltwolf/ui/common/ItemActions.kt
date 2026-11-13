package com.pyoungws.cobaltwolf.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.library.ItemQuery
import com.pyoungws.cobaltwolf.shared.library.LibraryRepository
import com.pyoungws.cobaltwolf.shared.model.MediaItem
import com.pyoungws.cobaltwolf.shared.model.MediaKind
import com.pyoungws.cobaltwolf.shared.model.Outcome
import com.pyoungws.cobaltwolf.shared.playback.PlayQueueRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The long-press action sheet for a media card: queue operations, playlist and
 * collection management, watched/favorite flags and playlist membership.
 * [containerId]/[containerKind] let the menu offer "remove from this playlist"
 * while browsing a playlist's contents.
 */
@Composable
fun ItemContextMenu(
    item: MediaItem,
    expanded: Boolean,
    onDismiss: () -> Unit,
    containerKind: MediaKind? = null,
    containerId: String? = null,
    onChanged: () -> Unit = {},
) {
    val playQueue: PlayQueueRepository = koinInject()
    val repository: LibraryRepository = koinInject()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showCollectionPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPlayOn by remember { mutableStateOf(false) }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            item.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (item.isPlayable || item.isContainer) {
            val target = if (item.isPlayable) item else null
            target?.let {
                DropdownMenuItem(text = { Text(stringResource(R.string.player_play_next)) }, onClick = { playQueue.playNext(it); onDismiss() })
                DropdownMenuItem(text = { Text(stringResource(R.string.player_add_to_queue)) }, onClick = { playQueue.enqueue(it); onDismiss() })
            }
        }
        DropdownMenuItem(text = { Text(stringResource(R.string.action_add_to_playlist)) }, onClick = { showPlaylistPicker = true })
        if (item.kind != MediaKind.Song && item.kind != MediaKind.MusicAlbum && item.kind != MediaKind.MusicArtist) {
            DropdownMenuItem(text = { Text(stringResource(R.string.action_add_to_collection)) }, onClick = { showCollectionPicker = true })
        }
        DropdownMenuItem(
            text = { Text(stringResource(if (item.isPlayed) R.string.detail_mark_unwatched else R.string.detail_mark_watched)) },
            onClick = { scope.launch { repository.setPlayed(item, !item.isPlayed); onChanged() }; onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(if (item.isFavorite) R.string.detail_unfavorite else R.string.detail_favorite)) },
            onClick = { scope.launch { repository.setFavorite(item, !item.isFavorite); onChanged() }; onDismiss() },
        )
        if (containerKind == MediaKind.Playlist && item.playlistItemId != null && containerId != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_remove_from_playlist)) },
                onClick = { scope.launch { repository.removeFromPlaylist(containerId, listOfNotNull(item.playlistItemId)); onChanged() }; onDismiss() },
            )
        }
        if (containerKind == MediaKind.BoxSet && containerId != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_remove_from_collection)) },
                onClick = { scope.launch { repository.removeFromCollection(containerId, listOf(item.id)); onChanged() }; onDismiss() },
            )
        }
        if (item.isPlayable) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_play_on)) },
                onClick = { showPlayOn = true },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_copy_stream_url)) },
                onClick = { repository.streamUrl(item)?.let { clipboard.setText(AnnotatedString(it)) }; onDismiss() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_share_stream)) },
                onClick = {
                    repository.streamUrl(item)?.let { url ->
                        val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, url).setType("text/plain")
                        context.startActivity(Intent.createChooser(send, item.name))
                    }
                    onDismiss()
                },
            )
        }
        if (item.canDelete) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = { showDeleteConfirm = true },
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(stringResource(R.string.action_delete_confirm, item.name)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteItems(listOf(item)); onChanged() }
                    showDeleteConfirm = false; onDismiss()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showPlayOn) {
        PlayOnDialog(item = item, onDismiss = { showPlayOn = false }, onSent = onDismiss)
    }
    if (showPlaylistPicker) {
        ItemPickerDialog(
            titleRes = R.string.action_add_to_playlist,
            newLabelRes = R.string.action_new_playlist,
            kind = MediaKind.Playlist,
            onPick = { playlistId -> repository.addToPlaylist(playlistId, listOf(item.id)) },
            onCreate = { name -> repository.createPlaylist(name, listOf(item.id)) },
            onDismiss = { showPlaylistPicker = false; onDismiss() },
        )
    }
    if (showCollectionPicker) {
        ItemPickerDialog(
            titleRes = R.string.action_add_to_collection,
            newLabelRes = R.string.action_new_collection,
            kind = MediaKind.BoxSet,
            onPick = { collectionId -> repository.addToCollection(collectionId, listOf(item.id)) },
            onCreate = { name -> repository.createCollection(name, listOf(item.id)) },
            onDismiss = { showCollectionPicker = false; onDismiss() },
        )
    }
}

@Composable
private fun ItemPickerDialog(
    titleRes: Int,
    newLabelRes: Int,
    kind: MediaKind,
    onPick: suspend (String) -> Outcome<Unit>,
    onCreate: suspend (String) -> Outcome<String>,
    onDismiss: () -> Unit,
) {
    val repository: LibraryRepository = koinInject()
    val scope = rememberCoroutineScope()
    val candidates by produceState<List<MediaItem>>(emptyList(), kind) {
        value = (repository.items(ItemQuery(includeItemKinds = setOf(kind), limit = 100)) as? Outcome.Success)?.value?.items.orEmpty()
    }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column {
                candidates.forEach { candidate ->
                    TextButton(onClick = { scope.launch { onPick(candidate.id); onDismiss() } }, modifier = Modifier.fillMaxWidth()) {
                        Text(candidate.name, modifier = Modifier.fillMaxWidth())
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(newLabelRes)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { scope.launch { if (newName.isNotBlank()) onCreate(newName.trim()); onDismiss() } },
                enabled = newName.isNotBlank(),
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** "Play on" remote control: pick an active session, the server tells that client to play [item]. */
@Composable
private fun PlayOnDialog(item: MediaItem, onDismiss: () -> Unit, onSent: () -> Unit) {
    val admin: com.pyoungws.cobaltwolf.shared.admin.AdminRepository = koinInject()
    val repository: LibraryRepository = koinInject()
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<com.pyoungws.cobaltwolf.shared.admin.ActiveSession>?>(null) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        when (val result = admin.sessions()) {
            is Outcome.Success -> sessions = result.value
            is Outcome.Failure -> error = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_play_on)) },
        text = {
            Column {
                when {
                    error -> Text(stringResource(R.string.play_on_no_sessions))
                    sessions == null -> CircularProgressIndicator(Modifier.padding(16.dp))
                    sessions.orEmpty().isEmpty() -> Text(stringResource(R.string.play_on_no_sessions))
                    else -> sessions.orEmpty().forEach { session ->
                        TextButton(
                            onClick = {
                                scope.launch { repository.playOnSession(session.id, listOf(item.id)) }
                                onDismiss(); onSent()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(listOfNotNull(session.deviceName, session.client, "(${session.userName})").joinToString(" · "), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_close)) } },
    )
}
