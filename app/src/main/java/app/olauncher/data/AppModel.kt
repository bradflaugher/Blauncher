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
    abstract val emphasized: Boolean

    /**
     * True when this row is not emphasized but another row in the same group is; the drawer
     * renders it lighter. Carried on the model so list diffing rebinds every affected row.
     */
    abstract val dimmed: Boolean

    /** Stable prefs key for per-app emphasis. Empty for rows that cannot be emphasized. */
    val emphasisKey: String
        get() = when (this) {
            is App -> emphasisKeyFor(appPackage, user.toString(), null)
            is PinnedShortcut -> emphasisKeyFor(appPackage, user.toString(), shortcutId)
            is PrivateSpaceHeader -> ""
        }

    companion object {
        /** The emphasis key for an app or pinned shortcut stored as package + user string (home slots). */
        fun emphasisKeyFor(appPackage: String, userString: String, shortcutId: String?): String = when {
            appPackage.isBlank() -> ""
            shortcutId.isNullOrBlank() -> "$appPackage|$userString"
            else -> "shortcut:$shortcutId|$userString"
        }
    }

    fun withDimmed(dimmed: Boolean): AppModel = when (this) {
        is App -> if (this.dimmed == dimmed) this else copy(dimmed = dimmed)
        is PinnedShortcut -> if (this.dimmed == dimmed) this else copy(dimmed = dimmed)
        is PrivateSpaceHeader -> this
    }

    data class App(
        override val appLabel: String,
        override val key: CollationKey?,
        override val appPackage: String,
        val activityClassName: String?,
        override val isNew: Boolean = false,
        override val user: UserHandle,
        override val category: AppCategory = AppCategory.OTHER,
        override val emphasized: Boolean = false,
        override val dimmed: Boolean = false,
    ) : AppModel()

    data class PinnedShortcut(
        override val appLabel: String,
        override val key: CollationKey?,
        override val appPackage: String,
        val shortcutId: String,
        override val isNew: Boolean = false,
        override val user: UserHandle,
        override val category: AppCategory = AppCategory.OTHER,
        override val emphasized: Boolean = false,
        override val dimmed: Boolean = false,
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
        override val emphasized: Boolean = false
        override val dimmed: Boolean = false
    }

    override fun compareTo(other: AppModel): Int = when {
        key != null && other.key != null -> key!!.compareTo(other.key)
        else -> appLabel.compareTo(other.appLabel, true)
    }
}
