package com.pyoungws.bluewolfplayer.shared.playback

import com.pyoungws.bluewolfplayer.shared.library.SESSION
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlaybackSourceMappingTest {
    private fun source(
        directPlay: Boolean = true,
        directStream: Boolean = true,
        transcodingUrl: String? = null,
        streams: List<MediaStream> = emptyList(),
    ) = MediaSourceInfo(
        protocol = MediaProtocol.HTTP, id = "ms-1", type = MediaSourceType.DEFAULT, isRemote = false,
        readAtNativeFramerate = false, ignoreDts = false, ignoreIndex = false, genPtsInput = false,
        supportsTranscoding = true, supportsDirectStream = directStream, supportsDirectPlay = directPlay,
        isInfiniteStream = false, useMostCompatibleTranscodingProfile = false, requiresOpening = false,
        requiresClosing = false, requiresLooping = false, supportsProbing = true, mediaStreams = streams,
        mediaAttachments = emptyList(), formats = emptyList(), transcodingUrl = transcodingUrl, transcodingSubProtocol = org.jellyfin.sdk.model.api.MediaStreamProtocol.HLS, hasSegments = false,
    )

    private fun subtitle(index: Int, codec: String, url: String?, method: SubtitleDeliveryMethod = SubtitleDeliveryMethod.EXTERNAL) = MediaStream(
        type = MediaStreamType.SUBTITLE, index = index, codec = codec, language = "eng", displayTitle = "English ($codec)",
        deliveryMethod = method, deliveryUrl = url, isInterlaced = false, isDefault = index == 2, isForced = false,
        isExternal = false, isTextSubtitleStream = true, supportsExternalStream = true, isHearingImpaired = false,
        isOriginal = false,
    )

    @Test
    fun `direct play streams the original file with the access token in a header`() {
        val result = source().toPlaybackSource(SESSION, "item-1", "ps-1")

        assertEquals("https://jellyfin.example.com/Videos/item-1/stream?static=true&mediaSourceId=ms-1", result.url)
        assertEquals(PlaybackMethod.DirectPlay, result.method)
        assertEquals("ps-1", result.playSessionId)
        assertEquals("MediaBrowser Token=\"token\"", result.headers["Authorization"])
    }

    @Test
    fun `direct stream is used when direct play is not possible`() {
        assertEquals(PlaybackMethod.DirectStream, source(directPlay = false).toPlaybackSource(SESSION, "item-1", null).method)
    }

    @Test
    fun `transcode uses the server provided url and ignores external subtitles`() {
        val result = source(
            directPlay = false, directStream = false, transcodingUrl = "/videos/item-1/master.m3u8?x=1",
            streams = listOf(subtitle(2, "srt", "/Videos/item-1/ms-1/Subtitles/2/0/Stream.srt")),
        ).toPlaybackSource(SESSION, "item-1", "ps-1")

        assertEquals("https://jellyfin.example.com/videos/item-1/master.m3u8?x=1", result.url)
        assertEquals(PlaybackMethod.Transcode, result.method)
        assertEquals(emptyList(), result.subtitles)
    }

    @Test
    fun `no usable route is an error`() {
        assertFailsWith<org.jellyfin.sdk.api.client.exception.InvalidContentException> {
            source(directPlay = false, directStream = false).toPlaybackSource(SESSION, "item-1", null)
        }
    }

    @Test
    fun `only externally delivered subtitles are listed, with mime types`() {
        val result = source(
            streams = listOf(
                subtitle(2, "srt", "/Videos/item-1/ms-1/Subtitles/2/0/Stream.srt"),
                subtitle(3, "ass", "/Videos/item-1/ms-1/Subtitles/3/0/Stream.ass"),
                subtitle(4, "pgssub", null, SubtitleDeliveryMethod.ENCODE),
                subtitle(5, "subrip", null, SubtitleDeliveryMethod.EMBED),
            ),
        ).toPlaybackSource(SESSION, "item-1", null)

        assertEquals(2, result.subtitles.size)
        assertEquals("https://jellyfin.example.com/Videos/item-1/ms-1/Subtitles/2/0/Stream.srt", result.subtitles[0].url)
        assertEquals("application/x-subrip", result.subtitles[0].mimeType)
        assertEquals("text/x-ssa", result.subtitles[1].mimeType)
        assertEquals(true, result.subtitles[0].isDefault)
    }
}
