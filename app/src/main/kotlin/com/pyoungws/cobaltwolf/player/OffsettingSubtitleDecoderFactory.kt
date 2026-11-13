package com.pyoungws.cobaltwolf.player

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SimpleSubtitleDecoder
import androidx.media3.extractor.text.Subtitle
import androidx.media3.extractor.text.SubtitleDecoder
import androidx.media3.extractor.text.SubtitleParser

/**
 * Wraps the default subtitle decoders so every cue's timing shifts by [offsetUs] (positive shows
 * cues later), which is what the player's subtitle-delay control drives. Formats without a text
 * parser — closed captions like CEA-608 — fall back to exact timing. ASS/SSA rendered by libass
 * bypass this path entirely, so the delay does not move styled subtitles.
 */
@OptIn(UnstableApi::class)
internal class OffsettingSubtitleDecoderFactory(private val offsetUs: () -> Long) : SubtitleDecoderFactory {
    private val parsers = DefaultSubtitleParserFactory()
    private val fallback = SubtitleDecoderFactory.DEFAULT

    override fun supportsFormat(format: Format): Boolean = fallback.supportsFormat(format)

    override fun createDecoder(format: Format): SubtitleDecoder =
        if (parsers.supportsFormat(format)) {
            OffsettingSubtitleDecoder(format.sampleMimeType ?: "subtitle", parsers.create(format), offsetUs)
        } else {
            fallback.createDecoder(format)
        }

    private class OffsettingSubtitleDecoder(
        name: String,
        private val parser: SubtitleParser,
        private val offsetUs: () -> Long,
    ) : SimpleSubtitleDecoder(name) {
        override fun decode(data: ByteArray, length: Int, reset: Boolean): Subtitle {
            if (reset) parser.reset()
            return DelayedSubtitle(parser.parseToLegacySubtitle(data, 0, length), offsetUs)
        }
    }
}

/** Shifts every event time by [offsetUs] so a positive offset delays the cues. */
@OptIn(UnstableApi::class)
internal class DelayedSubtitle(private val delegate: Subtitle, private val offsetUs: () -> Long) : Subtitle {
    override fun getNextEventTimeIndex(timeUs: Long): Int = delegate.getNextEventTimeIndex(timeUs - offsetUs())
    override fun getEventTimeCount(): Int = delegate.getEventTimeCount()
    override fun getEventTime(index: Int): Long = delegate.getEventTime(index) + offsetUs()
    override fun getCues(timeUs: Long): List<Cue> = delegate.getCues(timeUs - offsetUs())
}
