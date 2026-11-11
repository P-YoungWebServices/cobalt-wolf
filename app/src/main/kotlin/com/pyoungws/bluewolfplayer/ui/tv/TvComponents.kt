package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrTitle
import com.pyoungws.bluewolfplayer.ui.common.cardSubtitle
import com.pyoungws.bluewolfplayer.ui.common.cardTitle
import com.pyoungws.bluewolfplayer.ui.discover.SeerrStatusBadge
import com.pyoungws.bluewolfplayer.ui.message

/** Card chrome shared by every focusable item: scale + a bright border and glow when focused. */
@Composable
private fun TvCard(onClick: () -> Unit, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp), content: @Composable () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
        ),
        glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = Color(0xFFA5C8FF), elevation = 16.dp)),
    ) { content() }
}

@Composable
private fun CardArtwork(url: String?, aspectRatio: Float, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(aspectRatio).clip(RoundedCornerShape(6.dp))) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).padding(0.dp))
        }
    }
}

@Composable
private fun CardLabel(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 6.dp)) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        if (subtitle != null) {
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 2:3 poster card for movies/series. */
@Composable
fun TvPosterCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier, width: Int = 150) {
    Column(modifier.width(width.dp)) {
        TvCard(onClick = onClick) { CardArtwork(item.posterUrl ?: item.landscapeUrl, aspectRatio = 2f / 3f, modifier = Modifier.fillMaxWidth()) }
        CardLabel(item.cardTitle(), item.cardSubtitle())
    }
}

/** 16:9 card for in-progress items and episodes, with a progress bar. */
@Composable
fun TvLandscapeCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier, width: Int = 260) {
    Column(modifier.width(width.dp)) {
        TvCard(onClick = onClick) {
            Box {
                CardArtwork(item.landscapeUrl ?: item.posterUrl, aspectRatio = 16f / 9f, modifier = Modifier.fillMaxWidth())
                item.progressFraction?.let {
                    LinearProgressIndicator(
                        progress = { it },
                        modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                    )
                }
            }
        }
        CardLabel(item.cardTitle(), item.cardSubtitle())
    }
}

/** 2:3 card for a Seerr title, with its library/request badge over the artwork. */
@Composable
fun TvSeerrCard(title: SeerrTitle, onClick: () -> Unit, modifier: Modifier = Modifier, width: Int = 150) {
    Column(modifier.width(width.dp)) {
        TvCard(onClick = onClick) {
            Box {
                CardArtwork(title.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.fillMaxWidth())
                SeerrStatusBadge(title, Modifier.align(Alignment.TopStart).padding(6.dp))
            }
        }
        CardLabel(title.title, title.year?.toString())
    }
}

/** 16:9 card for a top-level library. */
@Composable
fun TvLibraryCard(name: String, imageUrl: String?, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, width: Int = 260) {
    Column(modifier.width(width.dp)) {
        TvCard(onClick = { if (enabled) onClick() }) { CardArtwork(imageUrl, aspectRatio = 16f / 9f, modifier = Modifier.fillMaxWidth()) }
        Column(Modifier.padding(top = 6.dp)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (!enabled) {
                Text(stringResource(R.string.library_unsupported), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TvSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(horizontal = TvContentPadding, vertical = 6.dp),
    )
}

@Composable
fun TvLoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun TvErrorBox(error: LoadError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(error.message(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            if (onRetry != null) {
                androidx.tv.material3.Button(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
            }
        }
    }
}

@Composable
fun <T> TvCardRow(items: List<T>, key: (T) -> Any, content: @Composable (T) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = TvContentPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items.size, key = { key(items[it]) }) { content(items[it]) }
    }
}

/** Horizontal content edge padding used across the TV screens. */
val TvContentPadding = 48.dp
