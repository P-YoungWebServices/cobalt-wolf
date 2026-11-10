package com.pyoungws.bluewolfplayer.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackSelectionTest {
    private val auto = PlaybackSettings()

    private fun hevc(range: VideoRange, capabilities: DeviceCapabilities, settings: PlaybackSettings = auto) =
        hdrOutput(range, VideoCodec.Hevc, capabilities, settings)

    @Test
    fun `a dolby vision tv behind a capable decoder gets every format natively`() {
        assertEquals(HdrOutput.Hdr10, hevc(VideoRange.Hdr10, TV_CAPABILITIES))
        assertEquals(HdrOutput.Hdr10Plus, hevc(VideoRange.Hdr10Plus, TV_CAPABILITIES))
        assertEquals(HdrOutput.Hlg, hevc(VideoRange.Hlg, TV_CAPABILITIES))
        assertEquals(HdrOutput.DolbyVision, hevc(VideoRange.DolbyVision, TV_CAPABILITIES))
        assertEquals(HdrOutput.DolbyVision, hevc(VideoRange.DolbyVisionWithHdr10, TV_CAPABILITIES))
    }

    @Test
    fun `dolby vision the decoder has no profile for plays from its base layer`() {
        // The TV fixture decodes profiles 5 and 8, not 7.
        assertEquals(HdrOutput.Hdr10, hevc(VideoRange.DolbyVisionWithEl, TV_CAPABILITIES))
        assertEquals(HdrOutput.Hdr10Plus, hevc(VideoRange.DolbyVisionWithElHdr10Plus, TV_CAPABILITIES))
        assertEquals(HdrOutput.DolbyVision, hevc(VideoRange.DolbyVisionWithEl, TV_CAPABILITIES.copy(dolbyVisionProfiles = setOf(5, 7, 8))))
    }

    @Test
    fun `without a dolby vision display the base layer is used and profile 5 needs the server`() {
        assertEquals(HdrOutput.Hdr10, hevc(VideoRange.DolbyVisionWithHdr10, PHONE_CAPABILITIES))
        assertEquals(HdrOutput.Hlg, hevc(VideoRange.DolbyVisionWithHlg, PHONE_CAPABILITIES))
        assertEquals(HdrOutput.Sdr, hevc(VideoRange.DolbyVisionWithSdr, PHONE_CAPABILITIES))
        assertNull(hevc(VideoRange.DolbyVision, PHONE_CAPABILITIES))
    }

    @Test
    fun `hdr10 plus degrades to hdr10 on an hdr10 display`() {
        assertEquals(HdrOutput.Hdr10, hevc(VideoRange.Hdr10Plus, PHONE_CAPABILITIES))
        assertEquals(HdrOutput.Hdr10, hevc(VideoRange.DolbyVisionWithHdr10Plus, PHONE_CAPABILITIES))
    }

    @Test
    fun `an sdr display or a decoder without 10 bit leaves hdr to the server`() {
        val sdrDisplay = TV_CAPABILITIES.copy(displayHdrTypes = emptySet())
        assertNull(hevc(VideoRange.Hdr10, sdrDisplay))
        assertNull(hevc(VideoRange.Hlg, sdrDisplay))
        assertEquals(HdrOutput.Sdr, hevc(VideoRange.Sdr, sdrDisplay))
        // The fixtures' H.264 decoders are 8 bit, and FFmpeg cannot output HDR.
        assertNull(hdrOutput(VideoRange.Hdr10, VideoCodec.H264, TV_CAPABILITIES, auto))
        assertNull(hdrOutput(VideoRange.Hdr10, VideoCodec.Av1, PHONE_CAPABILITIES, auto))
    }

    @Test
    fun `av1 dolby vision needs profile 10`() {
        assertEquals(HdrOutput.Hdr10, hdrOutput(VideoRange.DolbyVisionWithHdr10, VideoCodec.Av1, TV_CAPABILITIES, auto))
        assertEquals(
            HdrOutput.DolbyVision,
            hdrOutput(VideoRange.DolbyVisionWithHdr10, VideoCodec.Av1, TV_CAPABILITIES.copy(dolbyVisionProfiles = setOf(10)), auto),
        )
    }

    @Test
    fun `the hdr overrides turn dolby vision or all hdr off`() {
        val noDolbyVision = auto.copy(dolbyVision = false)
        assertEquals(HdrOutput.Hdr10, hevc(VideoRange.DolbyVisionWithHdr10, TV_CAPABILITIES, noDolbyVision))
        assertNull(hevc(VideoRange.DolbyVision, TV_CAPABILITIES, noDolbyVision))

        val sdrOnly = auto.copy(hdr = HdrPreference.SdrOnly)
        assertNull(hevc(VideoRange.Hdr10, TV_CAPABILITIES, sdrOnly))
        assertNull(hevc(VideoRange.DolbyVisionWithHdr10, TV_CAPABILITIES, sdrOnly))
        assertEquals(HdrOutput.Sdr, hevc(VideoRange.DolbyVisionWithSdr, TV_CAPABILITIES, sdrOnly))
        assertEquals(HdrOutput.Sdr, hevc(VideoRange.Sdr, TV_CAPABILITIES, sdrOnly))
    }

    @Test
    fun `dolby vision codec strings give the profile and base layer`() {
        assertEquals(DolbyVisionTrack(8, VideoCodec.Hevc), parseDolbyVisionCodecs("dvhe.08.06"))
        assertEquals(DolbyVisionTrack(7, VideoCodec.Hevc), parseDolbyVisionCodecs("dvh1.07.06"))
        assertEquals(DolbyVisionTrack(5, null), parseDolbyVisionCodecs("dvhe.05.09"))
        assertEquals(DolbyVisionTrack(9, VideoCodec.H264), parseDolbyVisionCodecs("dvav.09.05"))
        assertEquals(DolbyVisionTrack(10, VideoCodec.Av1), parseDolbyVisionCodecs("dav1.10.09"))
        assertNull(parseDolbyVisionCodecs("hvc1.2.4.L153.B0"))
        assertNull(parseDolbyVisionCodecs("dvhe"))
        assertNull(parseDolbyVisionCodecs(null))
    }

    @Test
    fun `a dolby vision track the device cannot show as such is decoded from its base layer`() {
        // The TV fixture decodes profiles 5 and 8 on a Dolby Vision display.
        assertNull(dolbyVisionBaseLayerFallback("dvhe.08.06", TV_CAPABILITIES, auto))
        assertNull(dolbyVisionBaseLayerFallback("dvhe.05.06", TV_CAPABILITIES, auto))
        assertEquals(VideoCodec.Hevc, dolbyVisionBaseLayerFallback("dvhe.07.06", TV_CAPABILITIES, auto))
        assertEquals(VideoCodec.Hevc, dolbyVisionBaseLayerFallback("dvhe.08.06", TV_CAPABILITIES, auto.copy(dolbyVision = false)))
        assertEquals(VideoCodec.Hevc, dolbyVisionBaseLayerFallback("dvhe.08.06", PHONE_CAPABILITIES, auto))
        assertEquals(VideoCodec.Av1, dolbyVisionBaseLayerFallback("dav1.10.09", PHONE_CAPABILITIES, auto))
        // Profile 5 has no base layer: nothing to fall back to on the device.
        assertNull(dolbyVisionBaseLayerFallback("dvhe.05.06", PHONE_CAPABILITIES, auto))
        assertNull(dolbyVisionBaseLayerFallback("hvc1.2.4.L153.B0", PHONE_CAPABILITIES, auto))
    }

    @Test
    fun `video uses the device decoder, then ffmpeg up to 1080p, then the server`() {
        val hevc = videoSupport(VideoCodec.Hevc, PHONE_CAPABILITIES, auto)!!
        assertEquals(DecodePath.Device, hevc.path)
        assertEquals(4096 to 2176, hevc.maxWidth to hevc.maxHeight)
        assertEquals(10, hevc.maxBitDepth)
        assertEquals(setOf(VideoRange.Sdr, VideoRange.Hdr10, VideoRange.Hdr10Plus, VideoRange.Hlg), hevc.ranges.filterNot { it.isDolbyVision }.toSet())

        val av1 = videoSupport(VideoCodec.Av1, PHONE_CAPABILITIES, auto)!!
        assertEquals(DecodePath.Local, av1.path)
        assertNull(av1.decoderName)
        assertEquals(LOCAL_DECODE_MAX_WIDTH to LOCAL_DECODE_MAX_HEIGHT, av1.maxWidth to av1.maxHeight)
        assertEquals(setOf(VideoRange.Sdr), av1.ranges)

        assertNull(videoSupport(VideoCodec.Vc1, PHONE_CAPABILITIES, auto))
    }

    @Test
    fun `ffmpeg lifts an 8 bit decoder to 10 bit, such as h264 hi10p`() {
        assertEquals(10, videoSupport(VideoCodec.H264, PHONE_CAPABILITIES, auto)!!.maxBitDepth)
        assertEquals(8, videoSupport(VideoCodec.H264, PHONE_CAPABILITIES.copy(localVideoCodecs = emptySet()), auto)!!.maxBitDepth)
    }

    @Test
    fun `the decoding override prefers ffmpeg or sends everything to the server`() {
        val preferLocal = auto.copy(decoding = DecodingPreference.PreferLocal)
        assertEquals(DecodePath.Local, videoSupport(VideoCodec.Hevc, PHONE_CAPABILITIES, preferLocal)!!.path)
        assertEquals(DecodePath.Local, audioPath(AudioCodec.Aac, PHONE_CAPABILITIES, preferLocal))
        assertEquals(DecodePath.Local, audioPath(AudioCodec.TrueHd, TV_CAPABILITIES, preferLocal))

        val transcode = auto.copy(decoding = DecodingPreference.AlwaysTranscode)
        assertNull(videoSupport(VideoCodec.H264, TV_CAPABILITIES, transcode))
        assertEquals(DecodePath.Transcode, audioPath(AudioCodec.Aac, TV_CAPABILITIES, transcode))
    }

    @Test
    fun `audio prefers passthrough, then a device decoder, then ffmpeg, then the server`() {
        assertEquals(DecodePath.Passthrough, audioPath(AudioCodec.TrueHd, TV_CAPABILITIES, auto))
        assertEquals(DecodePath.Passthrough, audioPath(AudioCodec.Dts, TV_CAPABILITIES, auto))
        assertEquals(DecodePath.Passthrough, audioPath(AudioCodec.Eac3, TV_CAPABILITIES, auto))
        assertEquals(DecodePath.Device, audioPath(AudioCodec.Aac, TV_CAPABILITIES, auto))

        assertEquals(DecodePath.Local, audioPath(AudioCodec.TrueHd, PHONE_CAPABILITIES, auto))
        assertEquals(DecodePath.Local, audioPath(AudioCodec.Ac3, PHONE_CAPABILITIES, auto))
        assertEquals(DecodePath.Device, audioPath(AudioCodec.Pcm, PHONE_CAPABILITIES, auto))
        assertEquals(DecodePath.Transcode, audioPath(AudioCodec.TrueHd, PHONE_CAPABILITIES.copy(localAudioCodecs = emptySet()), auto))
    }

    @Test
    fun `the pcm override decodes what would have been passed through`() {
        val pcm = auto.copy(audioOutput = AudioOutputPreference.PcmOnly)
        assertEquals(DecodePath.Device, audioPath(AudioCodec.Ac3, TV_CAPABILITIES, pcm))
        assertEquals(DecodePath.Local, audioPath(AudioCodec.TrueHd, TV_CAPABILITIES, pcm))
    }

    @Test
    fun `display mode switching is automatic on tv only`() {
        assertEquals(true, auto.allowsDisplayModeSwitch(isTv = true))
        assertEquals(false, auto.allowsDisplayModeSwitch(isTv = false))
        assertEquals(false, auto.copy(frameRateMatching = FrameRateMatching.SeamlessOnly).allowsDisplayModeSwitch(isTv = true))
        assertEquals(false, auto.copy(frameRateMatching = FrameRateMatching.Off).allowsDisplayModeSwitch(isTv = true))
    }

    // --- Fallback order ---

    private val sdr1080 = SourceMediaInfo(video = VideoStreamInfo(codec = "h264", width = 1920, height = 1080, range = VideoRange.Sdr), audio = AudioStreamInfo(codec = "dts"))

    private fun next(
        error: PlayerErrorKind,
        plan: DecodePlan = DecodePlan(),
        info: SourceMediaInfo? = sdr1080,
        method: PlaybackMethod = PlaybackMethod.DirectPlay,
        capabilities: DeviceCapabilities = PHONE_CAPABILITIES,
        canTranscode: Boolean = true,
    ) = nextFallback(method, plan, error, info, capabilities, canTranscode)

    @Test
    fun `decoder errors fall back to ffmpeg for the stream that failed`() {
        assertEquals(FallbackStep.LocalDecode(DecodePlan(video = DecoderChoice.Local)), next(PlayerErrorKind.VideoDecoder))
        assertEquals(FallbackStep.LocalDecode(DecodePlan(audio = DecoderChoice.Local)), next(PlayerErrorKind.AudioDecoder))
        assertEquals(
            FallbackStep.LocalDecode(DecodePlan(DecoderChoice.Local, DecoderChoice.Local)),
            next(PlayerErrorKind.AudioDecoder, plan = DecodePlan(video = DecoderChoice.Local)),
        )
    }

    @Test
    fun `when ffmpeg already failed or cannot help the server transcodes`() {
        assertEquals(FallbackStep.Transcode, next(PlayerErrorKind.VideoDecoder, plan = DecodePlan(video = DecoderChoice.Local)))
        assertEquals(FallbackStep.Transcode, next(PlayerErrorKind.AudioDecoder, plan = DecodePlan(audio = DecoderChoice.Local)))
        assertEquals(FallbackStep.Transcode, next(PlayerErrorKind.VideoDecoder, info = sdr1080.copy(video = VideoStreamInfo(codec = "vc1"))))
        assertEquals(FallbackStep.Transcode, next(PlayerErrorKind.VideoDecoder, info = sdr1080.copy(video = VideoStreamInfo(codec = "hevc", width = 3840, height = 2160))))
        assertEquals(FallbackStep.Transcode, next(PlayerErrorKind.VideoDecoder, info = sdr1080.copy(video = VideoStreamInfo(codec = "hevc", width = 1920, height = 1080, range = VideoRange.Hdr10))))
        assertEquals(FallbackStep.Transcode, next(PlayerErrorKind.VideoDecoder, capabilities = PHONE_CAPABILITIES.copy(localVideoCodecs = emptySet())))
        assertEquals(FallbackStep.Transcode, next(PlayerErrorKind.Source))
        assertEquals(FallbackStep.Transcode, next(PlayerErrorKind.Other))
    }

    @Test
    fun `a transcode that fails can only try ffmpeg`() {
        assertEquals(
            FallbackStep.LocalDecode(DecodePlan(video = DecoderChoice.Local)),
            next(PlayerErrorKind.VideoDecoder, method = PlaybackMethod.Transcode),
        )
        assertEquals(FallbackStep.Fail, next(PlayerErrorKind.VideoDecoder, plan = DecodePlan(video = DecoderChoice.Local), method = PlaybackMethod.Transcode))
        assertEquals(FallbackStep.Fail, next(PlayerErrorKind.Source, method = PlaybackMethod.Transcode))
    }

    @Test
    fun `without a server ffmpeg is tried even for 4k hdr, and then playback fails`() {
        val uhd = SourceMediaInfo(video = VideoStreamInfo(codec = "hevc", width = 3840, height = 2160, range = VideoRange.Hdr10))
        assertEquals(FallbackStep.LocalDecode(DecodePlan(video = DecoderChoice.Local)), next(PlayerErrorKind.VideoDecoder, info = uhd, canTranscode = false))
        assertEquals(FallbackStep.LocalDecode(DecodePlan(video = DecoderChoice.Local)), next(PlayerErrorKind.VideoDecoder, info = null, canTranscode = false))
        assertEquals(FallbackStep.Fail, next(PlayerErrorKind.VideoDecoder, plan = DecodePlan(video = DecoderChoice.Local), info = null, canTranscode = false))
        assertEquals(FallbackStep.Fail, next(PlayerErrorKind.Source, info = null, canTranscode = false))
    }

    @Test
    fun `ffmpeg is tried for 4k hdr when the server would not re-encode the video anyway`() {
        val uhd = SourceMediaInfo(video = VideoStreamInfo(codec = "hevc", width = 3840, height = 2160, range = VideoRange.Hdr10))

        assertEquals(FallbackStep.Transcode, nextFallback(PlaybackMethod.DirectPlay, DecodePlan(), PlayerErrorKind.VideoDecoder, uhd, PHONE_CAPABILITIES, canTranscode = true))
        assertEquals(
            FallbackStep.LocalDecode(DecodePlan(video = DecoderChoice.Local)),
            nextFallback(PlaybackMethod.DirectPlay, DecodePlan(), PlayerErrorKind.VideoDecoder, uhd, PHONE_CAPABILITIES, canTranscode = true, serverTranscodesVideo = false),
        )
        val unlimited = videoSupport(VideoCodec.Hevc, PHONE_CAPABILITIES, auto, serverTranscodesVideo = false)!!
        assertEquals(VideoRange.entries.toSet(), unlimited.ranges)
        assertEquals(Int.MAX_VALUE, unlimited.maxWidth)
        assertNull(videoSupport(VideoCodec.Vc1, PHONE_CAPABILITIES, auto, serverTranscodesVideo = false))
    }

    @Test
    fun `the prefer ffmpeg override starts on ffmpeg`() {
        assertEquals(DecodePlan(), initialDecodePlan(auto))
        assertEquals(DecodePlan(DecoderChoice.Local, DecoderChoice.Local), initialDecodePlan(auto.copy(decoding = DecodingPreference.PreferLocal)))
    }

    @Test
    fun `codec names from the server are recognised`() {
        assertEquals(VideoCodec.Hevc, VideoCodec.fromJellyfin("HEVC"))
        assertEquals(VideoCodec.Hevc, VideoCodec.fromJellyfin("h265"))
        assertEquals(VideoCodec.Mpeg2, VideoCodec.fromJellyfin("mpeg2video"))
        assertNull(VideoCodec.fromJellyfin("prores"))
        assertEquals(AudioCodec.Pcm, AudioCodec.fromJellyfin("pcm_s24le"))
        assertEquals(AudioCodec.Dts, AudioCodec.fromJellyfin("dca"))
        assertEquals(AudioCodec.TrueHd, AudioCodec.fromJellyfin("truehd"))
        assertNull(AudioCodec.fromJellyfin(null))
    }
}
