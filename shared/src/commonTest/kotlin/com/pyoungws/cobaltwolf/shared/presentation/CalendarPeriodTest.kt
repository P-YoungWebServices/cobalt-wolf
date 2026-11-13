package com.pyoungws.cobaltwolf.shared.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class CalendarPeriodTest {

    // 2026-11-12 is a Thursday; its week is Mon 9th - Sun 15th.
    @Test
    fun `a week range covers Monday through Sunday around the anchor`() {
        assertEquals(
            LocalDate(2026, 11, 9) to LocalDate(2026, 11, 15),
            CalendarPeriod.weekRange(LocalDate(2026, 11, 12)),
        )
    }

    @Test
    fun `a Monday anchor still gives its own week`() {
        assertEquals(
            LocalDate(2026, 11, 9) to LocalDate(2026, 11, 15),
            CalendarPeriod.weekRange(LocalDate(2026, 11, 9)),
        )
    }

    // November 2026 runs Sunday 1st - Monday 30th, so the grid borrows 6 days from October
    // and 6 from December.
    @Test
    fun `the month grid starts on Monday and covers whole weeks`() {
        val days = CalendarPeriod.monthGridDays(LocalDate(2026, 11, 12))
        assertEquals(LocalDate(2026, 10, 26), days.first())
        assertEquals(LocalDate(2026, 12, 6), days.last())
        assertEquals(42, days.size)
    }

    @Test
    fun `a month starting on Monday needs no borrowed days at the front`() {
        // February 2027: Mon 1st - Sun 28th is exactly four weeks.
        val days = CalendarPeriod.monthGridDays(LocalDate(2027, 2, 10))
        assertEquals(LocalDate(2027, 2, 1), days.first())
        assertEquals(LocalDate(2027, 2, 28), days.last())
        assertEquals(28, days.size)
    }

    @Test
    fun `stepping moves by weeks or months but not in list mode`() {
        val anchor = LocalDate(2026, 11, 12)
        assertEquals(LocalDate(2026, 11, 19), CalendarPeriod.step(anchor, CalendarMode.Week, 1))
        assertEquals(LocalDate(2026, 11, 5), CalendarPeriod.step(anchor, CalendarMode.Week, -1))
        assertEquals(LocalDate(2026, 12, 12), CalendarPeriod.step(anchor, CalendarMode.Month, 1))
        assertEquals(LocalDate(2026, 10, 12), CalendarPeriod.step(anchor, CalendarMode.Month, -1))
        assertEquals(anchor, CalendarPeriod.step(anchor, CalendarMode.List, 1))
    }

    @Test
    fun `stepping a month across a year boundary keeps the day-of-month`() {
        assertEquals(
            LocalDate(2027, 1, 31),
            CalendarPeriod.step(LocalDate(2026, 12, 31), CalendarMode.Month, 1),
        )
    }

    @Test
    fun `the fetch range matches the visible period`() {
        assertEquals(null, CalendarMode.List.rangeAround(LocalDate(2026, 11, 12)))
        assertEquals(
            LocalDate(2026, 11, 9) to LocalDate(2026, 11, 15),
            CalendarMode.Week.rangeAround(LocalDate(2026, 11, 12)),
        )
        assertEquals(
            LocalDate(2026, 10, 26) to LocalDate(2026, 12, 6),
            CalendarMode.Month.rangeAround(LocalDate(2026, 11, 12)),
        )
    }
}
