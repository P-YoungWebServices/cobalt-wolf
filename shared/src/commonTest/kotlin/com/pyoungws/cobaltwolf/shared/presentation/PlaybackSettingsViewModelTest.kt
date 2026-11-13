package com.pyoungws.cobaltwolf.shared.presentation

import com.pyoungws.cobaltwolf.shared.playback.AudioCodec
import com.pyoungws.cobaltwolf.shared.playback.AudioOutputPreference
import com.pyoungws.cobaltwolf.shared.playback.DecodePath
import com.pyoungws.cobaltwolf.shared.playback.DecodingPreference
import com.pyoungws.cobaltwolf.shared.playback.FrameRateMatching
import com.pyoungws.cobaltwolf.shared.playback.HdrOutput
import com.pyoungws.cobaltwolf.shared.playback.HdrPreference
import com.pyoungws.cobaltwolf.shared.playback.InMemoryPlaybackSettingsStore
import com.pyoungws.cobaltwolf.shared.playback.PHONE_CAPABILITIES
import com.pyoungws.cobaltwolf.shared.playback.PlaybackSettings
import com.pyoungws.cobaltwolf.shared.playback.PlaybackSettingsRepository
import com.pyoungws.cobaltwolf.shared.playback.StreamBitrate
import com.pyoungws.cobaltwolf.shared.playback.TV_CAPABILITIES
import com.pyoungws.cobaltwolf.shared.playback.VideoCodec
import com.pyoungws.cobaltwolf.shared.playback.VideoRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackSettingsViewModelTest {
    private val store = InMemoryPlaybackSettingsStore()
    private var capabilities = TV_CAPABILITIES
    private val repository = PlaybackSettingsRepository(store)
    private val viewModel = PlaybackSettingsViewModel(repository) { capabilities }

    private fun state() = viewModel.uiState.value
    private fun audio(codec: AudioCodec) = state().audio.first { it.first == codec }.second
    private fun hdr(range: VideoRange) = state().hdr.first { it.range == range }.output

    @Test
    fun `shows what was detected and what will be done with it`() {
        assertEquals(TV_CAPABILITIES, state().capabilities)
        assertEquals(DecodePath.Device, state().video.first { it.first == VideoCodec.Hevc }.second?.path)
        assertNull(state().video.first { it.first == VideoCodec.Vc1 }.second)
        assertEquals(DecodePath.Passthrough, audio(AudioCodec.TrueHd))
        assertEquals(HdrOutput.DolbyVision, hdr(VideoRange.DolbyVisionWithHdr10))
        assertEquals(HdrOutput.Hdr10, hdr(VideoRange.DolbyVisionWithEl))
    }

    @Test
    fun `overrides are saved and change the selection`() {
        viewModel.setAudioOutput(AudioOutputPreference.PcmOnly)
        viewModel.setDolbyVision(false)
        viewModel.setHdr(HdrPreference.Auto)
        viewModel.setFrameRateMatching(FrameRateMatching.Off)
        viewModel.setStyledSubtitles(false)

        val expected = PlaybackSettings(
            dolbyVision = false, audioOutput = AudioOutputPreference.PcmOnly, frameRateMatching = FrameRateMatching.Off,
            styledSubtitles = false,
        )
        assertEquals(expected, state().settings)
        assertEquals(expected, store.saved)
        assertEquals(expected, repository.settings.value)
        assertEquals(DecodePath.Local, audio(AudioCodec.TrueHd))
        assertEquals(HdrOutput.Hdr10, hdr(VideoRange.DolbyVisionWithHdr10))
    }

    @Test
    fun `the bitrate cap is saved`() {
        viewModel.setMaxBitrate(StreamBitrate.Mbps20)

        assertEquals(StreamBitrate.Mbps20, state().settings.maxBitrate)
        assertEquals(StreamBitrate.Mbps20, store.saved.maxBitrate)
    }

    @Test
    fun `always transcode leaves nothing to the device`() {
        viewModel.setDecoding(DecodingPreference.AlwaysTranscode)

        assertEquals(true, state().video.all { it.second == null })
        assertEquals(true, state().audio.all { it.second == DecodePath.Transcode })
    }

    @Test
    fun `reset returns to automatic`() {
        viewModel.setDecoding(DecodingPreference.PreferLocal)
        viewModel.setHdr(HdrPreference.SdrOnly)

        viewModel.resetToAutomatic()

        assertEquals(PlaybackSettings(), store.saved)
    }

    @Test
    fun `refresh picks up a newly connected display or receiver`() {
        capabilities = PHONE_CAPABILITIES

        viewModel.refresh()

        assertEquals(PHONE_CAPABILITIES, state().capabilities)
        assertEquals(DecodePath.Local, audio(AudioCodec.TrueHd))
        assertNull(hdr(VideoRange.DolbyVision))
    }

    @Test
    fun `saved settings are loaded`() {
        val saved = PlaybackSettings(decoding = DecodingPreference.PreferLocal)

        assertEquals(saved, PlaybackSettingsRepository(InMemoryPlaybackSettingsStore(saved)).settings.value)
    }
}
