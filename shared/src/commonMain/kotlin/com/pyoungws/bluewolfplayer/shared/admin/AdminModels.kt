package com.pyoungws.bluewolfplayer.shared.admin

import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.UserProfile

/** How the server is feeding a session its media. */
enum class SessionPlayMethod { DirectPlay, DirectStream, Transcode }

/** What the server's transcoder is producing for a session, when it is transcoding. */
data class TranscodeDetails(
    val container: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    val bitrate: Int?,
    val width: Int?,
    val height: Int?,
    val audioChannels: Int?,
    val isVideoDirect: Boolean,
    val isAudioDirect: Boolean,
    /** Human-readable transcode reasons (e.g. "video codec not supported"). */
    val reasons: List<String>,
)

/** One active session on the server, as shown on the admin dashboard. */
data class ActiveSession(
    val id: String,
    val userName: String,
    val deviceName: String,
    /** The app the session belongs to (e.g. "Jellyfin Web", "Cobalt Wolf"). */
    val client: String?,
    /** What is playing, or null when the session is idle. */
    val item: MediaItem?,
    /** Playback position in 0..1, or null when it cannot be determined. */
    val progressFraction: Float?,
    val isPaused: Boolean,
    val playMethod: SessionPlayMethod?,
    /** Source codecs of the now-playing item (not what the transcoder outputs). */
    val videoCodec: String?,
    val audioCodec: String?,
    val transcoding: TranscodeDetails?,
)

/** Active sessions plus the latest media, fetched together for the dashboard. */
data class AdminDashboard(
    val sessions: List<ActiveSession>,
    val recentlyAdded: List<MediaItem>,
)

/**
 * Entries in the signed-in top navigation. [Dashboard] is for everyone; its content varies
 * by privilege. [Sessions] and the management screens behind the dashboard exist for
 * administrators only.
 */
enum class NavDestination { Search, Requests, PlaybackSettings, Dashboard, Sessions, RequestQueue, Users, Devices, Libraries, Tasks, Logs, SystemInfo, ActivityLog, ApiKeys, Plugins, ServerSettings, MediaManager, Downloads, Bazarr, Prowlarr, Services }

/** The admin management screens reachable from the dashboard. */
val MANAGEMENT_DESTINATIONS = listOf(
    NavDestination.RequestQueue,
    NavDestination.MediaManager,
    NavDestination.Downloads,
    NavDestination.Bazarr,
    NavDestination.Prowlarr,
    NavDestination.Users,
    NavDestination.Devices,
    NavDestination.Libraries,
    NavDestination.Tasks,
    NavDestination.Logs,
    NavDestination.ActivityLog,
    NavDestination.ApiKeys,
    NavDestination.SystemInfo,
    NavDestination.Plugins,
    NavDestination.ServerSettings,
    NavDestination.Services,
)

/** What this user may see and reach. Admin destinations are hidden for everyone else. */
fun UserProfile.navDestinations(): List<NavDestination> = buildList {
    add(NavDestination.Search)
    add(NavDestination.Requests)
    add(NavDestination.PlaybackSettings)
    add(NavDestination.Dashboard)
    if (isAdministrator) {
        add(NavDestination.Sessions)
        addAll(MANAGEMENT_DESTINATIONS)
    }
}

fun UserProfile.canNavigateTo(destination: NavDestination): Boolean = destination in navDestinations()

/** One server activity-log entry. */
data class ActivityEntry(
    val id: Long,
    val name: String,
    val type: String?,
    val overview: String?,
    val severity: String?,
    val userId: String?,
    val date: String?,
)

/** An installed server plugin as reported by the dashboard plugin list. */
data class AdminPlugin(
    val id: String,
    val name: String,
    val version: String,
    val status: String?,
    val description: String?,
    val canUninstall: Boolean,
)

/** The editable slice of the server configuration shown on the settings page. */
data class ServerSettings(
    val serverName: String,
)

/** A server-registered API key. */
data class ServerApiKey(
    val id: Long,
    val appName: String?,
    val accessToken: String,
    val deviceId: String?,
)
