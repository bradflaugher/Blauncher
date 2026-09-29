package app.olauncher.ui

import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.test.core.app.ApplicationProvider
import app.olauncher.MainActivity
import app.olauncher.R
import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Input arrives in bursts: one mouse-wheel flick is several scroll events, and a quick double
 * tap is two clicks. Each burst must open exactly one screen, never a stack of copies.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class NavigationBurstTest {

    @Before
    fun setUp() {
        Prefs(ApplicationProvider.getApplicationContext()).apply {
            firstOpen = false
            // Keep the periodic launcher restart from recreating the activity mid-test.
            launcherRestartTimestamp = System.currentTimeMillis()
        }
    }

    @Test
    fun aWheelFlickOpensOneDrawer() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val home = activity.findViewById<View>(R.id.mainLayout)

        repeat(6) { home.dispatchGenericMotionEvent(wheelEvent(home)) }
        idle()

        val navController = activity.findNavController(R.id.nav_host_fragment)
        assertEquals(R.id.appListFragment, navController.currentDestination?.id)
        assertEquals(listOf(R.id.mainFragment, R.id.appListFragment), navController.stackIds())
    }

    @Test
    fun repeatedRightClicksOpenOneSettings() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val home = activity.findViewById<View>(R.id.mainLayout)

        repeat(3) { home.performContextClick() }
        idle()

        val navController = activity.findNavController(R.id.nav_host_fragment)
        assertEquals(listOf(R.id.mainFragment, R.id.settingsFragment), navController.stackIds())
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun NavController.stackIds(): List<Int> =
        currentBackStack.value.map { it.destination.id }.filter { it != R.id.nav_graph }

    private fun wheelEvent(target: View): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            // Over the empty space between the date and the tip card.
            x = target.width / 2f
            y = target.height / 3f
            setAxisValue(MotionEvent.AXIS_VSCROLL, -1f)
        }
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(
            now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0,
        )
    }
}
