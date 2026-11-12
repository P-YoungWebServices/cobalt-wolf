package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarUiState
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarViewModel
import com.pyoungws.bluewolfplayer.ui.calendar.labelRes
import org.koin.compose.viewmodel.koinViewModel

/** Upcoming Radarr/Sonarr releases on TV; admins get a "Services" button that swaps the list for the settings form. */
@Composable
fun TvCalendarScreen(
    viewModel: CalendarViewModel = koinViewModel(),
    canConfigureServices: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val failedNames = buildList {
        for (service in (state as? CalendarUiState.Ready)?.failedServices.orEmpty()) add(stringResource(service.labelRes()))
    }.joinToString()

    if (showSettings) {
        TvServiceSettings(onDone = { showSettings = false })
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TvContentPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title") { TvSectionTitle(stringResource(R.string.calendar_title)) }
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                if (current.items.isEmpty()) {
                    item(key = "none") {
                        Text(
                            stringResource(R.string.calendar_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(current.items) { item ->
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
            }
        }
    }
}
