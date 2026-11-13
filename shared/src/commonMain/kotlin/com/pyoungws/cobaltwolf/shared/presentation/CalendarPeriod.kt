package com.pyoungws.cobaltwolf.shared.presentation

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Date math for the calendar's List / Week / Month views. Weeks run Monday–Sunday. */
object CalendarPeriod {

    /** The Monday..Sunday of the week containing [anchor]. */
    fun weekRange(anchor: LocalDate): Pair<LocalDate, LocalDate> {
        val monday = anchor.minus(anchor.dayOfWeek.ordinal, DateTimeUnit.DAY)
        return monday to monday.plus(6, DateTimeUnit.DAY)
    }

    /** Every date shown on the month grid containing [anchor], including the leading and
     * trailing days borrowed from the neighbouring months. Always a multiple of 7. */
    fun monthGridDays(anchor: LocalDate): List<LocalDate> {
        val first = LocalDate(anchor.year, anchor.month, 1)
        val nextMonth = first.plus(1, DateTimeUnit.MONTH)
        val last = nextMonth.minus(1, DateTimeUnit.DAY)
        val start = first.minus(first.dayOfWeek.ordinal, DateTimeUnit.DAY)
        val end = last.plus(6 - last.dayOfWeek.ordinal, DateTimeUnit.DAY)
        return generateSequence(start) { it.plus(1, DateTimeUnit.DAY).takeIf { d -> d <= end } }.toList()
    }

    /** Steps [anchor] one unit of [mode] backwards ([amount] -1) or forwards (+1). */
    fun step(anchor: LocalDate, mode: CalendarMode, amount: Int): LocalDate = when (mode) {
        CalendarMode.List -> anchor
        CalendarMode.Week -> anchor.plus(7 * amount, DateTimeUnit.DAY)
        CalendarMode.Month -> anchor.plus(amount, DateTimeUnit.MONTH)
    }
}

enum class CalendarMode { List, Week, Month }

/** The fetch range for [mode] around [anchor], or null for the repository's default window. */
fun CalendarMode.rangeAround(anchor: LocalDate): Pair<LocalDate, LocalDate>? = when (this) {
    CalendarMode.List -> null
    CalendarMode.Week -> CalendarPeriod.weekRange(anchor)
    CalendarMode.Month -> {
        val days = CalendarPeriod.monthGridDays(anchor)
        days.first() to days.last()
    }
}
