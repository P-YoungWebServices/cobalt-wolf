package com.pyoungws.bluewolfplayer.ui.library

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.library.ItemQuery
import com.pyoungws.bluewolfplayer.shared.library.LibraryRepository
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import com.pyoungws.bluewolfplayer.shared.model.GenreInfo
import com.pyoungws.bluewolfplayer.shared.model.Library
import com.pyoungws.bluewolfplayer.shared.model.LibraryType
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.ui.common.PosterCard
import org.koin.compose.koinInject

private enum class LibraryView(val labelRes: Int) {
    Grid(R.string.view_grid), Genres(R.string.view_genres), Letters(R.string.view_letters), Suggested(R.string.view_suggested)
}

/** Browses one library: paged grid with sort/filter plus genre, letter and suggested views. */
@Composable
fun LibraryScreen(library: Library, onBack: () -> Unit, onOpenItem: (String) -> Unit) {
    val repository: LibraryRepository = koinInject()
    var view by remember { mutableStateOf(LibraryView.Grid) }
    var genreName by remember { mutableStateOf<String?>(null) }
    var letter by remember { mutableStateOf<String?>(null) }

    val genres by produceState<List<GenreInfo>>(emptyList(), library.id) {
        value = (repository.genres(library.id) as? Outcome.Success)?.value.orEmpty()
    }
    val suggestions by produceState<List<MediaItem>>(emptyList(), library.id, view) {
        if (view == LibraryView.Suggested) value = (repository.suggestions(library) as? Outcome.Success)?.value.orEmpty()
    }

    val kind = when (library.type) {
        LibraryType.TvShows -> MediaKind.Series
        LibraryType.MusicVideos -> MediaKind.MusicVideo
        LibraryType.BoxSets -> MediaKind.BoxSet
        LibraryType.Playlists -> MediaKind.Playlist
        else -> MediaKind.Movie
    }
    val query = ItemQuery(
        libraryId = library.id,
        includeItemKinds = setOf(kind),
        genres = genreName?.let(::listOf).orEmpty(),
        nameStartsWith = letter,
    )

    when (view) {
        LibraryView.Suggested -> SuggestedView(library.name, suggestions, onBack = onBack, onOpenItem = onOpenItem, view = view, onViewChange = {
            view = it; genreName = null; letter = null
        })
        else -> {
            var title = library.name
            genreName?.let { title += " · $it" }
            letter?.let { title += " · $it" }
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                ViewModeRow(view = view, genres = genres, genreName = genreName, letter = letter,
                    onViewChange = { view = it; genreName = null; letter = null },
                    onGenre = { genreName = it; letter = null },
                    onLetter = { letter = it; genreName = null })
                Box(Modifier.fillMaxSize()) { ItemBrowseScreen(title, query, onBack = onBack, onOpenItem = onOpenItem) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestedView(title: String, items: List<MediaItem>, onBack: () -> Unit, onOpenItem: (String) -> Unit, view: LibraryView, onViewChange: (LibraryView) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ViewModeRow(view = view, genres = emptyList(), genreName = null, letter = null,
                onViewChange = onViewChange, onGenre = {}, onLetter = {})
            if (items.isEmpty()) {
                Text(stringResource(R.string.library_empty), modifier = Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { PosterCard(it, onClick = { onOpenItem(it.id) }, width = 120.dp) }
                }
            }
        }
    }
}

@Composable
private fun ViewModeRow(
    view: LibraryView,
    genres: List<GenreInfo>,
    genreName: String?,
    letter: String?,
    onViewChange: (LibraryView) -> Unit,
    onGenre: (String?) -> Unit,
    onLetter: (String?) -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryView.entries.forEach { entry ->
                FilterChip(selected = view == entry, onClick = { onViewChange(entry) }, label = { Text(stringResource(entry.labelRes)) })
            }
        }
        if (view == LibraryView.Genres) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = genreName == null, onClick = { onGenre(null) }, label = { Text(stringResource(R.string.filter_all)) })
                genres.forEach { genre ->
                    FilterChip(selected = genreName == genre.name, onClick = { onGenre(genre.name) }, label = { Text(genre.name) })
                }
            }
        }
        if (view == LibraryView.Letters) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = letter == null, onClick = { onLetter(null) }, label = { Text(stringResource(R.string.filter_all)) })
                (listOf("#") + ('A'..'Z').map { it.toString() }).forEach { l ->
                    FilterChip(selected = letter == l, onClick = { onLetter(l) }, label = { Text(l) })
                }
            }
        }
    }
}
