package app.olauncher.helper

/**
 * First-run coaching. Each tip teaches one gesture and retires for good once the user has
 * performed it (or skipped the tips); nothing here runs on a timer or a launch counter, so a
 * tip never disappears before it has done its job.
 */
enum class Tip {
    /** Home screen: swipe up for the app drawer. */
    OPEN_DRAWER,

    /** Home screen: long-press empty space for settings. */
    OPEN_SETTINGS,

    /** Home screen: the date opens the calendar, the key the password manager; long-press to change. */
    HOME_SHORTCUTS,

    /** App drawer: type to find an app, long-press an app for its menu. */
    APP_MENU,
}

object Onboarding {

    /** The home-screen tips, in the order they are taught. */
    val homeTips = listOf(Tip.OPEN_DRAWER, Tip.OPEN_SETTINGS, Tip.HOME_SHORTCUTS)

    /** The home-screen tip to show now, or null once every one has been learned. */
    fun nextHomeTip(learned: Set<Tip>): Tip? = homeTips.firstOrNull { it !in learned }

    /** 1-based position of a home-screen tip, for the "1 of 3" step label. */
    fun homeStep(tip: Tip): Int = homeTips.indexOf(tip) + 1

    /**
     * Tips added after the original two-step tour. Someone who had already finished that tour
     * knows their way around, so an update hands them these as learned instead of re-opening
     * the card; see [tipsLearnedOnUpgrade].
     */
    private val addedInRevision2 = setOf(Tip.HOME_SHORTCUTS)

    /**
     * What an install that stored [learned] under an older tips revision should have learned
     * now: the newer tips are added for anyone who had learned (or skipped) every older home
     * tip, and left for everyone still part-way through.
     */
    fun tipsLearnedOnUpgrade(learned: Set<Tip>): Set<Tip> =
        if (learned.containsAll(homeTips - addedInRevision2)) learned + addedInRevision2 else learned

    /** Stored names back to tips; names from a newer or older build are dropped. */
    fun parse(stored: Set<String>): Set<Tip> =
        stored.mapNotNullTo(mutableSetOf()) { name -> Tip.entries.firstOrNull { it.name == name } }
}
