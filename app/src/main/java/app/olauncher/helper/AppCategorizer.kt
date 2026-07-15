package app.olauncher.helper

import android.content.Context
import android.content.pm.ApplicationInfo
import app.olauncher.data.AppCategory
import app.olauncher.data.AppModel
import app.olauncher.data.AppRoutine
import app.olauncher.data.Prefs
import java.util.Calendar

object AppCategorizer {
    private val packageCategories = linkedMapOf(
        "com.openai.chatgpt" to AppCategory.AI_AGENTS,
        "com.anthropic.claude" to AppCategory.AI_AGENTS,
        "com.google.android.apps.bard" to AppCategory.AI_AGENTS,
        "ai.perplexity.app.android" to AppCategory.AI_AGENTS,
        "com.microsoft.copilot" to AppCategory.AI_AGENTS,
        "com.amazon.kindle" to AppCategory.MEDIA,
        "com.audible" to AppCategory.MEDIA,
        "libby" to AppCategory.MEDIA,
        "com.google.android.apps.maps" to AppCategory.TRAVEL,
        "com.google.android.gm" to AppCategory.COMMUNICATION,
        "com.google.android.apps.photos" to AppCategory.MEDIA,
        "com.dd.doordash" to AppCategory.SHOPPING,
    )

    private val keywordCategories = linkedMapOf(
        AppCategory.AI_AGENTS to setOf(
            "chatgpt", "claude", "copilot", "gemini", "grok", "mistral", "perplexity", "poe"
        ),
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
            "exercise", "fit", "fitness", "health", "meditate", "run", "sleep", "strava",
            "tennis", "workout"
        ),
        AppCategory.TRAVEL to setOf(
            "airline", "flight", "hotel", "maps", "transit", "travel", "uber"
        ),
        AppCategory.SHOPPING to setOf(
            "amazon", "delivery", "doordash", "ebay", "food", "instacart", "shop", "store",
            "ubereats"
        ),
        AppCategory.MEDIA to setOf(
            "audible", "book", "camera", "gallery", "kindle", "libby", "movie", "music",
            "news", "photo", "pocket", "podcast", "reader", "spotify", "video", "youtube"
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
        val androidCategory = declaredCategory ?: try {
            context.packageManager.getApplicationInfo(packageName, 0).category
        } catch (_: Exception) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
        return resolveCategory(
            prefs.getAppCategoryOverride(packageName),
            packageName,
            label,
            androidCategory,
        )
    }

    internal fun resolveCategory(
        override: AppCategory?,
        packageName: String,
        label: String,
        declaredCategory: Int,
    ): AppCategory {
        override?.let { return it }
        packageCategory(packageName)?.let { return it }
        aiCategoryFromText("$packageName $label")?.let { return it }
        return when (declaredCategory) {
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
    }

    fun sortForNow(prefs: Prefs, apps: MutableList<AppModel>) {
        val routine = currentRoutine(prefs)
        val categoryOrder = categoryOrder(routine, prefs.pinnedCategory)
            .withIndex().associate { it.value to it.index }
        apps.sortWith(
            compareBy<AppModel> { categoryOrder[it.category] ?: Int.MAX_VALUE }
                .thenByDescending { routineScore(it, routine) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appLabel }
        )
    }

    fun sortByCategory(prefs: Prefs, apps: MutableList<AppModel>) {
        val order = categoryOrder(currentRoutine(prefs), prefs.pinnedCategory)
            .withIndex().associate { it.value to it.index }
        apps.sortWith(
            compareBy<AppModel> { order[it.category] ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appLabel }
        )
    }

    fun currentRoutine(
        prefs: Prefs,
        minuteOfDay: Int = currentMinuteOfDay(),
        dayOfWeek: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK),
    ): AppRoutine {
        val weekdayRoutine = resolveRoutine(
            minuteOfDay,
            prefs.routineReadingStart,
            prefs.routineCommuteStart,
            prefs.routineWorkStart,
            prefs.routineFitnessStart,
            prefs.routineFamilyStart,
            prefs.routineEveningStart,
        )
        return resolveMode(prefs.vacationMode, dayOfWeek, weekdayRoutine)
    }

    internal fun resolveMode(
        vacationMode: Boolean,
        dayOfWeek: Int,
        weekdayRoutine: AppRoutine,
    ): AppRoutine = when {
        vacationMode -> AppRoutine.VACATION
        dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY -> AppRoutine.WEEKEND
        else -> weekdayRoutine
    }

    internal fun resolveRoutine(
        minuteOfDay: Int,
        readingStart: Int,
        commuteStart: Int,
        workStart: Int,
        fitnessStart: Int,
        familyStart: Int,
        eveningStart: Int,
    ): AppRoutine {
        val starts = listOf(
            readingStart to AppRoutine.READING,
            commuteStart to AppRoutine.COMMUTE,
            workStart to AppRoutine.WORK,
            fitnessStart to AppRoutine.FITNESS,
            familyStart to AppRoutine.FAMILY,
            eveningStart to AppRoutine.EVENING,
        ).sortedBy { it.first }
        val active = starts.lastOrNull { it.first <= minuteOfDay }?.second ?: starts.last().second
        if (active != AppRoutine.FITNESS) return active
        val fitnessElapsed = (minuteOfDay - fitnessStart + 1440) % 1440
        return if (fitnessElapsed < 120) AppRoutine.FITNESS else AppRoutine.WORK
    }

    fun categoryOrder(
        routine: AppRoutine,
        pinnedCategory: AppCategory? = null,
    ): List<AppCategory> = (listOfNotNull(pinnedCategory) + when (routine) {
        AppRoutine.READING -> listOf(
            AppCategory.MEDIA, AppCategory.PRODUCTIVITY, AppCategory.HEALTH,
            AppCategory.COMMUNICATION, AppCategory.TOOLS
        )
        AppRoutine.COMMUTE -> listOf(
            AppCategory.TRAVEL, AppCategory.MEDIA, AppCategory.COMMUNICATION,
            AppCategory.PRODUCTIVITY, AppCategory.TOOLS
        )
        AppRoutine.WORK -> listOf(
            AppCategory.PRODUCTIVITY, AppCategory.COMMUNICATION, AppCategory.FINANCE,
            AppCategory.TOOLS, AppCategory.TRAVEL
        )
        AppRoutine.FITNESS -> listOf(
            AppCategory.HEALTH, AppCategory.TRAVEL, AppCategory.COMMUNICATION,
            AppCategory.MEDIA, AppCategory.PRODUCTIVITY
        )
        AppRoutine.FAMILY -> listOf(
            AppCategory.COMMUNICATION, AppCategory.MEDIA, AppCategory.SHOPPING,
            AppCategory.TRAVEL, AppCategory.HEALTH
        )
        AppRoutine.EVENING -> listOf(
            AppCategory.MEDIA, AppCategory.COMMUNICATION, AppCategory.HEALTH,
            AppCategory.TOOLS, AppCategory.PRODUCTIVITY
        )
        AppRoutine.WEEKEND -> listOf(
            AppCategory.HEALTH, AppCategory.COMMUNICATION, AppCategory.MEDIA,
            AppCategory.SHOPPING, AppCategory.TRAVEL
        )
        AppRoutine.VACATION -> listOf(
            AppCategory.TRAVEL, AppCategory.MEDIA, AppCategory.COMMUNICATION,
            AppCategory.HEALTH, AppCategory.SHOPPING
        )
    } + AppCategory.entries).distinct()

    internal fun categoryFromText(text: String): AppCategory {
        val normalized = text.lowercase()
        packageCategory(normalized)?.let { return it }
        return keywordCategories.entries.firstOrNull { (_, keywords) ->
            keywords.any { normalized.contains(it) }
        }?.key ?: AppCategory.OTHER
    }

    private fun packageCategory(packageName: String): AppCategory? =
        packageCategories.entries.firstOrNull { packageName.lowercase().contains(it.key) }?.value

    private fun aiCategoryFromText(text: String): AppCategory? =
        AppCategory.AI_AGENTS.takeIf { category ->
            keywordCategories[category].orEmpty().any { text.contains(it, ignoreCase = true) }
        }

    private fun routineScore(app: AppModel, routine: AppRoutine): Int =
        routineScore(app.appPackage, app.appLabel, routine)

    internal fun routineScore(packageName: String, label: String, routine: AppRoutine): Int {
        val text = "$packageName $label".lowercase()
        val priorities = when (routine) {
            AppRoutine.READING -> listOf("kindle", "book", "read", "news", "reader", "pocket")
            AppRoutine.COMMUTE -> listOf(
                "maps", "transit", "train", "uber", "audiobook", "audible", "podcast", "music"
            )
            AppRoutine.WORK -> listOf(
                "calendar", "mail", "slack", "teams", "notion", "docs", "drive", "notes", "task", "work"
            )
            AppRoutine.FITNESS -> listOf(
                "tennis", "court", "fitness", "fit", "workout", "health", "maps", "calendar"
            )
            AppRoutine.FAMILY -> listOf(
                "family", "message", "phone", "camera", "photo", "calendar", "food", "delivery"
            )
            AppRoutine.EVENING -> listOf(
                "audiobook", "audible", "kindle", "book", "read", "reader", "pocket", "podcast"
            )
            AppRoutine.WEEKEND -> listOf(
                "tennis", "family", "camera", "photo", "maps", "food", "delivery", "book", "podcast"
            )
            AppRoutine.VACATION -> listOf(
                "maps", "flight", "airline", "hotel", "translate", "camera", "photo", "audiobook", "book"
            )
        }
        val match = priorities.indexOfFirst { text.contains(it) }
        return if (match == -1) 0 else priorities.size - match
    }

    private fun currentMinuteOfDay(): Int = Calendar.getInstance().run {
        get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
    }
}
