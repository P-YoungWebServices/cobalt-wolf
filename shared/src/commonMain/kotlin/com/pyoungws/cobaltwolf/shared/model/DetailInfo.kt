package com.pyoungws.cobaltwolf.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * One entry on the detail page's info badge row. Rendering (colours, icons, sizing) is up to the
 * UI layer; this only decides what appears and in what order.
 */
sealed interface InfoBadge {
    /** Community rating on the 0–10 Jellyfin scale, shown with a star. */
    data class StarRating(val value: Float) : InfoBadge

    /** Critic rating as a whole percentage. */
    data class CriticRating(val percent: Int) : InfoBadge

    /** Unboxed text such as the year or the S1:E2 label. */
    data class Text(val text: String) : InfoBadge

    /** Runtime, shown with a clock icon. */
    data class Runtime(val minutes: Int) : InfoBadge

    /** Air/premiere date; the UI decides how to format it. */
    data class AirDate(val date: LocalDate) : InfoBadge

    /** Small boxed chip: parental rating, resolution, codec names, subtitle indicators. */
    data class Chip(val text: String) : InfoBadge

    /** Coloured status chip (continuing = positive, ended = negative, unreleased = neutral). */
    data class Status(val status: SeriesStatus, val tone: BadgeTone) : InfoBadge
}

enum class BadgeTone { Neutral, Positive, Negative }

/** The fixed set of info-item labels; the UI layer maps them to string resources. */
enum class DetailInfoLabel { DirectedBy, Seasons, Runs, Ends }

/** "Directed by" / "Seasons" style label-value pairs under the badge row. */
data class DetailInfoItem(val label: DetailInfoLabel, val value: String)

private const val MINUTES_PER_HOUR = 60

/** "1:33" style runtime; under an hour shows "0:58" like the reference clients. */
fun formatRuntime(minutes: Int): String = "%d:%02d".format(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)

private fun videoBadgeResolution(stream: MediaStreamInfo): String? {
    val height = stream.height ?: return stream.width?.let { if (it >= 3800) "4K" else null }
    return when {
        height >= 2160 -> "4K"
        height >= 1440 -> "1440p"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height >= 480 -> "480p"
        else -> "${height}p"
    }
}

private fun audioCodecBadge(stream: MediaStreamInfo): String? {
    val profile = stream.profile.orEmpty()
    return when {
        profile.contains("Dolby Atmos", ignoreCase = true) -> when (stream.codec?.uppercase()) {
            "EAC3" -> "EAC3 Atmos"
            "TRUEHD" -> "TrueHD Atmos"
            else -> "Dolby Atmos"
        }
        profile.contains("DTS:X", ignoreCase = true) -> "DTS:X"
        profile.contains("DTS:HD", ignoreCase = true) || profile.contains("DTS-HD", ignoreCase = true) -> "DTS-HD"
        else -> stream.codec?.uppercase()
    }
}

private fun MediaItem.mediaBadges(): List<InfoBadge> = buildList {
    val subtitleStreams = streams.filter { it.kind == StreamKind.Subtitle }
    if (subtitleStreams.any { it.isHearingImpaired }) add(InfoBadge.Chip("SDH"))
    if (subtitleStreams.any { !it.isHearingImpaired }) add(InfoBadge.Chip("CC"))
    streams.firstOrNull { it.kind == StreamKind.Video }?.let { video ->
        videoBadgeResolution(video)?.let { add(InfoBadge.Chip(it)) }
        video.videoRange?.let { add(InfoBadge.Chip(it)) }
    }
    streams.firstOrNull { it.kind == StreamKind.Audio }?.let { audio ->
        audioCodecBadge(audio)?.takeIf { it.isNotBlank() }?.let { add(InfoBadge.Chip(it)) }
        audio.channelLayout?.takeIf { it.isNotBlank() }?.let { add(InfoBadge.Chip(it.uppercase())) }
    }
}

/** Ordered badge row for the detail page: ratings first, then identity info, then media chips. */
fun MediaItem.infoBadges(): List<InfoBadge> = buildList {
    communityRating?.let { add(InfoBadge.StarRating(it)) }
    criticRating?.let { add(InfoBadge.CriticRating(it.toInt())) }

    when (kind) {
        MediaKind.Episode -> {
            episodeLabel()?.let { add(InfoBadge.Text(it)) }
            premiereDate?.let { add(InfoBadge.AirDate(it)) }
        }
        MediaKind.Series -> {
            premiereDate?.let { add(InfoBadge.AirDate(it)) } ?: year?.let { add(InfoBadge.Text(it.toString())) }
            when (seriesStatus) {
                SeriesStatus.Continuing -> add(InfoBadge.Status(seriesStatus, BadgeTone.Positive))
                SeriesStatus.Ended -> add(InfoBadge.Status(seriesStatus, BadgeTone.Negative))
                SeriesStatus.Unreleased -> add(InfoBadge.Status(seriesStatus, BadgeTone.Neutral))
                null -> Unit
            }
        }
        else -> premiereDate?.let { add(InfoBadge.AirDate(it)) } ?: year?.let { add(InfoBadge.Text(it.toString())) }
    }

    runtimeMinutes?.let { add(InfoBadge.Runtime(it)) }
    officialRating?.takeIf { it.isNotBlank() }?.let { add(InfoBadge.Chip(it)) }
    addAll(mediaBadges())
}

/** The label/value trio under the badges: "Directed by"/"Seasons", "Runs", "Ends". */
fun MediaItem.detailInfoItems(endTime: LocalTime?): List<DetailInfoItem> = buildList {
    if (kind == MediaKind.Series) {
        childCount?.let { add(DetailInfoItem(DetailInfoLabel.Seasons, it.toString())) }
    } else {
        val director = people.firstOrNull { it.kind == "Director" }?.name
        if (director != null) add(DetailInfoItem(DetailInfoLabel.DirectedBy, director))
    }
    runtimeMinutes?.let { add(DetailInfoItem(DetailInfoLabel.Runs, formatRuntime(it))) }
    endTime?.let { add(DetailInfoItem(DetailInfoLabel.Ends, "%d:%02d".format(it.hour, it.minute))) }
}

/** Wall-clock time playback would finish if started now: now + remaining runtime. */
fun MediaItem.endTime(now: LocalDateTime): LocalTime? {
    val totalTicks = runtimeTicks ?: runtimeMinutes?.let { it * 600_000_000L } ?: return null
    val remainingMinutes = ((totalTicks - positionTicks) / 600_000_000L).coerceAtLeast(0)
    val endMinutes = ((now.hour * MINUTES_PER_HOUR + now.minute) + remainingMinutes) % (MINUTES_PER_HOUR * 24)
    return LocalTime((endMinutes / MINUTES_PER_HOUR).toInt(), (endMinutes % MINUTES_PER_HOUR).toInt())
}
