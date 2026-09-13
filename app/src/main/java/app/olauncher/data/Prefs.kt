package app.olauncher.data

import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

class Prefs(context: Context) {
    private val PREFS_FILENAME = "app.olauncher"

    private val FIRST_OPEN = "FIRST_OPEN"
    private val FIRST_SETTINGS_OPEN = "FIRST_SETTINGS_OPEN"
    private val USER_STATE = "USER_STATE"
    private val SEARCH_DRAFT = "SEARCH_DRAFT"
    private val SEARCH_ENGINE = "SEARCH_ENGINE"
    private val AUTO_SHOW_KEYBOARD = "AUTO_SHOW_KEYBOARD"
    private val HOME_ALIGNMENT = "HOME_ALIGNMENT"
    private val APP_LABEL_ALIGNMENT = "APP_LABEL_ALIGNMENT"
    private val DATE_BOLD = "DATE_BOLD"
    private val SWIPE_LEFT_ENABLED = "SWIPE_LEFT_ENABLED"
    private val SWIPE_RIGHT_ENABLED = "SWIPE_RIGHT_ENABLED"
    private val APP_THEME = "APP_THEME"
    private val SWIPE_DOWN_ACTION = "SWIPE_DOWN_ACTION"
    private val TEXT_SIZE_SCALE = "TEXT_SIZE_SCALE"
    private val HIDE_SET_DEFAULT_LAUNCHER = "HIDE_SET_DEFAULT_LAUNCHER"
    private val LAUNCHER_RESTART_TIMESTAMP = "LAUNCHER_RECREATE_TIMESTAMP"
    private val APP_CATEGORY_OVERRIDE_PREFIX = "APP_CATEGORY_OVERRIDE_"
    private val EMPHASIZED_APPS = "EMPHASIZED_APPS"
    private val PINNED_CATEGORY = "PINNED_CATEGORY"
    private val PINNED_CATEGORIES = "PINNED_CATEGORIES"
    private val CATEGORY_USAGE_DATA = "CATEGORY_USAGE_DATA"
    // Home button for recents feature disabled
    // private val HOME_BUTTON_SHOW_RECENTS = "HOME_BUTTON_SHOW_RECENTS"

    private val APP_NAME_SWIPE_LEFT = "APP_NAME_SWIPE_LEFT"
    private val APP_NAME_SWIPE_RIGHT = "APP_NAME_SWIPE_RIGHT"
    private val APP_PACKAGE_SWIPE_LEFT = "APP_PACKAGE_SWIPE_LEFT"
    private val APP_PACKAGE_SWIPE_RIGHT = "APP_PACKAGE_SWIPE_RIGHT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT = "APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT = "APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT"
    private val APP_USER_SWIPE_LEFT = "APP_USER_SWIPE_LEFT"
    private val APP_USER_SWIPE_RIGHT = "APP_USER_SWIPE_RIGHT"
    private val CALENDAR_APP_PACKAGE = "CALENDAR_APP_PACKAGE"
    private val CALENDAR_APP_USER = "CALENDAR_APP_USER"
    private val CALENDAR_APP_CLASS_NAME = "CALENDAR_APP_CLASS_NAME"
    private val PASSWORD_APP_NAME = "PASSWORD_APP_NAME"
    private val PASSWORD_APP_PACKAGE = "PASSWORD_APP_PACKAGE"
    private val PASSWORD_APP_USER = "PASSWORD_APP_USER"
    private val PASSWORD_APP_CLASS_NAME = "PASSWORD_APP_CLASS_NAME"

    private val SHORTCUT_ID_SWIPE_LEFT = "SHORTCUT_ID_SWIPE_LEFT"
    private val IS_SHORTCUT_SWIPE_LEFT = "IS_SHORTCUT_SWIPE_LEFT"
    private val SHORTCUT_ID_SWIPE_RIGHT = "SHORTCUT_ID_SWIPE_RIGHT"
    private val IS_SHORTCUT_SWIPE_RIGHT = "IS_SHORTCUT_SWIPE_RIGHT"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0)

    init {
        val obsoleteKeys = arrayOf(
            "SCREEN_TIME_LAST_UPDATED",
            "SCREEN_TIME_APP_PACKAGE",
            "SCREEN_TIME_APP_USER",
            "SCREEN_TIME_APP_CLASS_NAME",
            "WALLPAPER_MSG_SHOWN",
            "FIRST_OPEN_TIME",
            "KEYBOARD_MESSAGE",
            "SHOW_HINT_COUNTER",
            "ABOUT_CLICKED",
            "RATE_CLICKED",
            "SHARE_SHOWN_TIME",
            "PRO_MESSAGE_SHOWN",
            "SHOWN_ON_DAY_OF_YEAR",
            "FIRST_HIDE",
            // Hiding apps was removed; groups with an emphasized app collapse instead.
            "HIDDEN_APPS",
            "HIDDEN_APPS_UPDATED",
            "ROUTINE_READING_START",
            "ROUTINE_COMMUTE_START",
            "ROUTINE_WORK_START",
            "ROUTINE_FITNESS_START",
            "ROUTINE_FAMILY_START",
            "ROUTINE_EVENING_START",
            "VACATION_MODE",
            "LOCK_MODE",
            // Pinned home-screen apps were replaced by the search bar and password shortcut.
            "HOME_APPS_NUM",
            "HOME_APP_WEIGHT",
            "HOME_BOTTOM_ALIGNMENT",
            "SHOW_CLOCK",
            *(1..8).flatMap { slot ->
                listOf(
                    "APP_NAME_$slot",
                    "APP_PACKAGE_$slot",
                    "APP_ACTIVITY_CLASS_NAME_$slot",
                    "APP_USER_$slot",
                    "IS_SHORTCUT_$slot",
                    "SHORTCUT_ID_$slot",
                )
            }.toTypedArray(),
        )
        if (obsoleteKeys.any(prefs::contains)) {
            prefs.edit { obsoleteKeys.forEach(::remove) }
        }
        migratePinnedCategory()
    }

    // The single pinned group grew into an ordered list; carry the old choice over once.
    private fun migratePinnedCategory() {
        if (!prefs.contains(PINNED_CATEGORY)) return
        if (!prefs.contains(PINNED_CATEGORIES)) {
            val stored = prefs.getString(PINNED_CATEGORY, null)
            val migrated = when {
                stored == null || stored == "NONE" -> ""
                else -> runCatching { AppCategory.valueOf(stored).name }
                    .getOrDefault(AppCategory.AI_AGENTS.name)
            }
            prefs.edit { putString(PINNED_CATEGORIES, migrated) }
        }
        prefs.edit { remove(PINNED_CATEGORY) }
    }

    var firstOpen: Boolean
        get() = prefs.getBoolean(FIRST_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_OPEN, value).apply() }

    var firstSettingsOpen: Boolean
        get() = prefs.getBoolean(FIRST_SETTINGS_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_SETTINGS_OPEN, value).apply() }

    var userState: String
        get() = prefs.getString(USER_STATE, Constants.UserState.START).toString()
        set(value) = prefs.edit { putString(USER_STATE, value).apply() }

    var autoShowKeyboard: Boolean
        get() = prefs.getBoolean(AUTO_SHOW_KEYBOARD, true)
        set(value) = prefs.edit { putBoolean(AUTO_SHOW_KEYBOARD, value).apply() }

    /** Unsent text in the home-screen search bar; kept until it is sent or cleared. */
    var searchDraft: String
        get() = prefs.getString(SEARCH_DRAFT, "").toString()
        set(value) = prefs.edit {
            if (value.isBlank()) remove(SEARCH_DRAFT) else putString(SEARCH_DRAFT, value)
        }

    /** Where the home-screen search bar sends its text. */
    var searchEngine: SearchEngine
        get() = SearchEngine.fromName(prefs.getString(SEARCH_ENGINE, null))
        set(value) = prefs.edit { putString(SEARCH_ENGINE, value.name) }

    var homeAlignment: Int
        get() = prefs.getInt(HOME_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(HOME_ALIGNMENT, value).apply() }

    var appLabelAlignment: Int
        get() = prefs.getInt(APP_LABEL_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(APP_LABEL_ALIGNMENT, value).apply() }

    /** Draws the home-screen date in the same medium face the emphasized apps use. */
    var dateBold: Boolean
        get() = prefs.getBoolean(DATE_BOLD, false)
        set(value) = prefs.edit { putBoolean(DATE_BOLD, value).apply() }

    /** Ordered groups that always stay on top of the drawer. */
    var pinnedCategories: List<AppCategory>
        get() {
            val stored = prefs.getString(PINNED_CATEGORIES, null)
                ?: return listOf(AppCategory.AI_AGENTS)
            return stored.split(',')
                .filter { it.isNotBlank() }
                .mapNotNull { runCatching { AppCategory.valueOf(it) }.getOrNull() }
                .distinct()
        }
        set(value) = prefs.edit {
            putString(PINNED_CATEGORIES, value.distinct().joinToString(",") { it.name })
        }

    /** Locally learned launch weights used by SmartOrder. Never leaves the device. */
    var categoryUsageData: String?
        get() = prefs.getString(CATEGORY_USAGE_DATA, null)
        set(value) = prefs.edit { putString(CATEGORY_USAGE_DATA, value) }

    fun clearCategoryUsageData() = prefs.edit { remove(CATEGORY_USAGE_DATA) }

    var swipeLeftEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_LEFT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_LEFT_ENABLED, value).apply() }

    var swipeRightEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_RIGHT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_RIGHT_ENABLED, value).apply() }

    var appTheme: Int
        get() = prefs.getInt(APP_THEME, AppCompatDelegate.MODE_NIGHT_YES)
        set(value) = prefs.edit { putInt(APP_THEME, value).apply() }

    var textSizeScale: Float
        get() = prefs.getFloat(TEXT_SIZE_SCALE, 1.0f)
        set(value) = prefs.edit { putFloat(TEXT_SIZE_SCALE, value).apply() }

    var hideSetDefaultLauncher: Boolean
        get() = prefs.getBoolean(HIDE_SET_DEFAULT_LAUNCHER, false)
        set(value) = prefs.edit { putBoolean(HIDE_SET_DEFAULT_LAUNCHER, value).apply() }

    var launcherRestartTimestamp: Long
        get() = prefs.getLong(LAUNCHER_RESTART_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(LAUNCHER_RESTART_TIMESTAMP, value).apply() }

    // Home button for recents feature disabled
    // var homeButtonShowRecents: Boolean
    //     get() = prefs.getBoolean(HOME_BUTTON_SHOW_RECENTS, false)
    //     set(value) = prefs.edit { putBoolean(HOME_BUTTON_SHOW_RECENTS, value).apply() }

    var swipeDownAction: Int
        get() = prefs.getInt(SWIPE_DOWN_ACTION, Constants.SwipeDownAction.NOTIFICATIONS)
        set(value) = prefs.edit { putInt(SWIPE_DOWN_ACTION, value).apply() }

    var appNameSwipeLeft: String
        get() = prefs.getString(APP_NAME_SWIPE_LEFT, "Camera").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_LEFT, value).apply() }

    var appNameSwipeRight: String
        get() = prefs.getString(APP_NAME_SWIPE_RIGHT, "Phone").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_RIGHT, value).apply() }

    var appPackageSwipeLeft: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_LEFT, value).apply() }

    var appActivityClassNameSwipeLeft: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, value).apply() }

    var appPackageSwipeRight: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_RIGHT, value).apply() }

    var appActivityClassNameRight: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, value).apply() }

    var appUserSwipeLeft: String
        get() = prefs.getString(APP_USER_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_LEFT, value).apply() }

    var appUserSwipeRight: String
        get() = prefs.getString(APP_USER_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_RIGHT, value).apply() }

    var calendarAppPackage: String
        get() = prefs.getString(CALENDAR_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_PACKAGE, value).apply() }

    var calendarAppUser: String
        get() = prefs.getString(CALENDAR_APP_USER, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_USER, value).apply() }

    var calendarAppClassName: String?
        get() = prefs.getString(CALENDAR_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_CLASS_NAME, value).apply() }

    /** The app behind the home-screen password shortcut; blank until chosen or auto-detected. */
    var passwordAppName: String
        get() = prefs.getString(PASSWORD_APP_NAME, "").toString()
        set(value) = prefs.edit { putString(PASSWORD_APP_NAME, value) }

    var passwordAppPackage: String
        get() = prefs.getString(PASSWORD_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(PASSWORD_APP_PACKAGE, value) }

    var passwordAppUser: String
        get() = prefs.getString(PASSWORD_APP_USER, "").toString()
        set(value) = prefs.edit { putString(PASSWORD_APP_USER, value) }

    var passwordAppClassName: String?
        get() = prefs.getString(PASSWORD_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(PASSWORD_APP_CLASS_NAME, value) }

    fun clearPasswordApp() = prefs.edit {
        remove(PASSWORD_APP_NAME)
        remove(PASSWORD_APP_PACKAGE)
        remove(PASSWORD_APP_USER)
        remove(PASSWORD_APP_CLASS_NAME)
    }

    var shortcutIdSwipeLeft: String
        get() = prefs.getString(SHORTCUT_ID_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_SWIPE_LEFT, value) }

    var isShortcutSwipeLeft: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_SWIPE_LEFT, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_SWIPE_LEFT, value) }

    var shortcutIdSwipeRight: String
        get() = prefs.getString(SHORTCUT_ID_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_SWIPE_RIGHT, value) }

    var isShortcutSwipeRight: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_SWIPE_RIGHT, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_SWIPE_RIGHT, value) }

    fun updateAppActivityClassName(packageName: String, activityClassName: String) {
        if (calendarAppPackage == packageName) calendarAppClassName = activityClassName
        if (passwordAppPackage == packageName) passwordAppClassName = activityClassName
        if (appPackageSwipeLeft == packageName) appActivityClassNameSwipeLeft = activityClassName
        if (appPackageSwipeRight == packageName) appActivityClassNameRight = activityClassName
    }

    fun getAppRenameLabel(appPackage: String): String = prefs.getString(appPackage, "").toString()

    fun setAppRenameLabel(appPackage: String, renameLabel: String) = prefs.edit { putString(appPackage, renameLabel) }

    fun getAppCategoryOverrides(appPackage: String): List<AppCategory>? {
        val raw = prefs.getString(APP_CATEGORY_OVERRIDE_PREFIX + appPackage, null) ?: return null
        val categories = raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { runCatching { AppCategory.valueOf(it) }.getOrNull() }
            .distinct()
        return categories.ifEmpty { null }
    }

    /** First manual category, or null when using automatic placement. Legacy single-value keys still work. */
    fun getAppCategoryOverride(appPackage: String): AppCategory? =
        getAppCategoryOverrides(appPackage)?.firstOrNull()

    fun setAppCategoryOverride(appPackage: String, category: AppCategory) =
        setAppCategoryOverrides(appPackage, listOf(category))

    fun setAppCategoryOverrides(appPackage: String, categories: Collection<AppCategory>) {
        val distinct = categories.distinct()
        if (distinct.isEmpty()) {
            clearAppCategoryOverride(appPackage)
            return
        }
        prefs.edit {
            putString(
                APP_CATEGORY_OVERRIDE_PREFIX + appPackage,
                distinct.joinToString(",") { it.name },
            )
        }
    }

    fun clearAppCategoryOverride(appPackage: String) =
        prefs.edit { remove(APP_CATEGORY_OVERRIDE_PREFIX + appPackage) }

    fun clearAppCategoryOverrides() = prefs.edit {
        prefs.all.keys.filter { it.startsWith(APP_CATEGORY_OVERRIDE_PREFIX) }.forEach(::remove)
    }

    /** Emphasis keys (see [AppModel.emphasisKey]) of apps that render bold and first in their group. */
    var emphasizedApps: Set<String>
        get() = prefs.getStringSet(EMPHASIZED_APPS, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet(EMPHASIZED_APPS, value.toSet()) }

    fun isAppEmphasized(key: String): Boolean =
        key.isNotBlank() && emphasizedApps.contains(key)

    fun setAppEmphasized(key: String, emphasized: Boolean) {
        if (key.isBlank()) return
        val next = emphasizedApps.toMutableSet()
        if (emphasized) next.add(key) else next.remove(key)
        emphasizedApps = next
    }

    /** Flips emphasis for [key] and returns the new state. */
    fun toggleAppEmphasized(key: String): Boolean {
        val next = !isAppEmphasized(key)
        setAppEmphasized(key, next)
        return next
    }
}
