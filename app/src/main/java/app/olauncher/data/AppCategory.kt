package app.olauncher.data

enum class AppCategory(val displayName: String, val symbol: String, val color: Int) {
    AI_AGENTS("AI Agents", "✦", 0xFFA78BFA.toInt()),
    COMMUNICATION("People", "↔", 0xFF569CD6.toInt()),
    PRODUCTIVITY("Focus", "✓", 0xFF4EC9B0.toInt()),
    MEDIA("Media", "▷", 0xFFC586C0.toInt()),
    GAMES("Games", "◆", 0xFFF48771.toInt()),
    FINANCE("Money", "$", 0xFFB5CEA8.toInt()),
    SHOPPING("Shopping", "◇", 0xFFDCB45F.toInt()),
    TRAVEL("Places", "↑", 0xFF4FC1FF.toInt()),
    HEALTH("Health", "+", 0xFFCE9178.toInt()),
    TOOLS("Tools", "⚙", 0xFF9CDCFE.toInt()),
    OTHER("Other", "·", 0xFF969696.toInt()),
}
