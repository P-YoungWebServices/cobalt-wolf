package com.pyoungws.bluewolfplayer.shared.admin

import com.pyoungws.bluewolfplayer.shared.auth.ADMIN
import com.pyoungws.bluewolfplayer.shared.auth.VIEWER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The admin dashboard is only reachable through [NavDestination.Dashboard], so these
 * destinations are the single source of truth for what the UI may show and navigate to.
 */
class AdminAccessTest {

    @Test
    fun `non-admin users get no admin navigation entry`() {
        assertEquals(
            listOf(NavDestination.Search, NavDestination.Requests, NavDestination.PlaybackSettings),
            VIEWER.navDestinations(),
        )
        assertFalse(NavDestination.Dashboard in VIEWER.navDestinations())
        assertFalse(VIEWER.canNavigateTo(NavDestination.Dashboard))
    }

    @Test
    fun `administrators get the dashboard navigation entry`() {
        assertTrue(NavDestination.Dashboard in ADMIN.navDestinations())
        assertTrue(ADMIN.canNavigateTo(NavDestination.Dashboard))
    }

    @Test
    fun `non-admin users keep all viewer entries`() {
        assertTrue(VIEWER.canNavigateTo(NavDestination.Search))
        assertTrue(VIEWER.canNavigateTo(NavDestination.Requests))
        assertTrue(VIEWER.canNavigateTo(NavDestination.PlaybackSettings))
    }
}
