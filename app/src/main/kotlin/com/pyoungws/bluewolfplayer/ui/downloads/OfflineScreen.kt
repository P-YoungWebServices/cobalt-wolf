package com.pyoungws.bluewolfplayer.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import org.koin.compose.koinInject

/** Lists media saved for offline playback. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineScreen(onBack: () -> Unit, onPlay: (String) -> Unit) {
    val downloads: OfflineDownloads = koinInject()
    val saved by downloads.items.collectAsStateWithLifecycle()
    val jobs by downloads.jobs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.offline_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (saved.isEmpty() && jobs.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.offline_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(jobs.values.toList(), key = { "job-${it.itemId}" }) { job ->
                Column(Modifier.fillMaxWidth()) {
                    Text(job.name, style = MaterialTheme.typography.bodyMedium)
                    if (job.failed) {
                        Text(stringResource(R.string.offline_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } else {
                        val pct = if (job.total > 0) job.received.toFloat() / job.total else 0f
                        LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth())
                        Text(formatBytes(job.received) + if (job.total > 0) " / " + formatBytes(job.total) else "", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
            items(saved, key = { it.itemId }) { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium)
                        Text(formatBytes(item.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onPlay(item.itemId) }) { Text(stringResource(R.string.detail_play)) }
                    TextButton(onClick = { downloads.delete(item.itemId) }) { Text(stringResource(R.string.offline_delete)) }
                }
                HorizontalDivider()
            }
        }
    }
}

