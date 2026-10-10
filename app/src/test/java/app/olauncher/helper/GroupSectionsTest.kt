package app.olauncher.helper

import app.olauncher.data.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupSectionsTest {

    private data class Row(
        val name: String,
        val group: AppCategory? = AppCategory.AI_AGENTS,
        val isNew: Boolean = false,
        val section: Boolean = false,
    )

    private fun build(rows: List<Row>, expanded: String? = null): List<String> =
        GroupSections.build(
            rows,
            expanded,
            describe = { GroupSections.Input(it.group, it.isNew, it.section) },
            header = { h ->
                val marks = (if (h.hasNewApp) " ✦" else "") + (if (h.expanded) " open" else "")
                Row("[${h.group.displayName} ${h.appCount}$marks]", null)
            },
        ).map { it.name }

    private val rows = listOf(
        Row("Claude"),
        Row("Gemini"),
        Row("Spotify", AppCategory.MEDIA),
        Row("YouTube", AppCategory.MEDIA, isNew = true),
        Row("Maps", AppCategory.TRAVEL),
    )

    @Test
    fun everyCategoryStartsAsOneClosedRowInListOrder() {
        assertEquals(listOf("[AI Agents 2]", "[Media 2 ✦]", "[Places 1]"), build(rows))
    }

    @Test
    fun theOpenCategoryListsItsAppsUnderItsHeader() {
        assertEquals(
            listOf("[AI Agents 2]", "[Media 2 ✦ open]", "Spotify", "YouTube", "[Places 1]"),
            build(rows, expanded = GroupSections.key(0, AppCategory.MEDIA)),
        )
    }

    @Test
    fun privateSpaceKeepsItsPlaceAndGroupsItsOwnApps() {
        val withPrivate = rows + Row("Private space", null, section = true) + Row("Signal", AppCategory.COMMUNICATION)
        assertEquals(
            listOf("[AI Agents 2]", "[Media 2 ✦]", "[Places 1]", "Private space", "[People 1 open]", "Signal"),
            build(withPrivate, expanded = GroupSections.key(1, AppCategory.COMMUNICATION)),
        )
        // The same key in the main section opens nothing in Private Space.
        assertEquals(
            listOf("[AI Agents 2]", "[Media 2 ✦]", "[Places 1]", "Private space", "[People 1]"),
            build(withPrivate, expanded = GroupSections.key(0, AppCategory.COMMUNICATION)),
        )
    }

    @Test
    fun rowsWithoutACategoryFollowTheHeaders() {
        assertEquals(listOf("[AI Agents 2]", "padding"), build(rows.take(2) + Row("padding", null)))
    }
}
