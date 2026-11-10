package com.example.ebf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Warme, papierähnliche Farben für die App-Oberfläche. */
internal val LightColors = lightColorScheme(
    primary = Color(0xFF8B5A2B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF5DEC4),
    onPrimaryContainer = Color(0xFF331F0B),
    secondary = Color(0xFF725A44),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0E0CF),
    onSecondaryContainer = Color(0xFF2A1A0B),
    tertiary = Color(0xFF586249),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F3EC),
    onBackground = Color(0xFF221B15),
    surface = Color(0xFFF8F3EC),
    onSurface = Color(0xFF221B15),
    surfaceVariant = Color(0xFFEDE1D4),
    onSurfaceVariant = Color(0xFF51443A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3ECE4),
    surfaceContainer = Color(0xFFEFE6DC),
    surfaceContainerHigh = Color(0xFFE9E0D6),
    surfaceContainerHighest = Color(0xFFE3DAD0),
    outline = Color(0xFF84756A),
    outlineVariant = Color(0xFFD6C3B5),
    inverseSurface = Color(0xFF382F28),
    inverseOnSurface = Color(0xFFFCEEE3),
    inversePrimary = Color(0xFFE6BC8F),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFE6BC8F),
    onPrimary = Color(0xFF452B10),
    primaryContainer = Color(0xFF603F21),
    onPrimaryContainer = Color(0xFFFFDDBF),
    secondary = Color(0xFFD9C1A9),
    onSecondary = Color(0xFF3C2D1C),
    secondaryContainer = Color(0xFF54432F),
    onSecondaryContainer = Color(0xFFF6DEC5),
    tertiary = Color(0xFFBFCBA9),
    onTertiary = Color(0xFF2A331E),
    background = Color(0xFF15120F),
    onBackground = Color(0xFFEDE0D6),
    surface = Color(0xFF15120F),
    onSurface = Color(0xFFEDE0D6),
    surfaceVariant = Color(0xFF51443A),
    onSurfaceVariant = Color(0xFFD6C3B5),
    surfaceContainerLowest = Color(0xFF0F0D0A),
    surfaceContainerLow = Color(0xFF1D1A16),
    surfaceContainer = Color(0xFF221E1A),
    surfaceContainerHigh = Color(0xFF2C2824),
    surfaceContainerHighest = Color(0xFF37322E),
    outline = Color(0xFF9F8D80),
    outlineVariant = Color(0xFF51443A),
    inverseSurface = Color(0xFFEDE0D6),
    inverseOnSurface = Color(0xFF382F28),
    inversePrimary = Color(0xFF8B5A2B),
)

/** Farben rund um die Seiten im Reader. */
data class ReaderColors(
    /** Hintergrund hinter den Seiten ("Tisch"). */
    val backdrop: Color,
    /** Papierfarbe einer Seite. */
    val paper: Color,
    /** Kanten der darunterliegenden Blätter. */
    val paperEdge: Color,
    /** Schatten am Bundsteg und beim Umblättern. */
    val shade: Color,
)

internal val LightReaderColors = ReaderColors(
    backdrop = Color(0xFFE4DBCE),
    paper = Color(0xFFFFFDF8),
    paperEdge = Color(0xFFD9CFC1),
    shade = Color(0xFF3A2A1A),
)

internal val DarkReaderColors = ReaderColors(
    backdrop = Color(0xFF0C0B0A),
    paper = Color(0xFF1F1D1A),
    paperEdge = Color(0xFF3A3631),
    shade = Color(0xFF000000),
)
