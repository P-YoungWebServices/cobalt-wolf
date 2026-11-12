package com.pyoungws.bluewolfplayer.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.arrs.UpcomingItem
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarMode
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarPeriod
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarUiState
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.rangeAround
import com.pyoungws.bluewolfplayer.ui.common.Artwork
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.SectionTitle
import org.koin.compose.viewmodel.koinViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import java.time.LocalDate as JavaLocalDate

/** Upcoming releases from Radarr and Sonarr, merged into one date-ordered list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    canConfigureServices: Boolean,
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val anchor by viewModel.anchor.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
                actions = {
                    if (canConfigureServices) {
                        TextButton(onClick = onSettings) { Text(stringResource(R.string.calendar_settings)) }
                    }
                    TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                CalendarUiState.Loading -> LoadingBox()
                CalendarUiState.NotConfigured -> NotConfigured(onSettings.takeIf { canConfigureServices })
                is CalendarUiState.Error -> ErrorBox(current.error, onRetry = viewModel::refresh)
                is CalendarUiState.Ready -> Column(Modifier.fillMaxSize()) {
                    ModeChips(mode) { next -> viewModel.show(next, anchor) }
                    val step = { delta: Int -> viewModel.show(mode, CalendarPeriod.step(anchor, mode, delta)) }
                    when (mode) {
                        CalendarMode.List -> CalendarList(current)
                        CalendarMode.Week -> WeekCalendar(current, anchor, step)
                        CalendarMode.Month -> MonthCalendar(current, anchor, step)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotConfigured(onSettings: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.calendar_not_configured),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onSettings != null) {
            Button(onClick = onSettings, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.calendar_settings))
            }
        }
    }
}

@Composable
private fun CalendarList(state: CalendarUiState.Ready) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (state.failedServices.isNotEmpty()) {
            item { FailedWarning(state.failedServices) }
        }
        if (state.items.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.calendar_empty),
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.items.groupBy { it.date }.forEach { (date, items) ->
            item(key = "day-$date") { SectionTitle(formatDay(date)) }
            items(items) { item -> UpcomingRow(item) }
        }
    }
}

@Composable
private fun UpcomingRow(item: UpcomingItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(item.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.width(44.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                listOfNotNull(stringResource(item.service.labelRes()), item.subtitle).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (item.hasFile) {
            Text(
                stringResource(R.string.calendar_downloaded),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

internal fun ArrService.labelRes(): Int = when (this) {
    ArrService.Radarr -> R.string.arr_service_radarr
    ArrService.Sonarr -> R.string.arr_service_sonarr
    ArrService.Bazarr -> R.string.arr_service_bazarr
    ArrService.Prowlarr -> R.string.arr_service_prowlarr
}

@Composable
private fun ModeChips(mode: CalendarMode, onSelect: (CalendarMode) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CalendarMode.entries.forEach { option ->
            FilterChip(
                selected = option == mode,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        stringResource(
                            when (option) {
                                CalendarMode.List -> R.string.calendar_view_list
                                CalendarMode.Week -> R.string.calendar_view_week
                                CalendarMode.Month -> R.string.calendar_view_month
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun PeriodHeader(label: String, onStep: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onStep(-1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = null) }
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(onClick = { onStep(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) }
    }
}

@Composable
private fun WeekCalendar(state: CalendarUiState.Ready, anchor: LocalDate, onStep: (Int) -> Unit) {
    val (start, end) = CalendarPeriod.weekRange(anchor)
    val byDate = state.items.groupBy { it.date }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { PeriodHeader(weekLabel(start, end), onStep) }
        state.failedServices.takeIf { it.isNotEmpty() }?.let { failed ->
            item { FailedWarning(failed) }
        }
        generateSequence(start) { it.plus(1, DateTimeUnit.DAY).takeIf { d -> d <= end } }.forEach { date ->
            item(key = "day-$date") { SectionTitle(formatWeekday(date)) }
            val items = byDate[date].orEmpty()
            if (items.isEmpty()) {
                item(key = "empty-$date") { EmptyDay() }
            } else {
                items(items) { item -> UpcomingRow(item) }
            }
        }
    }
}

@Composable
private fun MonthCalendar(state: CalendarUiState.Ready, anchor: LocalDate, onStep: (Int) -> Unit) {
    val days = remember(anchor) { CalendarPeriod.monthGridDays(anchor) }
    val byDate = state.items.groupBy { it.date }
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    var selected by remember(anchor) { mutableStateOf(if (today.month == anchor.month && today.year == anchor.year) today else days.first { it.month == anchor.month }) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { PeriodHeader(monthLabel(anchor), onStep) }
        state.failedServices.takeIf { it.isNotEmpty() }?.let { failed ->
            item { FailedWarning(failed) }
        }
        item {
            val firstDay = days.first().toJava()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                (0..6).forEach { offset ->
                    Text(
                        firstDay.plusDays(offset.toLong()).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        days.chunked(7).forEachIndexed { weekIndex, week ->
            item(key = "week-$weekIndex") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                    week.forEach { date -> DayCell(date, anchor, today, selected, byDate[date].orEmpty().map { it.service }, Modifier.weight(1f)) { selected = date } }
                }
            }
        }
        item(key = "selected-title") { SectionTitle(formatDay(selected)) }
        val selectedItems = byDate[selected].orEmpty()
        if (selectedItems.isEmpty()) {
            item(key = "selected-empty") { EmptyDay() }
        } else {
            items(selectedItems) { item -> UpcomingRow(item) }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    month: LocalDate,
    today: LocalDate,
    selected: LocalDate,
    services: List<ArrService>,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    val inMonth = date.month == month.month && date.year == month.year
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (date == selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
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
                Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(serviceDotColor(service)))
            }
        }
    }
}

@Composable
private fun serviceDotColor(service: ArrService) = when (service) {
    ArrService.Radarr -> MaterialTheme.colorScheme.primary
    ArrService.Sonarr -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

@Composable
private fun FailedWarning(failed: List<ArrService>) {
    val failedNames = buildList {
        for (service in failed) add(stringResource(service.labelRes()))
    }.joinToString()
    Text(
        stringResource(R.string.calendar_service_failed, failedNames),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun EmptyDay() {
    Text(
        stringResource(R.string.calendar_empty_day),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val weekDayFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)

private fun weekLabel(start: LocalDate, end: LocalDate): String =
    "${start.toJava().format(weekDayFormatter)} – ${end.toJava().format(weekDayFormatter)}"

private fun monthLabel(anchor: LocalDate): String =
    "${java.time.Month.of(anchor.monthNumber).getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())} ${anchor.year}"

private fun LocalDate.toJava(): JavaLocalDate = JavaLocalDate.of(year, monthNumber, dayOfMonth)

@Composable
private fun formatWeekday(date: LocalDate): String {
    val javaDate = date.toJava()
    val dow = javaDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    return "$dow ${javaDate.format(dayFormatter)}"
}

@Composable
private fun formatDay(date: LocalDate): String {
    val java = JavaLocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
    val today = JavaLocalDate.now()
    return when (java) {
        today -> stringResource(R.string.calendar_today)
        today.plusDays(1) -> stringResource(R.string.calendar_tomorrow)
        today.minusDays(1) -> stringResource(R.string.calendar_yesterday)
        else -> java.format(dayFormatter)
    }
}
