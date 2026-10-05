package app.olauncher.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.olauncher.MainActivity
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * Settings → Rate Blauncher opens the Play listing, in the Play Store app when there is one and
 * else in the browser, and says so when neither can open it. It is only ever a tap: nothing in
 * the launcher starts the store by itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RateAppTest {

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun setUp() {
        Prefs(app).apply {
            firstOpen = false
            // Keep the periodic launcher restart from recreating the activity mid-test.
            launcherRestartTimestamp = System.currentTimeMillis()
        }
        // Starting an intent nothing handles throws, as on a device.
        shadowOf(app).checkActivities(true)
    }

    @Test
    fun theEntryIsAPlainTargetInTheBlauncherCard() {
        val activity = openSettings()
        val rate = activity.findViewById<TextView>(R.id.rateApp)

        assertEquals(View.VISIBLE, rate.visibility)
        assertEquals(activity.getString(R.string.rate_app), rate.text.toString())
        assertTrue(rate.isClickable)
        assertTrue(rate.minHeight >= 48 * activity.resources.displayMetrics.density)
        // Opening the launcher and its settings never sends anyone to the store.
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun opensThePlayStoreAppWhenThereIsOne() {
        handle("market", "store")
        handle("https", "browser")
        val activity = openSettings()

        activity.findViewById<View>(R.id.rateApp).performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(Constants.URL_PLAY_STORE_APP, started.dataString)
    }

    @Test
    fun fallsBackToTheWebListing() {
        handle("https", "browser")
        val activity = openSettings()

        activity.findViewById<View>(R.id.rateApp).performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(Constants.URL_PLAY_STORE, started.dataString)
    }

    @Test
    fun saysSoWhenNothingCanOpenIt() {
        val activity = openSettings()

        activity.findViewById<View>(R.id.rateApp).performClick()

        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(activity.getString(R.string.no_app_for_link), ShadowToast.getTextOfLatestToast())
    }

    private fun openSettings(): Activity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<View>(R.id.mainLayout).performContextClick()
        idle()
        return activity
    }

    /** Registers a stand-in app that views [scheme] links. */
    private fun handle(scheme: String, name: String) {
        val component = ComponentName("test.$name", "test.$name.ViewActivity")
        val packageManager = shadowOf(app.packageManager)
        packageManager.addActivityIfNotPresent(component)
        packageManager.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme(scheme)
            },
        )
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
