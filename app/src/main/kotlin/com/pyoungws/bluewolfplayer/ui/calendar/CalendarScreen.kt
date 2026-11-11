package com.pyoungws.bluewolfplayer.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.arrs.UpcomingItem
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarUiState
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarViewModel
import com.pyoungws.bluewolfplayer.ui.common.Artwork
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.SectionTitle
import org.koin.compose.viewmodel.koinViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.datetime.LocalDate
import java.time.LocalDate as JavaLocalDate

/** Upcoming releases from Radarr and Sonarr, merged into one date-ordered list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
                actions = {
                    TextButton(onClick = onSettings) { Text(stringResource(R.string.calendar_settings)) }
                    TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                CalendarUiState.Loading -> LoadingBox()
                CalendarUiState.NotConfigured -> NotConfigured(onSettings)
                is CalendarUiState.Error -> ErrorBox(current.error, onRetry = viewModel::refresh)
                is CalendarUiState.Ready -> CalendarList(current)
            }
        }
    }
}

@Composable
private fun NotConfigured(onSettings: () -> Unit) {
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
        Button(onClick = onSettings, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.calendar_settings))
        }
    }
}

@Composable
private fun CalendarList(state: CalendarUiState.Ready) {
    val failedNames = buildList {
        for (service in state.failedServices) add(stringResource(service.labelRes()))
    }.joinToString()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
        if (failedNames.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.calendar_service_failed, failedNames),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
}

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

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
