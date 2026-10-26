package com.kexu.campus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6B57),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6EBE1),
    onPrimaryContainer = Color(0xFF12352A),
    secondary = Color(0xFF52624F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3EBDD),
    onSecondaryContainer = Color(0xFF1B2A18),
    tertiary = Color(0xFF8A5A2B),
    background = Color(0xFFFAF9F5),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFFFBF6),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFEDEFE8),
    onSurfaceVariant = Color(0xFF414943),
    outline = Color(0xFF7A857D),
    outlineVariant = Color(0xFFD6DCD4),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BD3BE),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF1B4D3D),
    onPrimaryContainer = Color(0xFFD6EBE1),
    secondary = Color(0xFFB9C8B3),
    onSecondary = Color(0xFF243322),
    background = Color(0xFF121412),
    onBackground = Color(0xFFE3E5DF),
    surface = Color(0xFF181B18),
    onSurface = Color(0xFFE3E5DF),
    surfaceVariant = Color(0xFF343A34),
    onSurfaceVariant = Color(0xFFC3CAC3),
    outline = Color(0xFF8D958E),
    outlineVariant = Color(0xFF3F4640),
    error = Color(0xFFF2B8B5)
)

private val CoursePalette = listOf(
    Color(0xFFCFE8DA),
    Color(0xFFD5E0F7),
    Color(0xFFF6DFC8),
    Color(0xFFEAD8F2),
    Color(0xFFF3D6D6),
    Color(0xFFD9E9C3),
    Color(0xFFC8E8EA),
    Color(0xFFEDE3C8)
)

val CourseTextColor = Color(0xFF17211C)

fun courseColor(index: Int): Color = CoursePalette[((index % CoursePalette.size) + CoursePalette.size) % CoursePalette.size]

@Composable
fun KexuTheme(darkMode: Int, content: @Composable () -> Unit) {
    val dark = when (darkMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
