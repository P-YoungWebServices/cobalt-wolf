package com.pyoungws.cobaltwolf.shared.qbittorrent

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Persists the qBittorrent `SID` session cookie as a `name=value` pair. Platform implementations
 * must store it securely.
 */
interface QBittorrentSessionStore {
    suspend fun load(): String?
    suspend fun save(cookie: String)
    suspend fun clear()
}

/** Keeps the `SID` cookie in [store]; the Web UI sets it on login and clears it on logout. */
class QBittorrentCookieStorage(private val store: QBittorrentSessionStore) : CookiesStorage {
    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        // Older servers set "SID"; newer ones use a per-port "QBT_SID_<port>" name.
        if (cookie.name != SID_COOKIE && !cookie.name.startsWith(QBT_SID_PREFIX)) return
        if (cookie.value.isBlank()) store.clear() else store.save("${cookie.name}=${cookie.value}")
    }

    override suspend fun get(requestUrl: Url): List<Cookie> =
        store.load()?.let {
            val name = it.substringBefore('=')
            val value = it.substringAfter('=', "")
            if (value.isEmpty()) emptyList() else listOf(Cookie(name = name, value = value, path = "/", encoding = CookieEncoding.RAW))
        } ?: emptyList()

    override fun close() = Unit

    private companion object {
        const val SID_COOKIE = "SID"
        const val QBT_SID_PREFIX = "QBT_SID"
    }
}

internal val qbittorrentJson: Json = Json { ignoreUnknownKeys = true }

/** Shared client configuration; tests install the same plugins on a mock engine. */
fun HttpClientConfig<*>.qbittorrentDefaults(sessionStore: QBittorrentSessionStore) {
    expectSuccess = true
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 15_000
    }
    install(HttpCookies) { storage = QBittorrentCookieStorage(sessionStore) }
    install(ContentNegotiation) { json(qbittorrentJson) }
}

fun qbittorrentHttpClient(sessionStore: QBittorrentSessionStore): HttpClient =
    HttpClient(OkHttp) { qbittorrentDefaults(sessionStore) }
