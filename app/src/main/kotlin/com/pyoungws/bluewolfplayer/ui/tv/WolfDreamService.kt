package com.pyoungws.bluewolfplayer.ui.tv

import android.service.dreams.DreamService
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.pyoungws.bluewolfplayer.shared.library.LibraryRepository
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext

/** Android daydream/screensaver: slowly cycles recently-added backdrops with a clock. */
class WolfDreamService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(ComposeView(this).apply { setContent { Screensaver() } })
    }
}

@Composable
private fun Screensaver() {
    var backdrops by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        val repository = runCatching { GlobalContext.get().get<LibraryRepository>() }.getOrNull() ?: return@LaunchedEffect
        repository.loadHome().let { outcome ->
            if (outcome is Outcome.Success) {
                backdrops = outcome.value.recentlyAdded.flatMap { it.items }
                    .mapNotNull { it.backdropUrl ?: it.landscapeUrl }.distinct().shuffled()
            }
        }
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(backdrops.size) {
        while (true) {
            delay(12_000)
            if (backdrops.isNotEmpty()) index = (index + 1) % backdrops.size
        }
    }
    var now by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = currentTime()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = backdrops.getOrNull(index), label = "backdrop") { url ->
            if (url != null) {
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))),
            ),
        )
        Text(
            now,
            style = MaterialTheme.typography.displayMedium,
            color = Color.White,
            textAlign = TextAlign.End,
            modifier = Modifier.align(Alignment.BottomEnd).padding(48.dp),
        )
    }
}

private fun currentTime(): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())
