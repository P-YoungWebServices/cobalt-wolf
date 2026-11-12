package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.arrs.UpcomingItem
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarMode
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarPeriod
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarUiState
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarViewModel
import com.pyoungws.bluewolfplayer.ui.calendar.labelRes
import com.pyoungws.bluewolfplayer.ui.common.tvListScroll
import org.koin.compose.viewmodel.koinViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import java.time.LocalDate as JavaLocalDate

/** Upcoming Radarr/Sonarr releases on TV; admins get a "Services" button that swaps the list for the settings form. */
@Composable
fun TvCalendarScreen(
    viewModel: CalendarViewModel = koinViewModel(),
    canConfigureServices: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val anchor by viewModel.anchor.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val failedNames = buildList {
        for (service in (state as? CalendarUiState.Ready)?.failedServices.orEmpty()) add(stringResource(service.labelRes()))
    }.joinToString()

    if (showSettings) {
        TvServiceSettings(onDone = { showSettings = false })
        return
    }

    var selected by remember(anchor) { mutableStateOf<LocalDate?>(null) }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().tvListScroll(listState),
        contentPadding = PaddingValues(horizontal = TvContentPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title") { TvSectionTitle(stringResource(R.string.calendar_title)) }
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalendarMode.entries.forEach { option ->
                    val label = stringResource(
                        when (option) {
                            CalendarMode.List -> R.string.calendar_view_list
                            CalendarMode.Week -> R.string.calendar_view_week
                            CalendarMode.Month -> R.string.calendar_view_month
                        },
                    )
                    if (option == mode) {
                        Button(onClick = {}) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { viewModel.show(option, anchor) }) { Text(label) }
                    }
                }
                if (canConfigureServices) {
                    OutlinedButton(onClick = { showSettings = true }) { Text(stringResource(R.string.calendar_settings)) }
                }
                OutlinedButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) }
            }
        }
        when (val current = state) {
            CalendarUiState.Loading -> item(key = "loading") { TvLoadingBox() }
            CalendarUiState.NotConfigured -> item(key = "empty") {
                Text(
                    stringResource(R.string.calendar_not_configured),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is CalendarUiState.Error -> item(key = "error") { TvErrorBox(current.error, onRetry = viewModel::refresh) }
            is CalendarUiState.Ready -> {
                if (failedNames.isNotEmpty()) {
                    item(key = "failed") {
                        Text(
                            stringResource(R.string.calendar_service_failed, failedNames),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                when (mode) {
                    CalendarMode.List -> tvCalendarListItems(current)
                    CalendarMode.Week -> tvCalendarWeekItems(current, anchor, viewModel)
                    CalendarMode.Month -> tvCalendarMonthItems(current, anchor, viewModel, selected) { selected = it }
                }
            }
        }
    }
}

private fun LazyListScope.tvCalendarListItems(current: CalendarUiState.Ready) {
    if (current.items.isEmpty()) {
        item(key = "none") {
            Text(
                stringResource(R.string.calendar_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(current.items) { item -> TvCalendarItem(item) }
}

private fun LazyListScope.tvCalendarWeekItems(current: CalendarUiState.Ready, anchor: LocalDate, viewModel: CalendarViewModel) {
    val (start, end) = CalendarPeriod.weekRange(anchor)
    val byDate = current.items.groupBy { it.date }
    item(key = "week-header") { TvPeriodHeader(weekLabel(start, end), viewModel, anchor, CalendarMode.Week) }
    generateSequence(start) { it.plus(1, DateTimeUnit.DAY).takeIf { d -> d <= end } }.forEach { date ->
        item(key = "day-$date") {
            Text(tvFormatWeekday(date), style = MaterialTheme.typography.titleMedium)
        }
        val dayItems = byDate[date].orEmpty()
        if (dayItems.isEmpty()) {
            item(key = "empty-$date") {
                Text(
                    stringResource(R.string.calendar_empty_day),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(dayItems) { item -> TvCalendarItem(item) }
        }
    }
}

private fun LazyListScope.tvCalendarMonthItems(
    current: CalendarUiState.Ready,
    anchor: LocalDate,
    viewModel: CalendarViewModel,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val days = CalendarPeriod.monthGridDays(anchor)
    val byDate = current.items.groupBy { it.date }
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val selectedDay = selected ?: if (today.month == anchor.month && today.year == anchor.year) today else days.first { it.month == anchor.month }
    item(key = "month-header") { TvPeriodHeader(monthLabel(anchor), viewModel, anchor, CalendarMode.Month) }
    item(key = "dow") {
        val firstDay = days.first().toJava()
        Row(Modifier.fillMaxWidth()) {
            (0..6).forEach { offset ->
                Text(
                    firstDay.plusDays(offset.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    days.chunked(7).forEachIndexed { index, week ->
        item(key = "week-$index") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { date ->
                    TvDayCell(
                        date = date,
                        month = anchor,
                        today = today,
                        selected = selectedDay,
                        services = byDate[date].orEmpty().map { it.service },
                        modifier = Modifier.weight(1f),
                        onSelect = { onSelect(date) },
                    )
                }
            }
        }
    }
    item(key = "selected-title") { Text(tvFormatDay(selectedDay), style = MaterialTheme.typography.titleMedium) }
    val selectedItems = byDate[selectedDay].orEmpty()
    if (selectedItems.isEmpty()) {
        item(key = "selected-empty") {
            Text(
                stringResource(R.string.calendar_empty_day),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(selectedItems) { item -> TvCalendarItem(item) }
    }
}

@Composable
private fun TvPeriodHeader(label: String, viewModel: CalendarViewModel, anchor: LocalDate, mode: CalendarMode) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = { viewModel.show(mode, CalendarPeriod.step(anchor, mode, -1)) }) { Text("‹") }
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        OutlinedButton(onClick = { viewModel.show(mode, CalendarPeriod.step(anchor, mode, 1)) }) { Text("›") }
    }
}

@Composable
private fun TvDayCell(
    date: LocalDate,
    month: LocalDate,
    today: LocalDate,
    selected: LocalDate,
    services: List<ArrService>,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    val inMonth = date.month == month.month && date.year == month.year
    Surface(
        onClick = onSelect,
        modifier = modifier,
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (date == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            Modifier.padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    date == today -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (date == today) FontWeight.Bold else null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp).height(4.dp)) {
                services.take(3).forEach { service ->
                    Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(tvServiceDotColor(service)))
                }
            }
        }
    }
}

@Composable
private fun tvServiceDotColor(service: ArrService) = when (service) {
    ArrService.Radarr -> MaterialTheme.colorScheme.primary
    ArrService.Sonarr -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

@Composable
private fun TvCalendarItem(item: UpcomingItem) {
    Column {
        Text(item.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            listOfNotNull(
                item.date.toString(),
                stringResource(item.service.labelRes()),
                item.subtitle,
                stringResource(R.string.calendar_downloaded).takeIf { item.hasFile },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val tvDayFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val tvShortFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)

private fun tvFormatWeekday(date: LocalDate): String {
    val java = date.toJava()
    val dow = java.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$dow ${java.format(tvDayFormatter)}"
}

@Composable
private fun tvFormatDay(date: LocalDate): String {
    val java = date.toJava()
    val today = JavaLocalDate.now()
    return when (java) {
        today -> stringResource(R.string.calendar_today)
        today.plusDays(1) -> stringResource(R.string.calendar_tomorrow)
        today.minusDays(1) -> stringResource(R.string.calendar_yesterday)
        else -> java.format(tvDayFormatter)
    }
}

private fun weekLabel(start: LocalDate, end: LocalDate): String =
    "${start.toJava().format(tvShortFormatter)} – ${end.toJava().format(tvShortFormatter)}"

private fun monthLabel(anchor: LocalDate): String =
    "${java.time.Month.of(anchor.monthNumber).getDisplayName(TextStyle.FULL, Locale.getDefault())} ${anchor.year}"

private fun LocalDate.toJava(): JavaLocalDate = JavaLocalDate.of(year, monthNumber, dayOfMonth)
