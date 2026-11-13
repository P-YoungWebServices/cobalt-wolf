package com.pyoungws.bluewolfplayer.shared.library

import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.SessionState
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.model.Session
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException

/**
 * Runs authenticated Jellyfin calls for the current session and converts SDK failures to [Outcome].
 * A 401 means the token was revoked, so the session is invalidated and the app returns to sign-in.
 */
class SessionGuard(private val authRepository: AuthRepository) {

    /** The active session if one is already signed in, without attempting a restore. */
    fun sessionOrNull(): Session? =
        (authRepository.sessionState.value as? SessionState.SignedIn)?.session

    suspend fun <T> run(block: suspend (Session) -> T): Outcome<T> {
        // A screen recreated after process death (the player is its own activity) can get here
        // before the app's start-up restore has run.
        if (authRepository.sessionState.value == SessionState.Unknown) authRepository.restoreSession()
        val session = (authRepository.sessionState.value as? SessionState.SignedIn)?.session
            ?: return Outcome.Failure(LoadError.SessionExpired)
        return try {
            Outcome.Success(block(session))
        } catch (e: ApiClientException) {
            if (e is InvalidStatusException && e.status == HTTP_UNAUTHORIZED) {
                authRepository.invalidateSession()
                Outcome.Failure(LoadError.SessionExpired)
            } else {
                Outcome.Failure(e.toLoadError())
            }
        }
    }

    private fun ApiClientException.toLoadError(): LoadError = when (this) {
        is InvalidStatusException -> when (status) {
            HTTP_FORBIDDEN -> LoadError.Forbidden
            HTTP_NOT_FOUND -> LoadError.NotFound
            else -> LoadError.Unexpected(message)
        }
        is TimeoutException -> LoadError.ServerUnreachable
        is SecureConnectionException -> LoadError.SecureConnectionFailed
        else -> LoadError.Unexpected(message)
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
    }
}
