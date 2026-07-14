package app.olauncher.data

import android.graphics.Color

enum class AppCategory(val displayName: String, val color: Int) {
    COMMUNICATION("People", Color.rgb(86, 156, 214)),
    PRODUCTIVITY("Focus", Color.rgb(78, 201, 176)),
    MEDIA("Media", Color.rgb(197, 134, 192)),
    GAMES("Games", Color.rgb(244, 135, 113)),
    FINANCE("Money", Color.rgb(181, 206, 168)),
    SHOPPING("Shopping", Color.rgb(220, 180, 95)),
    TRAVEL("Places", Color.rgb(79, 193, 255)),
    HEALTH("Health", Color.rgb(206, 145, 120)),
    TOOLS("Tools", Color.rgb(156, 220, 254)),
    OTHER("Other", Color.rgb(150, 150, 150)),
}
