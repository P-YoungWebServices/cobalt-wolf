package com.pyoungws.bluewolfplayer.shared.seerr

import com.pyoungws.bluewolfplayer.shared.net.normalizeServerUrl
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
 * Persists the Seerr session cookie as a `name=value` pair. Platform implementations must store
 * it securely. The name is kept because it differs across flavours: `connect.sid` on
 * Jellyseerr/Overseerr, `sb.sid` on Seerr.
 */
interface SeerrSessionStore {
    suspend fun load(): String?
    suspend fun save(cookie: String)
    suspend fun clear()
}

private val SESSION_COOKIE_NAMES = setOf("connect.sid", "sb.sid")

/**
 * Feeds the persisted session cookie to Ktor's [HttpCookies] plugin and writes back any session
 * cookie the server sets (sign-in) or clears (logout).
 */
class SeerrCookieStorage(private val store: SeerrSessionStore) : CookiesStorage {
    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        if (cookie.name !in SESSION_COOKIE_NAMES) return
        if (cookie.value.isBlank()) store.clear() else store.save("${cookie.name}=${cookie.value}")
    }

    override suspend fun get(requestUrl: Url): List<Cookie> =
        store.load()?.let {
            val name = it.substringBefore('=')
            val value = it.substringAfter('=', "")
            // RAW: the stored value is already percent-encoded (e.g. express-session's `s%3A..sig`);
            // letting Ktor encode it again double-encodes and breaks the session signature.
            if (value.isEmpty()) emptyList() else listOf(Cookie(name = name, value = value, path = "/", encoding = CookieEncoding.RAW))
        } ?: emptyList()

    override fun close() = Unit
}

internal val seerrJson: Json = Json { ignoreUnknownKeys = true }

/** Shared client configuration; tests install the same plugins on a mock engine. */
fun HttpClientConfig<*>.seerrDefaults(sessionStore: SeerrSessionStore) {
    expectSuccess = true
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 15_000
    }
    install(HttpCookies) { storage = SeerrCookieStorage(sessionStore) }
    install(ContentNegotiation) { json(seerrJson) }
}

fun seerrHttpClient(sessionStore: SeerrSessionStore): HttpClient =
    HttpClient(OkHttp) { seerrDefaults(sessionStore) }

/**
 * Accepts `seerr.example.com`, `http://host:5055/base` and similar input; returns a normalized
 * base URL (scheme + host + optional path, no trailing slash), or null when the input is not a
 * plausible host. HTTPS is assumed when no scheme is given.
 */
fun normalizeSeerrUrl(input: String): String? = normalizeServerUrl(input)
