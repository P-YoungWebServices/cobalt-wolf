package com.pyoungws.cobaltwolf.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.admin.ActivityEntry
import com.pyoungws.cobaltwolf.shared.admin.AdminRepository
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import com.pyoungws.cobaltwolf.ui.message
import org.koin.compose.koinInject

/**
 * The recent server activity feed, mirroring the bell flyout in jellyfin-web's header. The
 * activity log is an admin endpoint, so callers only show the entry point for administrators.
 */
@Composable
fun NotificationsDialog(onDismiss: () -> Unit) {
    val repository: AdminRepository = koinInject()
    var entries by remember { mutableStateOf<List<ActivityEntry>?>(null) }
    var error by remember { mutableStateOf<LoadError?>(null) }
    LaunchedEffect(Unit) {
        when (val result = repository.activityLog(limit = 20)) {
            is Outcome.Success -> entries = result.value
            is Outcome.Failure -> error = result.error
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_notifications)) },
        text = {
            when {
                error != null -> Text(error!!.message())
                entries == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
                entries!!.isEmpty() -> Text(stringResource(R.string.notifications_empty))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(entries!!, key = { it.id }) { entry ->
                        Column {
                            Text(entry.name, style = MaterialTheme.typography.titleSmall)
                            entry.overview?.takeIf { it != entry.name }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            entry.date?.let {
                                Text(it.take(19).replace('T', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_close)) } },
    )
}
