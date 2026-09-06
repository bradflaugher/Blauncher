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
            is App -> "$appPackage|$user"
            is PinnedShortcut -> "shortcut:$shortcutId|$user"
            is PrivateSpaceHeader -> ""
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
