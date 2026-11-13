package com.pyoungws.cobaltwolf.shared.playback

import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceProfileTest {
    private fun DeviceProfile.videoCodecs() = directPlayProfiles.single().videoCodec!!.split(",")
    private fun DeviceProfile.audioCodecs() = directPlayProfiles.single().audioCodec!!.split(",")
    private fun DeviceProfile.codec(name: String): CodecProfile = codecProfiles.single { it.codec == name }
    private fun CodecProfile.value(property: ProfileConditionValue) = conditions.single { it.property == property }.value!!
    private fun CodecProfile.ranges() = value(ProfileConditionValue.VIDEO_RANGE_TYPE).split("|").toSet()

    @Test
    fun `a phone direct plays what its decoders or ffmpeg handle`() {
        val profile = buildDeviceProfile(PHONE_CAPABILITIES, PlaybackSettings())

        assertEquals(listOf("h264", "hevc", "av1", "vp9", "vp8", "mpeg2video"), profile.videoCodecs())
        assertTrue(profile.audioCodecs().containsAll(listOf("aac", "ac3", "eac3", "truehd", "dts", "flac", "pcm_s16le", "pcm_s24le")))
        assertEquals("aac", profile.transcodingProfiles.single().audioCodec)
    }

    @Test
    fun `each codec is limited to the picture its best decoder takes`() {
        val profile = buildDeviceProfile(PHONE_CAPABILITIES, PlaybackSettings())

        val hevc = profile.codec("hevc")
        assertEquals("4096", hevc.value(ProfileConditionValue.WIDTH))
        assertEquals("2176", hevc.value(ProfileConditionValue.HEIGHT))
        assertEquals("10", hevc.value(ProfileConditionValue.VIDEO_BIT_DEPTH))
        // AV1 is FFmpeg only on the phone fixture: 1080p, SDR.
        val av1 = profile.codec("av1")
        assertEquals("1920", av1.value(ProfileConditionValue.WIDTH))
        assertEquals("1088", av1.value(ProfileConditionValue.HEIGHT))
        assertEquals(setOf("SDR"), av1.ranges())
        assertTrue(hevc.conditions.none { it.isRequired })
    }

    @Test
    fun `hdr the display cannot show is left out so the server tone-maps it`() {
        val phone = buildDeviceProfile(PHONE_CAPABILITIES, PlaybackSettings()).codec("hevc").ranges()
        assertEquals(
            setOf("SDR", "HDR10", "HDR10Plus", "HLG", "DOVIWithHDR10", "DOVIWithHLG", "DOVIWithSDR", "DOVIWithEL", "DOVIWithHDR10Plus", "DOVIWithELHDR10Plus"),
            phone,
        )

        val tv = buildDeviceProfile(TV_CAPABILITIES, PlaybackSettings()).codec("hevc").ranges()
        assertTrue("DOVI" in tv)

        val sdrDisplay = buildDeviceProfile(TV_CAPABILITIES.copy(displayHdrTypes = emptySet()), PlaybackSettings()).codec("hevc").ranges()
        assertEquals(setOf("SDR", "DOVIWithSDR"), sdrDisplay)
    }

    @Test
    fun `a receiver gets dolby digital when the server has to transcode`() {
        val transcoding = buildDeviceProfile(TV_CAPABILITIES, PlaybackSettings()).transcodingProfiles.single()

        assertEquals("ac3,aac", transcoding.audioCodec)
        assertEquals("6", transcoding.maxAudioChannels)
        assertEquals("2", buildDeviceProfile(TV_CAPABILITIES.copy(maxPcmChannels = 2), PlaybackSettings()).transcodingProfiles.single().maxAudioChannels)
    }

    @Test
    fun `without ffmpeg or passthrough unsupported codecs are left to the server`() {
        val bare = PHONE_CAPABILITIES.copy(localVideoCodecs = emptySet(), localAudioCodecs = emptySet())
        val profile = buildDeviceProfile(bare, PlaybackSettings())

        assertEquals(listOf("h264", "hevc", "vp9"), profile.videoCodecs())
        assertFalse("truehd" in profile.audioCodecs())
        assertFalse("ac3" in profile.audioCodecs())
        assertEquals("8", profile.codec("h264").value(ProfileConditionValue.VIDEO_BIT_DEPTH))
    }

    @Test
    fun `no video limits are sent when the server would only repackage the video`() {
        val profile = buildDeviceProfile(PHONE_CAPABILITIES, PlaybackSettings(), serverTranscodesVideo = false)

        assertEquals(listOf("h264", "hevc", "av1", "vp9", "vp8", "mpeg2video"), profile.videoCodecs())
        assertEquals(emptyList(), profile.codecProfiles)
    }

    @Test
    fun `always transcode offers nothing for direct play`() {
        val profile = buildDeviceProfile(TV_CAPABILITIES, PlaybackSettings(decoding = DecodingPreference.AlwaysTranscode))

        assertEquals(emptyList(), profile.directPlayProfiles)
        assertEquals(emptyList(), profile.codecProfiles)
        assertEquals(1, profile.transcodingProfiles.size)
    }

    @Test
    fun `the preferred transcode codec changes what the server is offered`() {
        assertEquals("h264", buildDeviceProfile(TV_CAPABILITIES, PlaybackSettings()).transcodingProfiles.single().videoCodec)
        assertEquals("ts", buildDeviceProfile(TV_CAPABILITIES, PlaybackSettings()).transcodingProfiles.single().container)

        val hevc = buildDeviceProfile(TV_CAPABILITIES, PlaybackSettings(transcodeCodec = TranscodeCodec.Hevc)).transcodingProfiles.single()
        assertEquals("hevc,h264", hevc.videoCodec)
        assertEquals("mp4", hevc.container)

        val av1 = buildDeviceProfile(TV_CAPABILITIES, PlaybackSettings(transcodeCodec = TranscodeCodec.Av1)).transcodingProfiles.single()
        assertEquals("av1,hevc,h264", av1.videoCodec)
        assertEquals("mp4", av1.container)
    }

    @Test
    fun `a codec level cap becomes a profile condition so the server transcodes above it`() {
        val profile = buildDeviceProfile(TV_CAPABILITIES, PlaybackSettings(h264LevelCap = 42, hevcLevelCap = 153))

        assertEquals("42", profile.codec("h264").value(ProfileConditionValue.VIDEO_LEVEL))
        assertEquals("153", profile.codec("hevc").value(ProfileConditionValue.VIDEO_LEVEL))
        assertTrue(profile.codec("vp9").conditions.none { it.property == ProfileConditionValue.VIDEO_LEVEL })
        assertTrue(buildDeviceProfile(TV_CAPABILITIES, PlaybackSettings()).codec("h264").conditions.none { it.property == ProfileConditionValue.VIDEO_LEVEL })
    }

    @Test
    fun `styled and bitmap subtitles are taken from the container, text ones also as files`() {
        val subtitles = buildDeviceProfile(PHONE_CAPABILITIES, PlaybackSettings()).subtitleProfiles
        val embedded = subtitles.filter { it.method == SubtitleDeliveryMethod.EMBED }.map { it.format }
        val external = subtitles.filter { it.method == SubtitleDeliveryMethod.EXTERNAL }.map { it.format }

        assertTrue(embedded.containsAll(listOf("ass", "ssa", "srt", "subrip", "pgssub")))
        assertTrue(external.containsAll(listOf("ass", "ssa", "srt")))
        assertFalse("pgssub" in external)
    }

    @Test
    fun `every range maps to a distinct server range type`() {
        assertEquals(VideoRange.entries.size, VideoRange.entries.map { it.toSdk() }.toSet().size)
        VideoRange.entries.forEach { assertEquals(it, it.toSdk().toVideoRange()) }
        assertEquals(null, org.jellyfin.sdk.model.api.VideoRangeType.DOVI_INVALID.toVideoRange())
    }
}
