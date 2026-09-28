package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class AppCategoryTest {

    /** WCAG relative luminance of an opaque ARGB color. */
    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((color shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrastOnWhite(color: Int): Double = 1.05 / (luminance(color) + 0.05)

    @Test
    fun lightThemeColorsMeetTextContrastOnWhite() {
        AppCategory.entries.forEach { category ->
            val ratio = contrastOnWhite(category.lightColor)
            assertTrue("${category.name} is only %.2f:1 on white".format(ratio), ratio >= 4.5)
        }
    }

    @Test
    fun colorForPicksTheThemeVariant() {
        AppCategory.entries.forEach { category ->
            assertEquals(category.color, category.colorFor(isDark = true))
            assertEquals(category.lightColor, category.colorFor(isDark = false))
        }
    }
}
