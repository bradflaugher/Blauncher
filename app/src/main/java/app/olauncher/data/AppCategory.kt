package app.olauncher.data

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import app.olauncher.R

/**
 * [color] is the pastel tuned for the dark theme; [lightColor] is the same hue darkened to at
 * least 4.5:1 contrast on white for the light theme. Views pick one through [colorFor].
 */
enum class AppCategory(
    val displayName: String,
    @DrawableRes val iconRes: Int,
    @ColorInt val color: Int,
    @ColorInt val lightColor: Int,
) {
    AI_AGENTS("AI Agents", R.drawable.ic_category_ai, 0xFFA78BFA.toInt(), 0xFF7E55F8.toInt()),
    COMMUNICATION("People", R.drawable.ic_category_people, 0xFF569CD6.toInt(), 0xFF2D79B8.toInt()),
    PRODUCTIVITY("Focus", R.drawable.ic_category_focus, 0xFF4EC9B0.toInt(), 0xFF28826F.toInt()),
    NEWS("News", R.drawable.ic_category_news, 0xFFE8A87C.toInt(), 0xFFB55D20.toInt()),
    MEDIA("Media", R.drawable.ic_category_media, 0xFFC586C0.toInt(), 0xFFAC52A5.toInt()),
    GAMES("Games", R.drawable.ic_category_games, 0xFFF48771.toInt(), 0xFFDC3311.toInt()),
    FINANCE("Money", R.drawable.ic_category_money, 0xFFB5CEA8.toInt(), 0xFF5A7F47.toInt()),
    SHOPPING("Shopping", R.drawable.ic_category_shopping, 0xFFDCB45F.toInt(), 0xFF946F20.toInt()),
    TRAVEL("Places", R.drawable.ic_category_places, 0xFF4FC1FF.toInt(), 0xFF007ABD.toInt()),
    HEALTH("Health", R.drawable.ic_category_health, 0xFFCE9178.toInt(), 0xFFAE5F3F.toInt()),
    SLEEP("Sleep", R.drawable.ic_category_sleep, 0xFF8C9EFF.toInt(), 0xFF3D5AFE.toInt()),
    TOOLS("Tools", R.drawable.ic_category_tools, 0xFF9CDCFE.toInt(), 0xFF027ABA.toInt()),
    OTHER("Other", R.drawable.ic_category_other, 0xFF969696.toInt(), 0xFF757575.toInt());

    @ColorInt
    fun colorFor(isDark: Boolean): Int = if (isDark) color else lightColor

    /** The color for the theme [context] is drawn in. */
    @ColorInt
    fun colorFor(context: Context): Int = colorFor(
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    )
}
