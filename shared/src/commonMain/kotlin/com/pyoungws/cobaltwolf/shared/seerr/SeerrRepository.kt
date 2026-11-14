package com.pyoungws.cobaltwolf.shared.seerr

import com.pyoungws.cobaltwolf.shared.auth.AuthRepository
import com.pyoungws.cobaltwolf.shared.auth.SessionState
import com.pyoungws.cobaltwolf.shared.auth.SignInError
import com.pyoungws.cobaltwolf.shared.auth.SignInResult
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import com.pyoungws.cobaltwolf.shared.net.toLoadError
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.serialization.JsonConvertException
import kotlinx.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.net.ssl.SSLHandshakeException

sealed interface SeerrAuthState {
    /** A restore or validation is in flight. */
    data object Checking : SeerrAuthState
    /** No Seerr server URL is configured. */
    data object NotConfigured : SeerrAuthState
    /** Configured but signed out. [error] is set when a stored session could not be validated. */
    data class SignedOut(val error: LoadError? = null) : SeerrAuthState
    data class SignedIn(val account: SeerrAccount) : SeerrAuthState
}

sealed interface SeerrSignInResult {
    data class Success(val account: SeerrAccount) : SeerrSignInResult
    data class Failure(val error: SignInError) : SeerrSignInResult
}

/** One row of discoverable titles per section of the Discover screen. */
data class DiscoverFeed(
    val trending: List<SeerrTitle>,
    val movies: List<SeerrTitle>,
    val series: List<SeerrTitle>,
)

/**
 * Wraps the Seerr API: keeps the sign-in state, signs users in with their Jellyfin account
 * (the Seerr server verifies the password against the Jellyfin server itself), and converts
 * failures to [Outcome]. The session cookie is reset whenever the Jellyfin user or the
 * configured Seerr server changes, so one user can never see another's requests.
 */
class SeerrRepository(
    private val dataSource: SeerrDataSource,
    private val settings: SeerrSettingsRepository,
    private val sessionStore: SeerrSessionStore,
    private val authRepository: AuthRepository,
    scope: CoroutineScope,
) {
    private val _authState = MutableStateFlow<SeerrAuthState>(SeerrAuthState.Checking)
    val authState: StateFlow<SeerrAuthState> = _authState.asStateFlow()

    /** "url|userId" pairs already auto-attempted, so a failure doesn't retry forever. */
    private var autoSignInKey: String? = null

    init {
        scope.launch { restore() }
        // A different Seerr server means the stored cookie no longer applies.
        scope.launch { settings.settings.drop(1).collect { resetSession(); tryAutoSignIn() } }
        // The cookie identifies the Jellyfin user; drop it when that user signs out,
        // and sign in to Seerr automatically once a Jellyfin session exists.
        scope.launch {
            authRepository.sessionState.collect { state ->
                when (state) {
                    is SessionState.SignedOut -> { resetSession(); autoSignInKey = null }
                    is SessionState.SignedIn -> tryAutoSignIn()
                    else -> {}
                }
            }
        }
    }

    /**
     * Signs in to Seerr with the password the current Jellyfin session was created with,
     * when it is still held in memory — so a fresh sign-in needs no second password prompt.
     * Fails quietly into [SeerrAuthState.SignedOut]: the manual sign-in stays available.
     */
    private suspend fun tryAutoSignIn() {
        val url = settings.serverUrl ?: return
        if (_authState.value is SeerrAuthState.SignedIn) return
        val session = (authRepository.sessionState.value as? SessionState.SignedIn)?.session ?: return
        val password = authRepository.jellyfinPassword() ?: return
        val key = "$url|${session.userId}"
        if (autoSignInKey == key) return
        autoSignInKey = key
        // Checking keeps the UI on its loading phase instead of flashing the password form.
        _authState.value = SeerrAuthState.Checking
        if (signInWithJellyfin(password) !is SeerrSignInResult.Success) {
            // Allow a later trigger (URL change, new sign-in) to retry; the manual form stays available.
            autoSignInKey = null
            _authState.value = SeerrAuthState.SignedOut()
        }
    }

    /**
     * Normalizes user input into a Seerr base URL, probing the server when no scheme was given:
     * HTTPS first, then HTTP (LAN Seerr installs rarely have TLS). Returns null when nothing
     * responds or the input is not a plausible host.
     */
    suspend fun resolveUrl(input: String): String? {
        val trimmed = input.trim()
        if ("://" in trimmed) return normalizeSeerrUrl(trimmed)
        for (scheme in listOf("https", "http")) {
            val candidate = normalizeSeerrUrl("$scheme://$trimmed") ?: continue
            if (runCatching { dataSource.status(candidate) }.isSuccess) return candidate
        }
        return null
    }

    /** Revalidates the stored session; called at startup and exposed for retries. */
    suspend fun restore() {
        val url = settings.serverUrl
        if (url == null) {
            _authState.value = SeerrAuthState.NotConfigured
            return
        }
        if (sessionStore.load() == null) {
            _authState.value = SeerrAuthState.SignedOut()
            return
        }
        _authState.value = try {
            SeerrAuthState.SignedIn(dataSource.currentUser(url))
        } catch (e: ClientRequestException) {
            if (e.isAuthError()) {
                sessionStore.clear()
                SeerrAuthState.SignedOut()
            } else {
                SeerrAuthState.SignedOut(error = e.toLoadError())
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // An unreachable server keeps the cookie: the next restore can still validate it.
            SeerrAuthState.SignedOut(error = e.toLoadError())
        }
    }

    /** The Jellyfin username, for pre-filling the Seerr sign-in prompt. */
    suspend fun jellyfinUsername(): String? =
        (authRepository.currentUser() as? SignInResult.Success)?.user?.name

    /**
     * Signs in to Seerr as the current Jellyfin user. Seerr checks [password] against the
     * Jellyfin server the user is signed in to; the resulting session cookie is stored securely.
     */
    suspend fun signInWithJellyfin(password: String): SeerrSignInResult {
        val url = settings.serverUrl
            ?: return SeerrSignInResult.Failure(SignInError.Unexpected("No Seerr server configured"))
        val jellyfinSession = (authRepository.sessionState.value as? SessionState.SignedIn)?.session
            ?: return SeerrSignInResult.Failure(SignInError.SessionExpired)
        val user = when (val result = authRepository.currentUser()) {
            is SignInResult.Failure -> return SeerrSignInResult.Failure(result.error)
            is SignInResult.Success -> result.user
        }
        return try {
            val account = dataSource.signInWithJellyfin(url, user.name, password, hostname = jellyfinSession.serverAddress)
            SeerrSignInResult.Success(account.also { _authState.value = SeerrAuthState.SignedIn(it) })
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SeerrSignInResult.Failure(e.toSignInError())
        }
    }

    suspend fun signOut() {
        sessionStore.clear()
        val url = settings.serverUrl
        _authState.value = if (url == null) SeerrAuthState.NotConfigured else SeerrAuthState.SignedOut()
        if (url != null) runCatching { dataSource.signOut(url) }
    }

    suspend fun discover(): Outcome<DiscoverFeed> = run { url ->
        coroutineScope {
            // Sections fail independently: a server can disable e.g. series discovery (403), which
            // should leave an empty section rather than sink the whole feed. Only when every
            // section fails is the error propagated.
            val sections = listOf<suspend () -> List<SeerrTitle>>(
                { dataSource.trending(url).items },
                { dataSource.discoverMovies(url).items },
                { dataSource.discoverSeries(url).items },
            ).map { section -> async { runCatching { section() } } }
            val results = sections.awaitAll()
            if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
            DiscoverFeed(
                results[0].getOrDefault(emptyList()),
                results[1].getOrDefault(emptyList()),
                results[2].getOrDefault(emptyList()),
            )
        }
    }

    suspend fun search(query: String): Outcome<List<SeerrTitle>> {
        val term = query.trim()
        if (term.isEmpty()) return Outcome.Success(emptyList())
        return run { dataSource.search(it, term).items }
    }

    suspend fun details(type: SeerrMediaType, tmdbId: Int): Outcome<SeerrTitleDetails> = run { url ->
        if (type == SeerrMediaType.Movie) dataSource.movieDetails(url, tmdbId) else dataSource.seriesDetails(url, tmdbId)
    }

    /** Requests a movie ([seasons] ignored) or the selected [seasons] of a series. */
    suspend fun request(type: SeerrMediaType, tmdbId: Int, seasons: List<Int>? = null): Outcome<SeerrRequest> {
        if (type == SeerrMediaType.Series && seasons.isNullOrEmpty()) {
            return Outcome.Failure(LoadError.Unexpected("No seasons selected"))
        }
        return run { dataSource.createRequest(it, type, tmdbId, if (type == SeerrMediaType.Series) seasons else null) }
    }

    /** The user's own requests, enriched with titles and posters (Seerr does not embed them). */
    suspend fun myRequests(): Outcome<List<SeerrRequest>> = run { url ->
        enrich(url, dataSource.requests(url).items)
    }

    /**
     * Requests waiting for approval across all users, newest first. Only succeeds for Seerr
     * accounts with manage permission; viewers get [LoadError.Forbidden].
     */
    suspend fun pendingRequests(): Outcome<List<SeerrRequest>> = run { url ->
        val pending = dataSource.requests(url, SeerrRequestFilter.Pending).items
            .filter { it.status == SeerrRequestStatus.Pending }
        enrich(url, pending)
    }

    /** A single request, enriched with its title and poster. */
    suspend fun requestDetails(requestId: Int): Outcome<SeerrRequest> = run { url ->
        enrich(url, dataSource.requestDetails(url, requestId))
    }

    /** Approves a pending request and returns its new state. Requires manage permission. */
    suspend fun approveRequest(requestId: Int): Outcome<SeerrRequest> = run { url ->
        dataSource.approveRequest(url, requestId)
    }

    /** Declines a pending request and returns its new state. Requires manage permission. */
    suspend fun declineRequest(requestId: Int): Outcome<SeerrRequest> = run { url ->
        dataSource.declineRequest(url, requestId)
    }

    private suspend fun enrich(url: String, requests: List<SeerrRequest>): List<SeerrRequest> =
        coroutineScope { requests.map { async { enrich(url, it) } }.awaitAll() }

    /** Title and poster come from a separate details call; a failed lookup keeps the request's own data. */
    private suspend fun enrich(url: String, request: SeerrRequest): SeerrRequest {
        val details = runCatching {
            if (request.type == SeerrMediaType.Movie) dataSource.movieDetails(url, request.tmdbId)
            else dataSource.seriesDetails(url, request.tmdbId)
        }.getOrNull()
        return request.copy(
            title = details?.title?.title,
            posterUrl = details?.title?.posterUrl,
            mediaStatus = details?.title?.mediaStatus ?: request.mediaStatus,
        )
    }

    private suspend fun resetSession() {
        sessionStore.clear()
        restore()
    }

    private suspend fun <T> run(block: suspend (String) -> T): Outcome<T> {
        val url = settings.serverUrl ?: return Outcome.Failure(LoadError.Unexpected("No Seerr server configured"))
        return try {
            Outcome.Success(block(url))
        } catch (e: ClientRequestException) {
            if (e.isAuthError()) {
                sessionStore.clear()
                _authState.value = SeerrAuthState.SignedOut()
                Outcome.Failure(LoadError.SessionExpired)
            } else {
                Outcome.Failure(e.toLoadError())
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Outcome.Failure(e.toLoadError())
        }
    }

    private fun ClientRequestException.isAuthError(): Boolean =
        // 401 means no usable session. Seerr answers 403 for a well-formed but unprivileged or
        // unverifiable cookie too, which must not be treated as "session expired" — the user
        // stays signed in and sees the error instead of being silently signed out.
        response.status.value == HTTP_UNAUTHORIZED

    private fun Throwable.toSignInError(): SignInError = when (this) {
        is ClientRequestException -> if (isAuthError()) SignInError.InvalidCredentials else SignInError.Unexpected(message)
        else -> when (val error = toLoadError()) {
            LoadError.ServerUnreachable -> SignInError.ServerUnreachable
            LoadError.SecureConnectionFailed -> SignInError.SecureConnectionFailed
            else -> SignInError.Unexpected(message)
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
    }
}
