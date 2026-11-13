@file:OptIn(UnstableApi::class)

package com.pyoungws.bluewolfplayer.player

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreTime
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import com.pyoungws.bluewolfplayer.BuildConfig
import coil3.compose.AsyncImage
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.isTelevision
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import com.pyoungws.bluewolfplayer.shared.model.episodeLabel
import com.pyoungws.bluewolfplayer.shared.playback.MediaSegmentKind
import com.pyoungws.bluewolfplayer.shared.playback.SegmentBehavior
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackLevel
import com.pyoungws.bluewolfplayer.shared.playback.RepeatMode
import com.pyoungws.bluewolfplayer.shared.playback.PlayerErrorKind
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackRequest
import com.pyoungws.bluewolfplayer.shared.playback.StreamBitrate
import com.pyoungws.bluewolfplayer.shared.playback.summary
import com.pyoungws.bluewolfplayer.shared.presentation.PlayerUiState
import com.pyoungws.bluewolfplayer.shared.presentation.PlayerViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.SyncPlayUiCommand
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val PROGRESS_INTERVAL_MS = 10_000L
private const val STATS_INTERVAL_MS = 1_000L
private const val SYNC_DRIFT_TOLERANCE_MS = 1_500L
private const val TAG = "CobaltWolfPlayback"

@Composable
internal fun PlayerScreen(
    viewModel: PlayerViewModel,
    isInPip: Boolean,
    displayModeController: DisplayModeController,
    debugPreferredAudioMimeType: String?,
    debugSubtitleMimeType: String?,
    debugMaxChannels: Int? = null,
    debugShowStats: Boolean = false,
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
            is PlayerUiState.Ready -> PlayerContent(current, viewModel, isInPip, displayModeController, debugPreferredAudioMimeType, debugSubtitleMimeType, debugMaxChannels, debugShowStats, onPlayerChanged, onPlayingChanged, onEnterPip, onClose)
        }
    }
}

private enum class TrackDialog { Audio, Subtitles }

/** Zoom-mode step past AspectRatioFrameLayout's own constants: crops black bars it detected. */
private const val RESIZE_CROP = -1

private val SPEED_CHOICES = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

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
    debugShowStats: Boolean,
    onPlayerChanged: (ExoPlayer?) -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onEnterPip: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val request = ready.request
    // The view comes first: libass draws styled subtitles into its subtitle layer.
    val playerView = remember(ready.generation) { PlayerView(context) }
    val engine = remember(ready.generation) {
        buildPlayerEngine(context, request, playerView, debugMaxChannels, subtitleOffsetUs = { viewModel.subtitleDelayMs.value * 1_000 })
    }
    val player = engine.player
    // The controller's next button has no playlist item to move to: it lights up once the
    // server reports a following episode, and pressing it hands off to the view model.
    val controllerPlayer = remember(player) {
        object : ForwardingPlayer(player) {
            private fun canGoNext() = viewModel.nextItem.value != null && viewModel.membership.value == null

            override fun isCommandAvailable(command: Int) =
                (command == Player.COMMAND_SEEK_TO_NEXT && canGoNext()) || super.isCommandAvailable(command)

            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon().apply {
                    if (canGoNext()) add(Player.COMMAND_SEEK_TO_NEXT)
                }.build()

            override fun seekToNext() {
                if (canGoNext()) viewModel.playNext(currentPosition) else super.seekToNext()
            }
        }
    }

    val castController = remember { CastController(context) }
    DisposableEffect(castController, player) {
        castController.attach(controllerPlayer) {
            controllerPlayer.currentMediaItem?.forCastReceiver(castToken(request))
        }
        onDispose { castController.detach() }
    }
    val stats by engine.stats.stats.collectAsStateWithLifecycle()
    val membership by viewModel.membership.collectAsStateWithLifecycle()
    val nextItem by viewModel.nextItem.collectAsStateWithLifecycle()
    val segments by viewModel.segments.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    var showQueue by remember { mutableStateOf(false) }
    var tracks by remember(player) { mutableStateOf(Tracks.EMPTY) }
    var controlsVisible by remember(player) { mutableStateOf(true) }
    var dialog by remember(player) { mutableStateOf<TrackDialog?>(null) }
    var zoomMode by remember(player) {
        mutableIntStateOf(
            when (request.settings.defaultZoom) {
                com.pyoungws.bluewolfplayer.shared.playback.ZoomMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                com.pyoungws.bluewolfplayer.shared.playback.ZoomMode.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                com.pyoungws.bluewolfplayer.shared.playback.ZoomMode.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                com.pyoungws.bluewolfplayer.shared.playback.ZoomMode.Crop -> RESIZE_CROP
            },
        )
    }
    // Auto-crop zooms the texture output by the detected letterbox factor; detection runs once when
    // the mode is picked and 1f means "no bars found, show the frame as-is".
    var cropScale by remember(player) { mutableStateOf<Float?>(null) }
    LaunchedEffect(zoomMode) {
        if (zoomMode == RESIZE_CROP && cropScale == null) {
            cropScale = detectCropScale(request.source.url, request.source.headers, request.item.runtimeTicks, request.startPositionMs)
        }
    }
    val cropActive = zoomMode == RESIZE_CROP && (cropScale ?: 1f) > 1f
    // While cropping, video routes to a TextureView laid over the normal surface box; only a
    // texture can take the transform that scales the frame's letterbox away.
    var cropTextureView by remember(player) { mutableStateOf<android.view.TextureView?>(null) }
    LaunchedEffect(cropActive) {
        val frame = playerView.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame) ?: return@LaunchedEffect
        val overlay = cropTextureView
        if (cropActive && overlay == null) {
            val texture = android.view.TextureView(context)
            frame.addView(texture, android.widget.FrameLayout.LayoutParams(-1, -1))
            player.setVideoTextureView(texture)
            cropTextureView = texture
        } else if (!cropActive && overlay != null) {
            player.clearVideoTextureView(overlay)
            frame.removeView(overlay)
            cropTextureView = null
            playerView.player = null
            playerView.player = castController.activePlayer ?: controllerPlayer
        }
    }
    LaunchedEffect(cropTextureView, cropScale) {
        val texture = cropTextureView ?: return@LaunchedEffect
        val scale = cropScale ?: 1f
        texture.post {
            texture.setTransform(android.graphics.Matrix().apply { setScale(scale, scale, texture.width / 2f, texture.height / 2f) })
        }
    }
    var scrubPositionMs by remember(player) { mutableStateOf<Long?>(null) }
    var nextCountdownCancelled by remember(ready.generation) { mutableStateOf(false) }
    var showBitrateDialog by remember(player) { mutableStateOf(false) }
    var showSpeedDialog by remember(player) { mutableStateOf(false) }
    var showDelayDialog by remember(player) { mutableStateOf(false) }
    val subtitleDelayMs by viewModel.subtitleDelayMs.collectAsStateWithLifecycle()
    var nightMode by remember(player) { mutableStateOf(false) }
    var showChapters by remember(player) { mutableStateOf(false) }
    var gesturesLocked by remember(player) { mutableStateOf(false) }
    var gestureHint by remember(player) { mutableStateOf<String?>(null) }
    val hintScope = rememberCoroutineScope()
    val stillWatching by viewModel.stillWatching.collectAsStateWithLifecycle()
    var showStats by remember { mutableStateOf(debugShowStats) }
    var positionMs by remember(player) { mutableLongStateOf(request.startPositionMs) }
    var displayMatched by remember(player) { mutableStateOf(false) }
    val skipFocusRequester = remember { FocusRequester() }

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

    val itemTitle = request.item.let { if (it.episodeLabel() != null) "${it.seriesName} ${it.episodeLabel()}: ${it.name}" else it.name }

    val isAudio = request.item.kind == MediaKind.Song
    DisposableEffect(player) {
        val title = itemTitle
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(request.source.url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(request.item.artists.firstOrNull())
                        .setAlbumTitle(request.item.albumName)
                        .setArtworkUri((request.item.posterUrl ?: request.item.landscapeUrl)?.let(Uri::parse))
                        .build(),
                )
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
        var trackPrefsApplied = false
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlayingChanged(isPlaying)
                if (isPlaying) viewModel.onPlaybackStarted(player.currentPosition, isPaused = false)
                viewModel.onProgress(player.currentPosition, isPaused = !isPlaying)
                // SyncPlay: tell the group about local pauses and resumes; commands it sent
                // first are filtered inside the view model, so these only carry user actions.
                if (isPlaying) {
                    viewModel.onUserResume(player.currentPosition)
                } else if (player.playbackState == Player.STATE_READY) {
                    viewModel.onUserPause(player.currentPosition)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    // Episodes roll into the next one; anything else closes the player.
                    if (!viewModel.onEnded(player.duration.coerceAtLeast(0))) onClose()
                }
                // SyncPlay: the group waits for buffering members and resumes when they are ready.
                if (state == Player.STATE_BUFFERING) viewModel.onBuffering(player.currentPosition, player.playWhenReady)
                if (state == Player.STATE_READY) viewModel.onReady(player.currentPosition, player.isPlaying)
            }

            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    viewModel.onProgress(new.positionMs, isPaused = !player.isPlaying)
                    viewModel.onUserSeek(new.positionMs)
                }
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
                // Detail-page picks and language preferences are applied once, before the
                // generic English-audio fallback below (which yields when one landed).
                if (!trackPrefsApplied) {
                    trackPrefsApplied = true
                    audioPreferenceApplied = applyInitialTrackPrefs(player, newTracks, request) || audioPreferenceApplied
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
        player.playWhenReady = !request.startPaused
        PlaybackService.attach(context, player)
        onPlayerChanged(player)
        onDispose {
            onPlayerChanged(null)
            player.removeListener(listener)
            playerView.player = null
            if (isAudio && player.isPlaying) {
                // Background audio: the service's session keeps the player alive past this screen.
                player.clearVideoSurface()
            } else {
                PlaybackService.stop(context)
                engine.release()
            }
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

    // Commands coming from the SyncPlay group the user is in. Applying them triggers the same
    // player listeners as local actions; the view model filters those echoes out.
    LaunchedEffect(player) {
        viewModel.commands.collect { command ->
            when (command) {
                is SyncPlayUiCommand.SeekTo -> player.seekTo(command.positionMs)
                SyncPlayUiCommand.Pause -> player.pause()
                is SyncPlayUiCommand.Resume -> {
                    command.positionMs?.let {
                        if (kotlin.math.abs(it - player.currentPosition) > SYNC_DRIFT_TOLERANCE_MS) player.seekTo(it)
                    }
                    player.play()
                }
                SyncPlayUiCommand.Close -> onClose()
            }
        }
    }

    LaunchedEffect(player) {
        var logged = 0
        while (true) {
            delay(STATS_INTERVAL_MS)
            positionMs = player.currentPosition
            engine.stats.refreshDisplay()
            viewModel.onSyncTick(player.currentPosition)
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
    val activeKind = segments.firstOrNull { it.contains(positionMs) }?.kind
    val behavior = when (activeKind) {
        MediaSegmentKind.Intro, MediaSegmentKind.Recap -> request.settings.introBehavior
        MediaSegmentKind.Outro -> request.settings.outroBehavior
        else -> SegmentBehavior.Ask
    }
    val activeSegment = segments.firstOrNull { it.contains(positionMs) }?.takeIf { behavior != SegmentBehavior.Off }
    LaunchedEffect(activeSegment?.startMs) {
        val segment = activeSegment
        if (segment != null && behavior == SegmentBehavior.Auto) player.seekTo(segment.endMs)
    }
    LaunchedEffect(isTv, controlsVisible, activeSegment) {
        if (!isTv) return@LaunchedEffect
        when {
            // While a marked section is up the skip button takes focus — nothing else is
            // reachable once the controller hides, and the D-pad cannot leave the player view.
            activeSegment != null -> skipFocusRequester.requestFocus()
            controlsVisible -> playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus()
            else -> rootFocusRequester.requestFocus()
        }
    }

    // The controller only re-checks command availability on show: a next episode found while
    // the controls are up needs a redraw to enable its next button.
    LaunchedEffect(nextItem != null) {
        if (nextItem != null && controlsVisible) playerView.showController()
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
            val castActive = castController.activePlayer
            LaunchedEffect(castActive) { playerView.player = castActive ?: controllerPlayer }
            AndroidView(
                factory = {
                    playerView.apply {
                        this.player = castActive ?: controllerPlayer
                        setShowSubtitleButton(false)
                        // A single stream has no previous item; a permanently greyed button only confuses.
                        setShowPreviousButton(false)
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { controlsVisible = it == android.view.View.VISIBLE })
                        val chapters = request.item.chapters
                        (findViewById<android.view.View>(androidx.media3.ui.R.id.exo_progress) as? TimeBar)?.let { bar ->
                            if (chapters.isNotEmpty()) {
                                bar.setAdGroupTimesMs(
                                    chapters.map { it.startTicks / 10_000L }.toLongArray(),
                                    BooleanArray(chapters.size),
                                    chapters.size,
                                )
                            }
                            if (request.item.trickplay != null) {
                                bar.addListener(object : TimeBar.OnScrubListener {
                                    override fun onScrubStart(timeBar: TimeBar, position: Long) { scrubPositionMs = position }
                                    override fun onScrubMove(timeBar: TimeBar, position: Long) { scrubPositionMs = position }
                                    override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) { scrubPositionMs = null }
                                })
                            }
                        }
                        if (!isTv) attachPlayerGestures(
                            view = this,
                            player = player,
                            isLocked = { gesturesLocked },
                            onHint = { hint ->
                                gestureHint = hint
                                hintScope.launch { delay(1_200); if (gestureHint == hint) gestureHint = null }
                            },
                        )
                        post {
                            // Lift the position/duration readout out of the bottom bar and
                            // place it just above the seek bar, so the bottom band is only
                            // the overlay icon row plus Media3's own settings button.
                            val timeRow = findViewById<View>(androidx.media3.ui.R.id.exo_time)
                            val bottomBar = timeRow?.parent as? android.view.ViewGroup
                            val controllerRoot = bottomBar?.parent as? FrameLayout
                            if (timeRow != null && controllerRoot != null) {
                                bottomBar.removeView(timeRow)
                                val margin = (106 * resources.displayMetrics.density).toInt()
                                controllerRoot.addView(
                                    timeRow,
                                    FrameLayout.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                        android.view.Gravity.BOTTOM or android.view.Gravity.START,
                                    ).apply { bottomMargin = margin },
                                )
                            }
                        }
                    }
                },
                update = {
                it.useController = !isInPip
                // Crop draws through the texture overlay instead, so the layout keeps a plain fit.
                it.resizeMode = if (zoomMode == RESIZE_CROP) AspectRatioFrameLayout.RESIZE_MODE_FIT else zoomMode
                it.subtitleView?.setFractionalTextSize(0.0533f * request.settings.subtitleScale)
                it.subtitleView?.setStyle(
                    androidx.media3.ui.CaptionStyleCompat(
                        when (request.settings.subtitleColor) {
                            com.pyoungws.bluewolfplayer.shared.playback.SubtitleColor.White -> android.graphics.Color.WHITE
                            com.pyoungws.bluewolfplayer.shared.playback.SubtitleColor.Yellow -> android.graphics.Color.YELLOW
                            com.pyoungws.bluewolfplayer.shared.playback.SubtitleColor.Green -> android.graphics.Color.GREEN
                            com.pyoungws.bluewolfplayer.shared.playback.SubtitleColor.Cyan -> android.graphics.Color.CYAN
                        },
                        when (request.settings.subtitleBackground) {
                            com.pyoungws.bluewolfplayer.shared.playback.SubtitleBackground.Transparent -> android.graphics.Color.TRANSPARENT
                            com.pyoungws.bluewolfplayer.shared.playback.SubtitleBackground.SemiTransparent -> 0x80000000.toInt()
                            com.pyoungws.bluewolfplayer.shared.playback.SubtitleBackground.Opaque -> android.graphics.Color.BLACK
                        },
                        android.graphics.Color.TRANSPARENT,
                        if (request.settings.subtitleBold) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE else androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE,
                        android.graphics.Color.BLACK,
                        if (request.settings.subtitleBold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT,
                    ),
                )
            },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showStats && !isInPip) StatsOverlay(stats, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 8.dp, top = 56.dp))
        gestureHint?.let {
            Text(
                it,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (gesturesLocked) {
            IconButton(
                onClick = { gesturesLocked = false },
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 140.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) { Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.player_unlock), tint = Color.White) }
        }
        scrubPositionMs?.let { scrubMs ->
            request.item.trickplay?.let { tiles ->
                val index = (scrubMs / tiles.interval).toInt().coerceIn(0, tiles.thumbnailCount - 1)
                AsyncImage(
                    model = tiles.tileUrl(index / (tiles.tileWidth * tiles.tileHeight)),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp)
                        .border(2.dp, Color.White),
                )
            }
        }
        if (controlsVisible && !isInPip) {
            Row(
                Modifier.align(Alignment.TopEnd).fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    itemTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                membership?.let {
                    Text(
                        stringResource(R.string.syncplay_badge, it.groupName),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                ControlButton(onClick = onClose, icon = Icons.Outlined.Close, description = stringResource(R.string.player_close))
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(8.dp).padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ControlButton(onClick = { dialog = TrackDialog.Audio }, icon = Icons.AutoMirrored.Outlined.VolumeUp, description = stringResource(R.string.player_audio))
                ControlButton(onClick = { dialog = TrackDialog.Subtitles }, icon = Icons.Outlined.Subtitles, description = stringResource(R.string.player_subtitles))
                ControlButton(onClick = { showDelayDialog = true }, icon = Icons.Outlined.MoreTime, description = stringResource(R.string.player_subtitle_delay))
                ControlButton(onClick = { showBitrateDialog = true }, icon = Icons.Outlined.HighQuality, description = stringResource(R.string.player_bitrate))
                ControlButton(
                    onClick = {
                        zoomMode = when (zoomMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> RESIZE_CROP
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    icon = Icons.Outlined.AspectRatio,
                    description = stringResource(R.string.player_zoom),
                )
                if (queue.entries.isNotEmpty()) {
                    ControlButton(onClick = { showQueue = true }, icon = Icons.AutoMirrored.Outlined.PlaylistPlay, description = stringResource(R.string.player_queue))
                }
                ControlButton(onClick = { showSpeedDialog = true }, icon = Icons.Outlined.Speed, description = stringResource(R.string.player_speed))
                ControlButton(onClick = { nightMode = !nightMode; engine.setNightMode(nightMode) }, icon = Icons.Outlined.Nightlight, description = stringResource(R.string.player_night_mode))
                if (!isTv) {
                    ControlButton(onClick = { gesturesLocked = true }, icon = Icons.Outlined.Lock, description = stringResource(R.string.player_lock))
                }
                if (request.item.chapters.isNotEmpty()) {
                    ControlButton(onClick = { showChapters = true }, icon = Icons.AutoMirrored.Outlined.ListAlt, description = stringResource(R.string.player_chapters))
                }
                ControlButton(onClick = { showStats = !showStats }, icon = Icons.Outlined.QueryStats, description = stringResource(R.string.player_stats))
                if (!isTv) {
                    ControlButton(onClick = onEnterPip, icon = Icons.Outlined.PictureInPictureAlt, description = stringResource(R.string.player_pip))
                }
                if (castController.available) {
                    AndroidView(
                        factory = { castRouteButton(it) },
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
        val duration = player.duration
        if (!isInPip && (activeSegment != null || (controlsVisible && duration > 0))) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 106.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (controlsVisible && duration > 0) {
                    Text(
                        stringResource(R.string.player_ends_at, finishTimeLabel(positionMs, duration)),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                activeSegment?.let { segment ->
                    var skipFocused by remember { mutableStateOf(false) }
                    val buttonModifier = Modifier
                        .focusRequester(skipFocusRequester)
                        .onFocusChanged { skipFocused = it.isFocused }
                        .border(2.dp, if (skipFocused) Color.White else Color.Transparent, CircleShape)
                    when {
                        segment.kind == MediaSegmentKind.Outro && nextItem != null -> Button(onClick = { viewModel.playNext(player.currentPosition) }, modifier = buttonModifier) {
                            Text(stringResource(R.string.player_next_episode))
                        }
                        else -> Button(onClick = { player.seekTo(segment.endMs) }, modifier = buttonModifier) {
                            Text(stringResource(
                                when (segment.kind) {
                                    MediaSegmentKind.Intro -> R.string.player_skip_intro
                                    MediaSegmentKind.Recap -> R.string.player_skip_recap
                                    MediaSegmentKind.Outro -> R.string.player_skip_credits
                                }
                            ))
                        }
                    }
                }
                if (!nextCountdownCancelled && nextItem != null && membership == null) {
                    val remainingMs = duration - positionMs
                    if (remainingMs in 1..NEXT_UP_COUNTDOWN_MS) {
                        var secondsLeft by remember(nextItem) { mutableIntStateOf((remainingMs / 1000L).toInt().coerceIn(1, 99)) }
                        LaunchedEffect(nextItem) {
                            while (true) {
                                kotlinx.coroutines.delay(1000L)
                                val left = ((player.duration - player.currentPosition) / 1000L).toInt()
                                secondsLeft = left.coerceAtLeast(0)
                                if (left <= 0) { viewModel.playNext(player.currentPosition); break }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), CircleShape).padding(start = 12.dp),
                        ) {
                            Text(stringResource(R.string.player_next_in, secondsLeft), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { nextCountdownCancelled = true }) { Text(stringResource(R.string.player_cancel_next)) }
                        }
                    }
                }
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
    if (showQueue) {
        QueueDialog(
            queue = queue,
            onSelect = { viewModel.playQueueIndex(it, player.currentPosition); showQueue = false },
            onPrevious = { viewModel.playQueuePrevious(player.currentPosition); showQueue = false },
            onRemove = viewModel::removeQueueEntry,
            onCycleRepeat = viewModel::cycleRepeat,
            onToggleShuffle = viewModel::toggleShuffle,
            onDismiss = { showQueue = false },
        )
    }
    if (showBitrateDialog) {
        BitrateDialog(
            selected = request.settings.maxBitrate,
            onSelect = { bitrate ->
                viewModel.changeBitrate(bitrate, player.currentPosition)
                showBitrateDialog = false
            },
            onDismiss = { showBitrateDialog = false },
        )
    }
    if (showDelayDialog) {
        SubtitleDelayDialog(
            delayMs = subtitleDelayMs,
            onChange = viewModel::setSubtitleDelayMs,
            onDismiss = { showDelayDialog = false },
        )
    }
    if (showSpeedDialog) {
        TrackSelectionDialog(
            title = stringResource(R.string.player_speed),
            options = SPEED_CHOICES.map { TrackOption(it.toString() + "x", null, SPEED_CHOICES.indexOf(it), it == player.playbackParameters.speed) },
            onSelect = { option ->
                player.setPlaybackSpeed(SPEED_CHOICES[option.index])
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false },
        )
    }
    if (showChapters) {
        TrackSelectionDialog(
            title = stringResource(R.string.player_chapters),
            options = request.item.chapters.mapIndexed { index, chapter ->
                TrackOption(
                    chapter.name ?: stringResource(R.string.player_chapter_number, index + 1),
                    null,
                    index,
                    positionMs >= chapter.startTicks / 10_000 &&
                        (request.item.chapters.getOrNull(index + 1)?.startTicks ?: Long.MAX_VALUE) > positionMs * 10_000,
                )
            },
            onSelect = { option ->
                player.seekTo(request.item.chapters[option.index].startTicks / 10_000)
                showChapters = false
            },
            onDismiss = { showChapters = false },
        )
    }
    if (stillWatching) {
        LaunchedEffect(Unit) { player.pause() }
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.player_still_watching)) },
            text = { Text(stringResource(R.string.player_still_watching_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmStillWatching(player.currentPosition); player.play() }) {
                    Text(stringResource(R.string.player_still_watching_yes))
                }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.player_close)) } },
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

/**
 * Applies the detail-page stream picks ([PlaybackRequest.audioStreamIndex]/[subtitleStreamIndex])
 * and the user's language/subtitle preferences once tracks are known. Returns true when an audio
 * pick was made so the generic English-audio fallback stays out of the way.
 */
private fun applyInitialTrackPrefs(player: Player, tracks: Tracks, request: PlaybackRequest): Boolean {
    val settings = request.settings
    val builder = player.trackSelectionParameters.buildUpon()
    var audioPicked = false

    fun groupOrdinal(kind: com.pyoungws.bluewolfplayer.shared.model.StreamKind, streamIndex: Int): Int =
        request.item.streams.filter { it.kind == kind }.indexOfFirst { it.index == streamIndex }

    fun pickTrack(type: Int, ordinal: Int): Boolean {
        val groups = tracks.groups.filter { it.type == type }
        if (ordinal < 0 || ordinal >= groups.size) return false
        val group = groups[ordinal]
        val index = (0 until group.length).firstOrNull { group.isTrackSupported(it) } ?: return false
        builder.setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
        return true
    }

    // Audio: explicit index, then preferred language, else leave the default.
    request.audioStreamIndex?.let { idx ->
        if (pickTrack(C.TRACK_TYPE_AUDIO, groupOrdinal(com.pyoungws.bluewolfplayer.shared.model.StreamKind.Audio, idx))) audioPicked = true
    }
    if (!audioPicked) settings.preferredAudioLanguage?.let { lang ->
        val match = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.firstNotNullOfOrNull { g ->
            (0 until g.length).firstOrNull { i -> g.isTrackSupported(i) && g.getTrackFormat(i).language.matchesLanguagePref(lang) }?.let { g to it }
        }
        if (match != null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setOverrideForType(TrackSelectionOverride(match.first.mediaTrackGroup, match.second))
            audioPicked = true
        }
    }

    // Subtitles: explicit index (-1 = off), then the playback mode.
    val subIndex = request.subtitleStreamIndex
    when {
        subIndex == -1 || settings.subtitleMode == com.pyoungws.bluewolfplayer.shared.playback.SubtitlePlaybackMode.None ->
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        subIndex != null -> pickTrack(C.TRACK_TYPE_TEXT, groupOrdinal(com.pyoungws.bluewolfplayer.shared.model.StreamKind.Subtitle, subIndex))
        else -> {
            val lang = settings.preferredSubtitleLanguage
            val forcedOnly = settings.subtitleMode == com.pyoungws.bluewolfplayer.shared.playback.SubtitlePlaybackMode.Forced
            if (lang != null && settings.subtitleMode != com.pyoungws.bluewolfplayer.shared.playback.SubtitlePlaybackMode.Default) {
                val match = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.firstNotNullOfOrNull { g ->
                    (0 until g.length).firstOrNull { i ->
                        val f = g.getTrackFormat(i)
                        g.isTrackSupported(i) && f.language.matchesLanguagePref(lang) &&
                            (!forcedOnly || f.selectionFlags and C.SELECTION_FLAG_FORCED != 0)
                    }?.let { g to it }
                }
                if (match != null) {
                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(match.first.mediaTrackGroup, match.second))
                }
            }
        }
    }
    player.trackSelectionParameters = builder.build()
    return audioPicked
}

/** Matches a track language tag against a user preference ("eng", "en", "eng;en"). */
private fun String?.matchesLanguagePref(pref: String): Boolean {
    if (this == null || pref.isBlank()) return false
    val candidates = pref.split(';', ',').map { it.trim().lowercase() }
    val lang = lowercase()
    return candidates.any { lang == it || lang.startsWith("$it-") || it.length == 3 && lang.take(2) == it.take(2) }
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
private fun QueueDialog(
    queue: com.pyoungws.bluewolfplayer.shared.playback.PlayQueue,
    onSelect: (Int) -> Unit,
    onPrevious: () -> Unit,
    onRemove: (Int) -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.player_queue), modifier = Modifier.weight(1f))
                TextButton(onClick = onToggleShuffle) {
                    Text(
                        stringResource(R.string.player_shuffle),
                        color = if (queue.shuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                TextButton(onClick = onCycleRepeat) {
                    Text(
                        stringResource(R.string.player_repeat, stringResource(when (queue.repeat) {
                            RepeatMode.All -> R.string.player_repeat_all
                            RepeatMode.One -> R.string.player_repeat_one
                            else -> R.string.player_repeat_off
                        })),
                        color = if (queue.repeat != RepeatMode.Off) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        text = {
            if (queue.entries.isEmpty()) Text(stringResource(R.string.player_queue_empty))
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(queue.entries, key = { i, e -> "${e.item.id}-$i" }) { index, entry ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(index) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            entry.item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (index == queue.currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.player_remove_from_queue))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_close)) } },
    )
}

@Composable
private fun ControlButton(onClick: () -> Unit, icon: ImageVector, description: String) {
    var focused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .border(2.dp, if (focused) Color.White else Color.Transparent, CircleShape)
            .background(if (focused) Color.White.copy(alpha = 0.25f) else Color.Transparent, CircleShape),
    ) { Icon(icon, contentDescription = description, tint = Color.White) }
}

/** The wall-clock time playback ends at the current position (e.g. "Ends at 22:41"). */
private fun finishTimeLabel(positionMs: Long, durationMs: Long): String =
    LocalTime.now()
        .plus(Duration.ofMillis((durationMs - positionMs).coerceAtLeast(0)))
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
private fun BitrateDialog(selected: StreamBitrate, onSelect: (StreamBitrate) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_bitrate)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                StreamBitrate.entries.forEach { bitrate ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(bitrate) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = bitrate == selected, onClick = { onSelect(bitrate) })
                        Text(bitrateLabel(bitrate), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_close)) } },
    )
}

@Composable
private fun bitrateLabel(bitrate: StreamBitrate): String = when (bitrate) {
    StreamBitrate.Auto -> stringResource(R.string.playback_bitrate_auto)
    StreamBitrate.Mbps120 -> stringResource(R.string.playback_bitrate_120)
    StreamBitrate.Mbps80 -> stringResource(R.string.playback_bitrate_80)
    StreamBitrate.Mbps60 -> stringResource(R.string.playback_bitrate_60)
    StreamBitrate.Mbps40 -> stringResource(R.string.playback_bitrate_40)
    StreamBitrate.Mbps20 -> stringResource(R.string.playback_bitrate_20)
    StreamBitrate.Mbps10 -> stringResource(R.string.playback_bitrate_10)
    StreamBitrate.Mbps5 -> stringResource(R.string.playback_bitrate_5)
    StreamBitrate.Mbps2 -> stringResource(R.string.playback_bitrate_2)
}

@Composable
private fun SubtitleDelayDialog(delayMs: Long, onChange: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_subtitle_delay)) },
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onChange(delayMs - 100) }) { Text("-100ms") }
                Text(
                    stringResource(R.string.player_delay_value, if (delayMs >= 0) "+${delayMs}" else "$delayMs"),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = { onChange(delayMs + 100) }) { Text("+100ms") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_close)) } },
        dismissButton = if (delayMs == 0L) null else {
            { TextButton(onClick = { onChange(0) }) { Text(stringResource(R.string.player_delay_reset)) } }
        },
    )
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
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

private const val NEXT_UP_COUNTDOWN_MS = 15_000L

/**
 * Phone gestures on the video surface: double-tap seeks 10 s, a horizontal drag scrubs
 * (full width = [SCRUB_FULL_RANGE_MS]), vertical drags adjust volume (right half) and
 * brightness (left half), press-and-hold plays at 2x. The listener never consumes
 * events, so the built-in controller keeps working; while locked it eats everything.
 */
private fun attachPlayerGestures(view: PlayerView, player: ExoPlayer, isLocked: () -> Boolean, onHint: (String?) -> Unit) {
    val context = view.context
    val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    val activity = context as? android.app.Activity
    var heldSpeed: Float? = null
    var scrubbing = false
    var scrubMs = 0L
    var axis: Boolean? = null // true = vertical, false = horizontal

    val detector = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: android.view.MotionEvent) = !isLocked()

        override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
            val delta = if (e.x < view.width / 2f) -SEEK_GESTURE_MS else SEEK_GESTURE_MS
            player.seekTo((player.currentPosition + delta).coerceIn(0L, (player.duration).coerceAtLeast(0L)))
            onHint(if (delta < 0) "-10s" else "+10s")
            return true
        }

        override fun onLongPress(e: android.view.MotionEvent) {
            if (isLocked()) return
            heldSpeed = player.playbackParameters.speed
            player.setPlaybackSpeed(2f)
            onHint("2x")
        }

        override fun onScroll(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (isLocked() || e1 == null || e1.y > view.height * 0.85f) return false
            if (axis == null && (kotlin.math.abs(e2.x - e1.x) > 24 || kotlin.math.abs(e2.y - e1.y) > 24)) {
                axis = kotlin.math.abs(e2.y - e1.y) > kotlin.math.abs(e2.x - e1.x)
            }
            when (axis) {
                false -> {
                    scrubbing = true
                    scrubMs = ((e2.x - e1.x) / view.width * SCRUB_FULL_RANGE_MS).toLong()
                    onHint(if (scrubMs >= 0) "+${scrubMs / 1000}s" else "${scrubMs / 1000}s")
                }
                true -> {
                    val delta = -distanceY / view.height
                    if (e1.x > view.width / 2f) {
                        val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                        val next = (audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) + (delta * max).toInt()).coerceIn(0, max)
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, next, 0)
                        onHint("${next * 100 / max}%")
                    } else {
                        activity?.window?.let { window ->
                            val attrs = window.attributes
                            val next = ((if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness) + delta * 2f).coerceIn(0.05f, 1f)
                            attrs.screenBrightness = next
                            window.attributes = attrs
                            onHint("${(next * 100).toInt()}%")
                        }
                    }
                }
                null -> Unit
            }
            return true
        }
    })

    view.setOnTouchListener { _, event ->
        if (isLocked()) {
            heldSpeed = null; scrubbing = false; axis = null
            return@setOnTouchListener true
        }
        detector.onTouchEvent(event)
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                heldSpeed?.let { player.setPlaybackSpeed(it); heldSpeed = null; onHint(null) }
                if (scrubbing) {
                    scrubbing = false
                    player.seekTo((player.currentPosition + scrubMs).coerceIn(0L, player.duration.coerceAtLeast(0L)))
                    scrubMs = 0
                    onHint(null)
                }
                axis = null
            }
        }
        false
    }
}

private const val SEEK_GESTURE_MS = 10_000L
private const val SCRUB_FULL_RANGE_MS = 180_000L


/** Extracts the access token from the source's Authorization header for the Cast URL. */
private fun castToken(request: com.pyoungws.bluewolfplayer.shared.playback.PlaybackRequest): String? =
    request.source.headers["Authorization"]
        ?.let { Regex("Token=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
        ?: request.source.headers["X-Emby-Token"]
