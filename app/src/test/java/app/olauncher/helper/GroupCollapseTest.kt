package app.olauncher.helper

import app.olauncher.data.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupCollapseTest {

    private data class Row(
        val label: String,
        val group: AppCategory? = AppCategory.AI_AGENTS,
        val dimmed: Boolean = false,
        val isNew: Boolean = false,
        val header: Boolean = false,
    )

    private fun collapse(rows: List<Row>, expanded: Set<String> = emptySet()): List<String> =
        GroupCollapse.collapse(
            rows,
            expanded,
            describe = { GroupCollapse.Row(it.group, it.dimmed, it.isNew, it.header) },
            toggle = { key, group, collapsedApps, isExpanded ->
                val names = collapsedApps.joinToString(",") { it.label }
                Row("${if (isExpanded) "-" else "+"}$key[$names]", group)
            },
        ).map { it.label }

    private val aiGroup = listOf(
        Row("Claude"),
        Row("Gemini", dimmed = true),
        Row("Perplexity", dimmed = true),
        Row("Poe", dimmed = true),
    )
    private val mediaGroup = listOf(
        Row("Spotify", AppCategory.MEDIA),
        Row("YouTube", AppCategory.MEDIA),
    )

    @Test
    fun dimmedRunFoldsIntoOneToggleAfterTheEmphasizedRows() {
        assertEquals(
            listOf("Claude", "+0:AI_AGENTS[Gemini,Perplexity,Poe]", "Spotify", "YouTube"),
            collapse(aiGroup + mediaGroup),
        )
    }

    @Test
    fun groupsWithoutEmphasisAreLeftAlone() {
        assertEquals(listOf("Spotify", "YouTube"), collapse(mediaGroup))
    }

    @Test
    fun expandedGroupKeepsItsToggleAndShowsEveryRow() {
        assertEquals(
            listOf("Claude", "-0:AI_AGENTS[Gemini,Perplexity,Poe]", "Gemini", "Perplexity", "Poe"),
            collapse(aiGroup, expanded = setOf(GroupCollapse.toggleKey(0, AppCategory.AI_AGENTS))),
        )
    }

    @Test
    fun newAppsStayVisibleWhileTheirGroupIsCollapsed() {
        val rows = listOf(
            Row("Claude"),
            Row("Gemini", dimmed = true),
            Row("Kimi", dimmed = true, isNew = true),
            Row("Poe", dimmed = true),
        )
        assertEquals(listOf("Claude", "+0:AI_AGENTS[Gemini,Poe]", "Kimi"), collapse(rows))
    }

    @Test
    fun aRunOfOnlyNewAppsNeedsNoToggle() {
        val rows = listOf(Row("Claude"), Row("Kimi", dimmed = true, isNew = true))
        assertEquals(listOf("Claude", "Kimi"), collapse(rows))
    }

    @Test
    fun adjacentGroupsFoldSeparately() {
        val rows = listOf(
            Row("Gemini", dimmed = true),
            Row("Spotify", AppCategory.MEDIA, dimmed = true),
        )
        assertEquals(listOf("+0:AI_AGENTS[Gemini]", "+0:MEDIA[Spotify]"), collapse(rows))
    }

    @Test
    fun privateSpaceGetsItsOwnToggleForTheSameGroup() {
        val rows = aiGroup + listOf(Row("Private space", group = null, header = true)) + aiGroup
        val expanded = setOf(GroupCollapse.toggleKey(1, AppCategory.AI_AGENTS))
        assertEquals(
            listOf(
                "Claude", "+0:AI_AGENTS[Gemini,Perplexity,Poe]",
                "Private space",
                "Claude", "-1:AI_AGENTS[Gemini,Perplexity,Poe]", "Gemini", "Perplexity", "Poe",
            ),
            collapse(rows, expanded),
        )
    }
}
