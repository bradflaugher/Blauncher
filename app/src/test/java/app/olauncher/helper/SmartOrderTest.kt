package app.olauncher.helper

import app.olauncher.data.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartOrderTest {

    private val dayMillis = 24 * 60 * 60 * 1000L

    private fun rank(order: List<AppCategory>, category: AppCategory): Int = order.indexOf(category)

    @Test
    fun pinnedGroupsLeadInTheirOwnOrder() {
        val pinned = listOf(AppCategory.PRODUCTIVITY, AppCategory.AI_AGENTS)
        val order = SmartOrder.orderCategories(pinned, null, minuteOfDay = 20 * 60, weekend = false)
        assertEquals(pinned, order.take(2))
        assertEquals(AppCategory.entries.toSet(), order.toSet())
    }

    @Test
    fun everyGroupAppearsExactlyOnce() {
        val order = SmartOrder.orderCategories(
            listOf(AppCategory.AI_AGENTS),
            null,
            minuteOfDay = 12 * 60,
            weekend = true,
        )
        assertEquals(AppCategory.entries.size, order.size)
        assertEquals(AppCategory.entries.toSet(), order.toSet())
    }

    @Test
    fun priorsFollowTheDayWithoutAnyLearning() {
        val earlyMorning = SmartOrder.orderCategories(emptyList(), null, 6 * 60, weekend = false)
        assertTrue(rank(earlyMorning, AppCategory.NEWS) < rank(earlyMorning, AppCategory.MEDIA))
        assertTrue(rank(earlyMorning, AppCategory.NEWS) < rank(earlyMorning, AppCategory.PRODUCTIVITY))

        val workMorning = SmartOrder.orderCategories(emptyList(), null, 10 * 60, weekend = false)
        assertEquals(AppCategory.PRODUCTIVITY, workMorning.first())

        val evening = SmartOrder.orderCategories(emptyList(), null, 20 * 60 + 30, weekend = false)
        assertEquals(AppCategory.MEDIA, evening.first())
        assertTrue(rank(evening, AppCategory.SLEEP) <= 2)

        val bedtime = SmartOrder.orderCategories(emptyList(), null, 22 * 60, weekend = false)
        assertEquals(AppCategory.SLEEP, bedtime.first())
        assertTrue(rank(earlyMorning, AppCategory.SLEEP) > rank(earlyMorning, AppCategory.NEWS))

        val weekendMorning = SmartOrder.orderCategories(emptyList(), null, 9 * 60, weekend = true)
        assertTrue(rank(weekendMorning, AppCategory.HEALTH) < rank(weekendMorning, AppCategory.PRODUCTIVITY))
        assertTrue(rank(weekendMorning, AppCategory.NEWS) < rank(weekendMorning, AppCategory.PRODUCTIVITY))
    }

    @Test
    fun launchesTeachTheOrderAroundThatHour() {
        val stats = SmartOrder.UsageStats(updatedAt = 0L)
        repeat(3) {
            SmartOrder.recordLaunch(stats, AppCategory.GAMES, hourOfDay = 10, weekend = false, nowMillis = dayMillis)
        }

        val atTen = SmartOrder.orderCategories(emptyList(), stats, 10 * 60, weekend = false, nowMillis = dayMillis)
        assertEquals(AppCategory.GAMES, atTen.first())

        // The signal is local in time: it should not hijack the early morning.
        val atSix = SmartOrder.orderCategories(emptyList(), stats, 6 * 60, weekend = false, nowMillis = dayMillis)
        assertTrue(rank(atSix, AppCategory.NEWS) < rank(atSix, AppCategory.GAMES))
    }

    @Test
    fun pinnedGroupsIgnoreLearningAndStayOnTop() {
        val stats = SmartOrder.UsageStats(updatedAt = 0L)
        repeat(10) {
            SmartOrder.recordLaunch(stats, AppCategory.GAMES, hourOfDay = 10, weekend = false, nowMillis = dayMillis)
        }
        val order = SmartOrder.orderCategories(
            listOf(AppCategory.AI_AGENTS),
            stats,
            10 * 60,
            weekend = false,
            nowMillis = dayMillis,
        )
        assertEquals(AppCategory.AI_AGENTS, order.first())
        assertEquals(AppCategory.GAMES, order[1])
    }

    @Test
    fun oldHabitsFadeWithTime() {
        val stats = SmartOrder.UsageStats(updatedAt = 0L)
        SmartOrder.recordLaunch(stats, AppCategory.GAMES, hourOfDay = 10, weekend = false, nowMillis = dayMillis)

        val fresh = SmartOrder.usageScore(stats, AppCategory.GAMES, 10, weekend = false)
        assertTrue(fresh > 1.0)

        // Half-life is 14 days: after 28 days the decay factor is a quarter.
        assertEquals(0.25, SmartOrder.decayFactor(dayMillis, dayMillis + 28 * dayMillis), 0.001)

        val monthLater = SmartOrder.orderCategories(
            emptyList(),
            stats,
            10 * 60,
            weekend = false,
            nowMillis = dayMillis + 90 * dayMillis,
        )
        // After three quiet months the priors take over again.
        assertEquals(AppCategory.PRODUCTIVITY, monthLater.first())
    }

    @Test
    fun weekendLearningOnlySoftlyInfluencesWeekdays() {
        val stats = SmartOrder.UsageStats(updatedAt = 0L)
        SmartOrder.recordLaunch(stats, AppCategory.GAMES, hourOfDay = 15, weekend = true, nowMillis = dayMillis)

        val weekendScore = SmartOrder.usageScore(stats, AppCategory.GAMES, 15, weekend = true)
        val weekdayScore = SmartOrder.usageScore(stats, AppCategory.GAMES, 15, weekend = false)
        assertTrue(weekdayScore > 0.0)
        assertTrue(weekdayScore < weekendScore / 2)
    }

    @Test
    fun statsSurviveSerializationRoundTrip() {
        val stats = SmartOrder.UsageStats(updatedAt = 123456789L)
        SmartOrder.recordLaunch(stats, AppCategory.NEWS, hourOfDay = 7, weekend = false, nowMillis = 123456789L)
        SmartOrder.recordLaunch(stats, AppCategory.MEDIA, hourOfDay = 21, weekend = true, nowMillis = 123456789L)

        val restored = SmartOrder.parse(SmartOrder.serialize(stats))!!
        assertEquals(stats.updatedAt, restored.updatedAt)
        AppCategory.entries.forEach { category ->
            (0 until 24).forEach { hour ->
                assertEquals(
                    "weekday $category@$hour",
                    SmartOrder.usageScore(stats, category, hour, weekend = false),
                    SmartOrder.usageScore(restored, category, hour, weekend = false),
                    0.001,
                )
                assertEquals(
                    "weekend $category@$hour",
                    SmartOrder.usageScore(stats, category, hour, weekend = true),
                    SmartOrder.usageScore(restored, category, hour, weekend = true),
                    0.001,
                )
            }
        }
    }

    @Test
    fun corruptOrEmptyDataIsIgnored() {
        assertNull(SmartOrder.parse(null))
        assertNull(SmartOrder.parse(""))
        assertNull(SmartOrder.parse("not a stats blob"))
        assertNull(SmartOrder.parse("updated=notanumber\nNEWS;wd|1;we|2"))
    }

    @Test
    fun midnightWrapsAroundInsteadOfFallingOffTheClock() {
        val stats = SmartOrder.UsageStats(updatedAt = 0L)
        SmartOrder.recordLaunch(stats, AppCategory.MEDIA, hourOfDay = 23, weekend = false, nowMillis = dayMillis)
        // The launch at 23:00 should still be felt just after midnight.
        assertTrue(SmartOrder.usageScore(stats, AppCategory.MEDIA, 0, weekend = false) > 0.0)
    }

    @Test
    fun appsSortByGroupThenMostOpenedThenAlphabetically() {
        // The group comes first, whatever the usage.
        assertTrue(SmartOrder.compareDrawerRows(0, 0.0, "WSJ", 1, 9.0, "BBC") < 0)
        // Inside a group the more-opened app leads, even against the alphabet.
        assertTrue(SmartOrder.compareDrawerRows(0, 3.0, "WSJ", 0, 1.0, "BBC") < 0)
        // Equal (or no) use falls back to A-Z.
        assertTrue(SmartOrder.compareDrawerRows(0, 0.0, "bbc", 0, 0.0, "WSJ") < 0)
    }

    @Test
    fun eachLaunchAddsOneAndOldLaunchesFade() {
        val usage = SmartOrder.AppUsage(updatedAt = dayMillis)
        SmartOrder.recordAppLaunch(usage, "maps", nowMillis = dayMillis)
        SmartOrder.recordAppLaunch(usage, "maps", nowMillis = dayMillis)
        // Two weeks later Maps' two launches count as one, and the new app's one launch ties it.
        SmartOrder.recordAppLaunch(usage, "uber", nowMillis = dayMillis + 14 * dayMillis)
        assertEquals(1.0, usage.weights.getValue("maps"), 1e-9)
        assertEquals(1.0, usage.weights.getValue("uber"), 1e-9)
    }

    @Test
    fun appsUnopenedForMonthsAreForgotten() {
        val usage = SmartOrder.AppUsage(updatedAt = dayMillis)
        SmartOrder.recordAppLaunch(usage, "old", nowMillis = dayMillis)
        SmartOrder.recordAppLaunch(usage, "new", nowMillis = dayMillis + 120 * dayMillis)
        assertEquals(setOf("new"), usage.weights.keys)
    }

    @Test
    fun weightsAreReadAsTheyStandNotAsTheyWereStored() {
        val usage = SmartOrder.AppUsage(updatedAt = dayMillis)
        usage.weights["maps"] = 2.0
        usage.weights["uber"] = 0.015

        val later = SmartOrder.currentWeights(usage, nowMillis = dayMillis + 14 * dayMillis)

        // Two weeks halve Maps; Uber fades below the floor and drops back among the unopened.
        assertEquals(mapOf("maps" to 1.0), later)
    }

    @Test
    fun appUsageSurvivesARoundTripThroughPrefs() {
        val usage = SmartOrder.AppUsage(updatedAt = 42L)
        usage.weights["shortcut:com.example.mail/compose|UserHandle{0}"] = 2.5
        usage.weights["com.example.maps|UserHandle{0}"] = 1.0

        val parsed = SmartOrder.parseAppUsage(SmartOrder.serializeAppUsage(usage))!!

        assertEquals(42L, parsed.updatedAt)
        assertEquals(usage.weights, parsed.weights)
        assertNull(SmartOrder.parseAppUsage("not a blob"))
    }
}
