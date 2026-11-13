package com.pyoungws.bluewolfplayer.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DetailInfoTest {

    private fun movie(
        kind: MediaKind = MediaKind.Movie,
        streams: List<MediaStreamInfo> = emptyList(),
        people: List<MediaPerson> = emptyList(),
        communityRating: Float? = null,
        criticRating: Float? = null,
        officialRating: String? = null,
        runtimeMinutes: Int? = null,
        runtimeTicks: Long? = null,
        positionTicks: Long = 0,
        year: Int? = null,
        premiereDate: LocalDate? = null,
        seriesStatus: SeriesStatus? = null,
        childCount: Int? = null,
    ) = MediaItem(
        "id", "Name", kind,
        streams = streams, people = people,
        communityRating = communityRating, criticRating = criticRating,
        officialRating = officialRating, runtimeMinutes = runtimeMinutes,
        runtimeTicks = runtimeTicks, positionTicks = positionTicks,
        year = year, premiereDate = premiereDate,
        seriesStatus = seriesStatus, childCount = childCount,
    )

    private fun video(height: Int? = null, range: String? = null) =
        MediaStreamInfo(StreamKind.Video, codec = "hevc", height = height, videoRange = range)

    private fun audio(codec: String = "aac", layout: String? = null, profile: String? = null) =
        MediaStreamInfo(StreamKind.Audio, codec = codec, channelLayout = layout, profile = profile)

    @Test
    fun formatRuntimeUsesHourColonMinute() {
        assertEquals("1:33", formatRuntime(93))
        assertEquals("0:58", formatRuntime(58))
        assertEquals("2:00", formatRuntime(120))
    }

    @Test
    fun badgesStartWithRatingsThenIdentityThenMediaChips() {
        val item = movie(
            communityRating = 7.4f, criticRating = 82f,
            officialRating = "PG-13", runtimeMinutes = 93, year = 2009,
            streams = listOf(video(1080, "HDR10"), audio("eac3", "5.1", "Dolby Atmos")),
        )
        val badges = item.infoBadges()
        assertIs<InfoBadge.StarRating>(badges[0])
        assertEquals(7.4f, (badges[0] as InfoBadge.StarRating).value, 1e-4f)
        assertIs<InfoBadge.CriticRating>(badges[1])
        assertEquals(82, (badges[1] as InfoBadge.CriticRating).percent)
        assertIs<InfoBadge.Text>(badges[2])
        assertEquals("2009", (badges[2] as InfoBadge.Text).text)
        assertIs<InfoBadge.Runtime>(badges[3])
        assertIs<InfoBadge.Chip>(badges[4])
        assertEquals("PG-13", (badges[4] as InfoBadge.Chip).text)
        val chips = badges.filterIsInstance<InfoBadge.Chip>().map { it.text }
        assertTrue("4K" !in chips && "1080p" in chips)
        assertTrue("HDR10" in chips)
        assertTrue(chips.any { it.contains("Atmos") })
        assertTrue("5.1" in chips)
    }

    @Test
    fun seriesBadgeUsesStatusToneAndSeasonCount() {
        val item = movie(
            kind = MediaKind.Series, seriesStatus = SeriesStatus.Continuing,
            childCount = 3, premiereDate = LocalDate(2019, 4, 15),
        )
        val badges = item.infoBadges()
        val status = badges.filterIsInstance<InfoBadge.Status>().single()
        assertEquals(SeriesStatus.Continuing, status.status)
        assertEquals(BadgeTone.Positive, status.tone)
        val infos = item.detailInfoItems(null)
        assertEquals(DetailInfoLabel.Seasons, infos[0].label)
        assertEquals("3", infos[0].value)
    }

    @Test
    fun endedSeriesGetsNegativeTone() {
        val item = movie(kind = MediaKind.Series, seriesStatus = SeriesStatus.Ended)
        val status = item.infoBadges().filterIsInstance<InfoBadge.Status>().single()
        assertEquals(BadgeTone.Negative, status.tone)
    }

    @Test
    fun episodeBadgeCarriesEpisodeLabelAndAirDate() {
        val item = movie(
            kind = MediaKind.Episode, premiereDate = LocalDate(2020, 11, 2),
        ).copy(seasonNumber = 1, episodeNumber = 4)
        val badges = item.infoBadges()
        val texts = badges.filterIsInstance<InfoBadge.Text>().map { it.text }
        assertTrue(texts.any { it.contains("1") && it.contains("4") })
        assertTrue(badges.any { it is InfoBadge.AirDate && it.date == LocalDate(2020, 11, 2) })
    }

    @Test
    fun subtitleBadgesSeparateSdhFromCc() {
        val subs = listOf(
            MediaStreamInfo(StreamKind.Subtitle, codec = "subrip", isHearingImpaired = true),
            MediaStreamInfo(StreamKind.Subtitle, codec = "subrip"),
        )
        val chips = movie(streams = subs).infoBadges().filterIsInstance<InfoBadge.Chip>().map { it.text }
        assertTrue("SDH" in chips)
        assertTrue("CC" in chips)
    }

    @Test
    fun infoItemsCarryDirectorRunsAndEnds() {
        val item = movie(
            people = listOf(MediaPerson("p1", "Ada Director", role = null, kind = "Director", imageUrl = null)),
            runtimeMinutes = 90,
        )
        val infos = item.detailInfoItems(LocalTime(22, 30))
        assertEquals(listOf(DetailInfoLabel.DirectedBy, DetailInfoLabel.Runs, DetailInfoLabel.Ends),
            infos.map { it.label })
        assertEquals("Ada Director", infos[0].value)
        assertEquals("1:30", infos[1].value)
        assertEquals("22:30", infos[2].value)
    }

    @Test
    fun endTimeAddsRemainingRuntimeToClock() {
        val item = movie(runtimeTicks = 100L * 600_000_000L, positionTicks = 40L * 600_000_000L)
        val end = item.endTime(LocalDateTime(2024, 6, 1, 21, 10))
        assertEquals(LocalTime(22, 10), end) // 60 min remaining
    }

    @Test
    fun endTimeWrapsPastMidnight() {
        val item = movie(runtimeTicks = 200L * 600_000_000L)
        val end = item.endTime(LocalDateTime(2024, 6, 1, 23, 30))
        assertEquals(LocalTime(2, 50), end) // 200 min -> 3:20
    }

    @Test
    fun endTimeNullWithoutRuntime() {
        assertEquals(null, movie().endTime(LocalDateTime(2024, 6, 1, 20, 0)))
    }

    @Test
    fun resolutionBadgePickHighestBracket() {
        assertTrue("4K" in chipsOf(video(2160)))
        assertTrue("1440p" in chipsOf(video(1440)))
        assertTrue("1080p" in chipsOf(video(1080)))
        assertTrue("720p" in chipsOf(video(720)))
    }

    private fun chipsOf(stream: MediaStreamInfo): List<String> =
        movie(streams = listOf(stream)).infoBadges().filterIsInstance<InfoBadge.Chip>().map { it.text }
}
