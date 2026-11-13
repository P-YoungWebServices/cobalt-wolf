package com.pyoungws.cobaltwolf.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLibraryIdsTest {
    @Test
    fun `root is recognised and is not a library or item node`() {
        assertTrue(MediaLibraryIds.isRoot(MediaLibraryIds.ROOT))
        assertFalse(MediaLibraryIds.isLibraryNode(MediaLibraryIds.ROOT))
    }

    @Test
    fun `library nodes round-trip the library id`() {
        val node = MediaLibraryIds.library("abc123")
        assertTrue(MediaLibraryIds.isLibraryNode(node))
        assertFalse(MediaLibraryIds.isRoot(node))
        assertEquals("abc123", MediaLibraryIds.libraryId(node))
    }

    @Test
    fun `item nodes round-trip the item id`() {
        val node = MediaLibraryIds.item("deadbeef")
        assertEquals("deadbeef", MediaLibraryIds.itemId(node))
    }

    @Test
    fun `ids containing the prefix text still parse`() {
        // An item id that itself contains "item:" should not be mangled.
        assertEquals("lib:x", MediaLibraryIds.itemId(MediaLibraryIds.item("lib:x")))
    }
}
