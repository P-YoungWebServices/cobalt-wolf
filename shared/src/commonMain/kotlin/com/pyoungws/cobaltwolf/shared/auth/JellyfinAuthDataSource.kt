package com.pyoungws.cobaltwolf.shared.auth

import com.pyoungws.cobaltwolf.shared.model.PublicUser
import com.pyoungws.cobaltwolf.shared.model.UserProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.authenticateWithQuickConnect
import org.jellyfin.sdk.api.client.extensions.authenticationApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.QuickConnectResult
import org.jellyfin.sdk.model.api.UserDto

data class AuthenticatedUser(val accessToken: String, val user: UserProfile)

data class QuickConnectTicket(val code: String, val secret: String, val authenticated: Boolean)

/**
 * Jellyfin authentication calls. Implementations throw the SDK's
 * [org.jellyfin.sdk.api.client.exception.ApiClientException] subtypes on failure.
 */
interface JellyfinAuthDataSource {
    suspend fun getPublicUsers(serverAddress: String): List<PublicUser>
    suspend fun authenticateByName(serverAddress: String, username: String, password: String): AuthenticatedUser
    suspend fun isQuickConnectEnabled(serverAddress: String): Boolean
    suspend fun initiateQuickConnect(serverAddress: String): QuickConnectTicket
    suspend fun getQuickConnectState(serverAddress: String, secret: String): QuickConnectTicket
    suspend fun authenticateWithQuickConnect(serverAddress: String, secret: String): AuthenticatedUser
    suspend fun getCurrentUser(serverAddress: String, accessToken: String): UserProfile
    /** The server's display name from its public system info, or null when it cannot be fetched. */
    suspend fun getServerName(serverAddress: String): String?
    suspend fun logout(serverAddress: String, accessToken: String)
    /** Sends a password-reset PIN to the user's configured email; true when the request went through. */
    suspend fun forgotPassword(serverAddress: String, username: String): Boolean
}

/**
 * The SDK's OkHttp client reads response bodies on the calling thread, so every call is moved to
 * [ioDispatcher]; calling it from the main thread throws NetworkOnMainThreadException on Android.
 */
class SdkJellyfinAuthDataSource(
    private val jellyfin: Jellyfin,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JellyfinAuthDataSource {

    private suspend fun <T> call(serverAddress: String, accessToken: String? = null, block: suspend ApiClient.() -> T): T =
        withContext(ioDispatcher) { jellyfin.createApi(baseUrl = serverAddress, accessToken = accessToken).block() }

    override suspend fun getPublicUsers(serverAddress: String) = call(serverAddress) {
        userApi.getPublicUsers().content.map { it.toPublicUser(serverAddress) }
    }

    override suspend fun authenticateByName(serverAddress: String, username: String, password: String) = call(serverAddress) {
        authenticationApi.authenticateUserByName(username, password).content.toAuthenticatedUser()
    }

    override suspend fun isQuickConnectEnabled(serverAddress: String) = call(serverAddress) {
        authenticationApi.getQuickConnectEnabled().content
    }

    override suspend fun initiateQuickConnect(serverAddress: String) = call(serverAddress) {
        authenticationApi.initiateQuickConnect().content.toTicket()
    }

    override suspend fun getQuickConnectState(serverAddress: String, secret: String) = call(serverAddress) {
        authenticationApi.getQuickConnectState(secret).content.toTicket()
    }

    override suspend fun authenticateWithQuickConnect(serverAddress: String, secret: String) = call(serverAddress) {
        authenticationApi.authenticateWithQuickConnect(secret).content.toAuthenticatedUser()
    }

    override suspend fun getCurrentUser(serverAddress: String, accessToken: String) = call(serverAddress, accessToken) {
        userApi.getCurrentUser().content.toUserProfile()
    }

    override suspend fun getServerName(serverAddress: String): String? = call(serverAddress) {
        systemApi.getPublicSystemInfo().content.serverName
    }

    override suspend fun logout(serverAddress: String, accessToken: String) = call(serverAddress, accessToken) {
        sessionApi.reportSessionEnded()
        Unit
    }

    override suspend fun forgotPassword(serverAddress: String, username: String): Boolean = try {
        call(serverAddress) {
            request(HttpMethod.POST, "/Users/ForgotPassword", emptyMap(), emptyMap(), mapOf("Username" to username))
        }
        true
    } catch (e: ApiClientException) {
        false
    }
}

internal fun AuthenticationResult.toAuthenticatedUser(): AuthenticatedUser {
    val token = accessToken
    val user = user
    if (token.isNullOrBlank() || user == null) {
        throw InvalidContentException("Authentication response is missing the access token or user")
    }
    return AuthenticatedUser(token, user.toUserProfile())
}

internal fun UserDto.toUserProfile() = UserProfile(
    id = id.toString(),
    name = name.orEmpty(),
    isAdministrator = policy?.isAdministrator == true,
)

/**
 * `HasPassword` is deprecated in the 12.x API but still the only signal for passwordless users.
 * If it is missing (or removed later) we assume a password, so the prompt is never skipped wrongly;
 * a passwordless user can still submit an empty password.
 */
@Suppress("DEPRECATION")
internal fun UserDto.toPublicUser(serverAddress: String) = PublicUser(
    id = id.toString(),
    name = name.orEmpty(),
    hasPassword = hasPassword ?: true,
    imageUrl = primaryImageTag?.let { "${serverAddress.trimEnd('/')}/Users/$id/Images/Primary?tag=$it&quality=90" },
)

private fun QuickConnectResult.toTicket() = QuickConnectTicket(code = code, secret = secret, authenticated = authenticated)
