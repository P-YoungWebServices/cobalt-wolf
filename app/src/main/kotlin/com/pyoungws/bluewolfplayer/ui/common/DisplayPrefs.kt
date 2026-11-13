package com.pyoungws.bluewolfplayer.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class CardView { Grid, List }
enum class CardSize(val width: Dp) { Small(96.dp), Medium(120.dp), Large(160.dp) }
/** Which artwork a card prefers: the poster, the wide banner or the 16:9 thumb. */
enum class CardImage { Poster, Banner, Thumb }

data class ViewPrefs(
    val view: CardView = CardView.Grid,
    val size: CardSize = CardSize.Medium,
    val image: CardImage = CardImage.Poster,
)

/**
 * Per-page display preferences (grid/list, card size, artwork kind) kept in
 * SharedPreferences keyed by the page — library id, or a logical page name.
 */
class DisplayPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("display_prefs", Context.MODE_PRIVATE)

    fun get(key: String) = ViewPrefs(
        view = CardView.entries.getOrElse(prefs.getInt("$key.view", 0)) { CardView.Grid },
        size = CardSize.entries.getOrElse(prefs.getInt("$key.size", CardSize.Medium.ordinal)) { CardSize.Medium },
        image = CardImage.entries.getOrElse(prefs.getInt("$key.image", 0)) { CardImage.Poster },
    )

    fun set(key: String, prefs2: ViewPrefs) {
        prefs.edit()
            .putInt("$key.view", prefs2.view.ordinal)
            .putInt("$key.size", prefs2.size.ordinal)
            .putInt("$key.image", prefs2.image.ordinal)
            .apply()
    }
}

/** Remembered, writable view prefs for a page key. */
@Composable
fun rememberViewPrefs(key: String): Pair<ViewPrefs, (ViewPrefs) -> Unit> {
    val context = LocalContext.current
    val store = remember { DisplayPrefs(context) }
    var current by remember(key) { mutableStateOf(store.get(key)) }
    return current to { updated -> store.set(key, updated); current = updated }
}
