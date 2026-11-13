package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.pyoungws.bluewolfplayer.ui.theme.BlueWolfTheme

private val TvColors = darkColorScheme(
    primary = Color(0xFFA5C8FF),
    onPrimary = Color(0xFF00315E),
    primaryContainer = Color(0xFF004785),
    onPrimaryContainer = Color(0xFFD4E3FF),
    secondary = Color(0xFFBCC7DC),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3E4859),
    onSecondaryContainer = Color(0xFFDAE3F9),
    tertiary = Color(0xFFD9BDE3),
    onTertiary = Color(0xFF3D2946),
    tertiaryContainer = Color(0xFF553F5E),
    onTertiaryContainer = Color(0xFFF5D9FF),
    background = Color(0xFF0B1D36),
    onBackground = Color(0xFFDBE4F2),
    surface = Color(0xFF12264A),
    onSurface = Color(0xFFDBE4F2),
    surfaceVariant = Color(0xFF25365A),
    onSurfaceVariant = Color(0xFFB9C6DF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    border = Color(0xFF8B96AD),
)

/**
 * TV layout theme: always dark (10-foot UI), with the regular app theme inside so reused
 * Material 3 widgets (text fields, dialogs, progress) keep working in the TV screens.
 */
@Composable
fun CobaltWolfTvTheme(content: @Composable () -> Unit) {
    BlueWolfTheme(darkTheme = true) {
        MaterialTheme(colorScheme = TvColors) {
            // Both Material 3 flavors leave LocalContentColor at its black default; provide ours
            // so unstyled Text — tv widgets and shared phone screens alike — is light on navy.
            CompositionLocalProvider(
                LocalContentColor provides TvColors.onBackground,
                androidx.compose.material3.LocalContentColor provides TvColors.onBackground,
                content = content,
            )
        }
    }
}
