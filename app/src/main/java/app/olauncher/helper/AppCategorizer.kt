package app.olauncher.helper

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import app.olauncher.data.AppCategory
import app.olauncher.data.AppModel
import app.olauncher.data.Prefs
import java.util.Calendar
import kotlin.math.ln

object AppCategorizer {
    private const val HISTORY_DAYS = 21L
    private const val TIME_BUCKET_HOURS = 4

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

    fun sortForNow(context: Context, apps: MutableList<AppModel>) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val categoryOrder = categoryOrder(hour).withIndex().associate { it.value to it.index }
        val launches = launchesInCurrentTimeBucket(context, hour)
        apps.sortWith(
            compareBy<AppModel> { categoryOrder[it.category] ?: Int.MAX_VALUE }
                .thenByDescending { contextualScore(it, hour, launches) }
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
        in 5..8 -> listOf(
            AppCategory.HEALTH, AppCategory.PRODUCTIVITY, AppCategory.TRAVEL,
            AppCategory.COMMUNICATION, AppCategory.MEDIA
        )
        in 9..16 -> listOf(
            AppCategory.PRODUCTIVITY, AppCategory.COMMUNICATION, AppCategory.FINANCE,
            AppCategory.TOOLS, AppCategory.TRAVEL
        )
        in 17..21 -> listOf(
            AppCategory.COMMUNICATION, AppCategory.MEDIA, AppCategory.SHOPPING,
            AppCategory.HEALTH, AppCategory.GAMES
        )
        else -> listOf(
            AppCategory.MEDIA, AppCategory.COMMUNICATION, AppCategory.GAMES,
            AppCategory.TOOLS, AppCategory.PRODUCTIVITY
        )
    } + AppCategory.entries).distinct()

    private fun categoryFromText(text: String): AppCategory {
        val normalized = text.lowercase()
        return keywordCategories.entries.firstOrNull { (_, keywords) ->
            keywords.any { normalized.contains(it) }
        }?.key ?: AppCategory.OTHER
    }

    private fun contextualScore(
        app: AppModel,
        hour: Int,
        launches: Map<String, Int>,
    ): Double {
        val learnedScore = ln((launches[app.appPackage] ?: 0) + 1.0) * 20.0
        return learnedScore
    }

    private fun launchesInCurrentTimeBucket(context: Context, currentHour: Int): Map<String, Int> {
        if (!context.appUsagePermissionGranted()) return emptyMap()
        return try {
            val now = System.currentTimeMillis()
            val events = context.getSystemService(UsageStatsManager::class.java)
                .queryEvents(now - HISTORY_DAYS * 24 * 60 * 60 * 1000, now)
            val event = UsageEvents.Event()
            val counts = mutableMapOf<String, Int>()
            val calendar = Calendar.getInstance()
            val currentBucket = currentHour / TIME_BUCKET_HOURS
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
                calendar.timeInMillis = event.timeStamp
                if (calendar.get(Calendar.HOUR_OF_DAY) / TIME_BUCKET_HOURS == currentBucket) {
                    counts[event.packageName] = (counts[event.packageName] ?: 0) + 1
                }
            }
            counts
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
