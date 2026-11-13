package com.pyoungws.cobaltwolf.shared.admin

import com.pyoungws.cobaltwolf.shared.library.SessionGuard
import com.pyoungws.cobaltwolf.shared.model.MediaItem
import com.pyoungws.cobaltwolf.shared.model.Outcome
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Admin dashboard data: active sessions, recently added media and session messaging. */
class AdminRepository(
    private val dataSource: JellyfinAdminDataSource,
    private val guard: SessionGuard,
) {
    /** The server's active sessions, for the dashboard and the home-screen widget. */
    suspend fun sessions(): Outcome<List<ActiveSession>> = guard.run { dataSource.getSessions(it) }

    suspend fun dashboard(): Outcome<AdminDashboard> = guard.run { session ->
        coroutineScope {
            val sessions = async { dataSource.getSessions(session) }
            val latest = async { dataSource.getRecentlyAdded(session, RECENT_LIMIT) }
            AdminDashboard(sessions.await(), latest.await())
        }
    }

    /** Recently added media alone — the dashboard row viewers get without admin sessions. */
    suspend fun recentlyAdded(): Outcome<List<MediaItem>> =
        guard.run { dataSource.getRecentlyAdded(it, RECENT_LIMIT) }

    /** Sends a popup message to an active session. [text] is ignored when blank. */
    suspend fun sendMessage(sessionId: String, text: String): Outcome<Unit> {
        val message = text.trim()
        if (message.isEmpty()) return Outcome.Success(Unit)
        return guard.run { dataSource.sendMessage(it, sessionId, message) }
    }

    suspend fun users(): Outcome<List<AdminUser>> = guard.run { dataSource.getUsers(it) }

    /** Creates a user. A blank [password] creates an account without one. */
    suspend fun createUser(name: String, password: String): Outcome<AdminUser> =
        guard.run { dataSource.createUser(it, name.trim(), password.ifBlank { null }) }

    /** Applies the non-null fields of [edit] to the account. */
    suspend fun updateUser(userId: String, edit: AdminUserEdit): Outcome<Unit> =
        guard.run { dataSource.updateUser(it, userId, edit.copy(name = edit.name?.trim())) }

    suspend fun deleteUser(userId: String): Outcome<Unit> =
        guard.run { dataSource.deleteUser(it, userId) }

    suspend fun devices(): Outcome<List<AdminDevice>> = guard.run { dataSource.getDevices(it) }

    suspend fun renameDevice(deviceId: String, name: String): Outcome<Unit> =
        guard.run { dataSource.renameDevice(it, deviceId, name.trim()) }

    suspend fun deleteDevice(deviceId: String): Outcome<Unit> =
        guard.run { dataSource.deleteDevice(it, deviceId) }

    suspend fun libraries(): Outcome<List<AdminLibrary>> = guard.run { dataSource.getAdminLibraries(it) }

    suspend fun scheduledTasks(): Outcome<List<ScheduledTask>> = guard.run { dataSource.getScheduledTasks(it) }

    suspend fun startTask(taskId: String): Outcome<Unit> = guard.run { dataSource.startTask(it, taskId) }

    suspend fun stopTask(taskId: String): Outcome<Unit> = guard.run { dataSource.stopTask(it, taskId) }

    /** Log files, newest first. */
    suspend fun logFiles(): Outcome<List<ServerLogFile>> = guard.run { dataSource.getLogFiles(it) }

    suspend fun logContent(fileName: String): Outcome<String> =
        guard.run { dataSource.getLogContent(it, fileName) }

    suspend fun systemInfo(): Outcome<SystemInformation> = guard.run { dataSource.getSystemInfo(it) }

    suspend fun activityLog(startIndex: Int = 0, limit: Int = 50): Outcome<List<ActivityEntry>> = guard.run { dataSource.getActivityLog(it, startIndex, limit) }

    suspend fun apiKeys(): Outcome<List<ServerApiKey>> = guard.run { dataSource.getApiKeys(it) }

    suspend fun createApiKey(appName: String): Outcome<Unit> = guard.run { dataSource.createApiKey(it, appName) }

    suspend fun revokeApiKey(key: String): Outcome<Unit> = guard.run { dataSource.revokeApiKey(it, key) }

    suspend fun plugins(): Outcome<List<AdminPlugin>> = guard.run { dataSource.getPlugins(it) }

    suspend fun setPluginEnabled(pluginId: String, version: String, enabled: Boolean): Outcome<Unit> =
        guard.run { dataSource.setPluginEnabled(it, pluginId, version, enabled) }

    suspend fun uninstallPlugin(pluginId: String): Outcome<Unit> = guard.run { dataSource.uninstallPlugin(it, pluginId) }

    suspend fun serverSettings(): Outcome<ServerSettings> = guard.run { dataSource.getServerSettings(it) }

    suspend fun updateServerSettings(settings: ServerSettings): Outcome<Unit> =
        guard.run { dataSource.updateServerSettings(it, settings) }

    suspend fun restartServer(): Outcome<Unit> = guard.run { dataSource.restartServer(it) }

    suspend fun shutdownServer(): Outcome<Unit> = guard.run { dataSource.shutdownServer(it) }

    private companion object {
        const val RECENT_LIMIT = 16
    }
}
