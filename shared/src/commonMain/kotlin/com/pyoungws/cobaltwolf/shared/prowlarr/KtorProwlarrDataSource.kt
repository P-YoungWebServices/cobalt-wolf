package com.pyoungws.cobaltwolf.shared.prowlarr

import com.pyoungws.cobaltwolf.shared.arrs.ArrServiceSettings
import com.pyoungws.cobaltwolf.shared.arrs.arrJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class KtorProwlarrDataSource(
    private val client: HttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProwlarrDataSource {

    override suspend fun indexers(settings: ArrServiceSettings): List<ProwlarrIndexer> = call {
        client.get("${settings.serverUrl}/api/v1/indexer") { apiKey(settings) }
            .body<List<ProwlarrIndexerDto>>().map { it.toModel() }
    }

    override suspend fun indexerStats(settings: ArrServiceSettings): Map<Int, IndexerStats> = call {
        client.get("${settings.serverUrl}/api/v1/indexerstats") { apiKey(settings) }
            .body<ProwlarrStatsDto>().indexers.associate { it.indexerId to it.toModel() }
    }

    override suspend fun disabledIndexers(settings: ArrServiceSettings): Map<Int, String> = call {
        client.get("${settings.serverUrl}/api/v1/indexerstatus") { apiKey(settings) }
            .body<List<ProwlarrIndexerStatusDto>>()
            .mapNotNull { status -> status.indexerId?.let { it to status.disabledTill.orEmpty() } }
            .toMap()
    }

    override suspend fun health(settings: ArrServiceSettings): List<ProwlarrHealthIssue> = call {
        client.get("${settings.serverUrl}/api/v1/health") { apiKey(settings) }
            .body<List<ProwlarrHealthDto>>().map { it.toModel() }
    }

    override suspend fun indexerResource(settings: ArrServiceSettings, id: Int): JsonObject = call {
        client.get("${settings.serverUrl}/api/v1/indexer/$id") { apiKey(settings) }.body<JsonObject>()
    }

    override suspend fun testIndexer(settings: ArrServiceSettings, resource: JsonObject): List<String> = call {
        // A failed test answers 400 with the validation errors in the body — an array
        // on this endpoint, an object carrying them on save-style endpoints. opt out of
        // expectSuccess so the 400 body stays readable; rethrow other statuses manually.
        val response = client.post("${settings.serverUrl}/api/v1/indexer/test") {
            apiKey(settings)
            parameter("forceTest", "true")
            expectSuccess = false
            contentType(ContentType.Application.Json)
            setBody(resource)
        }
        if (response.status.value >= 400 && response.status != HttpStatusCode.BadRequest) {
            throw ClientRequestException(response, response.bodyAsText())
        }
        val body = runCatching { arrJson.parseToJsonElement(response.bodyAsText()) }.getOrNull()
        when (body) {
            is JsonArray -> body.messages()
            is JsonObject -> body["validationErrors"].messages() + body["validationWarnings"].messages()
            else -> emptyList()
        }
    }

    override suspend fun testAllIndexers(settings: ArrServiceSettings) {
        call { client.post("${settings.serverUrl}/api/v1/indexer/testall") { apiKey(settings) } }
    }

    private fun JsonElement?.messages(): List<String> =
        (this as? JsonArray)?.mapNotNull { (it as? JsonObject)?.get("errorMessage")?.jsonPrimitive?.contentOrNull }
            ?: emptyList()

    private fun HttpRequestBuilder.apiKey(settings: ArrServiceSettings) {
        header("X-Api-Key", settings.apiKey)
    }

    private suspend fun <T> call(block: suspend () -> T): T = withContext(ioDispatcher) { block() }
}

@Serializable
internal data class ProwlarrIndexerDto(
    val id: Int = 0,
    val name: String = "",
    val protocol: String? = null,
    val enable: Boolean = true,
) {
    fun toModel() = ProwlarrIndexer(id, name, protocol, enable)
}

@Serializable
internal data class ProwlarrIndexerStatusDto(
    val indexerId: Int? = null,
    val disabledTill: String? = null,
)

@Serializable
internal data class ProwlarrStatsDto(
    val indexers: List<IndexerStatsDto> = emptyList(),
)

@Serializable
internal data class IndexerStatsDto(
    val indexerId: Int = 0,
    val averageResponseTime: Int? = null,
    val numberOfQueries: Int = 0,
    val numberOfGrabs: Int = 0,
    val numberOfFailedQueries: Int = 0,
    val numberOfFailedGrabs: Int = 0,
) {
    fun toModel() = IndexerStats(numberOfQueries, numberOfGrabs, numberOfFailedQueries, numberOfFailedGrabs, averageResponseTime)
}

@Serializable
internal data class ProwlarrHealthDto(
    val source: String? = null,
    val type: String? = null,
    val message: String? = null,
) {
    fun toModel() = ProwlarrHealthIssue(source, type, message)
}
