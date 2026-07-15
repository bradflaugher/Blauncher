package app.olauncher.data

import androidx.annotation.DrawableRes
import app.olauncher.R

enum class AppCategory(val displayName: String, @DrawableRes val iconRes: Int, val color: Int) {
    AI_AGENTS("AI Agents", R.drawable.ic_category_ai, 0xFFA78BFA.toInt()),
    COMMUNICATION("People", R.drawable.ic_category_people, 0xFF569CD6.toInt()),
    PRODUCTIVITY("Focus", R.drawable.ic_category_focus, 0xFF4EC9B0.toInt()),
    MEDIA("Media", R.drawable.ic_category_media, 0xFFC586C0.toInt()),
    GAMES("Games", R.drawable.ic_category_games, 0xFFF48771.toInt()),
    FINANCE("Money", R.drawable.ic_category_money, 0xFFB5CEA8.toInt()),
    SHOPPING("Shopping", R.drawable.ic_category_shopping, 0xFFDCB45F.toInt()),
    TRAVEL("Places", R.drawable.ic_category_places, 0xFF4FC1FF.toInt()),
    HEALTH("Health", R.drawable.ic_category_health, 0xFFCE9178.toInt()),
    TOOLS("Tools", R.drawable.ic_category_tools, 0xFF9CDCFE.toInt()),
    OTHER("Other", R.drawable.ic_category_other, 0xFF969696.toInt()),
}
