package com.pyoungws.bluewolfplayer.shared.server

import com.pyoungws.bluewolfplayer.shared.model.ServerInfo
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.discovery.RecommendedServerIssue

sealed interface ServerError {
    data object InvalidAddress : ServerError
    data object Unreachable : ServerError
    data object SecureConnectionFailed : ServerError
    data object NotJellyfin : ServerError
    data class UnsupportedVersion(val version: String) : ServerError
}

sealed interface ServerValidationResult {
    data class Valid(val server: ServerInfo) : ServerValidationResult
    data class Invalid(val error: ServerError) : ServerValidationResult
}

class ServerRepository(private val discovery: ServerDiscovery) {

    /**
     * Validates user input as a Jellyfin server address. The SDK expands the input into candidate
     * addresses (HTTPS before HTTP, Jellyfin ports before default ports); the first candidate scored
     * GREAT wins, then the first scored GOOD. Anything lower is rejected with the most specific error.
     */
    suspend fun validate(input: String): ServerValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ServerValidationResult.Invalid(ServerError.InvalidAddress)

        val results = discovery.discover(trimmed)
        if (results.isEmpty()) return ServerValidationResult.Invalid(ServerError.InvalidAddress)

        val best = results.firstOrNull { it.score == RecommendedServerInfoScore.GREAT }
            ?: results.firstOrNull { it.score == RecommendedServerInfoScore.GOOD }
            ?: return ServerValidationResult.Invalid(mostSpecificError(results))

        val info = best.systemInfo.getOrNull()
        return ServerValidationResult.Valid(
            ServerInfo(
                address = best.address.trimEnd('/'),
                name = info?.serverName,
                version = info?.version,
                id = info?.id,
            ),
        )
    }

    private fun mostSpecificError(results: List<RecommendedServerInfo>): ServerError =
        results.flatMap { it.issues }
            .map(::toServerError)
            .minByOrNull(::priority)
            ?: ServerError.Unreachable

    private fun toServerError(issue: RecommendedServerIssue): ServerError = when (issue) {
        is RecommendedServerIssue.UnsupportedServerVersion -> ServerError.UnsupportedVersion(issue.version.toString())
        is RecommendedServerIssue.InvalidProductName, RecommendedServerIssue.MissingVersion -> ServerError.NotJellyfin
        is RecommendedServerIssue.MissingSystemInfo -> when (issue.throwable) {
            // Something answered, but not with Jellyfin's public system info.
            is InvalidStatusException, is InvalidContentException -> ServerError.NotJellyfin
            else -> ServerError.Unreachable
        }
        is RecommendedServerIssue.SecureConnectionFailed -> ServerError.SecureConnectionFailed
        else -> ServerError.Unreachable
    }

    private fun priority(error: ServerError): Int = when (error) {
        is ServerError.UnsupportedVersion -> 0
        ServerError.NotJellyfin -> 1
        ServerError.SecureConnectionFailed -> 2
        ServerError.Unreachable -> 3
        ServerError.InvalidAddress -> 4
    }
}
