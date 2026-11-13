package com.pyoungws.bluewolfplayer.shared.auth

import com.pyoungws.bluewolfplayer.shared.model.PublicUser
import com.pyoungws.bluewolfplayer.shared.model.Session
import com.pyoungws.bluewolfplayer.shared.model.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface SignInError {
    data object MissingUsername : SignInError
    data object InvalidCredentials : SignInError
    data object SessionExpired : SignInError
    data object ServerUnreachable : SignInError
    data object SecureConnectionFailed : SignInError
    data object QuickConnectDisabled : SignInError
    data object QuickConnectExpired : SignInError
    data class Unexpected(val message: String?) : SignInError
}

sealed interface SignInResult {
    data class Success(val user: UserProfile) : SignInResult
    data class Failure(val error: SignInError) : SignInResult
}

sealed interface QuickConnectState {
    data object Starting : QuickConnectState
    data class WaitingForApproval(val code: String) : QuickConnectState
    data class SignedIn(val user: UserProfile) : QuickConnectState
    data class Failed(val error: SignInError) : QuickConnectState
}

sealed interface SessionState {
    data object Unknown : SessionState
    /** [lastServerAddress] is the server the user just left, so the app can offer sign-in there again. */
    data class SignedOut(val lastServerAddress: String? = null) : SessionState
    data class SignedIn(val session: Session) : SessionState
}

class AuthRepository(
    private val dataSource: JellyfinAuthDataSource,
    private val sessionStore: SessionStore,
    private val profilesStore: ServerProfilesStore = InMemoryServerProfilesStore(),
    private val quickConnectPollInterval: Duration = 5.seconds,
    private val logoutTimeout: Duration = 5.seconds,
) {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unknown)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    /** Every account the user has signed in to, most recently added last. */
    private val _profiles = MutableStateFlow<List<ServerProfile>>(emptyList())
    val profiles: StateFlow<List<ServerProfile>> = _profiles.asStateFlow()

    suspend fun restoreSession() {
        _profiles.value = profilesStore.load()
        _sessionState.value = sessionStore.load()?.let(SessionState::SignedIn) ?: SessionState.SignedOut()
    }

    /**
     * Moves the app to [profile]'s server and account. The current session's profile stays saved,
     * so switching back later needs no sign-in. Returns false for an unknown profile.
     */
    suspend fun switchTo(profileId: String): Boolean {
        val profile = _profiles.value.find { it.id == profileId } ?: return false
        val current = (sessionState.value as? SessionState.SignedIn)?.session
        if (current != null && current.serverAddress == profile.serverAddress && current.userId == profile.userId) {
            return true
        }
        sessionStore.save(profile.toSession())
        _sessionState.value = SessionState.SignedIn(profile.toSession())
        return true
    }

    /**
     * Forgets [profileId]'s account. Removing the profile in use also ends that session on the
     * server and returns the app to the sign-in flow.
     */
    suspend fun removeProfile(profileId: String) {
        val profile = _profiles.value.find { it.id == profileId } ?: return
        val current = (sessionState.value as? SessionState.SignedIn)?.session
        if (current != null && current.serverAddress == profile.serverAddress && current.userId == profile.userId) {
            signOut()
        } else {
            saveProfiles(_profiles.value - profile)
        }
    }

    /**
     * Ends the session on the server (revoking the access token) and deletes it from the device.
     * The server call is best-effort: the local token is always removed, even when offline.
     */
    suspend fun signOut() {
        val session = (sessionState.value as? SessionState.SignedIn)?.session ?: return
        try {
            withTimeoutOrNull(logoutTimeout) { dataSource.logout(session.serverAddress, session.accessToken) }
        } catch (e: ApiClientException) {
            // Already invalid or unreachable; nothing more to revoke from here.
        }
        clearSession(session.serverAddress)
    }

    /**
     * Users the server shows on its login screen. Empty if the server hides all users or the list
     * can't be loaded; sign-in by typed username still works in both cases.
     */
    suspend fun publicUsers(serverAddress: String): List<PublicUser> = try {
        dataSource.getPublicUsers(serverAddress).filter { it.name.isNotBlank() }
    } catch (e: ApiClientException) {
        emptyList()
    }

    /** Asks the server to email the user a reset PIN; false when the request fails. */
    suspend fun forgotPassword(serverAddress: String, username: String): Boolean = try {
        dataSource.forgotPassword(serverAddress, username)
    } catch (e: Exception) {
        false
    }

    suspend fun signIn(serverAddress: String, username: String, password: String): SignInResult {
        val name = username.trim()
        if (name.isEmpty()) return SignInResult.Failure(SignInError.MissingUsername)
        return try {
            SignInResult.Success(persist(serverAddress, dataSource.authenticateByName(serverAddress, name, password)))
        } catch (e: ApiClientException) {
            SignInResult.Failure(e.toSignInError())
        }
    }

    /**
     * Runs a Quick Connect sign-in: shows a code, polls until the user approves it from another
     * signed-in device, then exchanges the secret for an access token. Cancelling collection stops polling.
     */
    fun quickConnect(serverAddress: String): Flow<QuickConnectState> = flow {
        emit(QuickConnectState.Starting)
        if (!dataSource.isQuickConnectEnabled(serverAddress)) {
            emit(QuickConnectState.Failed(SignInError.QuickConnectDisabled))
            return@flow
        }

        var ticket = try {
            dataSource.initiateQuickConnect(serverAddress)
        } catch (e: InvalidStatusException) {
            if (e.status != HTTP_UNAUTHORIZED) throw e
            emit(QuickConnectState.Failed(SignInError.QuickConnectDisabled))
            return@flow
        }
        emit(QuickConnectState.WaitingForApproval(ticket.code))

        while (!ticket.authenticated) {
            delay(quickConnectPollInterval)
            ticket = try {
                dataSource.getQuickConnectState(serverAddress, ticket.secret)
            } catch (e: InvalidStatusException) {
                // The server forgets the secret once the request times out.
                if (e.status != HTTP_NOT_FOUND) throw e
                emit(QuickConnectState.Failed(SignInError.QuickConnectExpired))
                return@flow
            }
        }

        val user = persist(serverAddress, dataSource.authenticateWithQuickConnect(serverAddress, ticket.secret))
        emit(QuickConnectState.SignedIn(user))
    }.catch { e ->
        if (e !is ApiClientException) throw e
        emit(QuickConnectState.Failed(e.toSignInError()))
    }

    /** Fetches the signed-in user's current profile and policy. Signs out if the token was revoked. */
    suspend fun currentUser(): SignInResult {
        val session = (sessionState.value as? SessionState.SignedIn)?.session
            ?: return SignInResult.Failure(SignInError.SessionExpired)
        return try {
            SignInResult.Success(dataSource.getCurrentUser(session.serverAddress, session.accessToken))
        } catch (e: ApiClientException) {
            if (e is InvalidStatusException && e.status == HTTP_UNAUTHORIZED) {
                clearSession(session.serverAddress)
                SignInResult.Failure(SignInError.SessionExpired)
            } else {
                SignInResult.Failure(e.toSignInError())
            }
        }
    }

    /** Called when the server rejects the stored token (e.g. revoked): drops it and returns to sign-in. */
    suspend fun invalidateSession() {
        val session = (sessionState.value as? SessionState.SignedIn)?.session ?: return
        clearSession(session.serverAddress)
    }

    private suspend fun clearSession(serverAddress: String) {
        val userId = (sessionState.value as? SessionState.SignedIn)?.session?.userId
        sessionStore.clear()
        if (userId != null) removeStoredProfile(serverAddress, userId)
        _sessionState.value = SessionState.SignedOut(lastServerAddress = serverAddress)
    }

    private suspend fun persist(serverAddress: String, auth: AuthenticatedUser): UserProfile {
        val session = Session(serverAddress = serverAddress, userId = auth.user.id, accessToken = auth.accessToken)
        sessionStore.save(session)
        upsertProfile(serverAddress, auth)
        _sessionState.value = SessionState.SignedIn(session)
        return auth.user
    }

    /** Adds or refreshes the switchable profile for a sign-in. A server-name lookup is best-effort. */
    private suspend fun upsertProfile(serverAddress: String, auth: AuthenticatedUser) {
        val id = ServerProfile.idFor(serverAddress, auth.user.id)
        val existing = _profiles.value.find { it.id == id }
        val serverName = try {
            dataSource.getServerName(serverAddress)
        } catch (e: ApiClientException) {
            existing?.serverName
        } ?: existing?.serverName
        val profile = ServerProfile(
            id = id,
            serverAddress = serverAddress,
            serverName = serverName,
            userId = auth.user.id,
            userName = auth.user.name,
            accessToken = auth.accessToken,
        )
        saveProfiles(_profiles.value.filterNot { it.id == id } + profile)
    }

    private suspend fun removeStoredProfile(serverAddress: String, userId: String) {
        val id = ServerProfile.idFor(serverAddress, userId)
        saveProfiles(_profiles.value.filterNot { it.id == id })
    }

    private suspend fun saveProfiles(profiles: List<ServerProfile>) {
        _profiles.value = profiles
        profilesStore.saveAll(profiles)
    }

    private fun ApiClientException.toSignInError(): SignInError = when (this) {
        is InvalidStatusException -> when (status) {
            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> SignInError.InvalidCredentials
            else -> SignInError.Unexpected(message)
        }
        is TimeoutException -> SignInError.ServerUnreachable
        is SecureConnectionException -> SignInError.SecureConnectionFailed
        else -> SignInError.Unexpected(message)
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
    }
}
