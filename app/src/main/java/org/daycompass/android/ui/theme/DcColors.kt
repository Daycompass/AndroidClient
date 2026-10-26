// Hex literals are the token values themselves, copied from the design system.
@file:Suppress("MagicNumber")

package org.daycompass.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Color tokens of the Daycompass design system. Both themes use the same names; see the
 * design system's tokens.json for the usage note of each one.
 */
@Immutable
data class DcColors(
    val bg: Color,
    val surface: Color,
    val surfaceSunken: Color,
    val text: Color,
    val textMuted: Color,
    val border: Color,
    val borderControl: Color,
    val primary: Color,
    val primaryPressed: Color,
    val primaryEdge: Color,
    val onPrimary: Color,
    val primarySoft: Color,
    val secondary: Color,
    val secondaryPressed: Color,
    val secondaryEdge: Color,
    val onSecondary: Color,
    val secondarySoft: Color,
    val neutralEdge: Color,
    val disabledBg: Color,
    val disabledText: Color,
    val focus: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val error: Color,
    val errorSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val dataStudy: Color,
    val dataDistraction: Color,
    val dataNeutral: Color,
    val isDark: Boolean,
)

// The hover tokens are left out: there is no pointer hover on a phone.

val LightDcColors =
    DcColors(
        bg = Color(0xFFFFF8EC),
        surface = Color(0xFFFFFFFF),
        surfaceSunken = Color(0xFFF7EFDF),
        text = Color(0xFF23202E),
        textMuted = Color(0xFF6B6577),
        border = Color(0xFFEFE7D8),
        borderControl = Color(0xFF8A8296),
        primary = Color(0xFFFFC21A),
        primaryPressed = Color(0xFFF2B200),
        primaryEdge = Color(0xFFD99A00),
        onPrimary = Color(0xFF23202E),
        primarySoft = Color(0xFFFFE7A6),
        secondary = Color(0xFFC2255C),
        secondaryPressed = Color(0xFFAD1F51),
        secondaryEdge = Color(0xFF8C1844),
        onSecondary = Color(0xFFFFFFFF),
        secondarySoft = Color(0xFFFCE4EE),
        neutralEdge = Color(0xFFE3DCCF),
        disabledBg = Color(0xFFEFE8DA),
        disabledText = Color(0xFFA39CAD),
        focus = Color(0xFF6741D9),
        success = Color(0xFF2B7A3B),
        successSoft = Color(0xFFEAF7EC),
        warning = Color(0xFFA35200),
        warningSoft = Color(0xFFFFF0DC),
        error = Color(0xFFC92A2A),
        errorSoft = Color(0xFFFFE5E5),
        info = Color(0xFF1C6DC2),
        infoSoft = Color(0xFFE2F0FD),
        dataStudy = Color(0xFFFFC21A),
        dataDistraction = Color(0xFFC2255C),
        dataNeutral = Color(0xFFB8B0C4),
        isDark = false,
    )

val DarkDcColors =
    DcColors(
        bg = Color(0xFF17151F),
        surface = Color(0xFF221F2D),
        surfaceSunken = Color(0xFF1C1A26),
        text = Color(0xFFF4F1F8),
        textMuted = Color(0xFFA9A3B6),
        border = Color(0xFF2F2B3B),
        borderControl = Color(0xFF7C758C),
        primary = Color(0xFFFFC93D),
        primaryPressed = Color(0xFFF2B620),
        primaryEdge = Color(0xFFB88600),
        onPrimary = Color(0xFF17151F),
        primarySoft = Color(0xFF3D3214),
        secondary = Color(0xFFF783AC),
        secondaryPressed = Color(0xFFF06595),
        secondaryEdge = Color(0xFFC2255C),
        onSecondary = Color(0xFF17151F),
        secondarySoft = Color(0xFF3A1A28),
        neutralEdge = Color(0xFF2F2B3B),
        disabledBg = Color(0xFF2A2735),
        disabledText = Color(0xFF615B6E),
        focus = Color(0xFFB197FC),
        success = Color(0xFF69DB7C),
        successSoft = Color(0xFF173322),
        warning = Color(0xFFFFA94D),
        warningSoft = Color(0xFF3A2814),
        error = Color(0xFFFF8787),
        errorSoft = Color(0xFF3D1A1C),
        info = Color(0xFF74C0FC),
        infoSoft = Color(0xFF14283D),
        dataStudy = Color(0xFFFFC93D),
        dataDistraction = Color(0xFFD6336C),
        dataNeutral = Color(0xFF4A4458),
        isDark = true,
    )
