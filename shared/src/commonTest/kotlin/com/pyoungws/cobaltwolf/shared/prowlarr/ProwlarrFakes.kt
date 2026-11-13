package com.pyoungws.cobaltwolf.shared.prowlarr

import com.pyoungws.cobaltwolf.shared.arrs.ArrServiceSettings
import kotlinx.serialization.json.JsonObject

class FakeProwlarrDataSource : ProwlarrDataSource {
    var indexers: List<ProwlarrIndexer> = emptyList()
    var stats: Map<Int, IndexerStats> = emptyMap()
    var disabled: Map<Int, String> = emptyMap()
    var healthIssues: List<ProwlarrHealthIssue> = emptyList()
    var error: Throwable? = null

    private fun failIfNeeded() {
        error?.let { throw it }
    }

    override suspend fun indexers(settings: ArrServiceSettings): List<ProwlarrIndexer> {
        failIfNeeded()
        return indexers
    }

    override suspend fun indexerStats(settings: ArrServiceSettings): Map<Int, IndexerStats> {
        failIfNeeded()
        return stats
    }

    override suspend fun disabledIndexers(settings: ArrServiceSettings): Map<Int, String> {
        failIfNeeded()
        return disabled
    }

    override suspend fun health(settings: ArrServiceSettings): List<ProwlarrHealthIssue> {
        failIfNeeded()
        return healthIssues
    }

    var resources = mutableMapOf<Int, JsonObject>()
    var testProblems: List<String> = emptyList()
    var testAllCalls = 0

    override suspend fun indexerResource(settings: ArrServiceSettings, id: Int): JsonObject {
        failIfNeeded()
        return resources.getOrPut(id) { JsonObject(mapOf("id" to kotlinx.serialization.json.JsonPrimitive(id))) }
    }

    override suspend fun testIndexer(settings: ArrServiceSettings, resource: JsonObject): List<String> {
        failIfNeeded()
        return testProblems
    }

    override suspend fun testAllIndexers(settings: ArrServiceSettings) {
        failIfNeeded()
        testAllCalls++
    }
}
