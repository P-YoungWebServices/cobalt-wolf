package com.pyoungws.cobaltwolf.ui.bazarr

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
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.bazarr.MissingSubtitle
import com.pyoungws.cobaltwolf.shared.bazarr.WantedEpisode
import com.pyoungws.cobaltwolf.shared.bazarr.WantedMovie
import com.pyoungws.cobaltwolf.shared.presentation.BazarrSearchStatus
import com.pyoungws.cobaltwolf.shared.presentation.BazarrUiState
import com.pyoungws.cobaltwolf.shared.presentation.BazarrViewModel
import com.pyoungws.cobaltwolf.ui.common.ErrorBox
import com.pyoungws.cobaltwolf.ui.common.LoadingBox
import com.pyoungws.cobaltwolf.ui.common.SectionTitle
import org.koin.compose.viewmodel.koinViewModel

/** Bazarr monitor: the movies and episodes still missing a wanted subtitle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BazarrScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    viewModel: BazarrViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searches by viewModel.searches.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bazarr_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
                actions = {
                    TextButton(onClick = onSettings) { Text(stringResource(R.string.calendar_settings)) }
                    TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.admin_refresh)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                BazarrUiState.Loading -> LoadingBox()
                BazarrUiState.NotConfigured -> NotConfigured(onSettings)
                is BazarrUiState.Error -> ErrorBox(current.error, onRetry = viewModel::refresh)
                is BazarrUiState.Ready -> WantedList(current, searches, viewModel)
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
            stringResource(R.string.bazarr_not_configured),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onSettings, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.calendar_settings))
        }
    }
}

@Composable
private fun WantedList(state: BazarrUiState.Ready, searches: Map<String, BazarrSearchStatus>, viewModel: BazarrViewModel) {
    val wanted = state.wanted
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
        if (wanted.total == 0) {
            item {
                Text(
                    stringResource(R.string.bazarr_nothing_wanted),
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (wanted.movies.isNotEmpty()) {
            item(key = "movies-title") { SectionTitle(stringResource(R.string.bazarr_movies)) }
            items(wanted.movies, key = { "movie-${it.title}" }) { movie ->
                WantedRow(
                    title = movie.title,
                    detail = null,
                    missing = movie.missing,
                    status = searches[viewModel.searchKey(movie)],
                    enabled = movie.radarrId != null,
                    onSearch = { viewModel.search(movie) },
                )
            }
        }
        if (wanted.episodes.isNotEmpty()) {
            item(key = "episodes-title") { SectionTitle(stringResource(R.string.bazarr_episodes)) }
            items(wanted.episodes, key = { "episode-${it.seriesTitle}-${it.episodeCode}-${it.episodeTitle}" }) { episode ->
                WantedRow(
                    title = episode.seriesTitle,
                    detail = listOfNotNull(episode.episodeCode, episode.episodeTitle).joinToString(" · "),
                    missing = episode.missing,
                    status = searches[viewModel.searchKey(episode)],
                    enabled = episode.sonarrEpisodeId != null && episode.sonarrSeriesId != null,
                    onSearch = { viewModel.search(episode) },
                )
            }
        }
    }
}

@Composable
private fun WantedRow(
    title: String,
    detail: String?,
    missing: List<MissingSubtitle>,
    status: BazarrSearchStatus?,
    enabled: Boolean,
    onSearch: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!detail.isNullOrEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onSearch, enabled = enabled && status !is BazarrSearchStatus.Searching) {
                Text(stringResource(if (status is BazarrSearchStatus.Searching) R.string.bazarr_searching else R.string.bazarr_search))
            }
        }
        MissingLine(missing)
        SearchStatusLine(status)
    }
}

@Composable
private fun SearchStatusLine(status: BazarrSearchStatus?) {
    val text = when (status) {
        is BazarrSearchStatus.Downloaded -> stringResource(R.string.bazarr_search_downloaded, status.count)
        BazarrSearchStatus.NothingFound -> stringResource(R.string.bazarr_search_none)
        is BazarrSearchStatus.Failed -> stringResource(R.string.bazarr_search_failed, status.error.toString())
        else -> return
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun MissingLine(missing: List<MissingSubtitle>) {
    if (missing.isEmpty()) return
    Text(
        stringResource(R.string.bazarr_missing, missing.joinToString { it.label }),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
