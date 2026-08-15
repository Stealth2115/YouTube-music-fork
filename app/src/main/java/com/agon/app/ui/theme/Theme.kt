package com.agon.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

@Composable
fun AgonAppTheme(
    accentName: String = "Redline",
    bgName: String = "Pure Black",
    content: @Composable () -> Unit,
) {
    val accent = AccentOptions[accentName] ?: RedlineRed
    val pureBlack = bgName != "Charcoal"

    val bg = if (pureBlack) NearBlack else Charcoal
    val surf = if (pureBlack) Color(0xFF0E0E11) else Color(0xFF17171C)
    val surfHigh = if (pureBlack) Color(0xFF17171B) else Color(0xFF1F1F26)
    val surfHighest = if (pureBlack) Color(0xFF202025) else Color(0xFF27272F)

    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = lerp(accent, Color.Black, 0.65f),
        onPrimaryContainer = Color(0xFFFFDAD6),
        secondary = accent,
        onSecondary = Color.White,
        secondaryContainer = lerp(accent, Color.Black, 0.75f),
        onSecondaryContainer = Color(0xFFFFDAD6),
        tertiary = accent,
        onTertiary = Color.White,
        background = bg,
        onBackground = TextPrimary,
        surface = surf,
        onSurface = TextPrimary,
        surfaceVariant = surfHigh,
        onSurfaceVariant = TextSecondary,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surf,
        surfaceContainer = surfHigh,
        surfaceContainerHigh = surfHighest,
        surfaceContainerHighest = surfHighest,
        surfaceTint = accent,
        outline = Color(0xFF41414A),
        outlineVariant = Color(0xFF2A2A31),
        error = Color(0xFFFF5449),
        onError = Color.White,
    )

    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
