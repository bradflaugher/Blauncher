package app.olauncher.helper

import android.graphics.Typeface

/** The two weights the launcher draws names in: light everywhere, medium for what stands out. */
object Typefaces {
    val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun forWeight(bold: Boolean): Typeface = if (bold) MEDIUM else LIGHT
}
