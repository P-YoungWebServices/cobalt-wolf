@file:OptIn(UnstableApi::class)

package com.pyoungws.cobaltwolf.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SniffFailure
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.TrackOutput
import com.pyoungws.cobaltwolf.shared.playback.DeviceCapabilities
import com.pyoungws.cobaltwolf.shared.playback.PlaybackSettings
import com.pyoungws.cobaltwolf.shared.playback.VideoCodec
import com.pyoungws.cobaltwolf.shared.playback.dolbyVisionBaseLayerFallback

/**
 * Presents Dolby Vision tracks that will not be played as Dolby Vision on this device as their
 * backwards-compatible base layer (plain HEVC, H.264 or AV1 with the same HDR10/HLG colour info).
 * That lets every decoder for the base codec take the track, including FFmpeg and including
 * profile 7, whose enhancement layer a base decoder simply skips. Tracks that do play as Dolby
 * Vision, and profile 5 (no base layer), are left untouched.
 */
internal fun ExtractorsFactory.withDolbyVisionBaseLayerFallback(capabilities: DeviceCapabilities, settings: PlaybackSettings): ExtractorsFactory {
    val rewrite = { format: Format -> format.toBaseLayer(capabilities, settings) }
    val delegate = this
    return object : ExtractorsFactory {
        override fun createExtractors(): Array<Extractor> = delegate.createExtractors().wrapped()

        override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
            delegate.createExtractors(uri, responseHeaders).wrapped()

        override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): ExtractorsFactory = apply {
            delegate.setSubtitleParserFactory(subtitleParserFactory)
        }

        @Deprecated("Deprecated in Media3")
        override fun experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled: Boolean): ExtractorsFactory = apply {
            @Suppress("DEPRECATION")
            delegate.experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled)
        }

        private fun Array<Extractor>.wrapped() = Array<Extractor>(size) { FormatRewritingExtractor(this[it], rewrite) }
    }
}

private fun Format.toBaseLayer(capabilities: DeviceCapabilities, settings: PlaybackSettings): Format {
    if (sampleMimeType != MimeTypes.VIDEO_DOLBY_VISION) return this
    val base = when (dolbyVisionBaseLayerFallback(codecs, capabilities, settings)) {
        VideoCodec.Hevc -> MimeTypes.VIDEO_H265
        VideoCodec.H264 -> MimeTypes.VIDEO_H264
        VideoCodec.Av1 -> MimeTypes.VIDEO_AV1
        else -> return this
    }
    // The Dolby Vision codecs string does not describe the base layer; decoders probe it themselves.
    return buildUpon().setSampleMimeType(base).setCodecs(null).build()
}

private class FormatRewritingExtractor(private val delegate: Extractor, private val rewrite: (Format) -> Format) : Extractor {
    override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

    override fun init(output: ExtractorOutput) = delegate.init(
        object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput = FormatRewritingTrackOutput(output.track(id, type), rewrite)
            override fun endTracks() = output.endTracks()
            override fun seekMap(seekMap: SeekMap) = output.seekMap(seekMap)
        },
    )

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = delegate.read(input, seekPosition)

    override fun seek(position: Long, timeUs: Long) = delegate.seek(position, timeUs)

    override fun release() = delegate.release()

    override fun getUnderlyingImplementation(): Extractor = delegate.underlyingImplementation

    override fun getSniffFailureDetails(): List<SniffFailure> = delegate.sniffFailureDetails
}

private class FormatRewritingTrackOutput(private val delegate: TrackOutput, private val rewrite: (Format) -> Format) : TrackOutput by delegate {
    override fun format(format: Format) = delegate.format(rewrite(format))

    // Interface defaults are not covered by `by`; forward the one that carries data.
    override fun durationUs(durationUs: Long) = delegate.durationUs(durationUs)
}
