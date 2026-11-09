package com.pyoungws.bluewolfplayer.shared.auth

import com.pyoungws.bluewolfplayer.shared.model.UserProfile
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.authenticateWithQuickConnect
import org.jellyfin.sdk.api.client.extensions.authenticationApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
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
    suspend fun authenticateByName(serverAddress: String, username: String, password: String): AuthenticatedUser
    suspend fun isQuickConnectEnabled(serverAddress: String): Boolean
    suspend fun initiateQuickConnect(serverAddress: String): QuickConnectTicket
    suspend fun getQuickConnectState(serverAddress: String, secret: String): QuickConnectTicket
    suspend fun authenticateWithQuickConnect(serverAddress: String, secret: String): AuthenticatedUser
    suspend fun getCurrentUser(serverAddress: String, accessToken: String): UserProfile
    suspend fun logout(serverAddress: String, accessToken: String)
}

class SdkJellyfinAuthDataSource(private val jellyfin: Jellyfin) : JellyfinAuthDataSource {

    private fun api(serverAddress: String, accessToken: String? = null) =
        jellyfin.createApi(baseUrl = serverAddress, accessToken = accessToken)

    override suspend fun authenticateByName(serverAddress: String, username: String, password: String) =
        api(serverAddress).authenticationApi.authenticateUserByName(username, password).content.toAuthenticatedUser()

    override suspend fun isQuickConnectEnabled(serverAddress: String) =
        api(serverAddress).authenticationApi.getQuickConnectEnabled().content

    override suspend fun initiateQuickConnect(serverAddress: String) =
        api(serverAddress).authenticationApi.initiateQuickConnect().content.toTicket()

    override suspend fun getQuickConnectState(serverAddress: String, secret: String) =
        api(serverAddress).authenticationApi.getQuickConnectState(secret).content.toTicket()

    override suspend fun authenticateWithQuickConnect(serverAddress: String, secret: String) =
        api(serverAddress).authenticationApi.authenticateWithQuickConnect(secret).content.toAuthenticatedUser()

    override suspend fun getCurrentUser(serverAddress: String, accessToken: String) =
        api(serverAddress, accessToken).userApi.getCurrentUser().content.toUserProfile()

    override suspend fun logout(serverAddress: String, accessToken: String) {
        api(serverAddress, accessToken).sessionApi.reportSessionEnded()
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

private fun QuickConnectResult.toTicket() = QuickConnectTicket(code = code, secret = secret, authenticated = authenticated)
