package app.olauncher.data

import android.content.Context
import android.os.Process
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.olauncher.helper.Onboarding
import app.olauncher.helper.Tip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrefsTest {

    private val prefs = Prefs(ApplicationProvider.getApplicationContext())
    private val user = Process.myUserHandle()

    private fun shortcut(appPackage: String, id: String) = AppModel.PinnedShortcut(
        appLabel = "",
        key = null,
        appPackage = appPackage,
        shortcutId = id,
        user = user,
    )

    @Test
    fun aBlankRenameRestoresTheOriginalLabel() {
        prefs.setAppRenameLabel("com.example.mail", "Inbox")
        assertEquals("Inbox", prefs.getAppRenameLabel("com.example.mail"))

        // Saving an emptied rename field must not pin some other name onto the row.
        prefs.setAppRenameLabel("com.example.mail", "  ")
        assertEquals("", prefs.getAppRenameLabel("com.example.mail"))
    }

    @Test
    fun shortcutsSharingAnIdKeepSeparateRenames() {
        val mail = shortcut("com.example.mail", "1")
        val maps = shortcut("com.example.maps", "1")
        assertNotEquals(mail.identityKey, maps.identityKey)

        prefs.setAppRenameLabel(mail.identityKey, "Compose")

        assertEquals("", prefs.getAppRenameLabel(maps.identityKey))
    }

    @Test
    fun aShortcutIdNamedLikeASettingLeavesTheSettingAlone() {
        prefs.searchDraft = "half-written query"

        assertEquals("", prefs.getAppRenameLabel(shortcut("com.example.notes", "SEARCH_DRAFT").identityKey))
        assertEquals("half-written query", prefs.searchDraft)
    }

    @Test
    fun emphasisLeftOverFromOlderBuildsIsCleared() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app.olauncher", 0).edit(commit = true) {
            putStringSet("EMPHASIZED_APPS", setOf("com.example.mail|0"))
        }

        Prefs(context)

        assertFalse(context.getSharedPreferences("app.olauncher", 0).contains("EMPHASIZED_APPS"))
    }

    @Test
    fun skippingTheTipsSkipsEveryHomeTip() {
        prefs.resetTips()
        prefs.learnAllTips()
        assertNull(Onboarding.nextHomeTip(prefs.learnedTips))
    }

    @Test
    fun anUpdateFromTheTwoTipTourTeachesOnlyTheSearchBar() {
        // What a build before the date-and-key tip left behind after its tour was done.
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app.olauncher", 0).edit(commit = true) {
            putStringSet("LEARNED_TIPS", setOf("OPEN_DRAWER", "OPEN_SETTINGS"))
            remove("TIPS_REVISION")
        }

        val upgraded = Prefs(context)

        assertTrue(Tip.HOME_SHORTCUTS in upgraded.learnedTips)
        // Swipe up no longer opens the apps, so the tip for the search bar comes back.
        assertEquals(Tip.OPEN_DRAWER, Onboarding.nextHomeTip(upgraded.learnedTips))
        upgraded.learnTip(Tip.OPEN_DRAWER)
        assertNull(Onboarding.nextHomeTip(Prefs(context).learnedTips))
        // Show tips again brings the whole tour back, the new tip included.
        upgraded.resetTips()
        assertEquals(Tip.OPEN_DRAWER, Onboarding.nextHomeTip(Prefs(context).learnedTips))
    }

    @Test
    fun theShortcutButtonWearsTheKeyUntilAnotherGlyphIsPicked() {
        assertEquals(ShortcutGlyph.KEY, prefs.shortcutGlyph)

        prefs.shortcutGlyph = ShortcutGlyph.CAMERA

        assertEquals(ShortcutGlyph.CAMERA, Prefs(ApplicationProvider.getApplicationContext()).shortcutGlyph)
    }

    @Test
    fun aGlyphNoLongerOfferedFallsBackToTheKey() {
        assertEquals(ShortcutGlyph.KEY, ShortcutGlyph.fromName("RETIRED"))
        assertEquals(ShortcutGlyph.KEY, ShortcutGlyph.fromName(null))
    }
}
