package com.pyoungws.bluewolfplayer.shared.admin

import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Admin dashboard data: active sessions, recently added media and session messaging. */
class AdminRepository(
    private val dataSource: JellyfinAdminDataSource,
    private val guard: SessionGuard,
) {
    suspend fun dashboard(): Outcome<AdminDashboard> = guard.run { session ->
        coroutineScope {
            val sessions = async { dataSource.getSessions(session) }
            val latest = async { dataSource.getRecentlyAdded(session, RECENT_LIMIT) }
            AdminDashboard(sessions.await(), latest.await())
        }
    }

    /** Sends a popup message to an active session. [text] is ignored when blank. */
    suspend fun sendMessage(sessionId: String, text: String): Outcome<Unit> {
        val message = text.trim()
        if (message.isEmpty()) return Outcome.Success(Unit)
        return guard.run { dataSource.sendMessage(it, sessionId, message) }
    }

    private companion object {
        const val RECENT_LIMIT = 16
    }
}
