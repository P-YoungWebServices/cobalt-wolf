package com.pyoungws.cobaltwolf.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.library.LibraryRepository
import com.pyoungws.cobaltwolf.shared.model.ItemImageInfo
import com.pyoungws.cobaltwolf.shared.model.MediaItem
import com.pyoungws.cobaltwolf.shared.model.Outcome
import com.pyoungws.cobaltwolf.ui.message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.ByteArrayOutputStream

private val EDITABLE_TYPES = listOf("Primary", "Backdrop", "Thumb", "Logo", "Banner", "Disc")

/**
 * The admin artwork editor: lists the item's images and uploads replacements with
 * optional 90° rotation or a center crop to poster/landscape aspect, all client-side.
 */
@Composable
fun ArtworkEditorDialog(item: MediaItem, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository: LibraryRepository = koinInject()
    val scope = rememberCoroutineScope()
    var images by remember { mutableStateOf<List<ItemImageInfo>?>(null) }
    var error by remember { mutableStateOf<com.pyoungws.cobaltwolf.shared.model.LoadError?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var targetType by remember { mutableStateOf(EDITABLE_TYPES.first()) }
    var typeMenu by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }

    fun load() = scope.launch {
        when (val result = repository.itemImages(item.id)) {
            is Outcome.Success -> images = result.value
            is Outcome.Failure -> error = result.error
        }
    }
    LaunchedEffect(Unit) { load() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            bitmap = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.artwork_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
                Box(Modifier.height(180.dp).fillMaxWidth()) {
                    when {
                        images == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        images!!.isEmpty() -> Text(
                            stringResource(R.string.artwork_empty),
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(images!!, key = { "${it.type}:${it.index}" }) { image ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${image.type} ${image.width ?: "?"}×${image.height ?: "?"}",
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    TextButton(onClick = {
                                        scope.launch {
                                            repository.deleteItemImage(item.id, image.type, image.index)
                                            load()
                                            onChanged()
                                        }
                                    }) { Text(stringResource(R.string.artwork_delete)) }
                                }
                            }
                        }
                    }
                }
                bitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            bitmap = bmp.rotate(90f)
                        }) { Text(stringResource(R.string.artwork_rotate)) }
                        TextButton(onClick = { bitmap = bmp.centerCrop(2f / 3f) }) {
                            Text(stringResource(R.string.artwork_crop_poster))
                        }
                        TextButton(onClick = { bitmap = bmp.centerCrop(16f / 9f) }) {
                            Text(stringResource(R.string.artwork_crop_landscape))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = { typeMenu = true }) { Text(targetType) }
                        DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                            EDITABLE_TYPES.forEach { type ->
                                DropdownMenuItem(text = { Text(type) }, onClick = { targetType = type; typeMenu = false })
                            }
                        }
                    }
                    TextButton(onClick = {
                        picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(stringResource(R.string.artwork_pick)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = bitmap != null && !uploading,
                onClick = {
                    val bmp = bitmap ?: return@TextButton
                    uploading = true
                    scope.launch {
                        val bytes = withContext(Dispatchers.IO) {
                            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
                        }
                        when (val upload = repository.setItemImage(item.id, targetType, bytes, "image/jpeg")) {
                            is Outcome.Success -> {
                                bitmap = null
                                uploading = false
                                load()
                                onChanged()
                            }
                            is Outcome.Failure -> { error = upload.error; uploading = false }
                        }
                    }
                },
            ) { Text(stringResource(if (uploading) R.string.artwork_uploading else R.string.artwork_upload)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun Bitmap.rotate(degrees: Float): Bitmap =
    Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees) }, true)

private fun Bitmap.centerCrop(aspect: Float): Bitmap {
    val current = width.toFloat() / height
    return if (current > aspect) {
        val w = (height * aspect).toInt()
        Bitmap.createBitmap(this, (width - w) / 2, 0, w, height)
    } else {
        val h = (width / aspect).toInt()
        Bitmap.createBitmap(this, 0, (height - h) / 2, width, h)
    }
}
