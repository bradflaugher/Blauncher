package app.olauncher.ui

import android.content.Intent
import android.os.Looper
import android.os.Process
import android.os.UserHandle
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
 * The home bar is one search for apps and the web: matching apps are listed above it and open
 * with a tap, while enter always goes to the search engine, so an app is never opened by accident.
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
    fun matchingAppsAreListedBestFirst() {
        val activity = homeWithApps("Google Maps", "Maps", "Weather")

        type(activity, "maps")

        assertEquals(listOf("Maps", "Google Maps"), suggestionTexts(activity))
    }

    @Test
    fun textThatIsNoAppNameListsNothing() {
        val activity = homeWithApps("Maps", "Weather")

        type(activity, "maps of italy")

        assertEquals(View.GONE, activity.findViewById<View>(R.id.searchSuggestions).visibility)
    }

    @Test
    fun enterSearchesTheWebEvenWhenAnAppMatches() {
        val activity = homeWithApps("Weather")

        type(activity, "weather")
        activity.findViewById<EditText>(R.id.searchInput).onEditorAction(EditorInfo.IME_ACTION_GO)
        idle()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertTrue(started.dataString!!.endsWith("?q=weather"))
        assertEquals("", activity.findViewById<EditText>(R.id.searchInput).text.toString())
    }

    @Test
    fun tappingAnAppOpensIt() {
        val activity = homeWithApps("Maps", "Weather")

        type(activity, "map")
        activity.findViewById<LinearLayout>(R.id.searchSuggestions).getChildAt(0).performClick()
        idle()

        // The fake app is not really installed, so the launch itself reports it is missing.
        assertEquals(activity.getString(R.string.app_not_found), ShadowToast.getTextOfLatestToast())
        assertNoWebSearch(activity)
        assertEquals("", activity.findViewById<EditText>(R.id.searchInput).text.toString())
    }

    @Test
    fun aWorkProfileCopyIsLabelledWithItsProfile() {
        val activity = homeWithApps("Slack")
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
        viewModel.appList.value = viewModel.appList.value!! + fakeApp("Slack", UserHandle.getUserHandleForUid(10 * 100000))
        idle()

        type(activity, "slack")

        assertEquals(listOf("Slack", "Slack  ·  Work profile"), suggestionTexts(activity))
    }

    private fun fakeApp(label: String, user: UserHandle = Process.myUserHandle()) = AppModel.App(
        appLabel = label,
        key = null,
        appPackage = "com.example.${label.lowercase().replace(" ", "")}",
        activityClassName = "Main",
        user = user,
        category = AppCategory.OTHER,
    )

    private fun homeWithApps(vararg labels: String): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()
        val apps = labels.map { fakeApp(it) }
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
