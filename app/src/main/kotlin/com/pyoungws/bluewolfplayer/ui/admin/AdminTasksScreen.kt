package com.pyoungws.bluewolfplayer.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.admin.AdminTaskState
import com.pyoungws.bluewolfplayer.shared.admin.ScheduledTask
import com.pyoungws.bluewolfplayer.shared.presentation.AdminActionStatus
import com.pyoungws.bluewolfplayer.shared.presentation.AdminTasksUiState
import com.pyoungws.bluewolfplayer.shared.presentation.AdminTasksViewModel
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.formatTimestamp
import org.koin.compose.viewmodel.koinViewModel

/** Scheduled tasks: live progress, start and stop. State refreshes itself while open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminTasksScreen(
    onBack: () -> Unit,
    viewModel: AdminTasksViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_tasks_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
                actions = { TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> LoadingBox()
                state.error != null && state.tasks.isEmpty() -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
                state.tasks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.admin_tasks_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> TaskList(state, onStart = viewModel::startTask, onStop = viewModel::stopTask)
            }
        }
    }
}

@Composable
private fun TaskList(state: AdminTasksUiState, onStart: (String) -> Unit, onStop: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.tasks, key = { it.id }) { task ->
            TaskCard(task, action = state.action, onStart = { onStart(task.id) }, onStop = { onStop(task.id) })
        }
    }
}

@Composable
private fun TaskCard(task: ScheduledTask, action: AdminActionStatus?, onStart: () -> Unit, onStop: () -> Unit) {
    val busy = (action as? AdminActionStatus.Running)?.key
        ?.let { it == "${AdminTasksViewModel.START_KEY}${task.id}" || it == "${AdminTasksViewModel.STOP_KEY}${task.id}" } == true
    val running = task.state == AdminTaskState.Running || task.state == AdminTaskState.Cancelling

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(task.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    task.category?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.padding(horizontal = 8.dp).size(20.dp), strokeWidth = 2.dp)
                } else if (running) {
                    TextButton(onClick = onStop) { Text(stringResource(R.string.admin_task_stop)) }
                } else {
                    TextButton(onClick = onStart) { Text(stringResource(R.string.admin_task_start)) }
                }
            }
            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { task.progressFraction ?: 0f },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        taskStateLabel(task),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    lastRunLabel(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            task.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun taskStateLabel(task: ScheduledTask): String = when (task.state) {
    AdminTaskState.Running -> stringResource(R.string.admin_task_running, ((task.progressFraction ?: 0f) * 100).toInt())
    AdminTaskState.Cancelling -> stringResource(R.string.admin_task_cancelling)
    AdminTaskState.Idle -> stringResource(R.string.admin_task_idle)
}

@Composable
private fun lastRunLabel(task: ScheduledTask): String =
    task.lastRun?.let { lastRun ->
        stringResource(R.string.admin_task_last_run, formatTimestamp(lastRun), task.lastResult.orEmpty())
    } ?: stringResource(R.string.admin_task_never_run)
