package com.pyoungws.cobaltwolf.shared.server

import com.pyoungws.cobaltwolf.shared.model.ServerInfo
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.discovery.RecommendedServerIssue
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.PublicSystemInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerRepositoryTest {
    private var discovered = emptyList<RecommendedServerInfo>()
    private val inputs = mutableListOf<String>()
    private val repository = ServerRepository { input ->
        inputs += input
        discovered
    }

    private fun candidate(
        address: String,
        score: RecommendedServerInfoScore,
        issues: List<RecommendedServerIssue> = emptyList(),
        info: PublicSystemInfo? = PublicSystemInfo(serverName = "Den", version = "12.4.0", id = "id-1", productName = "Jellyfin Server"),
    ) = RecommendedServerInfo(
        address = address,
        responseTime = 50,
        score = score,
        issues = issues,
        systemInfo = info?.let { Result.success(it) } ?: Result.failure(TimeoutException()),
    )

    @Test
    fun `blank input is rejected without probing`() = runTest {
        assertEquals(ServerValidationResult.Invalid(ServerError.InvalidAddress), repository.validate("   "))
        assertEquals(emptyList(), inputs)
    }

    @Test
    fun `input is trimmed before probing`() = runTest {
        discovered = listOf(candidate("https://den.example/", RecommendedServerInfoScore.GREAT))
        repository.validate("  den.example  ")
        assertEquals(listOf("den.example"), inputs)
    }

    @Test
    fun `unparseable input yields no candidates and is rejected`() = runTest {
        discovered = emptyList()
        assertEquals(ServerValidationResult.Invalid(ServerError.InvalidAddress), repository.validate("::not a url::"))
    }

    @Test
    fun `first GREAT candidate wins over an earlier GOOD one`() = runTest {
        discovered = listOf(
            candidate("https://den.example:8920/", RecommendedServerInfoScore.GOOD),
            candidate("https://den.example/", RecommendedServerInfoScore.GREAT),
            candidate("http://den.example:8096/", RecommendedServerInfoScore.GREAT),
        )

        assertEquals(
            ServerValidationResult.Valid(ServerInfo("https://den.example", "Den", "12.4.0", "id-1")),
            repository.validate("den.example"),
        )
    }

    @Test
    fun `first GOOD candidate is used when none is GREAT`() = runTest {
        discovered = listOf(
            candidate("https://den.example/", RecommendedServerInfoScore.BAD, info = null),
            candidate("http://den.example:8096/", RecommendedServerInfoScore.GOOD),
        )

        val result = repository.validate("den.example") as ServerValidationResult.Valid
        assertEquals("http://den.example:8096", result.server.address)
    }

    @Test
    fun `servers older than the SDK minimum are rejected with their version`() = runTest {
        val version = ServerVersion(10, 11, 3)
        discovered = listOf(
            candidate("https://old.example/", RecommendedServerInfoScore.BAD, listOf(RecommendedServerIssue.ServerUnreachable(TimeoutException()))),
            candidate("http://old.example:8096/", RecommendedServerInfoScore.OK, listOf(RecommendedServerIssue.UnsupportedServerVersion(version))),
        )

        assertEquals(
            ServerValidationResult.Invalid(ServerError.UnsupportedVersion(version.toString())),
            repository.validate("old.example"),
        )
    }

    @Test
    fun `a non-Jellyfin service is reported as such`() = runTest {
        discovered = listOf(
            candidate("https://site.example/", RecommendedServerInfoScore.BAD, listOf(RecommendedServerIssue.InvalidProductName("Emby Server"))),
            candidate("http://site.example/", RecommendedServerInfoScore.BAD, listOf(RecommendedServerIssue.MissingSystemInfo(InvalidStatusException(404)))),
        )

        assertEquals(ServerValidationResult.Invalid(ServerError.NotJellyfin), repository.validate("site.example"))
    }

    @Test
    fun `TLS failures are reported over plain unreachability`() = runTest {
        discovered = listOf(
            candidate("https://den.example/", RecommendedServerInfoScore.BAD, listOf(RecommendedServerIssue.SecureConnectionFailed(SecureConnectionException()))),
            candidate("http://den.example/", RecommendedServerInfoScore.BAD, listOf(RecommendedServerIssue.ServerUnreachable(TimeoutException()))),
        )

        assertEquals(ServerValidationResult.Invalid(ServerError.SecureConnectionFailed), repository.validate("den.example"))
    }

    @Test
    fun `nothing answering is reported as unreachable`() = runTest {
        discovered = listOf(
            candidate("https://gone.example/", RecommendedServerInfoScore.BAD, listOf(RecommendedServerIssue.ServerUnreachable(TimeoutException()))),
            candidate("http://gone.example/", RecommendedServerInfoScore.BAD, listOf(RecommendedServerIssue.MissingSystemInfo(TimeoutException()))),
        )

        assertEquals(ServerValidationResult.Invalid(ServerError.Unreachable), repository.validate("gone.example"))
    }
}
