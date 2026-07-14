package app.olauncher.helper

import android.content.Context
import android.content.pm.ApplicationInfo
import app.olauncher.data.AppCategory
import app.olauncher.data.AppModel
import app.olauncher.data.Prefs
import java.util.Calendar

object AppCategorizer {
    private val keywordCategories = linkedMapOf(
        AppCategory.COMMUNICATION to setOf(
            "chat", "discord", "email", "gmail", "mail", "meet", "message", "phone",
            "signal", "slack", "social", "teams", "telegram", "whatsapp", "zoom"
        ),
        AppCategory.PRODUCTIVITY to setOf(
            "calendar", "docs", "drive", "keep", "notes", "notion", "office", "sheets",
            "task", "todo", "work", "writer"
        ),
        AppCategory.FINANCE to setOf(
            "bank", "budget", "cash", "finance", "invest", "paypal", "wallet"
        ),
        AppCategory.HEALTH to setOf(
            "exercise", "fit", "fitness", "health", "meditate", "run", "sleep", "workout"
        ),
        AppCategory.TRAVEL to setOf(
            "airline", "flight", "hotel", "maps", "transit", "travel", "uber"
        ),
        AppCategory.SHOPPING to setOf(
            "amazon", "delivery", "ebay", "food", "shop", "store"
        ),
        AppCategory.MEDIA to setOf(
            "book", "camera", "gallery", "kindle", "movie", "music", "news", "photo",
            "podcast", "spotify", "video", "youtube"
        ),
        AppCategory.GAMES to setOf("game", "games", "playgames"),
        AppCategory.TOOLS to setOf(
            "authenticator", "calculator", "clock", "files", "keyboard", "launcher",
            "settings", "terminal", "tools", "vpn", "weather"
        ),
    )

    fun categorize(
        context: Context,
        prefs: Prefs,
        packageName: String,
        label: String,
        declaredCategory: Int? = null,
    ): AppCategory {
        prefs.getAppCategory(packageName)?.let { return it }

        val androidCategory = declaredCategory ?: try {
            context.packageManager.getApplicationInfo(packageName, 0).category
        } catch (_: Exception) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
        val category = when (androidCategory) {
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE,
            ApplicationInfo.CATEGORY_NEWS -> AppCategory.MEDIA
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.COMMUNICATION
            ApplicationInfo.CATEGORY_MAPS -> AppCategory.TRAVEL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
            else -> categoryFromText("$packageName $label")
        }
        prefs.setAppCategory(packageName, category)
        return category
    }

    fun sortForNow(apps: MutableList<AppModel>) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val categoryOrder = categoryOrder(hour).withIndex().associate { it.value to it.index }
        apps.sortWith(
            compareBy<AppModel> { categoryOrder[it.category] ?: Int.MAX_VALUE }
                .thenByDescending { routineScore(it, hour) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appLabel }
        )
    }

    fun sortByCategory(apps: MutableList<AppModel>) {
        val order = categoryOrder(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
            .withIndex().associate { it.value to it.index }
        apps.sortWith(
            compareBy<AppModel> { order[it.category] ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appLabel }
        )
    }

    fun categoryOrder(hour: Int): List<AppCategory> = (when (hour) {
        in 5..6 -> listOf(
            AppCategory.MEDIA, AppCategory.PRODUCTIVITY, AppCategory.HEALTH,
            AppCategory.COMMUNICATION, AppCategory.TOOLS
        )
        in 7..8 -> listOf(
            AppCategory.TRAVEL, AppCategory.MEDIA, AppCategory.COMMUNICATION,
            AppCategory.PRODUCTIVITY, AppCategory.TOOLS
        )
        in 9..11, in 14..16 -> listOf(
            AppCategory.PRODUCTIVITY, AppCategory.COMMUNICATION, AppCategory.FINANCE,
            AppCategory.TOOLS, AppCategory.TRAVEL
        )
        in 12..13 -> listOf(
            AppCategory.HEALTH, AppCategory.TRAVEL, AppCategory.COMMUNICATION,
            AppCategory.MEDIA, AppCategory.PRODUCTIVITY
        )
        in 17..19 -> listOf(
            AppCategory.COMMUNICATION, AppCategory.MEDIA, AppCategory.SHOPPING,
            AppCategory.TRAVEL, AppCategory.HEALTH
        )
        else -> listOf(
            AppCategory.MEDIA, AppCategory.COMMUNICATION, AppCategory.HEALTH,
            AppCategory.TOOLS, AppCategory.PRODUCTIVITY
        )
    } + AppCategory.entries).distinct()

    private fun categoryFromText(text: String): AppCategory {
        val normalized = text.lowercase()
        return keywordCategories.entries.firstOrNull { (_, keywords) ->
            keywords.any { normalized.contains(it) }
        }?.key ?: AppCategory.OTHER
    }

    private fun routineScore(app: AppModel, hour: Int): Int {
        val text = "${app.appPackage} ${app.appLabel}".lowercase()
        val priorities = when (hour) {
            in 5..6 -> listOf("kindle", "book", "read", "news", "reader", "pocket")
            in 7..8 -> listOf(
                "maps", "transit", "train", "uber", "audiobook", "audible", "podcast", "music"
            )
            in 9..11, in 14..16 -> listOf(
                "calendar", "mail", "slack", "teams", "docs", "drive", "notes", "task", "work"
            )
            in 12..13 -> listOf(
                "tennis", "court", "fitness", "fit", "workout", "health", "maps", "calendar"
            )
            in 17..19 -> listOf(
                "family", "message", "phone", "camera", "photo", "calendar", "food", "delivery"
            )
            else -> listOf(
                "audiobook", "audible", "kindle", "book", "read", "reader", "pocket", "podcast"
            )
        }
        val match = priorities.indexOfFirst { text.contains(it) }
        return if (match == -1) 0 else priorities.size - match
    }
}
