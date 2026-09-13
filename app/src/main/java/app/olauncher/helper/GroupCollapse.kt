package app.olauncher.helper

import app.olauncher.data.AppCategory

/**
 * Folds the faded rows of the drawer behind one toggle row per group.
 *
 * Once a group has an emphasized app, its other apps are marked [Row.dimmed] (see
 * [SmartOrder.applyGroupEmphasis]). Rather than trailing the bold rows as a long faded list,
 * they collapse into a single row that names them; tapping it expands the group in place.
 * Expansion is remembered per group only for the current drawer visit, so every fresh open
 * starts compact. Newly installed apps stay visible even while their group is collapsed.
 *
 * The algorithm is generic over the row type so it can be exercised without Android classes.
 */
object GroupCollapse {

    /** What the fold needs to know about one row. */
    data class Row(
        val group: AppCategory?,
        val dimmed: Boolean,
        val isNew: Boolean = false,
        /** True for rows that start a new section (the Private Space header). */
        val startsSection: Boolean = false,
    )

    /** Identity of a toggle: the same group in another section (Private Space) is a separate fold. */
    fun toggleKey(section: Int, group: AppCategory): String = "$section:${group.name}"

    /**
     * Returns [rows] with every run of dimmed rows of one group replaced by a toggle row, followed
     * by the run itself when its key is in [expanded]. New apps in a collapsed run are kept in
     * place instead of folded.
     */
    fun <T> collapse(
        rows: List<T>,
        expanded: Set<String>,
        describe: (T) -> Row,
        toggle: (key: String, group: AppCategory, collapsedApps: List<T>, expanded: Boolean) -> T,
    ): List<T> {
        val result = ArrayList<T>(rows.size)
        var section = 0
        var index = 0
        while (index < rows.size) {
            val row = rows[index]
            val info = describe(row)
            if (info.startsSection) section++
            val group = info.group
            if (!info.dimmed || group == null) {
                result.add(row)
                index++
                continue
            }
            var end = index + 1
            while (end < rows.size) {
                val next = describe(rows[end])
                if (!next.dimmed || next.group != group || next.startsSection) break
                end++
            }
            val run = rows.subList(index, end)
            val collapsedApps = run.filterNot { describe(it).isNew }
            if (collapsedApps.isEmpty()) {
                result.addAll(run)
            } else {
                val key = toggleKey(section, group)
                val isExpanded = key in expanded
                result.add(toggle(key, group, collapsedApps, isExpanded))
                if (isExpanded) result.addAll(run) else run.filterTo(result) { describe(it).isNew }
            }
            index = end
        }
        return result
    }
}
