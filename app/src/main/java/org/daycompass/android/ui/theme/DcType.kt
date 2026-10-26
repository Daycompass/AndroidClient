package org.daycompass.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.daycompass.android.R

// Both fonts are variable TTFs bundled from google/fonts (OFL, see assets/licenses). Each
// weight the type scale uses gets its own entry pinned to that point on the wght axis.

private fun variableFont(
    resId: Int,
    weight: FontWeight,
) = Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Display, headings and numbers. */
val Fredoka =
    FontFamily(listOf(FontWeight.SemiBold, FontWeight.Bold).map { variableFont(R.font.fredoka, it) })

/** All UI text. */
val Nunito =
    FontFamily(
        listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
            .map { variableFont(R.font.nunito, it) },
    )

/** Numbers that update live use tabular figures. */
private const val TABULAR_FIGURES = "tnum"

/** The design system's type scale. Body text never goes below [body] on a phone. */
@Immutable
data class DcTypography(
    val display: TextStyle =
        TextStyle(
            fontFamily = Fredoka,
            fontSize = 40.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.01).em,
        ),
    val heading1: TextStyle =
        TextStyle(fontFamily = Fredoka, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    val heading2: TextStyle =
        TextStyle(fontFamily = Fredoka, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    val numberXl: TextStyle =
        TextStyle(
            fontFamily = Fredoka,
            fontSize = 44.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = TABULAR_FIGURES,
        ),
    val numberLg: TextStyle =
        TextStyle(
            fontFamily = Fredoka,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = TABULAR_FIGURES,
        ),
    val heading3: TextStyle =
        TextStyle(fontFamily = Nunito, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold),
    val bodyLg: TextStyle =
        TextStyle(fontFamily = Nunito, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    val body: TextStyle =
        TextStyle(fontFamily = Nunito, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    val label: TextStyle =
        TextStyle(fontFamily = Nunito, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    val button: TextStyle =
        TextStyle(fontFamily = Nunito, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold),
    val caption: TextStyle =
        TextStyle(fontFamily = Nunito, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    /** Uppercase comes from the text itself, max two words. */
    val overline: TextStyle =
        TextStyle(
            fontFamily = Nunito,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.06.em,
        ),
)
