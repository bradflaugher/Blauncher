package app.olauncher.helper

import android.content.Context
import android.content.pm.ApplicationInfo
import app.olauncher.data.AppCategory
import app.olauncher.data.Prefs

object AppCategorizer {
    private val packageCategories = linkedMapOf(
        "com.openai.chatgpt" to AppCategory.AI_AGENTS,
        "com.anthropic.claude" to AppCategory.AI_AGENTS,
        "com.google.android.apps.bard" to AppCategory.AI_AGENTS,
        "ai.perplexity.app.android" to AppCategory.AI_AGENTS,
        "com.microsoft.copilot" to AppCategory.AI_AGENTS,
        "com.nytimes.android" to AppCategory.NEWS,
        "wsj.reader_sp" to AppCategory.NEWS,
        "com.economist.lamarr" to AppCategory.NEWS,
        "com.google.android.apps.magazines" to AppCategory.NEWS,
        "flipboard.app" to AppCategory.NEWS,
        "com.devhd.feedly" to AppCategory.NEWS,
        "com.bbc.news" to AppCategory.NEWS,
        "com.cnn.mobile.android.phone" to AppCategory.NEWS,
        "com.npr.nprnews" to AppCategory.NEWS,
        "com.groundnews" to AppCategory.NEWS,
        "com.medium.reader" to AppCategory.NEWS,
        "com.reddit.frontpage" to AppCategory.NEWS,
        "com.spotify.music" to AppCategory.MEDIA,
        "com.google.android.youtube" to AppCategory.MEDIA,
        "com.netflix.mediaclient" to AppCategory.MEDIA,
        "com.amazon.kindle" to AppCategory.MEDIA,
        "com.audible.application" to AppCategory.MEDIA,
        "com.overdrive.mobile.android.libby" to AppCategory.MEDIA,
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
        AppCategory.NEWS to setOf(
            "ap news", "article", "bbc", "breaking news", "cnn", "daily news", "economist",
            "feedly", "flipboard", "fox news", "ground news", "guardian", "headline", "magazine",
            "medium", "news", "newspaper", "npr", "nyt", "new york times", "pocket", "press",
            "reuters", "rss", "substack", "wsj", "wall street journal"
        ),
        AppCategory.MEDIA to setOf(
            "audible", "audio", "audiobook", "book", "classical", "gallery", "kindle",
            "libby", "movie", "music", "netflix", "now playing", "podcast", "radio",
            "reader", "sonos", "spotify", "stream", "tv", "video", "youtube"
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
    ): AppCategory = categories(context, prefs, packageName, label, declaredCategory).first()

    /** All groups this app should appear under (manual multi-select or a single automatic group). */
    fun categories(
        context: Context,
        prefs: Prefs,
        packageName: String,
        label: String,
        declaredCategory: Int? = null,
    ): List<AppCategory> {
        val androidCategory = declaredCategory ?: try {
            context.packageManager.getApplicationInfo(packageName, 0).category
        } catch (_: Exception) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
        return resolveCategories(
            prefs.getAppCategoryOverrides(packageName),
            packageName,
            label,
            androidCategory,
        )
    }

    internal fun resolveCategories(
        overrides: List<AppCategory>?,
        packageName: String,
        label: String,
        declaredCategory: Int,
    ): List<AppCategory> {
        if (!overrides.isNullOrEmpty()) return overrides.distinct()
        return listOf(resolveCategory(null, packageName, label, declaredCategory))
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
        val normalizedPackage = normalizeText(packageName)
        val normalizedLabel = normalizeText(label)
        semanticTerms.forEach { (category, terms) ->
            val semanticScore = terms.sumOf { term ->
                maxOf(
                    if (containsTerm(normalizedLabel, term)) 3 + term.count { it == ' ' } else 0,
                    if (containsTerm(normalizedPackage, term)) 2 + term.count { it == ' ' } else 0,
                )
            }
            if (semanticScore > 0) scores[category] = scores.getOrDefault(category, 0) + semanticScore
        }
        return scores.maxByOrNull { it.value }?.key
            ?: androidCategory(declaredCategory)
            ?: AppCategory.OTHER
    }

    private fun androidCategory(declaredCategory: Int): AppCategory? =
        when (declaredCategory) {
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE -> AppCategory.MEDIA
            ApplicationInfo.CATEGORY_NEWS -> AppCategory.NEWS
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.COMMUNICATION
            ApplicationInfo.CATEGORY_MAPS -> AppCategory.TRAVEL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
            else -> null
        }

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
}
