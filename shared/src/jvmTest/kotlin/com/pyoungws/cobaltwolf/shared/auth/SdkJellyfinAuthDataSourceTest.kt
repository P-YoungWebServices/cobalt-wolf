package com.pyoungws.cobaltwolf.shared.auth

import com.pyoungws.cobaltwolf.shared.model.PublicUser
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the real SDK client against a local HTTP server. */
class SdkJellyfinAuthDataSourceTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val baseUrl = "http://127.0.0.1:${server.address.port}"
    private val jellyfin = createJellyfin {
        clientInfo = ClientInfo(name = "Cobalt Wolf tests", version = "0")
        deviceInfo = DeviceInfo(id = "test-device", name = "JVM")
    }
    private val mainThread = Executors.newSingleThreadExecutor { Thread(it, "fake-main") }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        mainThread.shutdownNow()
    }

    /** Sends the headers and the first byte of the body, pauses, then sends the rest. */
    private fun respondSlowly(path: String, body: String, pauseMillis: Long) {
        server.createContext(path) { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out ->
                out.write(body.take(1).toByteArray())
                out.flush()
                Thread.sleep(pauseMillis)
                out.write(body.drop(1).toByteArray())
            }
        }
    }

    @Test
    fun `public users are parsed, and the network read does not block the calling thread`() {
        respondSlowly(
            "/Users/Public",
            """[
                {"Name":"alice","Id":"0f8fad5bd9cb469fa16570867728950e","HasPassword":true},
                {"Name":"kids","Id":"1f8fad5bd9cb469fa16570867728950e","HasPassword":false},
                {"Name":"legacy","Id":"2f8fad5bd9cb469fa16570867728950e"}
            ]""",
            pauseMillis = 800,
        )
        val dataSource = SdkJellyfinAuthDataSource(jellyfin)

        runBlocking(mainThread.asCoroutineDispatcher()) {
            // If the SDK read the response on this single thread, this tick could not run until after it.
            val tick = async {
                delay(100)
                System.nanoTime()
            }
            val users = dataSource.getPublicUsers(baseUrl)
            val finishedAt = System.nanoTime()

            assertTrue(tick.await() < finishedAt - 300_000_000, "calling thread was blocked by the network read")
            assertEquals(
                listOf(
                    PublicUser("0f8fad5b-d9cb-469f-a165-70867728950e", "alice", hasPassword = true),
                    PublicUser("1f8fad5b-d9cb-469f-a165-70867728950e", "kids", hasPassword = false),
                    PublicUser("2f8fad5b-d9cb-469f-a165-70867728950e", "legacy", hasPassword = true),
                ),
                users,
            )
        }
    }
}
