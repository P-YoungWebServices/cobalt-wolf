@file:OptIn(UnstableApi::class)

package com.pyoungws.bluewolfplayer.player

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.ui.PlayerView
import com.pyoungws.bluewolfplayer.shared.playback.AudioOutputPreference
import com.pyoungws.bluewolfplayer.shared.playback.DecoderChoice
import com.pyoungws.bluewolfplayer.shared.playback.FrameRateMatching
import com.pyoungws.bluewolfplayer.shared.playback.MOVIE_AUDIO_ATTRIBUTES
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackRequest
import com.pyoungws.bluewolfplayer.shared.playback.PlayerErrorKind
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType

/** An ExoPlayer configured for one [PlaybackRequest], with the pieces that must be released with it. */
internal class PlayerEngine(
    val player: ExoPlayer,
    val stats: PlaybackStatsCollector,
    private val assHandler: AssHandler?,
) {
    fun release() {
        player.removeAnalyticsListener(stats)
        player.release()
        assHandler?.release()
    }
}

/**
 * Builds the player for [request]. Renderer order gives the fallback within one stream: audio is
 * passed through when the output accepts the format, otherwise decoded by a platform decoder and
 * otherwise by FFmpeg; video uses a platform decoder and FFmpeg only when there is none. A
 * [PlaybackRequest.plan] set to local puts FFmpeg first for that stream (after the device path
 * failed), leaving the device decoder for codecs FFmpeg does not have.
 * ASS/SSA subtitles are drawn by libass into [playerView]'s subtitle layer; PGS and SRT use
 * Media3's own decoders.
 */
internal fun buildPlayerEngine(
    context: Context,
    request: PlaybackRequest,
    playerView: PlayerView,
    pcmChannelCap: Int? = null,
    subtitleOffsetUs: () -> Long = { 0L },
): PlayerEngine {
    val settings = request.settings
    val baseRenderers = BlueWolfRenderersFactory(
        context,
        pcmOnly = settings.audioOutput == AudioOutputPreference.PcmOnly,
        localVideoFirst = request.plan.video == DecoderChoice.Local,
        localAudioFirst = request.plan.audio == DecoderChoice.Local,
        pcmChannelCap = pcmChannelCap,
        subtitleOffsetUs = subtitleOffsetUs,
    )
    baseRenderers.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    baseRenderers.setMediaCodecSelector(dolbyVisionSelector(settings.dolbyVision))
    baseRenderers.setEnableDecoderFallback(true)
    // Keeps 24-bit lossless audio (TrueHD, DTS-HD MA, FLAC) above 16 bits when it is decoded to PCM.
    baseRenderers.setEnableAudioFloatOutput(true)

    val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(request.source.headers)
    val dataSource = DefaultDataSource.Factory(context, http)
    // Matroska cues live at the end of the file, so the extractor does one ranged read of them
    // up front; FLAG_DISABLE_SEEK_FOR_CUES must stay off or the stream reports itself unseekable
    // and every seek control (rw/ff, scrub, skip) is disabled.
    val extractors: ExtractorsFactory = DefaultExtractorsFactory()
    val cuePoints = request.source.cuePoints
    fun ExtractorsFactory.finished(): ExtractorsFactory {
        var factory = this
        // After the ASS wrapper replaces the extractors so it also patches AssMatroskaExtractor.
        if (!cuePoints.isNullOrEmpty()) factory = factory.withMkvCueIndex(cuePoints)
        return factory.withDolbyVisionBaseLayerFallback(request.capabilities, settings)
    }

    val assHandler = if (settings.styledSubtitles) AssHandler(AssRenderType.OVERLAY_OPEN_GL) else null
    val mediaSourceFactory = if (assHandler != null) {
        val parserFactory = AssSubtitleParserFactory(assHandler)
        DefaultMediaSourceFactory(dataSource, extractors.withAssMkvSupport(parserFactory, assHandler).finished()).setSubtitleParserFactory(parserFactory)
    } else {
        DefaultMediaSourceFactory(dataSource, extractors.finished())
    }

    // High-bitrate direct plays (4K remuxes at ~70 Mbps) need a bigger buffer runway than the
    // default 50 s max: a short network dip should not stall playback. A deeper rebuffer
    // threshold means each recovery starts with enough cushion to ride out the next dip.
    val maxBufferMs = if (settings.bufferSeconds > 0) settings.bufferSeconds * 1_000 else 240_000
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(120_000, maxBufferMs, 4_000, 30_000)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    // Prefer an English 5.1 track when one exists: English otherwise, then the container default.
    // Both are soft preferences, so a file with only 8-channel audio still gets sound.
    val trackSelector = DefaultTrackSelector(context).apply {
        parameters = parameters.buildUpon()
            .setPreferredAudioLanguage("en")
            .setMaxAudioChannelCount(if (settings.audioChannelCap > 0) settings.audioChannelCap else 6)
            .build()
    }

    val player = ExoPlayer.Builder(context)
        .setRenderersFactory(if (assHandler != null) baseRenderers.withAssSupport(assHandler) else baseRenderers)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(loadControl)
        .setTrackSelector(trackSelector)
        .setAudioAttributes(MOVIE_AUDIO_ATTRIBUTES, /* handleAudioFocus = */ true)
        .setHandleAudioBecomingNoisy(true)
        .setVideoChangeFrameRateStrategy(
            if (settings.frameRateMatching == FrameRateMatching.Off) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS,
        )
        .build()
    if (assHandler != null) {
        playerView.subtitleView?.withAssSupport(assHandler)
        assHandler.init(player)
    }

    val stats = PlaybackStatsCollector(context, request)
    player.addAnalyticsListener(stats)
    return PlayerEngine(player, stats, assHandler)
}

/**
 * Platform renderers first and FFmpeg after them, or FFmpeg first for a stream type whose device
 * path failed. The audio sink decodes to PCM only when [pcmOnly]; otherwise it passes through
 * what the output accepts.
 */
private class BlueWolfRenderersFactory(
    context: Context,
    private val pcmOnly: Boolean,
    private val localVideoFirst: Boolean,
    private val localAudioFirst: Boolean,
    private val pcmChannelCap: Int?,
    private val subtitleOffsetUs: () -> Long,
) : NextRenderersFactory(context) {

    override fun buildTextRenderers(
        context: Context,
        textOutput: androidx.media3.exoplayer.text.TextOutput,
        textRendererLooper: android.os.Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        out.add(androidx.media3.exoplayer.text.TextRenderer(textOutput, textRendererLooper, OffsettingSubtitleDecoderFactory(subtitleOffsetUs)))
    }
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        val start = out.size
        super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, allowedVideoJoiningTimeMs, out)
        if (localVideoFirst) out.subList(start, out.size).sortBy { it !is FfmpegVideoRenderer }
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        val start = out.size
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out)
        if (localAudioFirst) out.subList(start, out.size).sortBy { it !is FfmpegAudioRenderer }
    }

    @Suppress("DEPRECATION") // The context-less builder is the one that honours explicit capabilities.
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
        val builder = when {
            // Debug only: caps PCM output for devices that mishandle wide PCM output.
            pcmChannelCap != null -> DefaultAudioSink.Builder().setAudioCapabilities(AudioCapabilities(IntArray(0), pcmChannelCap))
            pcmOnly -> DefaultAudioSink.Builder().setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
            else -> DefaultAudioSink.Builder(context)
        }
        return builder.setEnableFloatOutput(enableFloatOutput).setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams).build()
    }
}

/**
 * With Dolby Vision turned off no Dolby Vision decoder is offered, so the renderer falls back to
 * the track's backwards-compatible layer on the plain HEVC/AVC/AV1 decoder.
 */
private fun dolbyVisionSelector(enabled: Boolean) = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
    if (!enabled && mimeType == MimeTypes.VIDEO_DOLBY_VISION) {
        emptyList()
    } else {
        MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
    }
}

/** Sorts a player error into the kinds the fallback order distinguishes. */
internal fun PlaybackException.toErrorKind(): PlayerErrorKind {
    val exo = this as? ExoPlaybackException
    if (exo?.type == ExoPlaybackException.TYPE_RENDERER) {
        return when {
            exo.rendererException.isAudioOutputError() -> PlayerErrorKind.AudioOutput
            else -> when (MimeTypes.getTrackType(exo.rendererFormat?.sampleMimeType)) {
                C.TRACK_TYPE_VIDEO -> PlayerErrorKind.VideoDecoder
                C.TRACK_TYPE_AUDIO -> PlayerErrorKind.AudioDecoder
                else -> PlayerErrorKind.Other
            }
        }
    }
    return when (errorCode) {
        in 5000..5999 -> PlayerErrorKind.AudioDecoder
        in 2000..3999 -> PlayerErrorKind.Source
        else -> PlayerErrorKind.Other
    }
}

private fun Throwable.isAudioOutputError() = generateSequence(this) { it.cause }.any {
    it is AudioSink.InitializationException || it is AudioSink.WriteException || it is AudioSink.ConfigurationException
}
