package com.pyoungws.cobaltwolf.ui.downloads

import org.junit.Test
import kotlin.test.assertEquals

class TorrentFormatTest {

    @Test
    fun `speeds and sizes use 1024-based units`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 MB", formatBytes(1024 * 1024))
        assertEquals("4.0 GB", formatBytes(4L * 1024 * 1024 * 1024))
        assertEquals("1.5 MB/s", formatSpeed(1536 * 1024))
    }

    @Test
    fun `eta formats compactly`() {
        assertEquals("∞", formatEta(null))
        assertEquals("45 s", formatEta(45))
        assertEquals("12 m", formatEta(720))
        assertEquals("2 h 10 m", formatEta(7800))
        assertEquals("3 d 4 h", formatEta(273600))
    }
}
