package com.pyoungws.bluewolfplayer.shared.admin

import com.pyoungws.bluewolfplayer.shared.auth.ADMIN
import com.pyoungws.bluewolfplayer.shared.auth.VIEWER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Admin screens are only reachable through the admin destinations, so these
 * destinations are the single source of truth for what the UI may show and navigate to.
 */
class AdminAccessTest {

    private val viewerDestinations = listOf(NavDestination.Search, NavDestination.Requests, NavDestination.PlaybackSettings)
    private val adminDestinations = listOf(NavDestination.Dashboard) + MANAGEMENT_DESTINATIONS

    @Test
    fun `non-admin users get no admin navigation entry`() {
        assertEquals(viewerDestinations, VIEWER.navDestinations())
        adminDestinations.forEach { destination ->
            assertFalse(destination in VIEWER.navDestinations(), "$destination must be hidden for viewers")
            assertFalse(VIEWER.canNavigateTo(destination), "$destination must be unreachable for viewers")
        }
    }

    @Test
    fun `administrators get the dashboard and every management destination`() {
        adminDestinations.forEach { destination ->
            assertTrue(destination in ADMIN.navDestinations(), "$destination must be listed for admins")
            assertTrue(ADMIN.canNavigateTo(destination), "$destination must be reachable for admins")
        }
        assertEquals(viewerDestinations + adminDestinations, ADMIN.navDestinations())
    }

    @Test
    fun `the media manager is hidden from viewers and reachable by admins`() {
        assertFalse(VIEWER.canNavigateTo(NavDestination.MediaManager))
        assertFalse(NavDestination.MediaManager in VIEWER.navDestinations())
        assertTrue(ADMIN.canNavigateTo(NavDestination.MediaManager))
        assertTrue(NavDestination.MediaManager in ADMIN.navDestinations())
    }

    @Test
    fun `non-admin users keep all viewer entries`() {
        assertTrue(VIEWER.canNavigateTo(NavDestination.Search))
        assertTrue(VIEWER.canNavigateTo(NavDestination.Requests))
        assertTrue(VIEWER.canNavigateTo(NavDestination.PlaybackSettings))
    }
}
