package com.pyoungws.bluewolfplayer.shared.admin

import com.pyoungws.bluewolfplayer.shared.library.movie
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.Session

fun playingSession(
    id: String,
    user: String = "alice",
    device: String = "Shield TV",
    method: SessionPlayMethod? = SessionPlayMethod.DirectPlay,
    item: MediaItem? = movie("item-$id"),
) = ActiveSession(
    id = id,
    userName = user,
    deviceName = device,
    client = "Cobalt Wolf",
    item = item,
    progressFraction = 0.4f,
    isPaused = false,
    playMethod = method,
    videoCodec = "hevc",
    audioCodec = "truehd",
    transcoding = if (method == SessionPlayMethod.Transcode) {
        TranscodeDetails("hls", "h264", "aac", 8_000_000, 1920, 1080, 2, false, false, listOf("video codec not supported"))
    } else {
        null
    },
)

fun adminUser(
    id: String,
    name: String = id,
    admin: Boolean = false,
) = AdminUser(
    id = id,
    name = name,
    isAdministrator = admin,
    isDisabled = false,
    isHidden = false,
    hasPassword = true,
    lastLogin = "2026-11-10T10:00",
    lastActivity = "2026-11-10T11:00",
)

fun adminDevice(id: String, name: String = id) = AdminDevice(
    id = id,
    name = name,
    customName = null,
    appName = "Jellyfin Web",
    appVersion = "12.0.0",
    lastUserName = "alice",
    lastActivity = "2026-11-10T11:00",
)

fun adminLibrary(id: String, name: String = id) = AdminLibrary(
    id = id,
    name = name,
    collectionType = "movies",
    locations = listOf("/media/$id"),
    refreshStatus = null,
    refreshProgress = null,
)

fun scheduledTask(
    id: String,
    name: String = id,
    state: AdminTaskState = AdminTaskState.Idle,
    progress: Float? = null,
) = ScheduledTask(
    id = id,
    name = name,
    category = "Library",
    description = null,
    state = state,
    progressFraction = progress,
    lastRun = "2026-11-10T06:00",
    lastResult = "Completed",
)

fun logFile(name: String) = ServerLogFile(name = name, sizeBytes = 4_096, modified = "2026-11-10T12:00")

fun sampleSystemInfo(updateAvailable: Boolean = false) = SystemInformation(
    serverName = "wolf",
    version = "12.0.0",
    productName = "Jellyfin Server",
    operatingSystem = "Linux",
    architecture = "X64",
    localAddress = "http://192.168.1.10:8096",
    packageName = "docker",
    hasPendingRestart = false,
    hasUpdateAvailable = updateAvailable,
    webSocketPort = 8096,
    storage = emptyList(),
)

class FakeAdminDataSource : JellyfinAdminDataSource {
    val calls = mutableListOf<String>()
    var dashboardCalls = 0
        private set

    var sessions: () -> List<ActiveSession> = { emptyList() }
    var recentlyAdded: () -> List<MediaItem> = { emptyList() }
    var onSendMessage: (sessionId: String, text: String) -> Unit = { _, _ -> }

    var users: () -> List<AdminUser> = { emptyList() }
    var onCreateUser: (name: String, password: String?) -> AdminUser = { name, _ -> adminUser("new-$name", name) }
    var onUpdateUser: (userId: String, edit: AdminUserEdit) -> Unit = { _, _ -> }
    var onDeleteUser: (userId: String) -> Unit = { }

    var devices: () -> List<AdminDevice> = { emptyList() }
    var onRenameDevice: (deviceId: String, name: String) -> Unit = { _, _ -> }
    var onDeleteDevice: (deviceId: String) -> Unit = { }

    var libraries: () -> List<AdminLibrary> = { emptyList() }

    var tasks: () -> List<ScheduledTask> = { emptyList() }
    var onStartTask: (taskId: String) -> Unit = { }
    var onStopTask: (taskId: String) -> Unit = { }

    var logFiles: () -> List<ServerLogFile> = { emptyList() }
    var logContent: (fileName: String) -> String = { "" }

    var systemInfo: () -> SystemInformation = { sampleSystemInfo() }

    override suspend fun getSessions(session: Session): List<ActiveSession> {
        calls += "sessions"
        dashboardCalls++
        return sessions()
    }

    override suspend fun getRecentlyAdded(session: Session, limit: Int): List<MediaItem> {
        calls += "latest($limit)"
        return recentlyAdded()
    }

    override suspend fun sendMessage(session: Session, sessionId: String, text: String) {
        calls += "message($sessionId, $text)"
        onSendMessage(sessionId, text)
    }

    override suspend fun getUsers(session: Session): List<AdminUser> {
        calls += "users"
        return users()
    }

    override suspend fun createUser(session: Session, name: String, password: String?): AdminUser {
        calls += "createUser($name)"
        return onCreateUser(name, password)
    }

    override suspend fun updateUser(session: Session, userId: String, edit: AdminUserEdit) {
        calls += "updateUser($userId, $edit)"
        onUpdateUser(userId, edit)
    }

    override suspend fun deleteUser(session: Session, userId: String) {
        calls += "deleteUser($userId)"
        onDeleteUser(userId)
    }

    override suspend fun getDevices(session: Session): List<AdminDevice> {
        calls += "devices"
        return devices()
    }

    override suspend fun renameDevice(session: Session, deviceId: String, name: String) {
        calls += "renameDevice($deviceId, $name)"
        onRenameDevice(deviceId, name)
    }

    override suspend fun deleteDevice(session: Session, deviceId: String) {
        calls += "deleteDevice($deviceId)"
        onDeleteDevice(deviceId)
    }

    override suspend fun getAdminLibraries(session: Session): List<AdminLibrary> {
        calls += "adminLibraries"
        return libraries()
    }

    override suspend fun getScheduledTasks(session: Session): List<ScheduledTask> {
        calls += "tasks"
        return tasks()
    }

    override suspend fun startTask(session: Session, taskId: String) {
        calls += "startTask($taskId)"
        onStartTask(taskId)
    }

    override suspend fun stopTask(session: Session, taskId: String) {
        calls += "stopTask($taskId)"
        onStopTask(taskId)
    }

    override suspend fun getLogFiles(session: Session): List<ServerLogFile> {
        calls += "logFiles"
        return logFiles()
    }

    override suspend fun getLogContent(session: Session, fileName: String): String {
        calls += "logContent($fileName)"
        return logContent(fileName)
    }

    override suspend fun getSystemInfo(session: Session): SystemInformation {
        calls += "systemInfo"
        return systemInfo()
    }

    override suspend fun getActivityLog(session: Session, startIndex: Int, limit: Int): List<com.pyoungws.bluewolfplayer.shared.admin.ActivityEntry> {
        calls += "activityLog($startIndex,$limit)"
        return emptyList()
    }

    override suspend fun getApiKeys(session: Session): List<com.pyoungws.bluewolfplayer.shared.admin.ServerApiKey> {
        calls += "apiKeys"
        return emptyList()
    }

    override suspend fun createApiKey(session: Session, appName: String) {
        calls += "createApiKey($appName)"
    }

    override suspend fun revokeApiKey(session: Session, key: String) {
        calls += "revokeApiKey($key)"
    }

    var plugins: () -> List<AdminPlugin> = { emptyList() }
    var onSetPluginEnabled: (pluginId: String, version: String, enabled: Boolean) -> Unit = { _, _, _ -> }
    var onUninstallPlugin: (pluginId: String) -> Unit = { }
    var serverSettings: ServerSettings = ServerSettings(serverName = "wolf")
    var onUpdateServerSettings: (settings: ServerSettings) -> Unit = { }
    var onRestart: () -> Unit = { }
    var onShutdown: () -> Unit = { }

    override suspend fun getPlugins(session: Session): List<AdminPlugin> {
        calls += "plugins"
        return plugins()
    }

    override suspend fun setPluginEnabled(session: Session, pluginId: String, version: String, enabled: Boolean) {
        calls += "setPluginEnabled($pluginId,$enabled)"
        onSetPluginEnabled(pluginId, version, enabled)
    }

    override suspend fun uninstallPlugin(session: Session, pluginId: String) {
        calls += "uninstallPlugin($pluginId)"
        onUninstallPlugin(pluginId)
    }

    override suspend fun getServerSettings(session: Session): ServerSettings {
        calls += "serverSettings"
        return serverSettings
    }

    override suspend fun updateServerSettings(session: Session, settings: ServerSettings) {
        calls += "updateServerSettings(${settings.serverName})"
        onUpdateServerSettings(settings)
    }

    override suspend fun restartServer(session: Session) {
        calls += "restart"
        onRestart()
    }

    override suspend fun shutdownServer(session: Session) {
        calls += "shutdown"
        onShutdown()
    }
}
