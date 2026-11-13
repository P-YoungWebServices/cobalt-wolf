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

    fun displayOptions() = DisplayOptions(
        showBackdrops = prefs.getBoolean("opt.backdrops", true),
        blurBackdrops = prefs.getBoolean("opt.blur", false),
        watchedBadge = prefs.getBoolean("opt.watched_badge", true),
        clock = prefs.getBoolean("opt.clock", true),
        theme = AppTheme.entries.getOrElse(prefs.getInt("opt.theme", 0)) { AppTheme.System },
    )

    fun setDisplayOptions(options: DisplayOptions) {
        prefs.edit()
            .putBoolean("opt.backdrops", options.showBackdrops)
            .putBoolean("opt.blur", options.blurBackdrops)
            .putBoolean("opt.watched_badge", options.watchedBadge)
            .putBoolean("opt.clock", options.clock)
            .putInt("opt.theme", options.theme.ordinal)
            .apply()
    }
}

enum class AppTheme { System, Light, Dark }

/** Global look-and-feel options, shared by the phone and TV UIs. */
data class DisplayOptions(
    /** Backdrop artwork behind detail pages. */
    val showBackdrops: Boolean = true,
    /** Blur the backdrop so the foreground text stays readable. */
    val blurBackdrops: Boolean = false,
    /** Watched check / unplayed-count badge on cards. */
    val watchedBadge: Boolean = true,
    /** Clock in the TV top bar. */
    val clock: Boolean = true,
    val theme: AppTheme = AppTheme.System,
)

/** Remembered, writable view prefs for a page key. */
@Composable
fun rememberViewPrefs(key: String): Pair<ViewPrefs, (ViewPrefs) -> Unit> {
    val context = LocalContext.current
    val store = remember { DisplayPrefs(context) }
    var current by remember(key) { mutableStateOf(store.get(key)) }
    return current to { updated -> store.set(key, updated); current = updated }
}

/** Remembered, writable global [DisplayOptions]. */
@Composable
fun rememberDisplayOptions(): Pair<DisplayOptions, (DisplayOptions) -> Unit> {
    val context = LocalContext.current
    val store = remember { DisplayPrefs(context) }
    var current by remember { mutableStateOf(store.displayOptions()) }
    return current to { updated -> store.setDisplayOptions(updated); current = updated }
}
