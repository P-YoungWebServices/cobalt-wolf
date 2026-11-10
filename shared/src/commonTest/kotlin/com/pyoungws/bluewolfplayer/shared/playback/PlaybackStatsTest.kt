package com.pyoungws.bluewolfplayer.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackStatsTest {
    private fun hdr(
        source: VideoRange? = null,
        dolbyVisionTrack: Boolean = false,
        profile: Int? = null,
        transfer: HdrOutput = HdrOutput.Sdr,
        dolbyVisionDecoder: Boolean = false,
        kind: DecoderKind? = DecoderKind.Hardware,
    ) = describeHdr(source, dolbyVisionTrack, profile, transfer, dolbyVisionDecoder, kind)

    @Test
    fun `the server's classification names the range`() {
        assertEquals("HDR10+", hdr(source = VideoRange.Hdr10Plus, transfer = HdrOutput.Hdr10))
        assertEquals("SDR", hdr(source = VideoRange.Sdr))
    }

    @Test
    fun `without the server the stream's transfer function is used`() {
        assertEquals("HDR10", hdr(transfer = HdrOutput.Hdr10))
        assertEquals("HLG", hdr(transfer = HdrOutput.Hlg))
        assertEquals("SDR", hdr())
    }

    @Test
    fun `dolby vision shows as such only when a dolby vision decoder is used`() {
        assertEquals("Dolby Vision (profile 8)", hdr(VideoRange.DolbyVisionWithHdr10, dolbyVisionTrack = true, profile = 8, transfer = HdrOutput.Hdr10, dolbyVisionDecoder = true))
        assertEquals("Dolby Vision", hdr(dolbyVisionTrack = true, transfer = HdrOutput.Hdr10, dolbyVisionDecoder = true))
        assertEquals("HDR10 (Dolby Vision base layer)", hdr(VideoRange.DolbyVisionWithHdr10, dolbyVisionTrack = true, profile = 8, transfer = HdrOutput.Hdr10))
        assertEquals("HDR10 (Dolby Vision base layer)", hdr(dolbyVisionTrack = true, transfer = HdrOutput.Hdr10))
        assertEquals("HLG (Dolby Vision base layer)", hdr(VideoRange.DolbyVisionWithHlg, transfer = HdrOutput.Hlg))
    }

    @Test
    fun `ffmpeg output is flagged as sdr`() {
        assertEquals("HDR10, shown as SDR (software decode)", hdr(transfer = HdrOutput.Hdr10, kind = DecoderKind.Ffmpeg))
        assertEquals("SDR", hdr(kind = DecoderKind.Ffmpeg))
    }

    @Test
    fun `the summary lists known fields on one line`() {
        val stats = PlaybackStats(
            level = PlaybackLevel.DirectPlay, videoCodec = "video/hevc", width = 3840, height = 2160, hdr = "Dolby Vision (profile 8)",
            audioCodec = "audio/true-hd", audioOutput = AudioOutputMode.Passthrough, audioOutputFormat = "Dolby TrueHD",
            sampleRate = 48_000, channels = 8, droppedFrames = 3,
        )

        assertEquals(
            "level=DirectPlay video=video/hevc size=3840x2160 hdr=Dolby_Vision_(profile_8) audio=audio/true-hd " +
                "audioOutput=Passthrough audioFormat=Dolby_TrueHD sampleRate=48000 channels=8 dropped=3",
            stats.summary(),
        )
        assertEquals("dropped=0", PlaybackStats().summary())
    }

    @Test
    fun `the level in effect follows the decoders in use`() {
        assertEquals(PlaybackLevel.DirectPlay, effectiveLevel(PlaybackMethod.DirectPlay, DecoderKind.Hardware, null))
        assertEquals(PlaybackLevel.DirectPlay, effectiveLevel(PlaybackMethod.DirectStream, DecoderKind.Software, DecoderKind.Software))
        assertEquals(PlaybackLevel.LocalDecode, effectiveLevel(PlaybackMethod.DirectPlay, DecoderKind.Hardware, DecoderKind.Ffmpeg))
        assertEquals(PlaybackLevel.LocalDecode, effectiveLevel(PlaybackMethod.DirectPlay, DecoderKind.Ffmpeg, null))
        assertEquals(PlaybackLevel.Transcode, effectiveLevel(PlaybackMethod.Transcode, DecoderKind.Ffmpeg, DecoderKind.Hardware))
    }
}
