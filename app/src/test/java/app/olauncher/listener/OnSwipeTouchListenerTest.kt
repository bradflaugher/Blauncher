package app.olauncher.listener

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.test.core.app.ApplicationProvider
import app.olauncher.data.Constants
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class OnSwipeTouchListenerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun holdThen(endAction: Int?): Int {
        var longClicks = 0
        val listener = object : OnSwipeTouchListener(context) {
            override fun onLongClick() {
                longClicks++
            }
        }
        val view = View(context)
        val down = SystemClock.uptimeMillis()
        listener.onTouch(view, MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        // Past the platform long-press timeout: the detector reports a long press.
        shadowOf(Looper.getMainLooper())
            .idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 50L))
        if (endAction != null) {
            val end = SystemClock.uptimeMillis()
            listener.onTouch(view, MotionEvent.obtain(down, end, endAction, 10f, 10f, 0))
        }
        // Then wait out the launcher's own extra delay before it acts.
        Thread.sleep(Constants.LONG_PRESS_DELAY_MS + 200)
        shadowOf(Looper.getMainLooper()).idle()
        return longClicks
    }

    @Test
    fun aHeldPressOpensSettings() {
        assertEquals(1, holdThen(null))
    }

    @Test
    fun aCancelledHoldDoesNotOpenSettings() {
        // The system taking the gesture over (home or back swipe) sends CANCEL, not UP.
        assertEquals(0, holdThen(MotionEvent.ACTION_CANCEL))
    }

    @Test
    fun aReleasedHoldDoesNotOpenSettings() {
        assertEquals(0, holdThen(MotionEvent.ACTION_UP))
    }
}
