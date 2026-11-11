@file:OptIn(UnstableApi::class)

package com.pyoungws.bluewolfplayer.player

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.pyoungws.bluewolfplayer.BuildConfig
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.isTelevision
import com.pyoungws.bluewolfplayer.shared.model.episodeLabel
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackLevel
import com.pyoungws.bluewolfplayer.shared.playback.PlayerErrorKind
import com.pyoungws.bluewolfplayer.shared.playback.summary
import com.pyoungws.bluewolfplayer.shared.presentation.PlayerUiState
import com.pyoungws.bluewolfplayer.shared.presentation.PlayerViewModel
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import kotlinx.coroutines.delay
import java.util.Locale

private const val PROGRESS_INTERVAL_MS = 10_000L
private const val STATS_INTERVAL_MS = 1_000L
private const val TAG = "CobaltWolfPlayback"

@Composable
internal fun PlayerScreen(
    viewModel: PlayerViewModel,
    isInPip: Boolean,
    displayModeController: DisplayModeController,
    debugPreferredAudioMimeType: String?,
    debugSubtitleMimeType: String?,
    debugMaxChannels: Int? = null,
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
            is PlayerUiState.Ready -> PlayerContent(current, viewModel, isInPip, displayModeController, debugPreferredAudioMimeType, debugSubtitleMimeType, debugMaxChannels, onPlayerChanged, onPlayingChanged, onEnterPip, onClose)
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
    displayModeController: DisplayModeController,
    debugPreferredAudioMimeType: String?,
    debugSubtitleMimeType: String?,
    debugMaxChannels: Int?,
    onPlayerChanged: (ExoPlayer?) -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onEnterPip: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val request = ready.request
    // The view comes first: libass draws styled subtitles into its subtitle layer.
    val playerView = remember(ready.generation) { PlayerView(context) }
    val engine = remember(ready.generation) { buildPlayerEngine(context, request, playerView, debugMaxChannels) }
    val player = engine.player
    val stats by engine.stats.stats.collectAsStateWithLifecycle()
    var tracks by remember(player) { mutableStateOf(Tracks.EMPTY) }
    var controlsVisible by remember(player) { mutableStateOf(true) }
    var dialog by remember(player) { mutableStateOf<TrackDialog?>(null) }
    var showStats by remember { mutableStateOf(request.settings.showStats) }
    var displayMatched by remember(player) { mutableStateOf(false) }

    // Tell the user when a fallback replaced the first attempt.
    LaunchedEffect(ready.generation) {
        if (BuildConfig.DEBUG) {
            val url = request.source.url.replace(Regex("(?i)(api_?key)=[^&]+"), "$1=<redacted>")
            Log.i(TAG, "source generation=${ready.generation} method=${request.source.method} plan=${request.plan} url=$url")
        }
        val message = when (request.level) {
            PlaybackLevel.DirectPlay -> null
            PlaybackLevel.LocalDecode -> R.string.player_fallback_local
            PlaybackLevel.Transcode -> R.string.player_fallback_transcode
        }
        if (ready.generation > 1 && message != null) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

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
        val debugSelections = mutableMapOf(C.TRACK_TYPE_AUDIO to debugPreferredAudioMimeType, C.TRACK_TYPE_TEXT to debugSubtitleMimeType)
        var audioPreferenceApplied = false
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
                if (BuildConfig.DEBUG) {
                    newTracks.groups.forEach { Log.i(TAG, "track selected=${it.isSelected} supported=${it.isSupported} ${it.getTrackFormat(0)}") }
                }
                // Debug only: switch to the first audio/subtitle track of the type asked for on the intent.
                debugSelections.entries.filter { it.value != null }.forEach { (type, mimeType) ->
                    val group = newTracks.groups.firstOrNull { group ->
                        group.type == type && group.getTrackFormat(0).let { it.sampleMimeType == mimeType || it.codecs == mimeType }
                    } ?: return@forEach
                    debugSelections[type] = null
                    Log.i(TAG, "debug track selected: ${group.getTrackFormat(0)}")
                    selectTrack(player, type, TrackOption("", group, 0, false))
                }
                // A stream no renderer accepts would be silently skipped; treat it as a decoder
                // failure so the fallback order applies instead of playing without picture or sound.
                val unplayable = listOf(C.TRACK_TYPE_VIDEO to PlayerErrorKind.VideoDecoder, C.TRACK_TYPE_AUDIO to PlayerErrorKind.AudioDecoder)
                    .firstOrNull { (type, _) -> newTracks.containsType(type) && !newTracks.isTypeSupported(type, /* allowExceedsCapabilities = */ true) }
                if (unplayable != null) {
                    Log.w(TAG, "No decoder for the ${if (unplayable.first == C.TRACK_TYPE_VIDEO) "video" else "audio"} track at level ${request.level}")
                    player.stop()
                    viewModel.onPlayerError(player.currentPosition, unplayable.second)
                }
                // Prefer English 5.1 over higher-channel defaults (e.g. TrueHD Atmos on a receiver
                // that cannot bitstream it); manual or debug selections still win afterwards.
                if (!audioPreferenceApplied && debugPreferredAudioMimeType == null) {
                    audioPreferenceApplied = true
                    val audioGroups = newTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                    val pickedFormat = audioGroups.firstNotNullOfOrNull { g ->
                        (0 until g.length).firstNotNullOfOrNull { i -> if (g.isTrackSelected(i)) g.getTrackFormat(i) else null }
                    }
                    val candidate = audioGroups.firstNotNullOfOrNull { g ->
                        (0 until g.length).firstNotNullOfOrNull { i ->
                            g.getTrackFormat(i).takeIf { f -> f.language?.startsWith("en") == true && f.channelCount in 1..6 }?.let { g to i }
                        }
                    } ?: audioGroups.firstNotNullOfOrNull { g ->
                        (0 until g.length).firstNotNullOfOrNull { i ->
                            g.getTrackFormat(i).takeIf { f -> f.language?.startsWith("en") == true }?.let { g to i }
                        }
                    }
                    if (candidate != null && (pickedFormat == null || pickedFormat.channelCount > 6 || pickedFormat.language?.startsWith("en") != true)) {
                        Log.i(TAG, "Preferring English ${candidate.first.getTrackFormat(candidate.second).channelCount}ch audio over ${pickedFormat?.channelCount}ch default")
                        // Applied after a short delay: the audio sink configures passthrough/channel
                        // caps from the HDMI state, which is still settling if a display-mode switch
                        // just happened (like a manual track change after startup).
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!player.isReleased) selectTrack(player, C.TRACK_TYPE_AUDIO, TrackOption("", candidate.first, candidate.second, false))
                        }, 2_500)
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) = onPlayingChanged(player.isPlaying)

            override fun onPlayerError(error: PlaybackException) {
                // Walks the fallback order: FFmpeg for the decoder that failed, then a server transcode.
                val kind = error.toErrorKind()
                Log.w(TAG, "Playback error at level ${request.level}: ${error.errorCodeName} ($kind)", error)
                viewModel.onPlayerError(player.currentPosition, kind)
            }
        }
        player.addListener(listener)
        player.prepare()
        player.playWhenReady = true
        onPlayerChanged(player)
        onDispose {
            onPlayerChanged(null)
            player.removeListener(listener)
            playerView.player = null
            engine.release()
            displayModeController.reset()
        }
    }

    // Once the stream's frame rate and range are known, match the display. A mode switch blanks
    // the screen while HDMI re-syncs, so playback waits for it.
    LaunchedEffect(player, stats.hdr, stats.videoDecoderKind) {
        displayModeController.setHdr(engine.stats.isHdrOutput)
    }
    LaunchedEffect(player, stats.frameRate) {
        // Prefer the fps from the request so the HDMI switch settles before the audio sink
        // resolves its capabilities; a mid-playback switch kills the passthrough AudioTrack.
        val frameRate = request.source.info?.video?.frameRate ?: stats.frameRate
        if (frameRate == null || displayMatched) return@LaunchedEffect
        displayMatched = true
        val wasPlaying = player.playWhenReady
        player.playWhenReady = false
        try {
            displayModeController.matchFrameRate(frameRate, request)
        } finally {
            player.playWhenReady = wasPlaying
        }
    }

    LaunchedEffect(player) {
        var logged = 0
        while (true) {
            delay(STATS_INTERVAL_MS)
            engine.stats.refreshDisplay()
            // Debug builds log the stats shortly after start and then periodically, for device test runs.
            if (BuildConfig.DEBUG && player.isPlaying && (++logged == 5 || logged % 15 == 0)) {
                Log.i(TAG, "stats position=${player.currentPosition / 1000}s ${engine.stats.stats.value.summary()}")
            }
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(PROGRESS_INTERVAL_MS)
            if (player.isPlaying) viewModel.onProgress(player.currentPosition, isPaused = false)
        }
    }

    // On TV the D-pad needs a focus target while the controls are hidden: a focusable root that
    // shows the controller on select. When the controls appear, play/pause takes the focus.
    val isTv = remember { context.isTelevision() }
    val rootFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isTv, controlsVisible) {
        if (!isTv) return@LaunchedEffect
        if (controlsVisible) {
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus()
        } else {
            rootFocusRequester.requestFocus()
        }
    }
    Box(
        Modifier.fillMaxSize().then(
            if (isTv) {
                Modifier
                    .focusRequester(rootFocusRequester)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && !controlsVisible &&
                            (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            playerView.showController()
                            true
                        } else {
                            false
                        }
                    }
                    .focusable()
            } else {
                Modifier
            },
        ),
    ) {
        key(ready.generation) {
            AndroidView(
                factory = {
                    playerView.apply {
                        this.player = player
                        setShowSubtitleButton(false)
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { controlsVisible = it == android.view.View.VISIBLE })
                    }
                },
                update = { it.useController = !isInPip },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showStats && !isInPip) StatsOverlay(stats, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp))
        if (controlsVisible && !isInPip) {
            Row(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { dialog = TrackDialog.Audio }) { Text(stringResource(R.string.player_audio), color = Color.White) }
                TextButton(onClick = { dialog = TrackDialog.Subtitles }) { Text(stringResource(R.string.player_subtitles), color = Color.White) }
                TextButton(onClick = { showStats = !showStats }) { Text(stringResource(R.string.player_stats), color = Color.White) }
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
