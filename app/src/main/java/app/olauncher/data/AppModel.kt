package app.olauncher.data

import android.os.UserHandle
import java.text.CollationKey

sealed class AppModel : Comparable<AppModel> {
    abstract val appLabel: String
    abstract val key: CollationKey?
    abstract val appPackage: String
    abstract val user: UserHandle
    abstract val isNew: Boolean
    abstract val category: AppCategory?

    /**
     * Stable prefs key for one app or pinned shortcut in one profile: a shortcut's rename is
     * stored under it, and search uses it to list each app once. Empty for rows that are not apps.
     */
    val identityKey: String
        get() = when (this) {
            is App -> identityKeyFor(appPackage, user.toString(), null)
            is PinnedShortcut -> identityKeyFor(appPackage, user.toString(), shortcutId)
            is PrivateSpaceHeader, is GroupHeader -> ""
        }

    companion object {
        /**
         * The identity key for an app or pinned shortcut stored as package + user string. A
         * shortcut's key also names its package, since shortcut ids are only unique per app.
         */
        fun identityKeyFor(appPackage: String, userString: String, shortcutId: String?): String = when {
            appPackage.isBlank() -> ""
            shortcutId.isNullOrBlank() -> "$appPackage|$userString"
            else -> "shortcut:$appPackage/$shortcutId|$userString"
        }
    }

    data class App(
        override val appLabel: String,
        override val key: CollationKey?,
        override val appPackage: String,
        val activityClassName: String?,
        override val isNew: Boolean = false,
        override val user: UserHandle,
        override val category: AppCategory = AppCategory.OTHER,
    ) : AppModel()

    data class PinnedShortcut(
        override val appLabel: String,
        override val key: CollationKey?,
        override val appPackage: String,
        val shortcutId: String,
        override val isNew: Boolean = false,
        override val user: UserHandle,
        override val category: AppCategory = AppCategory.OTHER,
    ) : AppModel()

    data class PrivateSpaceHeader(
        val isLocked: Boolean = true,
        override val user: UserHandle = android.os.Process.myUserHandle(),
    ) : AppModel() {
        override val appLabel: String = ""
        override val key: CollationKey? = null
        override val appPackage: String = ""
        override val isNew: Boolean = false
        override val category: AppCategory? = null
    }

    /**
     * A category's row in the drawer while nothing is typed: its glyph, name and app count. A
     * tap opens it to list its apps below, closing whichever other was open. [sectionKey] tells
     * the main list's groups from Private Space's. Built by the drawer adapter, never stored.
     */
    data class GroupHeader(
        val group: AppCategory,
        val sectionKey: String,
        val appCount: Int,
        val hasNewApp: Boolean,
        val expanded: Boolean,
        override val user: UserHandle = android.os.Process.myUserHandle(),
    ) : AppModel() {
        override val appLabel: String = group.displayName
        override val key: CollationKey? = null
        override val appPackage: String = ""
        override val isNew: Boolean = false
        override val category: AppCategory = group
    }

    override fun compareTo(other: AppModel): Int = when {
        key != null && other.key != null -> key!!.compareTo(other.key)
        else -> appLabel.compareTo(other.appLabel, true)
    }
}
