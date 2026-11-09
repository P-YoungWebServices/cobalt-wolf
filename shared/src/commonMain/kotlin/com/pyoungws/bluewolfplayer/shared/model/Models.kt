package com.pyoungws.bluewolfplayer.shared.model

/** A Jellyfin server that responded and passed validation. */
data class ServerInfo(
    val address: String,
    val name: String?,
    val version: String?,
    val id: String?,
)

/** The signed-in user, with the role read from the Jellyfin user policy. */
data class UserProfile(
    val id: String,
    val name: String,
    val isAdministrator: Boolean,
)

/** Persisted credentials for the signed-in user. */
data class Session(
    val serverAddress: String,
    val userId: String,
    val accessToken: String,
)
