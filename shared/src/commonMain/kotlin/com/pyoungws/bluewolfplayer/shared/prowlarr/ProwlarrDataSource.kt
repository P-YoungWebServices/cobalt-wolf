package com.pyoungws.bluewolfplayer.shared.prowlarr

import com.pyoungws.bluewolfplayer.shared.arrs.ArrServiceSettings
import kotlinx.serialization.json.JsonObject

interface ProwlarrDataSource {
    /** The configured indexers; throws when Prowlarr is unreachable or the key is rejected. */
    suspend fun indexers(settings: ArrServiceSettings): List<ProwlarrIndexer>

    /** Usage counters per indexer id over the server's stats window. */
    suspend fun indexerStats(settings: ArrServiceSettings): Map<Int, IndexerStats>

    /** Indexer ids with an automatic disable in effect, mapped to its expiry timestamp. */
    suspend fun disabledIndexers(settings: ArrServiceSettings): Map<Int, String>

    /** The current health issues; empty when everything is fine. */
    suspend fun health(settings: ArrServiceSettings): List<ProwlarrHealthIssue>

    /** The full indexer resource (`GET /api/v1/indexer/{id}`) — the body a test call needs. */
    suspend fun indexerResource(settings: ArrServiceSettings, id: Int): JsonObject

    /**
     * Tests one indexer (`POST /api/v1/indexer/test`) with its full resource as the body.
     * Returns the validation error/warning messages Prowlarr reports; empty when it passed.
     */
    suspend fun testIndexer(settings: ArrServiceSettings, resource: JsonObject): List<String>

    /** Re-runs the test suite on every indexer (`POST /api/v1/indexer/testall`). */
    suspend fun testAllIndexers(settings: ArrServiceSettings)
}
