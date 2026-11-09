package com.pyoungws.bluewolfplayer.shared.auth

import com.pyoungws.bluewolfplayer.shared.model.UserProfile
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.model.api.AuthenticationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Decodes real-shaped `/Users/AuthenticateByName` responses with the SDK serializer and maps them. */
class AuthenticationMappingTest {

    private fun policyJson(isAdministrator: Boolean) = """
        {
          "IsAdministrator": $isAdministrator, "IsHidden": false, "IsDisabled": false,
          "EnableUserPreferenceAccess": true, "EnableRemoteControlOfOtherUsers": false,
          "EnableSharedDeviceControl": true, "EnableRemoteAccess": true, "EnableLiveTvManagement": false,
          "EnableLiveTvAccess": true, "EnableMediaPlayback": true, "EnableAudioPlaybackTranscoding": true,
          "EnableVideoPlaybackTranscoding": true, "EnablePlaybackRemuxing": true,
          "ForceRemoteSourceTranscoding": false, "EnableContentDeletion": false,
          "EnableContentDownloading": true, "EnableSyncTranscoding": true, "EnableMediaConversion": true,
          "EnableAllDevices": true, "EnableAllChannels": true, "EnableAllFolders": true,
          "InvalidLoginAttemptCount": 0, "LoginAttemptsBeforeLockout": -1, "MaxActiveSessions": 0,
          "EnablePublicSharing": true, "RemoteClientBitrateLimit": 0,
          "AuthenticationProviderId": "Jellyfin.Server.Implementations.Users.DefaultAuthenticationProvider",
          "PasswordResetProviderId": "Jellyfin.Server.Implementations.Users.DefaultPasswordResetProvider",
          "SyncPlayAccess": "CreateAndJoinGroups"
        }
    """.trimIndent()

    private fun response(policy: String?, accessToken: String? = "abc123") = """
        {
          "User": {
            "Name": "alice",
            "ServerId": "server-1",
            "Id": "0f8fad5bd9cb469fa16570867728950e",
            "HasPassword": true
            ${if (policy != null) ", \"Policy\": $policy" else ""}
          },
          "AccessToken": ${accessToken?.let { "\"$it\"" } ?: "null"},
          "ServerId": "server-1"
        }
    """.trimIndent()

    private fun decode(json: String) = ApiSerializer.json.decodeFromString(AuthenticationResult.serializer(), json)

    @Test
    fun `administrator flag is read from the user policy`() {
        val result = decode(response(policyJson(isAdministrator = true))).toAuthenticatedUser()

        assertEquals("abc123", result.accessToken)
        assertEquals(
            UserProfile(id = "0f8fad5b-d9cb-469f-a165-70867728950e", name = "alice", isAdministrator = true),
            result.user,
        )
    }

    @Test
    fun `non-administrator policy maps to viewer`() {
        val result = decode(response(policyJson(isAdministrator = false))).toAuthenticatedUser()
        assertEquals(false, result.user.isAdministrator)
    }

    @Test
    fun `missing policy is treated as not an administrator`() {
        val result = decode(response(policy = null)).toAuthenticatedUser()
        assertEquals(false, result.user.isAdministrator)
    }

    @Test
    fun `missing access token is rejected`() {
        assertFailsWith<InvalidContentException> {
            decode(response(policyJson(true), accessToken = null)).toAuthenticatedUser()
        }
    }
}
