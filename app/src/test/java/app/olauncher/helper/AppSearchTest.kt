package app.olauncher.helper

import app.olauncher.helper.AppSearch.Match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSearchTest {

    private fun search(labels: List<String>, query: String): List<String> =
        AppSearch.search(labels, query, label = { it }, key = { it }).map { it.first }

    @Test
    fun matchesIgnoreCaseAccentsAndSeparators() {
        assertEquals(Match.EXACT, AppSearch.match("Maps", "maps"))
        assertEquals(Match.PREFIX, AppSearch.match("Café Finder", "cafe"))
        assertEquals(Match.EXACT, AppSearch.match("G Maps", "gmaps"))
        assertEquals(Match.WORD_PREFIX, AppSearch.match("Google Maps", "map"))
        assertEquals(Match.CONTAINS, AppSearch.match("Showtime", "how"))
        assertNull(AppSearch.match("Maps", "maps of italy"))
        assertNull(AppSearch.match("Maps", "   "))
        // Separators alone normalize away but still find names made of them.
        assertEquals(Match.CONTAINS, AppSearch.match("C++", "+"))
        assertEquals(Match.CONTAINS, AppSearch.match("1.1.1.1", "."))
        assertNull(AppSearch.match("Maps", "+"))
    }

    @Test
    fun strongerMatchesComeFirstAndTiesKeepTheirOrder() {
        val labels = listOf("Bitmap", "Google Maps", "Maps.me", "Maps", "Mapper")
        assertEquals(listOf("Maps.me", "Maps", "Mapper", "Google Maps", "Bitmap"), search(labels, "map"))
        assertEquals(listOf("Maps", "Maps.me", "Google Maps"), search(labels, "maps"))
    }

    @Test
    fun anAppListedUnderSeveralGroupsIsOneResult() {
        val apps = listOf("Maps" to "travel", "Maps" to "tools", "Weather" to "tools")
        val results = AppSearch.search(apps, "ma", label = { it.first }, key = { it.first })
        assertEquals(listOf("Maps" to "travel"), results.map { it.first })
    }

}
