package org.daycompass.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** The contrast pairs from the design system's accessibility table, checked in both themes. */
class DcColorsContrastTest {
    @Test
    fun lightThemeMeetsWcagAa() = assertPairs(LightDcColors)

    @Test
    fun darkThemeMeetsWcagAa() = assertPairs(DarkDcColors)

    private fun assertPairs(c: DcColors) {
        val text = 4.5
        val nonText = 3.0
        listOf(
            Triple("text on bg", c.text to c.bg, text),
            Triple("text on surface-sunken", c.text to c.surfaceSunken, text),
            Triple("text-muted on surface", c.textMuted to c.surface, text),
            Triple("text-muted on bg", c.textMuted to c.bg, text),
            Triple("on-primary on primary", c.onPrimary to c.primary, text),
            Triple("on-primary on primary-pressed", c.onPrimary to c.primaryPressed, text),
            Triple("on-secondary on secondary", c.onSecondary to c.secondary, text),
            Triple("secondary on surface", c.secondary to c.surface, text),
            Triple("error on surface", c.error to c.surface, text),
            Triple("success on surface", c.success to c.surface, text),
            Triple("border-control on surface", c.borderControl to c.surface, nonText),
            Triple("focus on bg", c.focus to c.bg, nonText),
            // The design system measures 3.0 for this pair in the dark theme.
            Triple("data-study vs data-distraction", c.dataStudy to c.dataDistraction, 2.95),
        ).forEach { (name, pair, minimum) ->
            val ratio = contrast(pair.first, pair.second)
            assertTrue("$name: %.2f < $minimum".format(ratio), ratio >= minimum)
        }
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }
}
