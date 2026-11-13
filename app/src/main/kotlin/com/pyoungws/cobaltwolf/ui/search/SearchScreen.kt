package com.pyoungws.cobaltwolf.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.model.MediaItem
import com.pyoungws.cobaltwolf.shared.model.MediaKind
import com.pyoungws.cobaltwolf.shared.presentation.SearchViewModel
import com.pyoungws.cobaltwolf.shared.seerr.SeerrMediaType
import com.pyoungws.cobaltwolf.shared.seerr.SeerrTitle
import com.pyoungws.cobaltwolf.ui.common.Artwork
import com.pyoungws.cobaltwolf.ui.common.ErrorBox
import com.pyoungws.cobaltwolf.ui.common.cardSubtitle
import com.pyoungws.cobaltwolf.ui.common.cardTitle
import com.pyoungws.cobaltwolf.ui.discover.SeerrStatusBadge
import com.pyoungws.cobaltwolf.ui.message
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenTitle: (SeerrMediaType, Int) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focusRequester),
            )
            FilterRow(state.filter, viewModel::setFilter)
            Box(Modifier.fillMaxSize()) {
                when {
                    state.error != null -> ErrorBox(state.error!!, onRetry = null)
                    state.isSearching && state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { CircularProgressIndicator() }
                    state.showNoResults -> Text(stringResource(R.string.search_no_results), Modifier.align(Alignment.TopCenter).padding(16.dp))
                    state.query.isBlank() -> Text(stringResource(R.string.search_prompt), Modifier.align(Alignment.TopCenter).padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.filteredResults.isNotEmpty()) {
                            item(key = "header-local") { SearchSectionHeader(stringResource(R.string.search_on_server)) }
                            val groups = linkedMapOf<com.pyoungws.cobaltwolf.shared.model.MediaKind, MutableList<com.pyoungws.cobaltwolf.shared.model.MediaItem>>()
                            state.filteredResults.forEach { groups.getOrPut(it.kind) { mutableListOf() } += it }
                            val kindOrder = listOf(
                                com.pyoungws.cobaltwolf.shared.model.MediaKind.Movie,
                                com.pyoungws.cobaltwolf.shared.model.MediaKind.Series,
                                com.pyoungws.cobaltwolf.shared.model.MediaKind.Episode,
                                com.pyoungws.cobaltwolf.shared.model.MediaKind.Person,
                                com.pyoungws.cobaltwolf.shared.model.MediaKind.MusicArtist,
                                com.pyoungws.cobaltwolf.shared.model.MediaKind.MusicAlbum,
                                com.pyoungws.cobaltwolf.shared.model.MediaKind.Song,
                                com.pyoungws.cobaltwolf.shared.model.MediaKind.MusicVideo,
                                com.pyoungws.cobaltwolf.shared.model.MediaKind.BoxSet,
                                com.pyoungws.cobaltwolf.shared.model.MediaKind.Playlist,
                                com.pyoungws.cobaltwolf.shared.model.MediaKind.TvChannel,
                                com.pyoungws.cobaltwolf.shared.model.MediaKind.Recording,
                                com.pyoungws.cobaltwolf.shared.model.MediaKind.Video,
                            )
                            (kindOrder + groups.keys).distinct().filter { groups[it].orEmpty().isNotEmpty() }.forEach { kind ->
                                item(key = "header-${kind.name}") { SearchSectionHeader(stringResource(searchKindLabel(kind))) }
                                items(groups[kind].orEmpty(), key = { "${kind.name}-${it.id}" }) { SearchResultRow(it, onClick = { onOpenItem(it.id) }) }
                            }
                        }
                        if (state.seerrResults.isNotEmpty() || state.seerrError != null) {
                            item(key = "header-seerr") { SearchSectionHeader(stringResource(R.string.search_on_seerr)) }
                            items(state.seerrResults, key = { "seerr-${it.type}-${it.tmdbId}" }) {
                                SeerrResultRow(it, onClick = { onOpenTitle(it.type, it.tmdbId) })
                            }
                            state.seerrError?.let { error ->
                                item(key = "seerr-error") {
                                    Text(
                                        error.message(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The result-type chips; hidden until the box has been searched so they do not sit over the prompt. */
@Composable
private fun FilterRow(selected: com.pyoungws.cobaltwolf.shared.presentation.SearchFilter, onSelect: (com.pyoungws.cobaltwolf.shared.presentation.SearchFilter) -> Unit) {
    val filters = listOf(
        com.pyoungws.cobaltwolf.shared.presentation.SearchFilter.All to R.string.filter_all,
        com.pyoungws.cobaltwolf.shared.presentation.SearchFilter.Movies to R.string.kind_movies,
        com.pyoungws.cobaltwolf.shared.presentation.SearchFilter.Shows to R.string.kind_shows,
        com.pyoungws.cobaltwolf.shared.presentation.SearchFilter.Episodes to R.string.kind_episodes,
        com.pyoungws.cobaltwolf.shared.presentation.SearchFilter.People to R.string.kind_people,
        com.pyoungws.cobaltwolf.shared.presentation.SearchFilter.Music to R.string.kind_music,
        com.pyoungws.cobaltwolf.shared.presentation.SearchFilter.LiveTv to R.string.kind_livetv,
        com.pyoungws.cobaltwolf.shared.presentation.SearchFilter.Other to R.string.kind_other,
    )
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        items(filters, key = { it.first.name }) { (filter, label) ->
            androidx.compose.material3.FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun SearchSectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SeerrResultRow(title: SeerrTitle, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(title.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.width(56.dp))
        Column(Modifier.weight(1f)) {
            Text(title.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            title.year?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            Text(
                if (title.type == SeerrMediaType.Movie) stringResource(R.string.kind_movie) else stringResource(R.string.kind_series),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        SeerrStatusBadge(title)
    }
}

@Composable
private fun SearchResultRow(item: MediaItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(item.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.width(56.dp))
        Column(Modifier.weight(1f)) {
            Text(item.cardTitle(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.cardSubtitle()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(item.kind.label(), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MediaKind.label() = when (this) {
    MediaKind.Movie -> stringResource(R.string.kind_movie)
    MediaKind.Series -> stringResource(R.string.kind_series)
    MediaKind.Episode -> stringResource(R.string.kind_episode)
    MediaKind.MusicVideo -> stringResource(R.string.kind_music_video)
    else -> ""
}

private fun searchKindLabel(kind: com.pyoungws.cobaltwolf.shared.model.MediaKind): Int = when (kind) {
    com.pyoungws.cobaltwolf.shared.model.MediaKind.Movie -> R.string.kind_movies
    com.pyoungws.cobaltwolf.shared.model.MediaKind.Series, com.pyoungws.cobaltwolf.shared.model.MediaKind.Season -> R.string.kind_shows
    com.pyoungws.cobaltwolf.shared.model.MediaKind.Episode -> R.string.kind_episodes
    com.pyoungws.cobaltwolf.shared.model.MediaKind.Person -> R.string.kind_people
    com.pyoungws.cobaltwolf.shared.model.MediaKind.MusicArtist -> R.string.kind_artists
    com.pyoungws.cobaltwolf.shared.model.MediaKind.MusicAlbum -> R.string.kind_albums
    com.pyoungws.cobaltwolf.shared.model.MediaKind.Song -> R.string.kind_songs
    com.pyoungws.cobaltwolf.shared.model.MediaKind.TvChannel, com.pyoungws.cobaltwolf.shared.model.MediaKind.Program -> R.string.kind_livetv
    com.pyoungws.cobaltwolf.shared.model.MediaKind.Playlist -> R.string.kind_playlists
    com.pyoungws.cobaltwolf.shared.model.MediaKind.BoxSet -> R.string.kind_collections
    else -> R.string.kind_other
}
