package com.pyoungws.cobaltwolf.shared.net

import com.pyoungws.cobaltwolf.shared.model.LoadError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.serialization.JsonConvertException
import kotlinx.io.IOException
import javax.net.ssl.SSLHandshakeException

private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404

/** Maps a Ktor client failure to a user-facing [LoadError] for non-Jellyfin REST services. */
fun Throwable.toLoadError(): LoadError = when (this) {
    is ClientRequestException -> when (response.status.value) {
        HTTP_NOT_FOUND -> LoadError.NotFound
        HTTP_FORBIDDEN -> LoadError.Forbidden
        else -> LoadError.Unexpected(message)
    }
    is ServerResponseException, is RedirectResponseException -> LoadError.Unexpected(message)
    is JsonConvertException -> LoadError.Unexpected(message)
    is SSLHandshakeException -> LoadError.SecureConnectionFailed
    is HttpRequestTimeoutException, is SocketTimeoutException -> LoadError.ServerUnreachable
    is IOException -> LoadError.ServerUnreachable
    else -> LoadError.Unexpected(message)
}
