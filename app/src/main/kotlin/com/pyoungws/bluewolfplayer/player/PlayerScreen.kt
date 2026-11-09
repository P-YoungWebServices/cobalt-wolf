package com.pyoungws.bluewolfplayer.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.model.episodeLabel
import com.pyoungws.bluewolfplayer.shared.presentation.PlayerUiState
import com.pyoungws.bluewolfplayer.shared.presentation.PlayerViewModel
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import kotlinx.coroutines.delay
import java.util.Locale

private const val PROGRESS_INTERVAL_MS = 10_000L

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    isInPip: Boolean,
    onPlayerChanged: (ExoPlayer?) -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onEnterPip: () -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val current = state) {
            PlayerUiState.Loading -> LoadingBox()
            is PlayerUiState.Error -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                ErrorBox(current.error, onRetry = viewModel::retry, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = onClose) { Text(stringResource(R.string.player_close)) }
            }
            PlayerUiState.PlaybackFailed -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_error), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                TextButton(onClick = onClose) { Text(stringResource(R.string.player_close)) }
            }
            is PlayerUiState.Ready -> PlayerContent(current, viewModel, isInPip, onPlayerChanged, onPlayingChanged, onEnterPip, onClose)
        }
    }
}

private enum class TrackDialog { Audio, Subtitles }

private class TrackOption(val label: String, val group: Tracks.Group?, val index: Int, val selected: Boolean)

@Composable
private fun PlayerContent(
    ready: PlayerUiState.Ready,
    viewModel: PlayerViewModel,
    isInPip: Boolean,
    onPlayerChanged: (ExoPlayer?) -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onEnterPip: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val request = ready.request
    val player = remember(ready.generation) {
        val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(request.source.headers)
        ExoPlayer.Builder(context, DefaultRenderersFactory(context))
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    var tracks by remember(player) { mutableStateOf(Tracks.EMPTY) }
    var controlsVisible by remember(player) { mutableStateOf(true) }
    var dialog by remember(player) { mutableStateOf<TrackDialog?>(null) }

    DisposableEffect(player) {
        val title = request.item.let { if (it.episodeLabel() != null) "${it.seriesName} ${it.episodeLabel()}: ${it.name}" else it.name }
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(request.source.url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .setSubtitleConfigurations(
                    request.source.subtitles.map {
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                            .setMimeType(it.mimeType)
                            .setLanguage(it.language)
                            .setLabel(it.label)
                            .setSelectionFlags((if (it.isDefault) C.SELECTION_FLAG_DEFAULT else 0) or (if (it.isForced) C.SELECTION_FLAG_FORCED else 0))
                            .build()
                    },
                )
                .build(),
            request.startPositionMs,
        )
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlayingChanged(isPlaying)
                if (isPlaying) viewModel.onPlaybackStarted(player.currentPosition, isPaused = false)
                viewModel.onProgress(player.currentPosition, isPaused = !isPlaying)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    viewModel.onStopped(player.duration.coerceAtLeast(0))
                    onClose()
                }
            }

            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) viewModel.onProgress(new.positionMs, isPaused = !player.isPlaying)
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) = onPlayingChanged(player.isPlaying)

            override fun onPlayerError(error: PlaybackException) {
                // First failure falls back to a server transcode; a second one shows the error screen.
                android.util.Log.w("PlayerScreen", "Playback error: ${error.errorCodeName}", error)
                viewModel.onPlayerError(player.currentPosition)
            }
        }
        player.addListener(listener)
        player.prepare()
        player.playWhenReady = true
        onPlayerChanged(player)
        onDispose {
            onPlayerChanged(null)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(PROGRESS_INTERVAL_MS)
            if (player.isPlaying) viewModel.onProgress(player.currentPosition, isPaused = false)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    setShowSubtitleButton(false)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { controlsVisible = it == android.view.View.VISIBLE })
                }
            },
            update = { it.useController = !isInPip },
            modifier = Modifier.fillMaxSize(),
        )
        if (controlsVisible && !isInPip) {
            Row(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { dialog = TrackDialog.Audio }) { Text(stringResource(R.string.player_audio), color = Color.White) }
                TextButton(onClick = { dialog = TrackDialog.Subtitles }) { Text(stringResource(R.string.player_subtitles), color = Color.White) }
                TextButton(onClick = onEnterPip) { Text(stringResource(R.string.player_pip), color = Color.White) }
                TextButton(onClick = onClose) { Text(stringResource(R.string.player_close), color = Color.White) }
            }
        }
    }

    dialog?.let { which ->
        TrackSelectionDialog(
            title = stringResource(if (which == TrackDialog.Audio) R.string.player_audio else R.string.player_subtitles),
            options = trackOptions(tracks, if (which == TrackDialog.Audio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT, player, offLabel = if (which == TrackDialog.Subtitles) stringResource(R.string.player_subtitles_off) else null),
            onSelect = { option ->
                selectTrack(player, if (which == TrackDialog.Audio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT, option)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun trackOptions(tracks: Tracks, type: Int, player: Player, offLabel: String?): List<TrackOption> {
    val resources = LocalContext.current.resources
    val unknown = { n: Int -> resources.getString(R.string.player_track_unknown, n) }
    val options = mutableListOf<TrackOption>()
    var n = 0
    tracks.groups.filter { it.type == type }.forEach { group ->
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            n++
            val format = group.getTrackFormat(i)
            val language = format.language?.takeIf { it != "und" }?.let { Locale.forLanguageTag(it).displayLanguage.takeIf { l -> l.isNotBlank() } }
            val label = listOfNotNull(
                format.label ?: language,
                if (type == C.TRACK_TYPE_AUDIO && format.channelCount > 0) "${format.channelCount}ch" else null,
                format.codecs?.takeIf { type == C.TRACK_TYPE_AUDIO },
            ).joinToString(" · ").ifEmpty { unknown(n) }
            options += TrackOption(label, group, i, group.isTrackSelected(i))
        }
    }
    if (offLabel != null) {
        val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(type) || options.none { it.selected }
        options.add(0, TrackOption(offLabel, null, -1, disabled))
    }
    return options
}

private fun selectTrack(player: Player, type: Int, option: TrackOption) {
    val builder = player.trackSelectionParameters.buildUpon()
    if (option.group == null) {
        builder.setTrackTypeDisabled(type, true)
    } else {
        builder.setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
    }
    player.trackSelectionParameters = builder.build()
}

@Composable
private fun TrackSelectionDialog(title: String, options: List<TrackOption>, onSelect: (TrackOption) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (options.isEmpty()) {
                Text(stringResource(R.string.player_no_tracks))
            } else {
                Column {
                    options.forEach { option ->
                        Row(Modifier.fillMaxWidth().clickable { onSelect(option) }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = option.selected, onClick = { onSelect(option) })
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_close)) } },
    )
}
