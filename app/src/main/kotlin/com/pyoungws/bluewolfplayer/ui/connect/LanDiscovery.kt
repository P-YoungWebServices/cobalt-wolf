package com.pyoungws.bluewolfplayer.ui.connect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** A Jellyfin server found by UDP broadcast on the local network. */
data class DiscoveredServer(val name: String, val address: String)

/**
 * Jellyfin's LAN discovery: servers answer a broadcast of "who is JellyfinServer?" to UDP 7359
 * with a JSON object naming themselves and their address. Waits up to [timeoutMs] for replies.
 */
object LanDiscovery {
    private const val DISCOVERY_PORT = 7359
    private const val QUERY = "who is JellyfinServer?"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun discover(timeoutMs: Long = 3_000): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, DiscoveredServer>()
        val socket = try {
            DatagramSocket().apply { broadcast = true; soTimeout = 250 }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        try {
            val query = QUERY.toByteArray()
            try {
                socket.send(DatagramPacket(query, query.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT))
            } catch (e: Exception) {
                return@withContext emptyList()
            }
            withTimeoutOrNull(timeoutMs) {
                val buffer = ByteArray(4096)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: java.net.SocketTimeoutException) {
                        continue
                    }
                    val reply = String(packet.data, packet.offset, packet.length)
                    val parsed = runCatching {
                        val obj = json.parseToJsonElement(reply).jsonObject
                        val address = obj["Address"]?.toString()?.trim('"') ?: return@runCatching null
                        val name = obj["Name"]?.toString()?.trim('"') ?: address
                        if (address.isNotBlank()) DiscoveredServer(name, address) else null
                    }.getOrNull()
                    if (parsed != null) found.putIfAbsent(parsed.address, parsed)
                }
            }
        } finally {
            socket.close()
        }
        found.values.toList()
    }
}
