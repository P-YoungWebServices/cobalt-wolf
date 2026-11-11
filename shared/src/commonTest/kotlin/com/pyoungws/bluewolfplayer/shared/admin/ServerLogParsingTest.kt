package com.pyoungws.bluewolfplayer.shared.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerLogParsingTest {

    private val log = """
        [2026-11-10 12:00:00.000 +00:00] [INF] [1] Main: Jellyfin version: "12.0.0"
        [2026-11-10 12:00:01.000 +00:00] [WRN] [5] Startup: slow storage
        [2026-11-10 12:00:02.000 +00:00] [ERR] [7] Task: Refresh failed
        MediaBrowser.Common.FSError: boom
            at Foo.Bar()
        [2026-11-10 12:00:03.000 +00:00] [DBG] [9] Socket: ping
        [2026-11-10 12:00:04.000 +00:00] [FTL] [10] Main: crashed

    """.trimIndent()

    @Test
    fun `lines are tagged with their severity`() {
        val lines = parseServerLog(log)

        assertEquals(
            listOf(
                LogSeverity.Info,
                LogSeverity.Warning,
                LogSeverity.Error,
                LogSeverity.Error, // stack trace lines inherit the entry's severity
                LogSeverity.Error,
                LogSeverity.Debug,
                LogSeverity.Fatal,
            ),
            lines.map { it.severity },
        )
        assertEquals(7, lines.size)
        assertTrue(lines[0].text.contains("Jellyfin version"))
    }

    @Test
    fun `blank lines are dropped`() {
        val lines = parseServerLog("[2026-11-10 12:00:00.000 +00:00] [INF] ok\n\n   \n[2026-11-10 12:00:01.000 +00:00] [INF] next")

        assertEquals(2, lines.size)
    }

    @Test
    fun `a minimum severity hides everything quieter`() {
        val lines = parseServerLog(log)

        assertEquals(4, lines.filteredBy(LogSeverity.Error, "").size)
        assertEquals(
            listOf(LogSeverity.Warning, LogSeverity.Error, LogSeverity.Error, LogSeverity.Error, LogSeverity.Fatal),
            lines.filteredBy(LogSeverity.Warning, "").map { it.severity },
        )
    }

    @Test
    fun `a text query matches case insensitively`() {
        val lines = parseServerLog(log)

        assertEquals(1, lines.filteredBy(null, "STORAGE").size)
        assertEquals(0, lines.filteredBy(null, "no such line").size)
    }

    @Test
    fun `untagged lines only pass the severity filter when none is set`() {
        val lines = parseServerLog("free text\n[2026-11-10 12:00:00.000 +00:00] [ERR] real error")

        assertEquals(2, lines.filteredBy(null, "").size)
        assertEquals(listOf("real error"), lines.filteredBy(LogSeverity.Error, "").map { it.text.substringAfterLast("] ") })
    }
}
