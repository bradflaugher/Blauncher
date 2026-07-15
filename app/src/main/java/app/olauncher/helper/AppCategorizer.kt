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
    )

    private val semanticTerms = linkedMapOf(
        AppCategory.AI_AGENTS to setOf(
            "ai assistant", "chatbot", "chatgpt", "claude", "copilot", "gemini", "grok",
            "mistral", "perplexity", "poe"
        ),
        AppCategory.COMMUNICATION to setOf(
            "call", "camera", "chat", "class", "community", "companion", "contact", "dialer",
            "discord", "email", "family", "gmail", "home", "inbox", "mail", "meet", "message",
            "outlook", "parent", "people", "phone", "photo", "portrait", "school", "signal",
            "slack", "social", "teams", "telegram", "whatsapp", "zoom"
        ),
        AppCategory.PRODUCTIVITY to setOf(
            "calendar", "credential", "docs", "drive", "github", "keep", "notes", "notion",
            "office", "password", "print", "recorder", "scan", "sheets", "slides", "task",
            "terminal", "todo", "trello", "vpn", "work", "writer"
        ),
        AppCategory.FINANCE to setOf(
            "account", "bank", "banking", "budget", "card", "cash", "credit", "finance",
            "insurance", "invest", "money", "mortgage", "pay", "payroll", "retirement", "stock",
            "tax", "wallet"
        ),
        AppCategory.HEALTH to setOf(
            "club", "exercise", "fit", "fitness", "gym", "health", "meditate", "pilates", "run",
            "sleep", "strava", "tennis", "workout", "wellness"
        ),
        AppCategory.TRAVEL to setOf(
            "airline", "ballpark", "bicycle", "bike", "car", "cinema", "dining", "flight",
            "hotel", "map", "parking", "ranch", "reservation", "resort", "restaurant", "rideshare",
            "spa", "theater", "theatre", "ticket", "transit", "travel", "uber"
        ),
        AppCategory.GAMES to setOf(
            "arcade", "game", "gaming", "play store", "steam"
        ),
        AppCategory.SHOPPING to setOf(
            "amazon", "cafe", "coffee", "delivery", "doordash", "ebay", "food", "grocery",
            "instacart", "meal", "pizza", "retail", "shop", "store", "ubereats"
        ),
        AppCategory.MEDIA to setOf(
            "audible", "audio", "audiobook", "book", "classical", "economist", "gallery", "kindle",
            "libby", "movie", "music", "news", "now playing", "pocket", "podcast", "radio",
            "reader", "sonos", "spotify", "stream", "tv", "video", "wsj", "youtube"
        ),
        AppCategory.TOOLS to setOf(
            "authenticator", "browser", "calculator", "clock", "file", "keyboard", "launcher",
            "settings", "translate", "utility", "weather"
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
        return categoryFromEvidence(packageName, label, declaredCategory)
    }

    private fun categoryFromEvidence(
        packageName: String,
        label: String,
        declaredCategory: Int,
    ): AppCategory {
        val scores = mutableMapOf<AppCategory, Int>()
        androidCategory(declaredCategory)?.let { scores[it] = 1 }
        val normalized = normalizeText("$packageName $label")
        semanticTerms.forEach { (category, terms) ->
            val semanticScore = terms.sumOf { term ->
                if (containsTerm(normalized, term)) 2 + term.count { it == ' ' } else 0
            }
            if (semanticScore > 0) scores[category] = scores.getOrDefault(category, 0) + semanticScore
        }
        return scores.maxByOrNull { it.value }?.key ?: AppCategory.OTHER
    }

    private fun androidCategory(declaredCategory: Int): AppCategory? =
        when (declaredCategory) {
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE,
            ApplicationInfo.CATEGORY_NEWS -> AppCategory.MEDIA
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.COMMUNICATION
            ApplicationInfo.CATEGORY_MAPS -> AppCategory.TRAVEL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
            else -> null
        }

    fun sortForNow(prefs: Prefs, apps: MutableList<AppModel>) {
        val routine = currentRoutine(prefs)
        val categoryOrder = categoryOrder(routine, prefs.pinnedCategory)
            .withIndex().associate { it.value to it.index }
        apps.sortWith(
            compareBy<AppModel> { categoryOrder[it.category] ?: Int.MAX_VALUE }
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
        packageCategory(text)?.let { return it }
        return categoryFromEvidence(text.substringBefore(' '), text.substringAfter(' ', ""), -1)
    }

    private fun packageCategory(packageName: String): AppCategory? =
        packageCategories.entries.firstOrNull { packageName.lowercase().contains(it.key) }?.value

    private fun normalizeText(text: String): String = text
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun containsTerm(normalizedText: String, term: String): Boolean =
        " $normalizedText ".let { text ->
            text.contains(" $term ") || (!term.contains(' ') && text.contains(" ${term}s "))
        }

    private fun currentMinuteOfDay(): Int = Calendar.getInstance().run {
        get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
    }
}
