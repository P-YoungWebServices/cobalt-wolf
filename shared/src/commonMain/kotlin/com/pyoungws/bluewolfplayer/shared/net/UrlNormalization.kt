package com.pyoungws.bluewolfplayer.shared.net

import io.ktor.http.Url

/**
 * Accepts `service.example.com`, `http://host:5055/base` and similar input; returns a normalized
 * base URL (scheme + host + optional path, no trailing slash), or null when the input is not a
 * plausible host. HTTPS is assumed when no scheme is given.
 */
fun normalizeServerUrl(input: String): String? {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    val url = runCatching { Url(withScheme) }.getOrNull() ?: return null
    if (url.protocol.name != "http" && url.protocol.name != "https") return null
    if (url.host.isBlank() || !url.host.contains('.')) {
        // Bare "localhost" or "192.168.1.10" are valid; anything without a dot that is not
        // localhost or an IPv4/IPv6 literal probably is not a hostname.
        val isLiteral = url.host == "localhost" || url.host.all { it.isDigit() || it == '.' || it == ':' }
        if (!isLiteral) return null
    }
    return withScheme.trimEnd('/')
}
