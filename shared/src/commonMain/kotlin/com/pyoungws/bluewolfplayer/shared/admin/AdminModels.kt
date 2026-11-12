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
enum class NavDestination { Search, Requests, PlaybackSettings, Dashboard, Sessions, RequestQueue, Users, Devices, Libraries, Tasks, Logs, SystemInfo, MediaManager, Downloads, Bazarr, Prowlarr, Services }

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
    NavDestination.SystemInfo,
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
