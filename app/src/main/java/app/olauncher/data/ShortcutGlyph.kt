package app.olauncher.data

import androidx.annotation.DrawableRes
import app.olauncher.R

/**
 * The glyphs the shortcut button beside the search bar can wear. The key suits the password
 * manager it binds to on first run; any other app can pick whatever reads best for it. All are
 * the same line-art style, tinted to the theme, and the group glyphs from the drawer are offered
 * too. Stored by name, so reordering or adding entries never changes anyone's choice.
 */
enum class ShortcutGlyph(@DrawableRes val icon: Int, val label: String) {
    KEY(R.drawable.ic_key, "Key"),
    LOCK(R.drawable.ic_glyph_lock, "Lock"),
    SHIELD(R.drawable.ic_glyph_shield, "Shield"),
    CARD(R.drawable.ic_glyph_card, "Card"),
    MONEY(R.drawable.ic_category_money, "Money"),
    CAMERA(R.drawable.ic_glyph_camera, "Camera"),
    PHONE(R.drawable.ic_glyph_phone, "Phone"),
    CHAT(R.drawable.ic_glyph_chat, "Chat"),
    PEOPLE(R.drawable.ic_category_people, "People"),
    MAIL(R.drawable.ic_glyph_mail, "Mail"),
    MUSIC(R.drawable.ic_glyph_music, "Music"),
    MEDIA(R.drawable.ic_category_media, "Media"),
    MIC(R.drawable.ic_glyph_mic, "Microphone"),
    PIN(R.drawable.ic_glyph_pin, "Pin"),
    PLACES(R.drawable.ic_category_places, "Places"),
    HOUSE(R.drawable.ic_glyph_house, "House"),
    GLOBE(R.drawable.ic_glyph_globe, "Globe"),
    NEWS(R.drawable.ic_category_news, "News"),
    BOOK(R.drawable.ic_glyph_book, "Book"),
    PENCIL(R.drawable.ic_glyph_pencil, "Pencil"),
    FOCUS(R.drawable.ic_category_focus, "Focus"),
    CLOCK(R.drawable.ic_glyph_clock, "Clock"),
    SLEEP(R.drawable.ic_category_sleep, "Sleep"),
    SPARKLE(R.drawable.ic_glyph_sparkle, "Sparkle"),
    AI(R.drawable.ic_category_ai, "AI"),
    TERMINAL(R.drawable.ic_glyph_terminal, "Terminal"),
    TOOLS(R.drawable.ic_category_tools, "Tools"),
    BOLT(R.drawable.ic_glyph_bolt, "Bolt"),
    STAR(R.drawable.ic_glyph_star, "Star"),
    HEART(R.drawable.ic_glyph_heart, "Heart"),
    HEALTH(R.drawable.ic_category_health, "Health"),
    GAMES(R.drawable.ic_category_games, "Games"),
    SHOPPING(R.drawable.ic_category_shopping, "Shopping");

    companion object {
        fun fromName(name: String?): ShortcutGlyph = entries.firstOrNull { it.name == name } ?: KEY
    }
}
