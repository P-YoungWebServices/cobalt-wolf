package com.pyoungws.bluewolfplayer.shared.server

import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.discovery.RecommendedServerInfo

/** Probes the candidate addresses for a user-entered server address. */
fun interface ServerDiscovery {
    suspend fun discover(input: String): List<RecommendedServerInfo>
}

class SdkServerDiscovery(private val jellyfin: Jellyfin) : ServerDiscovery {
    override suspend fun discover(input: String): List<RecommendedServerInfo> =
        jellyfin.discovery.getRecommendedServers(input).toList()
}
