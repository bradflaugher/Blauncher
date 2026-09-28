package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingTest {

    @Test
    fun startsWithTheDrawerTip() {
        assertEquals(Tip.OPEN_DRAWER, Onboarding.nextHomeTip(emptySet()))
    }

    @Test
    fun movesToSettingsOnceTheDrawerIsLearned() {
        assertEquals(Tip.OPEN_SETTINGS, Onboarding.nextHomeTip(setOf(Tip.OPEN_DRAWER)))
    }

    @Test
    fun anUnlearnedEarlierTipStillComesFirst() {
        // Long-pressing for settings before ever swiping up must not skip the drawer tip.
        assertEquals(Tip.OPEN_DRAWER, Onboarding.nextHomeTip(setOf(Tip.OPEN_SETTINGS)))
    }

    @Test
    fun drawerTipDoesNotBlockTheHomeTour() {
        assertEquals(Tip.OPEN_DRAWER, Onboarding.nextHomeTip(setOf(Tip.APP_MENU)))
    }

    @Test
    fun noHomeTipOnceBothAreLearned() {
        assertNull(Onboarding.nextHomeTip(setOf(Tip.OPEN_DRAWER, Tip.OPEN_SETTINGS)))
    }

    @Test
    fun stepsAreNumberedFromOne() {
        assertEquals(1, Onboarding.homeStep(Tip.OPEN_DRAWER))
        assertEquals(2, Onboarding.homeStep(Tip.OPEN_SETTINGS))
        assertEquals(2, Onboarding.homeTips.size)
    }

    @Test
    fun parseIgnoresUnknownNames() {
        assertEquals(
            setOf(Tip.OPEN_DRAWER, Tip.APP_MENU),
            Onboarding.parse(setOf("OPEN_DRAWER", "APP_MENU", "SOMETHING_REMOVED")),
        )
    }
}
