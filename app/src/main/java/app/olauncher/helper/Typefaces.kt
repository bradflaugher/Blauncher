package app.olauncher.helper

import android.graphics.Typeface

/** The two weights the launcher draws app names in: light everywhere, medium for emphasis. */
object Typefaces {
    val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun forEmphasis(emphasized: Boolean): Typeface = if (emphasized) MEDIUM else LIGHT
}
