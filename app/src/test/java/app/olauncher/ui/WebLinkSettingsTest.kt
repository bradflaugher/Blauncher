package app.olauncher.ui

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import app.olauncher.MainActivity
import app.olauncher.R
import app.olauncher.data.AppCategory
import app.olauncher.data.Prefs
import app.olauncher.data.WebLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Settings → Web links adds a website to the drawer: its address (a bare domain is enough), an
 * optional name and its groups. Settings lists the links, and a tap edits or removes one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WebLinkSettingsTest {

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun setUp() {
        Prefs(app).apply {
            firstOpen = false
            launcherRestartTimestamp = System.currentTimeMillis()
        }
    }

    @Test
    fun addingALinkSavesItWithItsGroupsAndListsIt() {
        val activity = openSettings()

        activity.findViewById<View>(R.id.addWebLink).performClick()
        idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.findViewById<EditText>(R.id.webLinkUrl)!!.setText("news.ycombinator.com")
        dialog.findViewById<EditText>(R.id.webLinkName)!!.setText("HN")
        groupBox(dialog, AppCategory.NEWS).performClick()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()

        val link = Prefs(app).webLinks.single()
        assertEquals("HN", link.label)
        assertEquals("https://news.ycombinator.com", link.url)
        assertEquals(listOf(AppCategory.NEWS), link.categories)
        assertTrue(!dialog.isShowing)
        val rows = activity.findViewById<LinearLayout>(R.id.webLinkList)
        assertEquals(1, rows.childCount)
        assertTrue((rows.getChildAt(0) as TextView).text.startsWith("HN"))
    }

    @Test
    fun anAddressThatIsNoWebPageKeepsTheSheetOpen() {
        val activity = openSettings()

        activity.findViewById<View>(R.id.addWebLink).performClick()
        idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val url = dialog.findViewById<EditText>(R.id.webLinkUrl)!!
        url.setText("javascript:alert(1)")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()

        assertTrue(dialog.isShowing)
        assertEquals(activity.getString(R.string.web_link_invalid), url.error)
        assertEquals(emptyList<WebLink>(), Prefs(app).webLinks)
    }

    @Test
    fun tappingAListedLinkEditsOrRemovesIt() {
        Prefs(app).saveWebLink(WebLink(id = "a", label = "HN", url = "https://news.ycombinator.com"))
        val activity = openSettings()
        val rows = activity.findViewById<LinearLayout>(R.id.webLinkList)

        rows.getChildAt(0).performClick()
        idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertEquals("https://news.ycombinator.com", dialog.findViewById<EditText>(R.id.webLinkUrl)!!.text.toString())
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        idle()

        assertEquals(emptyList<WebLink>(), Prefs(app).webLinks)
        assertEquals(0, rows.childCount)
    }

    private fun groupBox(dialog: AlertDialog, category: AppCategory): CheckBox {
        val groups = dialog.findViewById<LinearLayout>(R.id.webLinkGroups)!!
        return (0 until groups.childCount).map { groups.getChildAt(it) as CheckBox }
            .first { it.text == category.displayName }
    }

    private fun openSettings(): Activity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<View>(R.id.mainLayout).performContextClick()
        idle()
        return activity
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
