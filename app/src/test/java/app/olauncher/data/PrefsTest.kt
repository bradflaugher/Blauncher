package app.olauncher.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrefsTest {

    private val prefs = Prefs(ApplicationProvider.getApplicationContext())

    @Test
    fun aBlankRenameRestoresTheOriginalLabel() {
        prefs.setAppRenameLabel("com.example.mail", "Inbox")
        assertEquals("Inbox", prefs.getAppRenameLabel("com.example.mail"))

        // Saving an emptied rename field must not pin some other name onto the row.
        prefs.setAppRenameLabel("com.example.mail", "  ")
        assertEquals("", prefs.getAppRenameLabel("com.example.mail"))
    }
}
