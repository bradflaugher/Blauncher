package app.olauncher.helper

import app.olauncher.data.AppCategory
import app.olauncher.data.AppModel
import app.olauncher.data.Prefs
import java.util.Calendar
import kotlin.math.exp
import kotlin.math.pow

/**
 * Orders app groups with a tiny on-device model instead of a fixed schedule.
 *
 * Every group gets a score for the current moment:
 * built-in smooth time-of-day curves (weekday and weekend variants) provide a
 * sensible default, and locally recorded launches sharpen it over time. Launch
 * counts live in a small preference blob, bucketed by hour and day type, and
 * fade with a two-week half-life so old habits stop steering the order.
 * Nothing is read from the system usage stats and nothing leaves the device.
 */
object SmartOrder {
    private const val HOURS = 24
    private const val HALF_LIFE_DAYS = 14.0
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    // Spread each launch (and each read) over nearby hours so 8:55 informs 9:10.
    private val KERNEL = doubleArrayOf(0.25, 0.7, 1.0, 0.7, 0.25)

    // How much the other day type still counts (weekend habits hint at weekdays).
    private const val CROSS_DAY_WEIGHT = 0.25

    /** Decayed launch weights per group, split into weekday and weekend hour buckets. */
    internal class UsageStats(
        var updatedAt: Long = 0L,
        val weekday: MutableMap<AppCategory, DoubleArray> = mutableMapOf(),
        val weekend: MutableMap<AppCategory, DoubleArray> = mutableMapOf(),
    )

    private class Curve(val base: Double, val weekday: List<Peak>, val weekend: List<Peak> = weekday)
    private class Peak(val hour: Double, val width: Double, val height: Double)

    // Smooth priors: when a group is likely wanted, absent any learned signal.
    private val priors = mapOf(
        AppCategory.AI_AGENTS to Curve(0.35, listOf()),
        AppCategory.COMMUNICATION to Curve(
            0.25,
            listOf(Peak(8.0, 3.0, 0.4), Peak(18.0, 4.0, 0.5)),
        ),
        AppCategory.PRODUCTIVITY to Curve(
            0.05,
            weekday = listOf(Peak(10.0, 2.5, 0.8), Peak(14.5, 3.0, 0.75)),
            weekend = listOf(Peak(11.0, 3.0, 0.2)),
        ),
        AppCategory.NEWS to Curve(
            0.1,
            weekday = listOf(Peak(6.0, 2.0, 0.9), Peak(18.0, 3.0, 0.3)),
            weekend = listOf(Peak(8.0, 3.0, 0.85), Peak(18.0, 3.0, 0.25)),
        ),
        AppCategory.MEDIA to Curve(
            0.1,
            weekday = listOf(Peak(20.5, 2.5, 0.85)),
            weekend = listOf(Peak(14.0, 4.0, 0.4), Peak(20.5, 2.5, 0.85)),
        ),
        AppCategory.GAMES to Curve(
            0.02,
            weekday = listOf(Peak(21.0, 2.0, 0.35)),
            weekend = listOf(Peak(15.0, 4.0, 0.3), Peak(21.0, 2.0, 0.45)),
        ),
        AppCategory.FINANCE to Curve(
            0.05,
            weekday = listOf(Peak(10.0, 3.0, 0.35), Peak(15.0, 3.0, 0.25)),
            weekend = listOf(),
        ),
        AppCategory.SHOPPING to Curve(
            0.05,
            weekday = listOf(Peak(19.0, 3.0, 0.3)),
            weekend = listOf(Peak(13.0, 4.0, 0.4)),
        ),
        AppCategory.TRAVEL to Curve(
            0.08,
            weekday = listOf(Peak(8.0, 1.5, 0.55), Peak(17.0, 2.0, 0.55)),
            weekend = listOf(Peak(10.0, 4.0, 0.5)),
        ),
        AppCategory.HEALTH to Curve(
            0.08,
            weekday = listOf(Peak(6.5, 1.5, 0.65), Peak(12.0, 1.5, 0.45)),
            weekend = listOf(Peak(9.0, 3.0, 0.75)),
        ),
        AppCategory.TOOLS to Curve(0.08, listOf()),
        AppCategory.OTHER to Curve(0.0, listOf()),
    )

    /** Sorts the drawer: pinned groups first in the user's order, the rest by score, apps A-Z inside. */
    fun sort(prefs: Prefs, apps: MutableList<AppModel>) {
        val order = currentOrder(prefs).withIndex().associate { it.value to it.index }
        apps.sortWith(
            compareBy<AppModel> { order[it.category] ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appLabel }
        )
    }

    /** The full group order for the current moment. */
    fun currentOrder(
        prefs: Prefs,
        now: Calendar = Calendar.getInstance(),
    ): List<AppCategory> = orderCategories(
        prefs.pinnedCategories,
        parse(prefs.categoryUsageData),
        minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
        weekend = now.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
            now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY,
        nowMillis = now.timeInMillis,
    )

    /** Records one launch so the group bubbles up around this hour on this day type. */
    fun recordLaunch(prefs: Prefs, category: AppCategory?, now: Calendar = Calendar.getInstance()) {
        category ?: return
        val stats = parse(prefs.categoryUsageData) ?: UsageStats(now.timeInMillis)
        recordLaunch(
            stats,
            category,
            hourOfDay = now.get(Calendar.HOUR_OF_DAY),
            weekend = now.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY,
            nowMillis = now.timeInMillis,
        )
        prefs.categoryUsageData = serialize(stats)
    }

    internal fun recordLaunch(
        stats: UsageStats,
        category: AppCategory,
        hourOfDay: Int,
        weekend: Boolean,
        nowMillis: Long,
    ) {
        val factor = decayFactor(stats.updatedAt, nowMillis)
        if (factor < 1.0) {
            (stats.weekday.values + stats.weekend.values).forEach { buckets ->
                for (i in buckets.indices) buckets[i] *= factor
            }
        }
        stats.updatedAt = maxOf(stats.updatedAt, nowMillis)
        val buckets = (if (weekend) stats.weekend else stats.weekday)
            .getOrPut(category) { DoubleArray(HOURS) }
        KERNEL.forEachIndexed { i, weight ->
            buckets[Math.floorMod(hourOfDay + i - KERNEL.size / 2, HOURS)] += weight
        }
    }

    internal fun orderCategories(
        pinned: List<AppCategory>,
        stats: UsageStats?,
        minuteOfDay: Int,
        weekend: Boolean,
        nowMillis: Long = stats?.updatedAt ?: 0L,
    ): List<AppCategory> {
        val remaining = AppCategory.entries.filterNot { it in pinned }
        val decay = stats?.let { decayFactor(it.updatedAt, nowMillis) } ?: 0.0
        val hour = minuteOfDay / 60.0
        val scored = remaining.sortedByDescending { category ->
            prior(category, hour, weekend) +
                (stats?.let { usageScore(it, category, minuteOfDay / 60, weekend) } ?: 0.0) * decay
        }
        return pinned + scored
    }

    internal fun prior(category: AppCategory, hourOfDay: Double, weekend: Boolean): Double {
        val curve = priors.getValue(category)
        val peaks = if (weekend) curve.weekend else curve.weekday
        return curve.base + peaks.sumOf { peak ->
            val direct = kotlin.math.abs(hourOfDay - peak.hour)
            val distance = minOf(direct, HOURS - direct)
            peak.height * exp(-0.5 * (distance / peak.width).pow(2))
        }
    }

    internal fun usageScore(
        stats: UsageStats,
        category: AppCategory,
        hourOfDay: Int,
        weekend: Boolean,
    ): Double {
        fun kernelSum(buckets: DoubleArray?): Double {
            buckets ?: return 0.0
            return KERNEL.foldIndexed(0.0) { i, acc, weight ->
                acc + weight * buckets[Math.floorMod(hourOfDay + i - KERNEL.size / 2, HOURS)]
            }
        }
        return kernelSum((if (weekend) stats.weekend else stats.weekday)[category]) +
            CROSS_DAY_WEIGHT * kernelSum((if (weekend) stats.weekday else stats.weekend)[category])
    }

    internal fun decayFactor(updatedAt: Long, nowMillis: Long): Double {
        if (updatedAt <= 0L || nowMillis <= updatedAt) return 1.0
        val elapsedDays = (nowMillis - updatedAt).toDouble() / DAY_MILLIS
        return 0.5.pow(elapsedDays / HALF_LIFE_DAYS)
    }

    // Plain-text blob: first line "updated=<millis>", then "<GROUP>;wd|<24 values>;we|<24 values>".
    internal fun serialize(stats: UsageStats): String {
        val lines = mutableListOf("updated=${stats.updatedAt}")
        (stats.weekday.keys + stats.weekend.keys).distinct().forEach { category ->
            fun render(buckets: DoubleArray?) =
                (buckets ?: DoubleArray(HOURS)).joinToString(",") { "%.4f".format(java.util.Locale.US, it) }
            lines.add(
                "${category.name};wd|${render(stats.weekday[category])};we|${render(stats.weekend[category])}"
            )
        }
        return lines.joinToString("\n")
    }

    internal fun parse(data: String?): UsageStats? {
        if (data.isNullOrBlank()) return null
        return runCatching {
            val lines = data.lines().filter { it.isNotBlank() }
            val stats = UsageStats(lines.first().substringAfter("updated=").toLong())
            lines.drop(1).forEach { line ->
                val parts = line.split(';')
                val category = AppCategory.valueOf(parts[0])
                fun buckets(part: String): DoubleArray {
                    val values = part.substringAfter('|').split(',').map { it.toDouble() }
                    return DoubleArray(HOURS) { values.getOrElse(it) { 0.0 } }
                }
                stats.weekday[category] = buckets(parts[1])
                stats.weekend[category] = buckets(parts[2])
            }
            stats
        }.getOrNull()
    }
}
