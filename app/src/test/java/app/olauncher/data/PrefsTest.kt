package app.olauncher.data

import android.os.Process
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun oldShortcutSettingsMoveToTheQualifiedKey() {
        val mail = shortcut("com.example.mail", "compose")
        val legacyEmphasis = AppModel.legacyShortcutKey("compose", user.toString())
        prefs.emphasizedApps = setOf(legacyEmphasis)
        prefs.setAppRenameLabel("compose", "Write")

        assertTrue(prefs.migrateShortcutKeys(mail))

        assertEquals(setOf(mail.emphasisKey), prefs.emphasizedApps)
        assertEquals("Write", prefs.getAppRenameLabel(mail.emphasisKey))
        assertEquals("", prefs.getAppRenameLabel("compose"))
        // A second shortcut with the same id finds nothing left to claim.
        assertFalse(prefs.migrateShortcutKeys(shortcut("com.example.maps", "compose")))
    }
}
