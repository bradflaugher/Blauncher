package app.olauncher.ui

import android.content.Intent
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.graphics.Rect
import android.view.InputDevice
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.os.SystemClock
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import app.olauncher.MainActivity
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppCategory
import app.olauncher.data.AppModel
import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.Duration

/**
 * Home and the app drawer are one surface: the search bar sits on a sheet of every app, swiping
 * up (or tapping the bar) lifts it, and typing narrows the apps below the bar. Matching apps open
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

        assertEquals(listOf("Maps", "Google Maps"), listedLabels(activity))
    }

    @Test
    fun textThatIsNoAppNameListsNothing() {
        val activity = homeWithApps("Maps", "Weather")

        type(activity, "maps of italy")

        assertEquals(emptyList<String>(), listedLabels(activity))
    }

    @Test
    fun clearingTheTextListsEveryAppAgain() {
        val activity = homeWithApps("Maps", "Weather")

        type(activity, "maps")
        type(activity, "")

        assertEquals(listOf("Maps", "Weather"), listedLabels(activity).sorted())
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
    fun theSheetRestsWithOnlyTheSearchBarShowing() {
        val activity = homeWithApps("Maps")

        assertFalse(isSheetOpen(activity))
        assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.appList).visibility)
        // The bar sits on screen, along the bottom.
        val root = activity.findViewById<View>(R.id.mainLayout)
        val bar = Rect().also { activity.findViewById<View>(R.id.searchBar).getGlobalVisibleRect(it) }
        assertTrue(bar.height() > 0)
        assertTrue(bar.top > root.height / 2)
    }

    @Test
    fun aSwipeUpLiftsTheSheetAndBackPutsItAway() {
        val activity = homeWithApps("Maps")
        val root = activity.findViewById<View>(R.id.mainLayout)

        swipe(root, fromY = root.height * 0.6f, toY = root.height * 0.2f)
        settle()

        assertTrue(isSheetOpen(activity))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.appList).visibility)
        // The bar has ridden up to the top of the screen.
        val bar = Rect().also { activity.findViewById<View>(R.id.searchBar).getGlobalVisibleRect(it) }
        assertTrue(bar.top < root.height / 4)

        activity.onBackPressedDispatcher.onBackPressed()
        settle()

        assertFalse(isSheetOpen(activity))
        assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.appList).visibility)
    }

    @Test
    fun aSwipeDownFromTheBarPutsTheSheetAway() {
        val activity = homeWithApps("Maps")
        val root = activity.findViewById<View>(R.id.mainLayout)
        swipe(root, fromY = root.height * 0.6f, toY = root.height * 0.2f)
        settle()

        val bar = Rect().also { activity.findViewById<View>(R.id.searchBar).getGlobalVisibleRect(it) }
        swipe(root, fromY = bar.exactCenterY(), toY = root.height * 0.8f)
        settle()

        assertFalse(isSheetOpen(activity))
    }

    @Test
    fun tappingTheBarLiftsTheSheet() {
        val activity = homeWithApps("Maps")

        activity.findViewById<EditText>(R.id.searchInput).performClick()
        settle()

        assertTrue(isSheetOpen(activity))
    }

    @Test
    fun tappingAnAppOpensIt() {
        val activity = openSheetWithApps("Maps", "Weather")

        type(activity, "map")
        rowTitle(activity, 0).performClick()
        idle()

        // The fake app is not really installed, so the launch itself reports it is missing.
        assertEquals(activity.getString(R.string.app_not_found), ShadowToast.getTextOfLatestToast())
        assertNoWebSearch(activity)
        assertEquals("", activity.findViewById<EditText>(R.id.searchInput).text.toString())
    }

    @Test
    fun aWorkProfileCopyIsLabelledWithItsProfile() {
        val activity = openSheetWithApps("Slack")
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
        viewModel.appList.value = viewModel.appList.value!! + fakeApp("Slack", UserHandle.getUserHandleForUid(10 * 100000))
        idle()

        type(activity, "slack")

        assertEquals(listOf("Slack", "Slack"), listedLabels(activity))
        assertTrue(rowTitle(activity, 1).contentDescription.contains(activity.getString(R.string.work_profile)))
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
        setApps(activity, *labels)
        return activity
    }

    /** Opening the sheet reloads the real (empty) app list, so the fakes go in afterwards. */
    private fun openSheetWithApps(vararg labels: String): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()
        activity.findViewById<View>(R.id.mainLayout).dispatchGenericMotionEvent(wheelEvent())
        settle()
        assertTrue(isSheetOpen(activity))
        setApps(activity, *labels)
        return activity
    }

    private fun setApps(activity: MainActivity, vararg labels: String) {
        ViewModelProvider(activity)[MainViewModel::class.java].appList.value = labels.map { fakeApp(it) }
        idle()
    }

    /** Sets the text without focusing the field, which would open the sheet and reload the apps. */
    private fun type(activity: MainActivity, text: String) {
        activity.findViewById<EditText>(R.id.searchInput).setText(text)
        idle()
    }

    private fun adapter(activity: MainActivity) =
        activity.findViewById<RecyclerView>(R.id.appList).adapter as AppDrawerAdapter

    /** The apps listed under the bar, in order, without group toggles or the padding row. */
    private fun listedLabels(activity: MainActivity): List<String> =
        adapter(activity).appFilteredList
            .filterIsInstance<AppModel.App>()
            .map { it.appLabel }
            .filter { it.isNotEmpty() }

    /** The title of the row at [position], once the list has caught up and laid it out. */
    private fun rowTitle(activity: MainActivity, position: Int): View {
        val list = activity.findViewById<RecyclerView>(R.id.appList)
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (true) {
            idle()
            // The list diffs off the main thread; wait for it to show what was asked for.
            if (adapter(activity).currentList == adapter(activity).appFilteredList) {
                list.measure(
                    View.MeasureSpec.makeMeasureSpec(list.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(list.height, View.MeasureSpec.EXACTLY),
                )
                list.layout(list.left, list.top, list.right, list.bottom)
                list.findViewHolderForAdapterPosition(position)?.let { return it.itemView.findViewById(R.id.appTitle) }
            }
            check(SystemClock.uptimeMillis() < deadline) { "row $position never appeared" }
            Thread.sleep(10)
        }
    }

    private fun isSheetOpen(activity: MainActivity): Boolean =
        activity.findViewById<View>(R.id.sheet).translationY == 0f

    private fun swipe(target: View, fromY: Float, toY: Float) {
        val x = target.width / 2f
        val start = SystemClock.uptimeMillis()
        fun event(action: Int, y: Float, t: Long) = MotionEvent.obtain(start, start + t, action, x, y, 0)
        target.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, fromY, 0))
        for (step in 1..10) {
            target.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, fromY + (toY - fromY) * step / 10, step * 16L))
        }
        target.dispatchTouchEvent(event(MotionEvent.ACTION_UP, toY, 176))
    }

    private fun wheelEvent(): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            x = 200f
            y = 600f
            setAxisValue(MotionEvent.AXIS_VSCROLL, -1f)
        }
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(
            now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0,
        )
    }

    private fun assertNoWebSearch(activity: MainActivity) {
        val started = shadowOf(activity).nextStartedActivity
        assertTrue(started == null || started.action != Intent.ACTION_VIEW)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Lets the sheet's animation run to its end. */
    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
}
