package com.pyoungws.bluewolfplayer.shared.arrs

import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

/** A [Clock] pinned to a fixed instant so repository tests see a deterministic "today". */
class FixedClock(instant: Instant) : Clock {
    private val instant = instant
    override fun now() = instant
}

fun fixedClock(instant: String = "2026-11-10T12:00:00Z") = FixedClock(Instant.parse(instant))

fun upcoming(
    service: ArrService,
    title: String,
    date: LocalDate = LocalDate(2026, 11, 12),
    subtitle: String? = null,
    hasFile: Boolean = false,
    monitored: Boolean = true,
) = UpcomingItem(
    service = service,
    title = title,
    subtitle = subtitle,
    date = date,
    hasFile = hasFile,
    monitored = monitored,
    posterUrl = null,
    overview = null,
)

class FakeArrDataSource : ArrDataSource {
    val calls = mutableListOf<String>()
    var items: Map<ArrService, List<UpcomingItem>> = emptyMap()
    var errors: Map<ArrService, Throwable> = emptyMap()
    val windows = mutableMapOf<ArrService, Pair<LocalDate, LocalDate>>()

    override suspend fun status(service: ArrService, settings: ArrServiceSettings) {
        calls += "status($service,${settings.serverUrl})"
        errors[service]?.let { throw it }
    }

    override suspend fun calendar(
        service: ArrService,
        settings: ArrServiceSettings,
        start: LocalDate,
        end: LocalDate,
    ): List<UpcomingItem> {
        calls += "calendar($service)"
        windows[service] = start to end
        errors[service]?.let { throw it }
        return items[service].orEmpty()
    }
}
