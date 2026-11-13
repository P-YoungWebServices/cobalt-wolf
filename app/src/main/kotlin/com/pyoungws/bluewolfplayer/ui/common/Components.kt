package com.pyoungws.bluewolfplayer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import com.pyoungws.bluewolfplayer.shared.model.episodeLabel
import com.pyoungws.bluewolfplayer.ui.message

@Composable
fun LoadingBox(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(error: LoadError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(error.message(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            if (onRetry != null) Button(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun Artwork(url: String?, aspectRatio: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Title shown under a card: the name, or "Series - S1:E2" style context for episodes. */
fun MediaItem.cardSubtitle(): String? = when (kind) {
    MediaKind.Episode -> listOfNotNull(episodeLabel(), name).joinToString(" ")
    MediaKind.Movie, MediaKind.Series, MediaKind.MusicVideo -> year?.toString()
    else -> null
}

fun MediaItem.cardTitle(): String = if (kind == MediaKind.Episode) seriesName ?: name else name

/** Watched-check / unwatched-count badge drawn over card artwork's top end, like the official clients. */
@Composable
fun WatchedBadge(item: MediaItem, modifier: Modifier = Modifier) {
    val count = item.unplayedCount
    when {
        item.isPlayed -> Box(
            modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(2.dp).height(14.dp))
        }
        count != null && count > 0 -> Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Artwork with the progress bar and status badge overlaid — the shared card face for both card shapes. */
@Composable
private fun CardFace(item: MediaItem, url: String?, aspectRatio: Float, modifier: Modifier = Modifier) {
    Box {
        Artwork(url, aspectRatio = aspectRatio, modifier = modifier)
        item.progressFraction?.let {
            LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter))
        }
        if (item.isPlayed || (item.unplayedCount ?: 0) > 0) {
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { WatchedBadge(item) }
        }
    }
}

@Composable
fun PosterCard(item: MediaItem, onClick: () -> Unit, width: Dp = 120.dp, modifier: Modifier = Modifier) {
    Column(modifier.width(width).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)) {
        CardFace(item, item.posterUrl ?: item.landscapeUrl, aspectRatio = 2f / 3f, modifier = Modifier.fillMaxWidth())
        Text(item.cardTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        item.cardSubtitle()?.let {
            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LandscapeCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(240.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)) {
        CardFace(item, item.landscapeUrl ?: item.posterUrl, aspectRatio = 16f / 9f, modifier = Modifier.fillMaxWidth())
        Text(item.cardTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        item.cardSubtitle()?.let {
            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "2026-11-10T12:34:56" style timestamps become "2026-11-10 12:34"; other values pass through. */
fun formatTimestamp(iso: String): String {
    val cleaned = iso.substringBefore('.').replace('T', ' ')
    return if (cleaned.count { it == ':' } >= 2) cleaned.substringBeforeLast(':') else cleaned
}

/** Formats a byte count as "12.3 GB" style text for storage displays. */
fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} ${units[0]}" else "%.1f %s".format(value, units[unit])
}

@Composable
fun <T> CardRow(items: List<T>, key: (T) -> Any, content: @Composable (T) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = key) { content(it) }
    }
}
