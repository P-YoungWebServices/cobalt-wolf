package com.pyoungws.bluewolfplayer.shared.admin

import com.pyoungws.bluewolfplayer.shared.library.toMediaItem
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.Session
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.extensions.deviceApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.libraryStructureApi
import org.jellyfin.sdk.api.client.extensions.scheduledTaskApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.api.CreateUserByName
import org.jellyfin.sdk.model.api.DeviceInfoDto
import org.jellyfin.sdk.model.api.FolderStorageDto
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.LogFile
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MessageCommand
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.SystemInfo
import org.jellyfin.sdk.model.api.SystemStorageDto
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskState
import org.jellyfin.sdk.model.api.TranscodingInfo
import org.jellyfin.sdk.model.api.UpdateUserPassword
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.VirtualFolderInfo
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest

/** Jellyfin admin queries. Implementations throw the SDK's `ApiClientException` subtypes on failure. */
interface JellyfinAdminDataSource {
    suspend fun getSessions(session: Session): List<ActiveSession>
    suspend fun getRecentlyAdded(session: Session, limit: Int): List<MediaItem>
    suspend fun sendMessage(session: Session, sessionId: String, text: String)

    suspend fun getUsers(session: Session): List<AdminUser>
    suspend fun createUser(session: Session, name: String, password: String?): AdminUser
    suspend fun updateUser(session: Session, userId: String, edit: AdminUserEdit)
    suspend fun deleteUser(session: Session, userId: String)

    suspend fun getDevices(session: Session): List<AdminDevice>
    suspend fun renameDevice(session: Session, deviceId: String, name: String)
    suspend fun deleteDevice(session: Session, deviceId: String)

    suspend fun getAdminLibraries(session: Session): List<AdminLibrary>

    suspend fun getScheduledTasks(session: Session): List<ScheduledTask>
    suspend fun startTask(session: Session, taskId: String)
    suspend fun stopTask(session: Session, taskId: String)

    suspend fun getLogFiles(session: Session): List<ServerLogFile>
    suspend fun getLogContent(session: Session, fileName: String): String

    suspend fun getSystemInfo(session: Session): SystemInformation
}

class SdkJellyfinAdminDataSource(
    private val jellyfin: Jellyfin,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JellyfinAdminDataSource {

    private suspend fun <T> call(session: Session, block: suspend ApiClient.() -> T): T =
        withContext(ioDispatcher) {
            jellyfin.createApi(baseUrl = session.serverAddress, accessToken = session.accessToken).block()
        }

    override suspend fun getSessions(session: Session) = call(session) {
        sessionApi.getSessions().content.map { it.toActiveSession(session.serverAddress) }
    }

    override suspend fun getRecentlyAdded(session: Session, limit: Int) = call(session) {
        libraryApi.getLatestMedia(
            GetLatestMediaRequest(
                userId = session.userId.toUuid(),
                fields = ITEM_FIELDS,
                limit = limit,
                enableUserData = true,
            ),
        ).content.map { it.toMediaItem(session.serverAddress) }
    }

    override suspend fun sendMessage(session: Session, sessionId: String, text: String) = call(session) {
        sessionApi.sendMessageCommand(
            sessionId,
            MessageCommand(header = MESSAGE_HEADER, text = text, timeoutMs = MESSAGE_TIMEOUT_MS),
        )
        Unit
    }

    override suspend fun getUsers(session: Session) = call(session) {
        userApi.getUsers().content.map { it.toAdminUser() }
    }

    override suspend fun createUser(session: Session, name: String, password: String?) = call(session) {
        userApi.createUserByName(CreateUserByName(name = name, password = password)).content.toAdminUser()
    }

    override suspend fun updateUser(session: Session, userId: String, edit: AdminUserEdit) = call(session) {
        val uuid = userId.toUuid()
        if (edit.name != null) {
            // The update endpoint expects the full user object; patch the name onto it.
            val user = userApi.getUserById(uuid).content
            userApi.updateUser(uuid, user.copy(name = edit.name))
        }
        if (edit.isAdministrator != null || edit.isDisabled != null || edit.isHidden != null) {
            // The server replaces the whole policy, so the current one is read back and patched.
            userApi.getUserById(uuid).content.policy?.let { policy ->
                userApi.updateUserPolicy(
                    uuid,
                    policy.copy(
                        isAdministrator = edit.isAdministrator ?: policy.isAdministrator,
                        isDisabled = edit.isDisabled ?: policy.isDisabled,
                        isHidden = edit.isHidden ?: policy.isHidden,
                    ),
                )
            }
        }
        edit.password?.let { password ->
            userApi.updateUserPassword(
                uuid,
                UpdateUserPassword(
                    currentPassword = null,
                    currentPw = null,
                    newPw = password.ifEmpty { null },
                    resetPassword = password.isEmpty(),
                ),
            )
        }
        Unit
    }

    override suspend fun deleteUser(session: Session, userId: String) = call(session) {
        userApi.deleteUser(userId.toUuid())
        Unit
    }

    override suspend fun getDevices(session: Session) = call(session) {
        deviceApi.getDevices().content.items.map { it.toAdminDevice() }
    }

    override suspend fun renameDevice(session: Session, deviceId: String, name: String) = call(session) {
        val options = deviceApi.getDeviceOptions(deviceId).content
        deviceApi.updateDeviceOptions(deviceId, options.copy(customName = name))
        Unit
    }

    override suspend fun deleteDevice(session: Session, deviceId: String) = call(session) {
        deviceApi.deleteDevice(listOf(deviceId))
        Unit
    }

    override suspend fun getAdminLibraries(session: Session) = call(session) {
        libraryStructureApi.getVirtualFolders().content.map { it.toAdminLibrary() }
    }

    override suspend fun getScheduledTasks(session: Session) = call(session) {
        scheduledTaskApi.getTasks().content.map { it.toScheduledTask() }
    }

    override suspend fun startTask(session: Session, taskId: String) = call(session) {
        scheduledTaskApi.startTask(taskId)
        Unit
    }

    override suspend fun stopTask(session: Session, taskId: String) = call(session) {
        scheduledTaskApi.stopTask(taskId)
        Unit
    }

    override suspend fun getLogFiles(session: Session) = call(session) {
        systemApi.getServerLogs().content.map { it.toServerLogFile() }.sortedByDescending { it.modified }
    }

    override suspend fun getLogContent(session: Session, fileName: String) = call(session) {
        // The log endpoint returns raw text. systemApi.getLogFile declares String, which the SDK
        // would try to JSON-decode and fail; requesting ByteArray returns the body untouched.
        request(HttpMethod.GET, "/System/Logs/Log", queryParameters = mapOf("name" to fileName))
            .createContent<ByteArray>()
            .decodeToString()
    }

    override suspend fun getSystemInfo(session: Session) = call(session) {
        val info = systemApi.getSystemInfo().content
        // Storage reporting is newer than the rest; a server that cannot answer still gets its info.
        val storage = runCatching { systemApi.getSystemStorage().content }.getOrNull()
        info.toSystemInformation(storage)
    }

    private companion object {
        const val MESSAGE_HEADER = "Cobalt Wolf"
        const val MESSAGE_TIMEOUT_MS = 15_000L
        val ITEM_FIELDS = listOf(ItemFields.OVERVIEW, ItemFields.GENRES)
    }
}

private fun String.toUuid(): UUID = this.toUUID()

internal fun SessionInfoDto.toActiveSession(serverAddress: String): ActiveSession {
    val item = nowPlayingItem?.toMediaItem(serverAddress)
    val streams = nowPlayingItem?.mediaStreams.orEmpty()
    val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audio = streams.firstOrNull { it.type == MediaStreamType.AUDIO && it.index == playState?.audioStreamIndex }
        ?: streams.firstOrNull { it.type == MediaStreamType.AUDIO }
    val positionTicks = playState?.positionTicks
    val runtimeTicks = nowPlayingItem?.runTimeTicks

    return ActiveSession(
        id = id.orEmpty(),
        userName = userName.orEmpty(),
        deviceName = deviceName.orEmpty(),
        client = client,
        item = item,
        progressFraction = if (positionTicks != null && runtimeTicks != null && runtimeTicks > 0) {
            (positionTicks.toFloat() / runtimeTicks).coerceIn(0f, 1f)
        } else {
            null
        },
        isPaused = playState?.isPaused == true,
        playMethod = playState?.playMethod?.toSessionPlayMethod(),
        videoCodec = video?.codec,
        audioCodec = audio?.codec,
        transcoding = transcodingInfo?.toTranscodeDetails(),
    )
}

private fun PlayMethod.toSessionPlayMethod() = when (this) {
    PlayMethod.DIRECT_PLAY -> SessionPlayMethod.DirectPlay
    PlayMethod.DIRECT_STREAM -> SessionPlayMethod.DirectStream
    PlayMethod.TRANSCODE -> SessionPlayMethod.Transcode
}

private fun TranscodingInfo.toTranscodeDetails() = TranscodeDetails(
    container = container,
    videoCodec = videoCodec,
    audioCodec = audioCodec,
    bitrate = bitrate,
    width = width,
    height = height,
    audioChannels = audioChannels,
    isVideoDirect = isVideoDirect,
    isAudioDirect = isAudioDirect,
    reasons = transcodeReasons.orEmpty().map { reason ->
        reason.name.lowercase().replace('_', ' ')
    },
)

internal fun UserDto.toAdminUser() = AdminUser(
    id = id.toString(),
    name = name.orEmpty(),
    isAdministrator = policy?.isAdministrator == true,
    isDisabled = policy?.isDisabled == true,
    isHidden = policy?.isHidden == true,
    hasPassword = hasConfiguredPassword == true || hasPassword == true,
    lastLogin = lastLoginDate?.toString(),
    lastActivity = lastActivityDate?.toString(),
)

internal fun DeviceInfoDto.toAdminDevice() = AdminDevice(
    id = id.orEmpty(),
    name = name.orEmpty(),
    customName = customName,
    appName = appName,
    appVersion = appVersion,
    lastUserName = lastUserName,
    lastActivity = dateLastActivity?.toString(),
)

internal fun VirtualFolderInfo.toAdminLibrary() = AdminLibrary(
    id = itemId ?: name.orEmpty(),
    name = name.orEmpty(),
    collectionType = collectionType?.serialName,
    locations = locations.orEmpty(),
    refreshStatus = refreshStatus,
    refreshProgress = refreshProgress?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() },
)

internal fun TaskInfo.toScheduledTask() = ScheduledTask(
    id = id.orEmpty(),
    name = name.orEmpty(),
    category = category,
    description = description,
    state = when (state) {
        TaskState.RUNNING -> AdminTaskState.Running
        TaskState.CANCELLING -> AdminTaskState.Cancelling
        else -> AdminTaskState.Idle
    },
    progressFraction = currentProgressPercentage?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() },
    lastRun = lastExecutionResult?.endTimeUtc?.toString(),
    lastResult = lastExecutionResult?.status?.serialName,
)

internal fun LogFile.toServerLogFile() = ServerLogFile(
    name = name.orEmpty(),
    sizeBytes = size,
    modified = dateModified?.toString(),
)

internal fun SystemInfo.toSystemInformation(storage: SystemStorageDto?) = SystemInformation(
    serverName = serverName,
    version = version,
    productName = productName,
    operatingSystem = operatingSystemDisplayName,
    architecture = systemArchitecture,
    localAddress = localAddress,
    packageName = packageName,
    hasPendingRestart = hasPendingRestart,
    hasUpdateAvailable = hasUpdateAvailable,
    webSocketPort = webSocketPortNumber,
    storage = storage.toStorageVolumes(),
)

private fun SystemStorageDto?.toStorageVolumes(): List<StorageVolume> {
    if (this == null) return emptyList()
    val volumes = mutableListOf<StorageVolume>()
    fun add(label: String, folder: FolderStorageDto?) {
        folder ?: return
        volumes += StorageVolume(label, folder.path, folder.usedSpace, folder.freeSpace)
    }
    add("Program data", programDataFolder)
    add("Web", webFolder)
    add("Image cache", imageCacheFolder)
    add("Cache", cacheFolder)
    add("Logs", logFolder)
    add("Metadata", internalMetadataFolder)
    add("Transcoding temp", transcodingTempFolder)
    for (library in libraries.orEmpty()) {
        for (folder in library.folders.orEmpty()) {
            volumes += StorageVolume(library.name.orEmpty(), folder.path, folder.usedSpace, folder.freeSpace)
        }
    }
    return volumes
}
