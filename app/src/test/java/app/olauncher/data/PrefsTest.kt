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
    fun shortcutsSharingAnIdKeepSeparateSettings() {
        val mail = shortcut("com.example.mail", "1")
        val maps = shortcut("com.example.maps", "1")
        assertNotEquals(mail.emphasisKey, maps.emphasisKey)

        prefs.setAppRenameLabel(mail.emphasisKey, "Compose")
        prefs.setAppEmphasized(mail.emphasisKey, true)

        assertEquals("", prefs.getAppRenameLabel(maps.emphasisKey))
        assertFalse(prefs.isAppEmphasized(maps.emphasisKey))
    }

    @Test
    fun oldShortcutEmphasisMovesToTheQualifiedKey() {
        val mail = shortcut("com.example.mail", "compose")
        prefs.emphasizedApps = setOf(AppModel.legacyShortcutKey("compose", user.toString()))

        assertTrue(prefs.migrateShortcutKeys(mail))

        assertEquals(setOf(mail.emphasisKey), prefs.emphasizedApps)
        // A second shortcut with the same id finds nothing left to claim.
        assertFalse(prefs.migrateShortcutKeys(shortcut("com.example.maps", "compose")))
    }

    @Test
    fun aShortcutIdNamedLikeASettingLeavesTheSettingAlone() {
        prefs.searchDraft = "half-written query"

        prefs.migrateShortcutKeys(shortcut("com.example.notes", "SEARCH_DRAFT"))

        assertEquals("half-written query", prefs.searchDraft)
        assertEquals("", prefs.getAppRenameLabel(shortcut("com.example.notes", "SEARCH_DRAFT").emphasisKey))
    }

    @Test
    fun skippingTheTipsSkipsEveryHomeTip() {
        prefs.resetTips()
        prefs.learnAllTips()
        assertNull(Onboarding.nextHomeTip(prefs.learnedTips))
    }

    @Test
    fun anUpdateFromTheTwoTipTourDoesNotReopenTheCard() {
        // What a build before the date-and-key tip left behind after its tour was done.
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app.olauncher", 0).edit(commit = true) {
            putStringSet("LEARNED_TIPS", setOf("OPEN_DRAWER", "OPEN_SETTINGS"))
            remove("TIPS_REVISION")
        }

        val upgraded = Prefs(context)

        assertTrue(Tip.HOME_SHORTCUTS in upgraded.learnedTips)
        assertNull(Onboarding.nextHomeTip(upgraded.learnedTips))
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
