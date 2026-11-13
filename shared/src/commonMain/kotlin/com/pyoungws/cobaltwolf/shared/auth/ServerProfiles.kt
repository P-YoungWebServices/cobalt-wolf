package com.pyoungws.cobaltwolf.shared.auth

import com.pyoungws.cobaltwolf.shared.model.Session
import kotlinx.serialization.Serializable

/**
 * One signed-in account on one Jellyfin server, kept so the user can switch back to it without
 * signing in again. Profiles are created on every successful sign-in (see
 * [AuthRepository]); signing out or a revoked token removes the matching profile.
 */
@Serializable
data class ServerProfile(
    /** Stable identity of server+user; re-signing into the same account updates this profile. */
    val id: String,
    val serverAddress: String,
    /** The server's display name, when it could be fetched (falls back to the address in the UI). */
    val serverName: String? = null,
    val userId: String,
    val userName: String,
    val accessToken: String,
) {
    fun toSession() = Session(serverAddress = serverAddress, userId = userId, accessToken = accessToken)

    companion object {
        fun idFor(serverAddress: String, userId: String): String = "${serverAddress.trimEnd('/')}|$userId"
    }
}

/**
 * Persists the list of [ServerProfile]s. Implementations must store access tokens securely
 * (same rules as [SessionStore]).
 */
interface ServerProfilesStore {
    suspend fun load(): List<ServerProfile>
    suspend fun saveAll(profiles: List<ServerProfile>)
}

/** Non-persistent fallback for platforms without a secure profiles store, and for tests. */
class InMemoryServerProfilesStore(var profiles: List<ServerProfile> = emptyList()) : ServerProfilesStore {
    override suspend fun load(): List<ServerProfile> = profiles
    override suspend fun saveAll(profiles: List<ServerProfile>) {
        this.profiles = profiles
    }
}
