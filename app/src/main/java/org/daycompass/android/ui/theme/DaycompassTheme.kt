package org.daycompass.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalDcColors = staticCompositionLocalOf { LightDcColors }
private val LocalDcTypography = staticCompositionLocalOf { DcTypography() }

/** Access to the design system tokens inside [DaycompassTheme]. */
object Dc {
    val colors: DcColors
        @Composable get() = LocalDcColors.current

    val type: DcTypography
        @Composable get() = LocalDcTypography.current
}

/**
 * Material 3 stays the technical base (semantics, focus, text input), but its colors and type
 * come from the Daycompass tokens. Surface tint is the surface itself, so elevation never
 * tints anything.
 */
@Composable
fun DaycompassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkDcColors else LightDcColors
    val type = DcTypography()
    CompositionLocalProvider(LocalDcColors provides colors, LocalDcTypography provides type) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = type.toMaterialTypography(),
            content = content,
        )
    }
}

private fun DcColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primarySoft,
        onPrimaryContainer = text,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondarySoft,
        onSecondaryContainer = text,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceSunken,
        onSurfaceVariant = textMuted,
        surfaceTint = surface,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceSunken,
        outline = borderControl,
        outlineVariant = border,
        error = error,
        onError = surface,
        errorContainer = errorSoft,
        onErrorContainer = text,
    )
}

private fun DcTypography.toMaterialTypography() =
    Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = numberLg,
        headlineLarge = heading1,
        headlineMedium = heading1,
        headlineSmall = heading2,
        titleLarge = heading2,
        titleMedium = heading3,
        titleSmall = label,
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = caption,
        labelLarge = button,
        labelMedium = label,
        labelSmall = overline,
    )
