package com.pyoungws.bluewolfplayer.ui.prowlarr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.pyoungws.bluewolfplayer.shared.presentation.ProwlarrTestStatus
import com.pyoungws.bluewolfplayer.shared.presentation.ProwlarrUiState
import com.pyoungws.bluewolfplayer.shared.presentation.ProwlarrViewModel
import com.pyoungws.bluewolfplayer.shared.prowlarr.ProwlarrHealthIssue
import com.pyoungws.bluewolfplayer.shared.prowlarr.ProwlarrIndexer
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.SectionTitle
import org.koin.compose.viewmodel.koinViewModel

/** Prowlarr monitor: the configured indexers with usage stats, plus current health issues. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProwlarrScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    viewModel: ProwlarrViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tests by viewModel.tests.collectAsStateWithLifecycle()
    val testingAll by viewModel.testingAll.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prowlarr_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
                actions = {
                    TextButton(onClick = onSettings) { Text(stringResource(R.string.calendar_settings)) }
                    TextButton(onClick = viewModel::testAll, enabled = !testingAll) {
                        Text(stringResource(if (testingAll) R.string.prowlarr_testing else R.string.prowlarr_test_all))
                    }
                    TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                ProwlarrUiState.Loading -> LoadingBox()
                ProwlarrUiState.NotConfigured -> NotConfigured(onSettings)
                is ProwlarrUiState.Error -> ErrorBox(current.error, onRetry = viewModel::refresh)
                is ProwlarrUiState.Ready -> IndexerList(current, tests, viewModel)
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
            stringResource(R.string.prowlarr_not_configured),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onSettings, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.calendar_settings))
        }
    }
}

@Composable
private fun IndexerList(
    state: ProwlarrUiState.Ready,
    tests: Map<Int, ProwlarrTestStatus>,
    viewModel: ProwlarrViewModel,
) {
    val overview = state.overview
    LazyColumn(Modifier.fillMaxSize()) {
        if (state.stale) {
            item {
                Text(
                    stringResource(R.string.downloads_stale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (overview.health.isNotEmpty()) {
            item(key = "health-title") { SectionTitle(stringResource(R.string.prowlarr_health)) }
            items(overview.health, key = { "health-${it.source}-${it.message}" }) { HealthRow(it) }
        }
        item(key = "indexers-title") { SectionTitle(stringResource(R.string.prowlarr_indexers)) }
        if (overview.indexers.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.prowlarr_no_indexers),
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(overview.indexers, key = { it.id }) { indexer ->
            IndexerRow(indexer, tests[indexer.id], onRetest = { viewModel.test(indexer) })
        }
    }
}

@Composable
private fun HealthRow(issue: ProwlarrHealthIssue) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            listOfNotNull(issue.source, issue.type).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        issue.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IndexerRow(indexer: ProwlarrIndexer, testStatus: ProwlarrTestStatus?, onRetest: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(indexer.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetest, enabled = testStatus !is ProwlarrTestStatus.Testing) {
                Text(stringResource(if (testStatus is ProwlarrTestStatus.Testing) R.string.prowlarr_testing else R.string.prowlarr_retest))
            }
        }
        val status = when {
            !indexer.enabled -> stringResource(R.string.prowlarr_disabled)
            indexer.disabledUntil != null -> stringResource(R.string.prowlarr_throttled)
            else -> null
        }
        val line = listOfNotNull(indexer.protocol, status).joinToString(" · ")
        if (line.isNotEmpty()) {
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = if (status == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        indexer.stats?.let { stats ->
            Text(
                stringResource(R.string.prowlarr_stats_line, stats.queries, stats.grabs, stats.averageResponseMs ?: 0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (testStatus) {
            ProwlarrTestStatus.Passed -> Text(
                stringResource(R.string.prowlarr_test_passed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            is ProwlarrTestStatus.Failed -> Text(
                stringResource(R.string.prowlarr_test_failed, testStatus.error.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            else -> {}
        }
    }
}
