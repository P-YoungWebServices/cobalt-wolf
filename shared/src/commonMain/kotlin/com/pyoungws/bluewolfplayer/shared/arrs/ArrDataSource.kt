package com.pyoungws.bluewolfplayer.shared.arrs

import kotlinx.datetime.LocalDate

interface ArrDataSource {
    /** Probes `GET /api/v3/system/status`; throws when the service is unreachable or the key is rejected. */
    suspend fun status(service: ArrService, settings: ArrServiceSettings)

    /** Calendar entries of [service] between [start] and [end] inclusive. */
    suspend fun calendar(
        service: ArrService,
        settings: ArrServiceSettings,
        start: LocalDate,
        end: LocalDate,
    ): List<UpcomingItem>
}
