package app.olauncher

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.olauncher.data.Prefs
import app.olauncher.helper.Onboarding
import app.olauncher.helper.Tip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The debug-only tip extras run once per launch, not again on every recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DebugTipExtrasTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun skipTipsAppliesOnceAndSurvivesRecreation() {
        assumeTrue("the extras exist only in debug builds", BuildConfig.DEBUG)
        val prefs = Prefs(context).apply {
            firstOpen = false
            launcherRestartTimestamp = System.currentTimeMillis()
            resetTips()
        }
        val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SKIP_TIPS, true)
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()

        assertNull(Onboarding.nextHomeTip(prefs.learnedTips))
        assertFalse(controller.get().intent.hasExtra(MainActivity.EXTRA_SKIP_TIPS))

        // Show tips again, then rotate: the launch extra must not skip them a second time.
        prefs.resetTips()
        controller.recreate()
        assertEquals(Tip.OPEN_DRAWER, Onboarding.nextHomeTip(Prefs(context).learnedTips))
    }
}
