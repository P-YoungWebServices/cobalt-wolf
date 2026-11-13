package com.pyoungws.bluewolfplayer.shared.presentation

import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchFilterTest {

    private fun item(kind: MediaKind) = MediaItem(id = kind.name, name = kind.name, kind = kind)

    @Test
    fun `each filter keeps only its own kinds`() {
        assertTrue(item(MediaKind.Movie).matches(SearchFilter.Movies))
        assertTrue(item(MediaKind.MusicVideo).matches(SearchFilter.Movies))
        assertFalse(item(MediaKind.Series).matches(SearchFilter.Movies))

        assertTrue(item(MediaKind.Series).matches(SearchFilter.Shows))
        assertFalse(item(MediaKind.Episode).matches(SearchFilter.Shows))

        assertTrue(item(MediaKind.Episode).matches(SearchFilter.Episodes))
        assertTrue(item(MediaKind.Person).matches(SearchFilter.People))

        assertTrue(item(MediaKind.Song).matches(SearchFilter.Music))
        assertTrue(item(MediaKind.MusicAlbum).matches(SearchFilter.Music))
        assertFalse(item(MediaKind.MusicVideo).matches(SearchFilter.Music))

        assertTrue(item(MediaKind.TvChannel).matches(SearchFilter.LiveTv))
        assertTrue(item(MediaKind.Recording).matches(SearchFilter.LiveTv))
    }

    @Test
    fun `other catches kinds the named filters do not`() {
        assertTrue(item(MediaKind.BoxSet).matches(SearchFilter.Other))
        assertTrue(item(MediaKind.Playlist).matches(SearchFilter.Other))
        MediaKind.entries.forEach { kind ->
            // Every kind matches All, and Other is exclusive with the named groups.
            assertTrue(item(kind).matches(SearchFilter.All), kind.name)
            if (item(kind).matches(SearchFilter.Other)) {
                assertEquals(
                    emptyList(),
                    SearchFilter.entries.filter { it != SearchFilter.All && it != SearchFilter.Other && item(kind).matches(it) },
                    kind.name,
                )
            }
        }
    }

    @Test
    fun `filteredResults narrows the result list while All keeps it`() {
        val results = listOf(item(MediaKind.Movie), item(MediaKind.Song))
        val state = SearchUiState(results = results)

        assertEquals(results, state.filteredResults)
        assertEquals(listOf(item(MediaKind.Movie)), state.copy(filter = SearchFilter.Movies).filteredResults)
        assertEquals(emptyList(), state.copy(filter = SearchFilter.Episodes).filteredResults)
    }
}
