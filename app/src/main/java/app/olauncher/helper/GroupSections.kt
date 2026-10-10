package app.olauncher.helper

import app.olauncher.data.AppCategory

/**
 * The drawer's rows while nothing is typed: one header per category, in the order the sorted
 * list already has them (pinned groups first, then smart ordering), and only the open
 * category's apps under its header. With the keyboard up there is room for little more than
 * the headers, so the drawer is for picking a category, and search is for picking an app.
 *
 * A row for which [Input.startsSection] is true (the Private Space header) is kept in place
 * and starts a new section with its own headers. The same category in two sections gets two
 * headers, opened separately. The algorithm is generic over the row type so it can be tested
 * without Android classes.
 */
object GroupSections {

    /** What the grouping needs to know about one row. */
    data class Input(
        val group: AppCategory?,
        val isNew: Boolean = false,
        val startsSection: Boolean = false,
    )

    /** What a header row shows. [key] names its section and category, see [key]. */
    data class Header(
        val group: AppCategory,
        val key: String,
        val appCount: Int,
        val hasNewApp: Boolean,
        val expanded: Boolean,
    )

    /** Identity of one category in one section: section 0 is the main list, 1 Private Space. */
    fun key(section: Int, group: AppCategory): String = "$section:${group.name}"

    /**
     * Returns the header rows for [rows], each followed by its apps when its key is [expanded].
     * Rows without a category are kept after the headers of their section.
     */
    fun <T> build(
        rows: List<T>,
        expanded: String?,
        describe: (T) -> Input,
        header: (Header) -> T,
    ): List<T> {
        val result = mutableListOf<T>()
        var section = 0
        val grouped = linkedMapOf<AppCategory, MutableList<T>>()
        val ungrouped = mutableListOf<T>()

        fun flush() {
            grouped.forEach { (group, apps) ->
                val key = key(section, group)
                val open = key == expanded
                result += header(Header(group, key, apps.size, apps.any { describe(it).isNew }, open))
                if (open) result += apps
            }
            result += ungrouped
            grouped.clear()
            ungrouped.clear()
        }

        rows.forEach { row ->
            val input = describe(row)
            when {
                input.startsSection -> {
                    flush()
                    section++
                    result += row
                }

                input.group == null -> ungrouped += row
                else -> grouped.getOrPut(input.group) { mutableListOf() } += row
            }
        }
        flush()
        return result
    }
}
