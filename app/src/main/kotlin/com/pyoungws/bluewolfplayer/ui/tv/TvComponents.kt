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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import com.pyoungws.bluewolfplayer.shared.model.MediaPerson
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrTitle
import com.pyoungws.bluewolfplayer.ui.common.WatchedBadge
import com.pyoungws.bluewolfplayer.ui.common.cardSubtitle
import com.pyoungws.bluewolfplayer.ui.common.cardTitle
import com.pyoungws.bluewolfplayer.ui.discover.SeerrStatusBadge
import com.pyoungws.bluewolfplayer.ui.message

/** Card chrome shared by every focusable item: scale + a bright border and glow when focused. */
@Composable
private fun TvCard(onClick: () -> Unit, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp), onMenu: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        onClick = onClick,
        modifier = if (onMenu == null) modifier else modifier.onKeyEvent { ev ->
            val n = ev.nativeKeyEvent
            if (n.action == android.view.KeyEvent.ACTION_DOWN &&
                n.keyCode == android.view.KeyEvent.KEYCODE_MENU) { onMenu(); true } else false
        },
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
        ),
        glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = Color(0xFFA5C8FF), elevation = 16.dp)),
    ) { content() }
}

@Composable
fun CardArtwork(url: String?, aspectRatio: Float, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(aspectRatio).clip(RoundedCornerShape(6.dp))) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).padding(0.dp))
        }
    }
}

@Composable
fun CardLabel(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 6.dp)) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        if (subtitle != null) {
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 2:3 poster card for movies/series. */
@Composable
fun TvPosterCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier, width: Int = 150, onMenu: (() -> Unit)? = null) {
    Column(modifier.width(width.dp)) {
        TvCard(onClick = onClick, onMenu = onMenu) {
            Box {
                CardArtwork(item.posterUrl ?: item.landscapeUrl, aspectRatio = 2f / 3f, modifier = Modifier.fillMaxWidth())
                item.progressFraction?.let {
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter))
                }
                if (item.isPlayed || (item.unplayedCount ?: 0) > 0) {
                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { WatchedBadge(item) }
                }
            }
        }
        CardLabel(item.cardTitle(), item.cardSubtitle())
    }
}

/** 16:9 card for in-progress items and episodes, with a progress bar. */
@Composable
fun TvLandscapeCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier, width: Int = 260, onMenu: (() -> Unit)? = null) {
    Column(modifier.width(width.dp)) {
        TvCard(onClick = onClick, onMenu = onMenu) {
            Box {
                CardArtwork(item.landscapeUrl ?: item.posterUrl, aspectRatio = 16f / 9f, modifier = Modifier.fillMaxWidth())
                item.progressFraction?.let {
                    LinearProgressIndicator(
                        progress = { it },
                        modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                    )
                }
                if (item.isPlayed || (item.unplayedCount ?: 0) > 0) {
                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { WatchedBadge(item) }
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

/** One row in a TV item context menu (remote "menu" button). */
data class TvMenuOption(val label: String, val action: () -> Unit)

/** Ready-made card menu for TV grids: details plus watched/favourite toggles. */
@Composable
fun TvItemMenuOverlay(item: MediaItem, onOpenItem: (String) -> Unit, onDismiss: () -> Unit) {
    val repository: com.pyoungws.bluewolfplayer.shared.library.LibraryRepository = org.koin.compose.koinInject()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    TvItemOptionsOverlay(
        title = item.name,
        options = buildList {
            add(TvMenuOption(stringResource(R.string.detail_options_details)) { onOpenItem(item.id); onDismiss() })
            add(TvMenuOption(stringResource(if (item.isPlayed) R.string.detail_mark_unwatched else R.string.detail_mark_watched)) {
                scope.launch { repository.setPlayed(item, !item.isPlayed) }; onDismiss()
            })
            add(TvMenuOption(stringResource(if (item.isFavorite) R.string.detail_unfavorite else R.string.detail_favorite)) {
                scope.launch { repository.setFavorite(item, !item.isFavorite) }; onDismiss()
            })
        },
        onDismiss = onDismiss,
    )
}

/** Centered context list shown by the remote "menu" button on item cards and detail pages. */
@Composable
fun TvItemOptionsOverlay(title: String, options: List<TvMenuOption>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Box(
        Modifier.fillMaxSize().background(Color(0xAA000000)).clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(360.dp).background(Color(0xFF10233A)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 6.dp))
            options.forEachIndexed { i, opt ->
                var focused by remember { mutableStateOf(false) }
                Text(
                    opt.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (focused) Color.Black else Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .onFocusChanged { focused = it.isFocused }
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (focused) Color.White else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .clickable { opt.action() },
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { first.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            delay(50)
        }
    }
}

/** Square person card for the detail page's Cast & crew row: face, name, role. */
@Composable
fun TvPersonCard(person: MediaPerson, modifier: Modifier = Modifier, width: Int = 110, onClick: () -> Unit = {}) {
    Column(modifier.width(width.dp)) {
        TvCard(onClick = onClick) { CardArtwork(person.imageUrl, aspectRatio = 1f, modifier = Modifier.fillMaxWidth()) }
        CardLabel(person.name, person.role)
    }
}
