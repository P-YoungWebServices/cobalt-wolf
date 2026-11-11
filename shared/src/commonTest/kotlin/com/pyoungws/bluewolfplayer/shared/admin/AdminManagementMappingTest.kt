package com.pyoungws.bluewolfplayer.shared.admin

import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.ClientCapabilitiesDto
import org.jellyfin.sdk.model.api.CollectionTypeOptions
import org.jellyfin.sdk.model.api.DeviceInfoDto
import org.jellyfin.sdk.model.api.FolderStorageDto
import org.jellyfin.sdk.model.api.LibraryStorageDto
import org.jellyfin.sdk.model.api.LogFile
import org.jellyfin.sdk.model.api.SyncPlayUserAccessType
import org.jellyfin.sdk.model.api.SystemInfo
import org.jellyfin.sdk.model.api.SystemStorageDto
import org.jellyfin.sdk.model.api.TaskCompletionStatus
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskResult
import org.jellyfin.sdk.model.api.TaskState
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.UserPolicy
import org.jellyfin.sdk.model.api.VirtualFolderInfo
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The management screens show what the SDK reports; keep the mappings honest. */
class AdminManagementMappingTest {

    private companion object {
        val DATE: LocalDateTime = LocalDateTime.parse("2026-11-10T12:00:00")
        val ENDED: LocalDateTime = LocalDateTime.parse("2026-11-10T12:05:00")
        val USER_ID: UUID = UUID.fromString("07b1927a-431c-4a13-bf3d-6bf581842d63")

        fun policy(admin: Boolean = false, disabled: Boolean = false, hidden: Boolean = false) = UserPolicy(
            isAdministrator = admin,
            isHidden = hidden,
            isDisabled = disabled,
            enableUserPreferenceAccess = true,
            enableRemoteControlOfOtherUsers = false,
            enableSharedDeviceControl = true,
            enableRemoteAccess = true,
            enableLiveTvManagement = false,
            enableLiveTvAccess = false,
            enableMediaPlayback = true,
            enableAudioPlaybackTranscoding = true,
            enableVideoPlaybackTranscoding = true,
            enablePlaybackRemuxing = true,
            forceRemoteSourceTranscoding = false,
            enableContentDeletion = false,
            enableContentDownloading = true,
            enableSyncTranscoding = true,
            enableMediaConversion = true,
            enableAllDevices = true,
            enableAllChannels = true,
            enableAllFolders = true,
            invalidLoginAttemptCount = 0,
            loginAttemptsBeforeLockout = 3,
            maxActiveSessions = 0,
            enablePublicSharing = false,
            remoteClientBitrateLimit = 0,
            authenticationProviderId = "default",
            passwordResetProviderId = "default",
            syncPlayAccess = SyncPlayUserAccessType.CREATE_AND_JOIN_GROUPS,
        )

        val capabilities = ClientCapabilitiesDto(
            playableMediaTypes = emptyList(),
            supportedCommands = emptyList(),
            supportsMediaControl = false,
            supportsPersistentIdentifier = true,
            deviceProfile = null,
            appStoreUrl = null,
            iconUrl = null,
        )

        fun userDto(policy: UserPolicy? = null) = UserDto(
            name = "alice",
            serverId = "server-1",
            serverName = "wolf",
            id = USER_ID,
            primaryImageTag = null,
            hasPassword = true,
            hasConfiguredPassword = true,
            hasConfiguredEasyPassword = false,
            enableAutoLogin = false,
            lastLoginDate = DATE,
            lastActivityDate = ENDED,
            configuration = null,
            policy = policy,
            primaryImageAspectRatio = null,
        )
    }

    @Test
    fun `a user maps its policy flags and activity timestamps`() {
        val user = userDto(policy(admin = true, hidden = true)).toAdminUser()

        assertEquals(USER_ID.toString(), user.id)
        assertEquals("alice", user.name)
        assertTrue(user.isAdministrator)
        assertTrue(user.isHidden)
        assertFalse(user.isDisabled)
        assertTrue(user.hasPassword)
        assertEquals("2026-11-10T12:00", user.lastLogin)
        assertEquals("2026-11-10T12:05", user.lastActivity)
    }

    @Test
    fun `a user without a policy has no privileges`() {
        val user = userDto().toAdminUser()

        assertFalse(user.isAdministrator)
        assertFalse(user.isDisabled)
        assertFalse(user.isHidden)
    }

    @Test
    fun `a device maps name, app and last activity`() {
        val device = DeviceInfoDto(
            name = "Shield TV",
            customName = "Living room",
            accessToken = null,
            id = "device-1",
            lastUserName = "alice",
            appName = "Jellyfin Android",
            appVersion = "2.6.0",
            lastUserId = USER_ID,
            dateLastActivity = DATE,
            capabilities = capabilities,
            iconUrl = null,
        ).toAdminDevice()

        assertEquals("device-1", device.id)
        assertEquals("Shield TV", device.name)
        assertEquals("Living room", device.customName)
        assertEquals("Living room", device.displayName)
        assertEquals("Jellyfin Android", device.appName)
        assertEquals("alice", device.lastUserName)
        assertEquals("2026-11-10T12:00", device.lastActivity)
    }

    @Test
    fun `a device without a custom name displays its own name`() {
        val device = DeviceInfoDto(
            name = "Pixel",
            customName = null,
            accessToken = null,
            id = "device-2",
            lastUserName = null,
            appName = null,
            appVersion = null,
            lastUserId = null,
            dateLastActivity = null,
            capabilities = capabilities,
            iconUrl = null,
        ).toAdminDevice()

        assertEquals("Pixel", device.displayName)
    }

    @Test
    fun `a virtual folder maps type, locations and scan state`() {
        val library = VirtualFolderInfo(
            name = "Movies",
            locations = listOf("/media/movies", "/media/more"),
            collectionType = CollectionTypeOptions.MOVIES,
            libraryOptions = null,
            itemId = "item-movies",
            primaryImageItemId = null,
            refreshProgress = 62.5,
            refreshStatus = "Scanning",
        ).toAdminLibrary()

        assertEquals("item-movies", library.id)
        assertEquals("movies", library.collectionType)
        assertEquals(listOf("/media/movies", "/media/more"), library.locations)
        assertEquals("Scanning", library.refreshStatus)
        assertEquals(0.625f, library.refreshProgress)
    }

    @Test
    fun `a running task maps its state and progress`() {
        val task = TaskInfo(
            name = "Scan media library",
            state = TaskState.RUNNING,
            currentProgressPercentage = 41.0,
            id = "task-1",
            lastExecutionResult = null,
            triggers = emptyList(),
            description = "Scans the library",
            category = "Library",
            isHidden = false,
            key = "RefreshLibrary",
        ).toScheduledTask()

        assertEquals(AdminTaskState.Running, task.state)
        assertEquals(0.41f, task.progressFraction)
        assertNull(task.lastRun)
        assertNull(task.lastResult)
    }

    @Test
    fun `an idle task maps the outcome of its last run`() {
        val task = TaskInfo(
            name = "Clean images",
            state = TaskState.IDLE,
            currentProgressPercentage = null,
            id = "task-2",
            lastExecutionResult = TaskResult(
                startTimeUtc = DATE,
                endTimeUtc = ENDED,
                status = TaskCompletionStatus.FAILED,
                name = "Clean images",
                key = "CleanImages",
                id = "task-2",
                errorMessage = "boom",
                longErrorMessage = null,
            ),
            triggers = emptyList(),
            description = null,
            category = null,
            isHidden = false,
            key = "CleanImages",
        ).toScheduledTask()

        assertEquals(AdminTaskState.Idle, task.state)
        assertNull(task.progressFraction)
        assertEquals("2026-11-10T12:05", task.lastRun)
        assertEquals("Failed", task.lastResult)
    }

    @Test
    fun `a cancelling task reports that it is stopping`() {
        val task = TaskInfo(
            name = "t",
            state = TaskState.CANCELLING,
            currentProgressPercentage = null,
            id = "task-3",
            lastExecutionResult = null,
            triggers = emptyList(),
            description = null,
            category = null,
            isHidden = false,
            key = "k",
        ).toScheduledTask()

        assertEquals(AdminTaskState.Cancelling, task.state)
    }

    @Test
    fun `a log file maps name, size and modification time`() {
        val file = LogFile(
            dateCreated = DATE,
            dateModified = ENDED,
            size = 12_345,
            name = "log_20261110.log",
        ).toServerLogFile()

        assertEquals("log_20261110.log", file.name)
        assertEquals(12_345, file.sizeBytes)
        assertEquals("2026-11-10T12:05", file.modified)
    }

    @Test
    fun `system info maps version, update status and storage`() {
        val info = systemInfo(hasUpdateAvailable = true).toSystemInformation(
            SystemStorageDto(
                programDataFolder = folder("/config", used = 2_000, free = 8_000),
                webFolder = folder("/web", used = 100, free = 9_900),
                imageCacheFolder = folder("/cache/images", used = 50, free = 9_950),
                cacheFolder = folder("/cache", used = 1_000, free = 9_000),
                logFolder = folder("/logs", used = 20, free = 9_980),
                internalMetadataFolder = folder("/metadata", used = 30, free = 9_970),
                transcodingTempFolder = folder("/transcodes", used = 40, free = 9_960),
                libraries = listOf(
                    LibraryStorageDto(
                        id = USER_ID,
                        name = "Movies",
                        folders = listOf(folder("/media/movies", used = 500, free = 500)),
                    ),
                ),
            ),
        )

        assertEquals("wolf", info.serverName)
        assertEquals("12.0.0", info.version)
        assertTrue(info.hasUpdateAvailable)
        assertFalse(info.hasPendingRestart)
        assertEquals(8096, info.webSocketPort)
        assertEquals(
            listOf(
                StorageVolume("Program data", "/config", 2_000, 8_000),
                StorageVolume("Web", "/web", 100, 9_900),
                StorageVolume("Image cache", "/cache/images", 50, 9_950),
                StorageVolume("Cache", "/cache", 1_000, 9_000),
                StorageVolume("Logs", "/logs", 20, 9_980),
                StorageVolume("Metadata", "/metadata", 30, 9_970),
                StorageVolume("Transcoding temp", "/transcodes", 40, 9_960),
                StorageVolume("Movies", "/media/movies", 500, 500),
            ),
            info.storage,
        )
    }

    @Test
    fun `system info without storage still maps`() {
        val info = systemInfo().toSystemInformation(null)

        assertEquals(emptyList(), info.storage)
    }

    private fun folder(path: String, used: Long, free: Long) =
        FolderStorageDto(path = path, freeSpace = free, usedSpace = used, storageType = null, deviceId = null)

    private fun systemInfo(hasUpdateAvailable: Boolean = false) = SystemInfo(
        localAddress = "http://192.168.1.10:8096",
        serverName = "wolf",
        version = "12.0.0",
        productName = "Jellyfin Server",
        operatingSystem = "Linux",
        id = "server-1",
        startupWizardCompleted = true,
        operatingSystemDisplayName = "Debian",
        packageName = "docker",
        hasPendingRestart = false,
        isShuttingDown = false,
        supportsLibraryMonitor = true,
        webSocketPortNumber = 8096,
        completedInstallations = emptyList(),
        canSelfRestart = true,
        canLaunchWebBrowser = false,
        programDataPath = "/config",
        webPath = "/web",
        itemsByNamePath = "/metadata",
        cachePath = "/cache",
        logPath = "/logs",
        internalMetadataPath = "/metadata",
        transcodingTempPath = "/cache/transcodes",
        castReceiverApplications = emptyList(),
        hasUpdateAvailable = hasUpdateAvailable,
        encoderLocation = "System",
        systemArchitecture = "X64",
    )
}
