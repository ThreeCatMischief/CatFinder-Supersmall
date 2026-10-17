package com.catfinder.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/* Always dark: the drive screen is a video feed, and the settings screen is
 * opened from it. Amber is the control colour, matching the launcher icon. */

val Amber = Color(0xFFF2B33D)
val Navy = Color(0xFF1E2A32)
val LinkOk = Color(0xFF6CCB8A)
val LinkBad = Color(0xFFE5675C)

private val Dark = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1D00),
    primaryContainer = Color(0xFF5A4310),
    onPrimaryContainer = Color(0xFFFFDFA6),
    secondary = Color(0xFFA9C4D0),
    onSecondary = Color(0xFF12303B),
    secondaryContainer = Color(0xFF2E4752),
    onSecondaryContainer = Color(0xFFD6E4EA),
    error = LinkBad,
    background = Color(0xFF12181C),
    onBackground = Color(0xFFE3E9EC),
    surface = Color(0xFF12181C),
    onSurface = Color(0xFFE3E9EC),
    surfaceVariant = Color(0xFF26323A),
    onSurfaceVariant = Color(0xFFB7C3CA),
    outline = Color(0xFF7E8B93),
    outlineVariant = Color(0xFF3A464E),
    surfaceContainerLow = Color(0xFF182026),
    surfaceContainer = Color(0xFF1C252B),
    surfaceContainerHigh = Color(0xFF232D34),
    surfaceContainerHighest = Color(0xFF2A353D),
)

@Composable
fun CatFinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, content = content)
}
