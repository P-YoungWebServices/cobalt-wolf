package com.pyoungws.cobaltwolf.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.admin.LogSeverity
import com.pyoungws.cobaltwolf.shared.presentation.AdminLogsUiState
import com.pyoungws.cobaltwolf.shared.presentation.AdminLogsViewModel
import com.pyoungws.cobaltwolf.ui.common.ErrorBox
import com.pyoungws.cobaltwolf.ui.common.LoadingBox
import com.pyoungws.cobaltwolf.ui.common.tvListScroll
import org.koin.compose.viewmodel.koinViewModel

/**
 * Live server logs: pick a log file (newest first), filter by severity and text.
 * The open file is re-fetched on the view model's poll interval.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLogsScreen(
    onBack: () -> Unit,
    viewModel: AdminLogsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var fileMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_logs_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
                actions = {
                    if (state.files.isNotEmpty()) {
                        Box {
                            TextButton(onClick = { fileMenuOpen = true }) {
                                Text(state.selectedFile.orEmpty(), maxLines = 1)
                            }
                            DropdownMenu(expanded = fileMenuOpen, onDismissRequest = { fileMenuOpen = false }) {
                                state.files.forEach { file ->
                                    DropdownMenuItem(
                                        text = { Text(file.name) },
                                        onClick = {
                                            viewModel.selectFile(file.name)
                                            fileMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FilterRow(state, onSeverity = viewModel::setMinSeverity, onQuery = viewModel::setQuery)
            Box(Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> LoadingBox()
                    state.error != null && state.lines.isEmpty() ->
                        ErrorBox(state.error!!, onRetry = viewModel::refresh)
                    state.files.isEmpty() -> CenteredText(stringResource(R.string.admin_logs_empty))
                    state.visibleLines.isEmpty() -> CenteredText(stringResource(R.string.admin_logs_no_match))
                    else -> LogLines(state)
                }
            }
        }
    }
}

@Composable
private fun FilterRow(state: AdminLogsUiState, onSeverity: (LogSeverity?) -> Unit, onQuery: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = { Text(stringResource(R.string.admin_logs_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.minSeverity == null,
                onClick = { onSeverity(null) },
                label = { Text(stringResource(R.string.admin_logs_all)) },
            )
            LogSeverity.entries.forEach { severity ->
                FilterChip(
                    selected = state.minSeverity == severity,
                    onClick = { onSeverity(severity) },
                    label = { Text(severityLabel(severity)) },
                )
            }
        }
    }
}

@Composable
private fun LogLines(state: AdminLogsUiState) {
    val lines = state.visibleLines
    val listState = rememberLazyListState()

    // Keep the view pinned to the newest line while the file is tailing.
    LaunchedEffect(state.lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().tvListScroll(listState)) {
        items(lines) { line ->
            Text(
                line.text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = when (line.severity) {
                    LogSeverity.Error, LogSeverity.Fatal -> MaterialTheme.colorScheme.error
                    LogSeverity.Warning -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun severityLabel(severity: LogSeverity): String = when (severity) {
    LogSeverity.Debug -> stringResource(R.string.admin_logs_debug)
    LogSeverity.Info -> stringResource(R.string.admin_logs_info)
    LogSeverity.Warning -> stringResource(R.string.admin_logs_warning)
    LogSeverity.Error -> stringResource(R.string.admin_logs_error)
    LogSeverity.Fatal -> stringResource(R.string.admin_logs_fatal)
}

@Composable
private fun CenteredText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
