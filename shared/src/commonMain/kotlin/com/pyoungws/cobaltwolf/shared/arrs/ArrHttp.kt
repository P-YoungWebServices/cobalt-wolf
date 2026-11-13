package com.pyoungws.cobaltwolf.shared.arrs

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal val arrJson: Json = Json { ignoreUnknownKeys = true }

/** Shared client configuration; tests install the same plugins on a mock engine. */
fun HttpClientConfig<*>.arrDefaults() {
    expectSuccess = true
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 15_000
    }
    install(ContentNegotiation) { json(arrJson) }
}

fun arrHttpClient(): HttpClient = HttpClient(OkHttp) { arrDefaults() }
