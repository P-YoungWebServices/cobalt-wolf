package com.pyoungws.bluewolfplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pyoungws.bluewolfplayer.isTelevision

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5FA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E3FF),
    onPrimaryContainer = Color(0xFF001C3A),
    secondary = Color(0xFF545F70),
    tertiary = Color(0xFF6D5677),
)

private val DarkColors = darkColorScheme(
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
    outline = Color(0xFF8B96AD),
)

/** The app's own palette on every screen (no dynamic color); TVs are always dark. */
@Composable
fun BlueWolfTheme(
    darkTheme: Boolean = isSystemInDarkTheme() || LocalContext.current.isTelevision(),
    content: @Composable () -> Unit,
) {
    val themePref = com.pyoungws.bluewolfplayer.ui.common.rememberDisplayOptions().first.theme
    val dark = when (themePref) {
        com.pyoungws.bluewolfplayer.ui.common.AppTheme.Dark -> true
        com.pyoungws.bluewolfplayer.ui.common.AppTheme.Light -> false
        com.pyoungws.bluewolfplayer.ui.common.AppTheme.System -> darkTheme
    } || LocalContext.current.isTelevision()
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
