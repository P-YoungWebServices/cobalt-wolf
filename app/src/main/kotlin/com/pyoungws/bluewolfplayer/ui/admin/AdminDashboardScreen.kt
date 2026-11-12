package com.pyoungws.bluewolfplayer.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.admin.MANAGEMENT_DESTINATIONS
import com.pyoungws.bluewolfplayer.shared.admin.NavDestination
import com.pyoungws.bluewolfplayer.shared.presentation.AdminDashboardUiState
import com.pyoungws.bluewolfplayer.shared.presentation.AdminDashboardViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.ServiceBadge
import com.pyoungws.bluewolfplayer.ui.common.CardRow
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.PosterCard
import com.pyoungws.bluewolfplayer.ui.common.SectionTitle
import org.koin.compose.viewmodel.koinViewModel

/** Admin dashboard: quick actions on top, recently added media, then the management cards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenManage: (NavDestination) -> Unit,
    onOpenSessions: () -> Unit,
    viewModel: AdminDashboardViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_dashboard_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> LoadingBox()
                state.error != null && state.sessions.isEmpty() && state.recentlyAdded.isEmpty() ->
                    ErrorBox(state.error!!, onRetry = viewModel::refresh)
                else -> DashboardContent(state, onOpenItem = onOpenItem, onOpenManage = onOpenManage, onOpenSessions = onOpenSessions)
            }
        }
    }
}

@Composable
private fun DashboardContent(
    state: AdminDashboardUiState,
    onOpenItem: (String) -> Unit,
    onOpenManage: (NavDestination) -> Unit,
    onOpenSessions: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "actions") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardActionCard(
                        title = stringResource(R.string.admin_active_sessions),
                        subtitle = pluralStringResource(R.plurals.admin_session_count, state.sessions.size, state.sessions.size),
                        onClick = onOpenSessions,
                        modifier = Modifier.weight(1f),
                    )
                    DashboardActionCard(
                        title = stringResource(R.string.home_downloads),
                        subtitle = stringResource(R.string.admin_manage_downloads_subtitle),
                        onClick = { onOpenManage(NavDestination.Downloads) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardActionCard(
                        title = stringResource(R.string.bazarr_title),
                        subtitle = bazarrSubtitle(state.bazarr),
                        onClick = { onOpenManage(NavDestination.Bazarr) },
                        modifier = Modifier.weight(1f),
                    )
                    DashboardActionCard(
                        title = stringResource(R.string.prowlarr_title),
                        subtitle = prowlarrSubtitle(state.prowlarr),
                        onClick = { onOpenManage(NavDestination.Prowlarr) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (state.recentlyAdded.isNotEmpty()) {
            item(key = "recent-title") { SectionTitle(stringResource(R.string.admin_recently_added)) }
            item(key = "recent-row") {
                CardRow(state.recentlyAdded, key = { it.id }) { PosterCard(it, onClick = { onOpenItem(it.id) }) }
            }
        }
        item(key = "manage-title") { SectionTitle(stringResource(R.string.admin_manage_title)) }
        items(MANAGEMENT_DESTINATIONS.filter { it !in DASHBOARD_CARD_DESTINATIONS }, key = { "manage-${it.name}" }) { destination ->
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp)).clickable { onOpenManage(destination) },
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(manageTitle(destination), style = MaterialTheme.typography.titleMedium)
                    Text(
                        manageSubtitle(destination),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Management entries that already have an action card at the top of the dashboard. */
private val DASHBOARD_CARD_DESTINATIONS = setOf(NavDestination.Downloads, NavDestination.Bazarr, NavDestination.Prowlarr)

@Composable
private fun bazarrSubtitle(badge: ServiceBadge): String {
    val count = badge.count
    return when {
        !badge.configured -> stringResource(R.string.service_card_not_configured)
        count != null -> pluralStringResource(R.plurals.bazarr_wanted_count, count, count)
        else -> stringResource(R.string.bazarr_card_subtitle)
    }
}

@Composable
private fun prowlarrSubtitle(badge: ServiceBadge): String {
    val count = badge.count
    return when {
        !badge.configured -> stringResource(R.string.service_card_not_configured)
        count != null -> pluralStringResource(R.plurals.prowlarr_indexer_count, count, count).let { line ->
            badge.issues?.let { line + " · " + pluralStringResource(R.plurals.prowlarr_issue_count, it, it) } ?: line
        }
        else -> stringResource(R.string.prowlarr_card_subtitle)
    }
}

@Composable
private fun DashboardActionCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun manageTitle(destination: NavDestination): String = when (destination) {
    NavDestination.RequestQueue -> stringResource(R.string.admin_manage_requests)
    NavDestination.MediaManager -> stringResource(R.string.admin_manage_media)
    NavDestination.Downloads -> stringResource(R.string.home_downloads)
    NavDestination.Services -> stringResource(R.string.arr_settings_title)
    NavDestination.Users -> stringResource(R.string.admin_manage_users)
    NavDestination.Devices -> stringResource(R.string.admin_manage_devices)
    NavDestination.Libraries -> stringResource(R.string.admin_manage_libraries)
    NavDestination.Tasks -> stringResource(R.string.admin_manage_tasks)
    NavDestination.Logs -> stringResource(R.string.admin_manage_logs)
    NavDestination.SystemInfo -> stringResource(R.string.admin_manage_system)
    else -> ""
}

@Composable
private fun manageSubtitle(destination: NavDestination): String = when (destination) {
    NavDestination.RequestQueue -> stringResource(R.string.admin_manage_requests_subtitle)
    NavDestination.MediaManager -> stringResource(R.string.admin_manage_media_subtitle)
    NavDestination.Downloads -> stringResource(R.string.admin_manage_downloads_subtitle)
    NavDestination.Services -> stringResource(R.string.admin_manage_services_subtitle)
    NavDestination.Users -> stringResource(R.string.admin_manage_users_subtitle)
    NavDestination.Devices -> stringResource(R.string.admin_manage_devices_subtitle)
    NavDestination.Libraries -> stringResource(R.string.admin_manage_libraries_subtitle)
    NavDestination.Tasks -> stringResource(R.string.admin_manage_tasks_subtitle)
    NavDestination.Logs -> stringResource(R.string.admin_manage_logs_subtitle)
    NavDestination.SystemInfo -> stringResource(R.string.admin_manage_system_subtitle)
    else -> ""
}
