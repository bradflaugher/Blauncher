package app.olauncher.helper

import android.content.Context
import android.content.pm.ApplicationInfo
import app.olauncher.data.AppCategory
import app.olauncher.data.Constants
import app.olauncher.data.Prefs

object AppCategorizer {
    /**
     * Known apps by package, for the ones whose names say little ("Chrome", "Venmo"). A key
     * matches its own package and any package under it ("com.ubercab" covers "com.ubercab.x"),
     * and the longest matching key wins, so "com.ubercab.eats" is Shopping while Uber is Places.
     * Keys are lower case; package names are compared in lower case.
     */
    private val packageCategories: Map<String, AppCategory> = buildMap {
        fun put(category: AppCategory, vararg packages: String) = packages.forEach { put(it, category) }

        put(
            AppCategory.AI_AGENTS,
            "com.openai.chatgpt", "com.anthropic.claude", "com.google.android.apps.bard",
            "ai.perplexity.app.android", "com.microsoft.copilot", "com.deepseek.chat",
            "ai.x.grok", "ai.mistral.chat",
        )
        put(
            AppCategory.COMMUNICATION,
            "com.google.android.apps.messaging", "com.google.android.dialer",
            "com.google.android.contacts", "com.samsung.android.messaging",
            "com.samsung.android.dialer", "com.samsung.android.app.contacts",
            "com.google.android.gm", "com.microsoft.office.outlook", "ch.protonmail.android",
            "com.whatsapp", "org.thoughtcrime.securesms", "org.telegram.messenger",
            "com.facebook.katana", "com.facebook.orca", "com.instagram.android",
            "com.instagram.barcelona", "com.snapchat.android", "com.twitter.android",
            "xyz.blueskyweb.app", "org.joinmastodon.android", "com.linkedin.android",
            "com.discord", "com.slack", "com.microsoft.teams", "us.zoom.videomeetings",
            "com.google.android.apps.tachyon", "com.google.android.apps.photos",
        )
        put(
            AppCategory.PRODUCTIVITY,
            "com.google.android.calendar", "com.samsung.android.calendar",
            "com.google.android.keep", "com.google.android.apps.docs",
            "com.google.android.apps.tasks", "com.microsoft.office.word",
            "com.microsoft.office.excel", "com.microsoft.office.powerpoint",
            "com.microsoft.office.onenote", "com.microsoft.todos", "com.microsoft.office.officehubrow",
            "com.todoist", "com.ticktick.task", "md.obsidian", "com.evernote", "notion.id",
            "com.dropbox.android", "com.github.android", "com.atlassian.android.jira.core",
            "com.trello", "com.adobe.reader", *Constants.KNOWN_PASSWORD_MANAGERS.toTypedArray(),
        )
        put(
            AppCategory.NEWS,
            "com.nytimes.android", "wsj.reader_sp", "com.economist.lamarr",
            "com.google.android.apps.magazines", "flipboard.app", "com.devhd.feedly",
            "com.bbc.news", "com.cnn.mobile.android.phone", "com.npr.nprnews", "com.groundnews",
            "com.medium.reader", "com.reddit.frontpage", "com.washingtonpost.rainbow",
            "com.guardian", "com.foxnews.android", "com.substack.app",
        )
        put(
            AppCategory.MEDIA,
            "com.spotify.music", "com.google.android.youtube", "com.google.android.apps.youtube.music",
            "com.netflix.mediaclient", "com.amazon.kindle", "com.audible.application",
            "com.overdrive.mobile.android.libby", "com.zhiliaoapp.musically", "tv.twitch.android.app",
            "com.hulu.plus", "com.disney.disneyplus", "com.wbd.stream",
            "com.amazon.avod.thirdpartyclient", "com.google.android.videos", "com.pandora.android",
            "com.apple.android.music", "com.soundcloud.android", "com.plexapp.android",
            "au.com.shiftyjelly.pocketcasts", "com.bambuna.podcastaddict", "com.shazam.android",
            "com.peacocktv.peacockandroid", "com.cbs.app", "com.crunchyroll.crunchyroid",
            "com.google.android.apps.youtube.kids",
        )
        put(
            AppCategory.GAMES,
            "com.nytimes.crossword", "com.chess", "com.roblox.client", "com.mojang.minecraftpe",
            "com.nianticlabs.pokemongo", "com.king", "com.supercell",
        )
        put(
            AppCategory.FINANCE,
            "com.venmo", "com.squareup.cash", "com.paypal.android.p2pmobile", "com.zellepay.zelle",
            "com.chase.sig.android", "com.wf.wellsfargomobile", "com.infonow.bofa",
            "com.konylabs.capitalone", "com.citi.citimobile", "com.usaa.mobile.android.usaa",
            "com.americanexpress.android.acctsvcs.us", "com.discoverfinancial.mobile",
            "com.robinhood.android", "com.coinbase.android", "com.fidelity.android",
            "com.vanguard", "com.schwab.mobile", "com.google.android.apps.walletnfcrel",
            "com.samsung.android.spay", "com.creditkarma.mobile", "com.youneedabudget.evergreen.app",
        )
        put(
            AppCategory.SHOPPING,
            "com.amazon.mshop.android.shopping", "com.ebay.mobile", "com.walmart.android",
            "com.target.ui", "com.etsy.android", "com.costco.app.android", "com.instacart.client",
            "com.ubercab.eats", "com.dd.doordash", "com.grubhub.android", "com.starbucks.mobilecard",
            "com.mcdonalds.app", "com.alibaba.aliexpresshd", "com.einnovation.temu", "com.zzkko",
            "com.bestbuy.android", "com.shopify.arrive", "com.rfi.sams.android",
        )
        put(
            AppCategory.TRAVEL,
            "com.google.android.apps.maps", "com.waze", "com.ubercab", "me.lyft.android",
            "com.airbnb.android", "com.booking", "com.expedia.bookings",
            "com.tripadvisor.tripadvisor", "com.delta.mobile.android", "com.united.mobile.android",
            "com.aa.android", "com.southwestairlines.mobile", "com.yelp.android",
            "com.opentable", "com.citymapper.app.release", "com.thetransitapp.droid",
        )
        put(
            AppCategory.HEALTH,
            "com.fitbit.fitbitmobile", "com.google.android.apps.fitness", "com.myfitnesspal.android",
            "com.nike.plusgps", "com.onepeloton.callisto", "com.sec.android.app.shealth",
            "com.ouraring.oura", "com.whoop.android", "com.garmin.android.apps.connectmobile",
            "epic.mychart.android", "com.strava",
        )
        put(
            AppCategory.SLEEP,
            "com.getsomeheadspace.android", "com.calm.android", "com.spotlightsix.zentimerlite2",
            "com.urbandroid.sleep",
        )
        put(
            AppCategory.TOOLS,
            "com.android.settings", "com.android.vending", "com.android.chrome",
            "org.mozilla.firefox", "com.brave.browser", "com.duckduckgo.mobile.android",
            "com.microsoft.emmx", "com.google.android.googlequicksearchbox",
            "com.google.android.deskclock", "com.google.android.calculator",
            "com.google.android.apps.nbu.files", "com.google.android.documentsui",
            "com.google.android.apps.translate", "com.google.android.apps.authenticator2",
            "com.google.android.apps.chromecast.app", "com.google.android.apps.weather",
            "com.sec.android.app.clockpackage", "com.sec.android.app.popupcalculator",
            "com.sec.android.app.myfiles", "com.google.android.apps.recorder",
        )
    }

    private val semanticTerms = linkedMapOf(
        AppCategory.AI_AGENTS to setOf(
            "ai assistant", "chatbot", "chatgpt", "claude", "copilot", "deepseek", "gemini",
            "grok", "mistral", "perplexity", "poe"
        ),
        AppCategory.COMMUNICATION to setOf(
            "bluesky", "call", "camera", "chat", "class", "community", "companion", "contact",
            "dialer", "discord", "email", "facebook", "family", "gmail", "inbox", "instagram",
            "linkedin", "mail", "mastodon", "meet", "message", "messenger", "outlook", "parent",
            "people", "phone", "photo", "portrait", "school", "signal", "slack", "snapchat",
            "social", "teams", "telegram", "threads", "whatsapp", "zoom"
        ),
        AppCategory.PRODUCTIVITY to setOf(
            "1password", "bitwarden", "calendar", "credential", "docs", "drive", "dropbox",
            "evernote", "excel", "github", "journal", "keep", "keepass", "notes", "notion",
            "obsidian", "office", "onenote", "password", "pdf", "planner", "powerpoint", "print",
            "recorder", "scan", "sheets", "slides", "task", "terminal", "todo", "todoist",
            "trello", "vpn", "work", "writer"
        ),
        AppCategory.FINANCE to setOf(
            "account", "bank", "banking", "budget", "card", "cash", "coinbase", "credit",
            "crypto", "finance", "insurance", "invest", "money", "mortgage", "pay", "paypal",
            "payroll", "retirement", "robinhood", "stock", "tax", "trading", "venmo", "wallet", "ynab",
            "zelle"
        ),
        AppCategory.HEALTH to setOf(
            "calorie", "doctor", "exercise", "fit", "fitbit", "fitness", "gym", "health",
            "medical", "mychart", "nutrition", "peloton", "pharmacy", "pilates", "run", "steps",
            "strava", "tennis", "workout", "wellness", "yoga"
        ),
        AppCategory.SLEEP to setOf(
            "bedtime", "breathe", "calm", "headspace", "insight timer", "meditate", "meditation",
            "mindful", "mindfulness", "sleep", "white noise"
        ),
        AppCategory.TRAVEL to setOf(
            "airbnb", "airline", "airport", "ballpark", "bicycle", "bike", "booking", "car",
            "cinema", "dining", "expedia", "flight", "hotel", "lyft", "map", "opentable",
            "parking", "rail", "ranch", "reservation", "resort", "restaurant", "rideshare", "spa",
            "taxi", "theater", "theatre", "ticket", "train", "transit", "travel", "trip", "uber",
            "waze", "yelp"
        ),
        AppCategory.GAMES to setOf(
            "arcade", "casino", "chess", "crossword", "game", "gaming", "minecraft", "poker",
            "puzzle", "roblox", "solitaire", "steam", "sudoku", "wordle"
        ),
        AppCategory.SHOPPING to setOf(
            "aliexpress", "amazon", "cafe", "coffee", "costco", "delivery", "doordash", "ebay",
            "etsy", "food", "grocery", "grubhub", "instacart", "meal", "pizza", "retail", "shop",
            "shopping", "starbucks", "temu", "ubereats", "walmart"
        ),
        AppCategory.NEWS to setOf(
            "ap news", "article", "bbc", "breaking news", "cnn", "daily news", "economist",
            "feedly", "flipboard", "fox news", "ground news", "guardian", "headline", "magazine",
            "medium", "news", "newspaper", "npr", "nyt", "new york times", "pocket", "press",
            "reuters", "rss", "substack", "wsj", "wall street journal"
        ),
        AppCategory.MEDIA to setOf(
            "audible", "audio", "audiobook", "book", "classical", "crunchyroll", "disney",
            "gallery", "hulu", "kindle", "libby", "movie", "music", "netflix", "now playing",
            "pandora", "plex", "podcast", "radio", "reader", "shazam", "sonos", "soundcloud",
            "spotify", "stream", "tiktok", "twitch", "tv", "video", "youtube"
        ),
        AppCategory.TOOLS to setOf(
            "authenticator", "backup", "battery", "bluetooth", "browser", "calculator", "chrome",
            "clock", "compass", "file", "firefox", "flashlight", "keyboard", "launcher",
            "magnifier", "play store", "qr", "remote", "settings", "smart home", "torch",
            "translate", "utility", "weather", "wifi"
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
        // The category the developer declared counts like a word in the app's name: it beats a
        // stray word in the package name, while a name that says otherwise can still outvote it.
        androidCategory(declaredCategory)?.let { scores[it] = scores.getOrDefault(it, 0) + DECLARED_WEIGHT }
        return scores.maxByOrNull { it.value }?.key ?: AppCategory.OTHER
    }

    private const val DECLARED_WEIGHT = 3

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
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> AppCategory.TOOLS
            else -> null
        }

    /** "package Label" in one string, as the tests write apps. */
    internal fun categoryFromText(text: String): AppCategory {
        val packageName = text.substringBefore(' ')
        packageCategory(packageName)?.let { return it }
        return categoryFromEvidence(packageName, text.substringAfter(' ', ""), -1)
    }

    private fun packageCategory(packageName: String): AppCategory? {
        val name = packageName.lowercase()
        return packageCategories.entries
            .filter { (key, _) -> name == key || name.startsWith("$key.") }
            .maxByOrNull { it.key.length }
            ?.value
    }

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
