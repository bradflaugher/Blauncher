package app.olauncher.helper

/**
 * First-run coaching. Each tip teaches one gesture and retires for good once the user has
 * performed it (or skipped the tips); nothing here runs on a timer or a launch counter, so a
 * tip never disappears before it has done its job.
 */
enum class Tip {
    /** Home screen: tap the search bar for the apps. (Once "swipe up"; the name is stored.) */
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
     * Tips whose gesture changed in revision 3: swipe up became an app of the user's own, and
     * the apps moved behind the search bar. Whoever learned the old gesture is taught the new one.
     */
    private val relearnInRevision3 = setOf(Tip.OPEN_DRAWER)

    /**
     * What an install that stored [learned] under tips revision [fromRevision] should have
     * learned now. Revision 2's new tip is added for anyone who had learned (or skipped) every
     * older home tip, and left for everyone still part-way through; revision 3's changed tip is
     * taught again to everyone.
     */
    fun tipsLearnedOnUpgrade(learned: Set<Tip>, fromRevision: Int): Set<Tip> {
        var upgraded = learned
        if (fromRevision < 2 && upgraded.containsAll(homeTips - addedInRevision2)) upgraded = upgraded + addedInRevision2
        if (fromRevision < 3) upgraded = upgraded - relearnInRevision3
        return upgraded
    }

    /** Stored names back to tips; names from a newer or older build are dropped. */
    fun parse(stored: Set<String>): Set<Tip> =
        stored.mapNotNullTo(mutableSetOf()) { name -> Tip.entries.firstOrNull { it.name == name } }
}
