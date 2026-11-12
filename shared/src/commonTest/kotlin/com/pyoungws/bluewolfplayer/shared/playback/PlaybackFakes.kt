package com.pyoungws.bluewolfplayer.shared.playback

import com.pyoungws.bluewolfplayer.shared.library.FakeLibraryDataSource
import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.model.Session
import org.jellyfin.sdk.model.api.DeviceProfile

val DIRECT_SOURCE = PlaybackSource(
    mediaSourceId = "ms-1",
    playSessionId = "ps-1",
    url = "https://jellyfin.example.com/Videos/m1/stream?static=true",
    method = PlaybackMethod.DirectPlay,
    subtitles = emptyList(),
    headers = emptyMap(),
)

class InMemoryPlaybackSettingsStore(var saved: PlaybackSettings = PlaybackSettings()) : PlaybackSettingsStore {
    override fun load() = saved
    override fun save(settings: PlaybackSettings) {
        saved = settings
    }
}

private fun decoder(codec: VideoCodec, width: Int, height: Int, tenBit: Boolean, hardware: Boolean = true) =
    VideoDecoderCapability(codec, "decoder.${codec.name.lowercase()}", hardware, width, height, tenBit)

private val FFMPEG_VIDEO = setOf(VideoCodec.H264, VideoCodec.Hevc, VideoCodec.Av1, VideoCodec.Vp9, VideoCodec.Vp8, VideoCodec.Mpeg2)
private val FFMPEG_AUDIO = AudioCodec.entries.toSet() - AudioCodec.Pcm

/** A phone: 1080p hardware decoders, an HDR10/HLG panel, stereo speakers and no passthrough. */
val PHONE_CAPABILITIES = DeviceCapabilities(
    videoDecoders = listOf(
        decoder(VideoCodec.H264, 1920, 1088, tenBit = false),
        decoder(VideoCodec.Hevc, 4096, 2176, tenBit = true),
        decoder(VideoCodec.Vp9, 4096, 2176, tenBit = true),
    ),
    displayHdrTypes = setOf(HdrType.Hdr10, HdrType.Hlg),
    displayModes = listOf(DisplayMode(1, 1080, 2400, 60f)),
    activeDisplayModeId = 1,
    audioDecoders = setOf(AudioCodec.Aac, AudioCodec.Mp3, AudioCodec.Flac, AudioCodec.Opus, AudioCodec.Vorbis, AudioCodec.Pcm),
    maxPcmChannels = 8,
    localVideoCodecs = FFMPEG_VIDEO,
    localAudioCodecs = FFMPEG_AUDIO,
)

/** A 4K streaming stick on a Dolby Vision TV behind an Atmos / DTS:X receiver. */
val TV_CAPABILITIES = DeviceCapabilities(
    videoDecoders = listOf(
        decoder(VideoCodec.H264, 4096, 2176, tenBit = false),
        decoder(VideoCodec.Hevc, 4096, 2176, tenBit = true),
        decoder(VideoCodec.Av1, 4096, 2176, tenBit = true),
        decoder(VideoCodec.Vp9, 4096, 2176, tenBit = true),
    ),
    dolbyVisionProfiles = setOf(5, 8),
    displayHdrTypes = setOf(HdrType.Hdr10, HdrType.Hdr10Plus, HdrType.Hlg, HdrType.DolbyVision),
    displayModes = listOf(DisplayMode(1, 3840, 2160, 60f), DisplayMode(2, 3840, 2160, 50f), DisplayMode(3, 3840, 2160, 24f), DisplayMode(4, 3840, 2160, 23.976f), DisplayMode(5, 1920, 1080, 24f)),
    activeDisplayModeId = 1,
    audioDecoders = setOf(AudioCodec.Aac, AudioCodec.Mp3, AudioCodec.Ac3, AudioCodec.Eac3, AudioCodec.Flac, AudioCodec.Opus, AudioCodec.Vorbis, AudioCodec.Pcm),
    passthrough = PassthroughFormat.entries.toSet(),
    maxPcmChannels = 8,
    localVideoCodecs = FFMPEG_VIDEO,
    localAudioCodecs = FFMPEG_AUDIO,
    isTv = true,
)

fun playbackRepository(
    library: FakeLibraryDataSource,
    playback: FakePlaybackDataSource,
    guard: SessionGuard,
    capabilities: DeviceCapabilities = PHONE_CAPABILITIES,
    settings: PlaybackSettings = PlaybackSettings(),
) = PlaybackRepository(library, playback, guard, { capabilities }, PlaybackSettingsRepository(InMemoryPlaybackSettingsStore(settings)))

class FakePlaybackDataSource : JellyfinPlaybackDataSource {
    val sourceRequests = mutableListOf<Triple<String, Long, Boolean>>()
    val profiles = mutableListOf<DeviceProfile>()
    val reports = mutableListOf<String>()
    val reported = mutableListOf<PlaybackReport>()

    var source: (forceTranscode: Boolean) -> PlaybackSource = { force ->
        if (force) DIRECT_SOURCE.copy(method = PlaybackMethod.Transcode, url = "https://jellyfin.example.com/hls") else DIRECT_SOURCE
    }

    val bitrates = mutableListOf<Int?>()

    override suspend fun getPlaybackSource(
        session: Session,
        itemId: String,
        startPositionTicks: Long,
        profile: DeviceProfile,
        forceTranscode: Boolean,
        maxBitrate: Int?,
    ): PlaybackSource {
        profiles += profile
        bitrates += maxBitrate
        sourceRequests += Triple(itemId, startPositionTicks, forceTranscode)
        return source(forceTranscode)
    }

    var transcodesVideo = true

    override suspend fun canTranscodeVideo(session: Session) = transcodesVideo

    override suspend fun reportStart(session: Session, report: PlaybackReport) {
        reports += "start"
        reported += report
    }

    override suspend fun reportProgress(session: Session, report: PlaybackReport) {
        reports += "progress"
        reported += report
    }

    override suspend fun reportStopped(session: Session, report: PlaybackReport) {
        reports += "stopped"
        reported += report
    }
}
