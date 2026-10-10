package app.olauncher.ui

import android.content.Intent
import android.os.Looper
import android.os.Process
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import app.olauncher.MainActivity
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppCategory
import app.olauncher.data.AppModel
import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * The home bar is one search for apps and the web: matching apps are listed above it, enter
 * opens the top one when the text starts its name, and anything else goes to the search engine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HomeSearchTest {

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun setUp() {
        Prefs(app).apply {
            firstOpen = false
            // Keep the periodic launcher restart from recreating the activity mid-test.
            launcherRestartTimestamp = System.currentTimeMillis()
        }
    }

    @Test
    fun matchingAppsAreListedBestFirstWithTheWebSearchLast() {
        val activity = homeWithApps("Google Maps", "Maps", "Weather")

        type(activity, "maps")

        assertEquals(listOf("Maps", "Google Maps", "Search the web for “maps”"), suggestionTexts(activity))
    }

    @Test
    fun textThatIsNoAppNameListsNothing() {
        val activity = homeWithApps("Maps", "Weather")

        type(activity, "maps of italy")

        assertEquals(View.GONE, activity.findViewById<View>(R.id.searchSuggestions).visibility)
    }

    @Test
    fun enterOpensTheTopApp() {
        val activity = homeWithApps("Maps", "Weather")

        type(activity, "map")
        activity.findViewById<EditText>(R.id.searchInput).onEditorAction(EditorInfo.IME_ACTION_GO)
        idle()

        // The fake app is not really installed, so the launch itself reports it is missing.
        assertEquals(activity.getString(R.string.app_not_found), ShadowToast.getTextOfLatestToast())
        assertNoWebSearch(activity)
        assertEquals("", activity.findViewById<EditText>(R.id.searchInput).text.toString())
    }

    @Test
    fun enterSearchesTheWebWhenNoAppStartsWithTheText() {
        val activity = homeWithApps("Showtime")

        // "how" is inside "Showtime" but starts none of its words: listed, not opened.
        type(activity, "how")
        assertEquals(listOf("Showtime"), suggestionTexts(activity))
        activity.findViewById<EditText>(R.id.searchInput).onEditorAction(EditorInfo.IME_ACTION_GO)
        idle()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertTrue(started.dataString!!.endsWith("?q=how"))
    }

    @Test
    fun theWebRowSearchesTheWebEvenWhenAnAppMatches() {
        val activity = homeWithApps("Maps")

        type(activity, "maps")
        val rows = activity.findViewById<LinearLayout>(R.id.searchSuggestions)
        rows.getChildAt(rows.childCount - 1).performClick()
        idle()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertTrue(started.dataString!!.endsWith("?q=maps"))
    }

    private fun homeWithApps(vararg labels: String): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()
        val apps = labels.map { label ->
            AppModel.App(
                appLabel = label,
                key = null,
                appPackage = "com.example.${label.lowercase().replace(" ", "")}",
                activityClassName = "Main",
                user = Process.myUserHandle(),
                category = AppCategory.OTHER,
            )
        }
        ViewModelProvider(activity)[MainViewModel::class.java].appList.value = apps
        idle()
        return activity
    }

    /** Sets the text without focusing the field, which would reload the real (empty) app list. */
    private fun type(activity: MainActivity, text: String) {
        activity.findViewById<EditText>(R.id.searchInput).setText(text)
        idle()
    }

    private fun suggestionTexts(activity: MainActivity): List<String> {
        val rows = activity.findViewById<LinearLayout>(R.id.searchSuggestions)
        assertEquals(View.VISIBLE, rows.visibility)
        return (0 until rows.childCount).map { (rows.getChildAt(it) as TextView).text.toString() }
    }

    private fun assertNoWebSearch(activity: MainActivity) {
        val started = shadowOf(activity).nextStartedActivity
        assertTrue(started == null || started.action != Intent.ACTION_VIEW)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
