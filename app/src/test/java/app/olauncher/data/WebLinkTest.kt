package app.olauncher.data

import androidx.test.core.app.ApplicationProvider
import android.content.Intent
import app.olauncher.helper.openWebLink
import app.olauncher.helper.webLinkModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WebLinkTest {

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs = Prefs(app)

    @Test
    fun aBareDomainBecomesAnHttpsAddress() {
        assertEquals("https://news.ycombinator.com", WebLink.normalizeUrl("  news.ycombinator.com "))
        assertEquals("https://example.com/a?b=c", WebLink.normalizeUrl("example.com/a?b=c"))
        assertEquals("https://localhost:8080", WebLink.normalizeUrl("localhost:8080"))
        assertEquals("http://example.com", WebLink.normalizeUrl("http://example.com"))
        assertEquals("https://Example.com", WebLink.normalizeUrl("HTTPS://Example.com"))
    }

    @Test
    fun onlyWebPagesAreAccepted() {
        assertNull(WebLink.normalizeUrl(""))
        assertNull(WebLink.normalizeUrl("weather"))
        assertNull(WebLink.normalizeUrl("hello world.com"))
        assertNull(WebLink.normalizeUrl("javascript:alert(1)"))
        assertNull(WebLink.normalizeUrl("file:///sdcard/a.html"))
        assertNull(WebLink.normalizeUrl("intent://example.com#Intent;end"))
        assertNull(WebLink.normalizeUrl("https://"))
    }

    @Test
    fun theShortAddressKeepsThePathSoLinksOnOneSiteDiffer() {
        assertEquals("example.com/a?b=c", WebLink.shortAddress("https://www.example.com/a?b=c"))
        assertEquals("example.com", WebLink.shortAddress("http://example.com/"))
    }

    @Test
    fun anUnnamedLinkShowsItsAddressAndSitsUnderOther() {
        val link = WebLink(label = " ", url = "https://www.Example.com/path")
        assertEquals("example.com", link.displayLabel)
        assertEquals(listOf(AppCategory.OTHER), link.groups)
    }

    @Test
    fun linksSurviveStorageAndUnreadableDataIsDropped() {
        val links = listOf(
            WebLink(id = "a", label = "HN", url = "https://news.ycombinator.com", categories = listOf(AppCategory.NEWS)),
            WebLink(id = "b", label = "", url = "https://example.com"),
        )
        assertEquals(links, WebLink.fromJson(WebLink.toJson(links)))
        assertEquals(emptyList<WebLink>(), WebLink.fromJson("not json"))
        assertEquals(
            listOf(WebLink(id = "c", label = "", url = "https://x.com")),
            WebLink.fromJson("""[{"id":"c","url":"https://x.com","categories":["GONE"]},{"id":"d"}]"""),
        )
    }

    @Test
    fun prefsAddEditAndRemoveLinks() {
        val link = WebLink(id = "a", label = "HN", url = "https://news.ycombinator.com")
        prefs.saveWebLink(link)
        prefs.saveWebLink(WebLink(id = "b", label = "", url = "https://example.com"))
        prefs.saveWebLink(link.copy(label = "Hacker News"))

        assertEquals(listOf("Hacker News", "example.com"), prefs.webLinks.map { it.displayLabel })

        prefs.removeWebLink("a")
        assertEquals(listOf("b"), prefs.webLinks.map { it.id })
        prefs.removeWebLink("b")
        assertEquals(emptyList<WebLink>(), prefs.webLinks)
    }

    @Test
    fun aLinkInSeveralGroupsIsListedUnderEach() {
        prefs.saveWebLink(
            WebLink(id = "a", label = "HN", url = "https://news.ycombinator.com",
                categories = listOf(AppCategory.NEWS, AppCategory.TOOLS)),
        )
        val rows = webLinkModels(prefs)
        assertEquals(listOf(AppCategory.NEWS, AppCategory.TOOLS), rows.map { it.category })
        assertEquals(setOf("weblink:a"), rows.map { it.identityKey }.toSet())
    }

    @Test
    fun aLinkOpensAsAWebPage() {
        assertTrue(openWebLink(app, "https://news.ycombinator.com"))

        val opened = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals("https://news.ycombinator.com", opened.dataString)
        assertTrue(opened.hasCategory(Intent.CATEGORY_BROWSABLE))
    }
}
