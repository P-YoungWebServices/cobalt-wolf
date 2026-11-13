package com.pyoungws.bluewolfplayer.ui.livetv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.isTelevision
import com.pyoungws.bluewolfplayer.shared.model.LiveTimer
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.presentation.LiveTvUiState
import com.pyoungws.bluewolfplayer.shared.presentation.LiveTvViewModel
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.PosterCard
import org.koin.compose.viewmodel.koinViewModel
import java.time.format.DateTimeFormatter

private enum class LiveTvTab(val labelRes: Int) {
    Channels(R.string.livetv_channels), Guide(R.string.livetv_guide), Recordings(R.string.livetv_recordings),
    Scheduled(R.string.livetv_scheduled), Series(R.string.livetv_series),
}

/** Live TV hub: channel lineup, program guide, recordings and recording schedules. */
@Composable
fun LiveTvScreen(onBack: () -> Unit, onOpenItem: (String) -> Unit) {
    val viewModel: LiveTvViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(LiveTvTab.Channels) }
    // On TV the pushed screen shares the top bar — grab focus onto the tab row
    // so the D-pad reaches it instead of staying on the nav buttons.
    val firstTab = remember { FocusRequester() }
    if (LocalContext.current.isTelevision()) {
        LaunchedEffect(Unit) { runCatching { firstTab.requestFocus() } }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LiveTvTab.entries.forEachIndexed { index, entry ->
                FilterChip(
                    modifier = if (index == 0) Modifier.focusRequester(firstTab) else Modifier,
                    selected = tab == entry,
                    onClick = {
                        tab = entry
                        // Guide/recordings/timers go stale while browsing; refresh on each switch.
                        when (entry) {
                            LiveTvTab.Guide -> viewModel.refreshGuide()
                            LiveTvTab.Recordings, LiveTvTab.Scheduled, LiveTvTab.Series -> viewModel.refreshBookkeeping()
                            else -> Unit
                        }
                    },
                    label = { Text(stringResource(entry.labelRes)) },
                )
            }
        }
        when (val current = state) {
            is LiveTvUiState.Loading -> LoadingBox(Modifier.fillMaxSize())
            is LiveTvUiState.Error -> ErrorBox(current.error, onRetry = viewModel::load, modifier = Modifier.fillMaxSize())
            is LiveTvUiState.Loaded -> when (tab) {
                LiveTvTab.Channels -> ChannelGrid(current.channels, onOpenItem)
                LiveTvTab.Guide -> GuideList(current, onOpenItem)
                LiveTvTab.Recordings -> RecordingGrid(current.recordings, onOpenItem)
                LiveTvTab.Scheduled -> TimerList(current.timers, onCancel = viewModel::cancelTimer)
                LiveTvTab.Series -> TimerList(current.seriesTimers, onCancel = viewModel::cancelSeriesTimer)
            }
        }
    }
}

@Composable
private fun ChannelGrid(channels: List<MediaItem>, onOpenItem: (String) -> Unit) {
    if (channels.isEmpty()) {
        EmptyText()
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(channels, key = { it.id }) { channel ->
            Column {
                PosterCard(channel, onClick = { onOpenItem(channel.id) }, width = 140.dp)
                channel.programInfo?.channelNumber?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GuideList(state: LiveTvUiState.Loaded, onOpenItem: (String) -> Unit) {
    if (state.channels.isEmpty()) {
        EmptyText()
        return
    }
    val now = remember { java.time.LocalDateTime.now(java.time.ZoneOffset.UTC) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(state.channels, key = { it.id }) { channel ->
            val programs = state.guide[channel.id].orEmpty()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    listOfNotNull(channel.programInfo?.channelNumber, channel.name).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (programs.isEmpty()) {
                    Text(stringResource(R.string.livetv_no_programs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    programs.forEach { program ->
                        ProgramRow(program, now, onClick = { onOpenItem(program.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramRow(program: MediaItem, now: java.time.LocalDateTime, onClick: () -> Unit) {
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val window = listOfNotNull(
        program.programInfo?.start?.format(timeFormat),
        program.programInfo?.end?.format(timeFormat),
    ).joinToString("–")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            window,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(program.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val badges = listOfNotNull(
                if (program.programInfo?.isLive == true) stringResource(R.string.livetv_live) else null,
                if (program.programInfo?.isPremiere == true) stringResource(R.string.livetv_premiere) else null,
                if (program.programInfo?.isRepeat == true) stringResource(R.string.livetv_repeat) else null,
                if (program.programInfo?.timerId != null) stringResource(R.string.livetv_recording) else null,
            ).joinToString(" · ")
            if (badges.isNotEmpty()) Text(badges, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RecordingGrid(recordings: List<MediaItem>, onOpenItem: (String) -> Unit) {
    if (recordings.isEmpty()) {
        EmptyText()
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(recordings, key = { it.id }) { recording -> PosterCard(recording, onClick = { onOpenItem(recording.id) }, width = 120.dp) }
    }
}

@Composable
private fun TimerList(timers: List<LiveTimer>, onCancel: (LiveTimer) -> Unit) {
    if (timers.isEmpty()) {
        EmptyText()
        return
    }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEE d MMM HH:mm") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(timers, key = { it.id }) { timer ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(timer.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val details = listOfNotNull(
                        timer.channelName,
                        timer.startDate?.format(dateFormat),
                        timer.days.takeIf { it.isNotEmpty() }?.joinToString(", "),
                    ).joinToString(" · ")
                    if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onCancel(timer) }) { Text(stringResource(R.string.livetv_cancel)) }
            }
        }
    }
}

@Composable
private fun EmptyText() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.livetv_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
