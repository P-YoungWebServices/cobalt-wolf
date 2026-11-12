package com.pyoungws.bluewolfplayer.shared.prowlarr

/** Usage counters Prowlarr reports per indexer over its stats window. */
data class IndexerStats(
    val queries: Int = 0,
    val grabs: Int = 0,
    val failedQueries: Int = 0,
    val failedGrabs: Int = 0,
    val averageResponseMs: Int? = null,
)

/** One indexer configured in Prowlarr, enriched with its stats and disable state. */
data class ProwlarrIndexer(
    val id: Int,
    val name: String,
    /** "torrent" or "usenet". */
    val protocol: String?,
    val enabled: Boolean,
    /** ISO timestamp of an automatic (rate-limit) disable, when the server flagged one. */
    val disabledUntil: String? = null,
    val stats: IndexerStats? = null,
)

/** A health problem Prowlarr is reporting (e.g. an indexer that keeps failing). */
data class ProwlarrHealthIssue(
    val source: String?,
    val type: String?,
    val message: String?,
)

/** The indexers plus the current health issues, fetched together for the screen. */
data class ProwlarrOverview(
    val indexers: List<ProwlarrIndexer>,
    val health: List<ProwlarrHealthIssue>,
)
