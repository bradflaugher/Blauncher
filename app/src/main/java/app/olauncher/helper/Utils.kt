package app.olauncher.helper

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import app.olauncher.BuildConfig
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.pow
import kotlin.math.sqrt

fun Context.showToast(message: String?, duration: Int = Toast.LENGTH_SHORT) {
    if (message.isNullOrBlank()) return
    Toast.makeText(this, message, duration).show()
}

fun Context.showToast(stringResource: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, getString(stringResource), duration).show()
}

suspend fun getAppsList(
    context: Context,
    prefs: Prefs,
    includeRegularApps: Boolean = true,
    includeHiddenApps: Boolean = false,
): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val appList: MutableList<AppModel> = mutableListOf()

        try {
            if (!prefs.hiddenAppsUpdated) upgradeHiddenApps(prefs)
            val hiddenApps = prefs.hiddenApps

            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val collator = Collator.getInstance()

            for (profile in userManager.userProfiles) {
                if (isPrivateSpaceProfile(context, profile)) continue
                for (app in launcherApps.getActivityList(null, profile)) {
                    val appLabelShown = prefs.getAppRenameLabel(app.applicationInfo.packageName)
                        .ifBlank { app.label.toString() }
                    val categories = AppCategorizer.categories(
                        context,
                        prefs,
                        app.applicationInfo.packageName,
                        app.label.toString(),
                        app.applicationInfo.category,
                    )
                    val appModels = categories.map { category ->
                        AppModel.App(
                            appLabel = appLabelShown,
                            key = collator.getCollationKey(app.label.toString()),
                            appPackage = app.applicationInfo.packageName,
                            activityClassName = app.componentName.className,
                            isNew = (System.currentTimeMillis() - app.firstInstallTime) < Constants.ONE_HOUR_IN_MILLIS,
                            user = profile,
                            category = category,
                        )
                    }

                    if (app.applicationInfo.packageName != BuildConfig.APPLICATION_ID) {
                        if (hiddenApps.contains(app.applicationInfo.packageName + "|" + profile.toString())) {
                            if (includeHiddenApps) {
                                appList.addAll(appModels)
                            }
                        } else if (includeRegularApps) {
                            appList.addAll(appModels)
                        }
                    }
                }
            }

            if (includeRegularApps) {
                val pinned = try {
                    getPinnedShortcuts(context, prefs, collator)
                } catch (_: Exception) {
                    emptyList()
                }
                appList.addAll(pinned)
            }

            SmartOrder.sort(prefs, appList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        appList
    }
}

private suspend fun getPinnedShortcuts(
    context: Context,
    prefs: Prefs,
    collator: Collator,
): List<AppModel.PinnedShortcut> =
    withContext(Dispatchers.IO) {
        val pinnedShortcuts = mutableListOf<AppModel.PinnedShortcut>()
        val shortcuts = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        if (shortcuts?.hasShortcutHostPermission() == true) {
            val query = LauncherApps.ShortcutQuery().apply {
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            }
            shortcuts.profiles.forEach { profile ->
                if (isPrivateSpaceProfile(context, profile)) return@forEach
                try {
                    shortcuts.getShortcuts(query, profile)?.forEach { shortcut ->
                        if (shortcut.isPinned && pinnedShortcuts.none { it.shortcutId == shortcut.id }) {
                            val label = prefs.getAppRenameLabel(shortcut.id)
                                .takeIf { it.isNotBlank() }
                                ?: shortcut.shortLabel?.toString()
                                ?: shortcut.longLabel?.toString().orEmpty()
                            val categories = AppCategorizer.categories(
                                context,
                                prefs,
                                shortcut.`package`,
                                label,
                            )
                            categories.forEach { category ->
                                pinnedShortcuts.add(
                                    AppModel.PinnedShortcut(
                                        appLabel = label,
                                        key = collator.getCollationKey(label),
                                        appPackage = shortcut.`package`,
                                        shortcutId = shortcut.id,
                                        isNew = false,
                                        user = profile,
                                        category = category,
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        pinnedShortcuts
    }

// One-time migration for installs that stored hidden apps without a user handle.
private fun upgradeHiddenApps(prefs: Prefs) {
    val hiddenAppsSet = prefs.hiddenApps
    val newHiddenAppsSet = mutableSetOf<String>()
    for (hiddenPackage in hiddenAppsSet) {
        if (hiddenPackage.contains("|")) newHiddenAppsSet.add(hiddenPackage)
        else newHiddenAppsSet.add(hiddenPackage + android.os.Process.myUserHandle().toString())
    }
    prefs.hiddenApps = newHiddenAppsSet
    prefs.hiddenAppsUpdated = true
}

fun isPackageInstalled(context: Context, packageName: String, userString: String): Boolean {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val activityInfo = launcher.getActivityList(packageName, getUserHandleFromString(context, userString))
    return activityInfo.isNotEmpty()
}

fun isPrivateSpaceProfile(context: Context, userHandle: UserHandle): Boolean {
    return try {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        launcherApps.getLauncherUserInfo(userHandle)?.userType == "android.os.usertype.profile.PRIVATE"
    } catch (_: Exception) {
        false
    }
}

fun isPrivateSpaceLocked(context: Context, userHandle: UserHandle): Boolean {
    return try {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        userManager.isQuietModeEnabled(userHandle)
    } catch (_: Exception) {
        true
    }
}

fun getPrivateSpaceUserHandle(context: Context): UserHandle? {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    for (profile in userManager.userProfiles) {
        if (isPrivateSpaceProfile(context, profile)) return profile
    }
    return null
}

suspend fun getPrivateSpaceApps(
    context: Context,
    prefs: Prefs,
): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val appList: MutableList<AppModel> = mutableListOf()
        try {
            val privateSpaceHandle = getPrivateSpaceUserHandle(context) ?: return@withContext appList
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val collator = Collator.getInstance()

            for (app in launcherApps.getActivityList(null, privateSpaceHandle)) {
                if (app.applicationInfo.packageName == BuildConfig.APPLICATION_ID) continue
                val appLabelShown = prefs.getAppRenameLabel(app.applicationInfo.packageName)
                    .ifBlank { app.label.toString() }
                val categories = AppCategorizer.categories(
                    context,
                    prefs,
                    app.applicationInfo.packageName,
                    app.label.toString(),
                    app.applicationInfo.category,
                )
                categories.forEach { category ->
                    appList.add(
                        AppModel.App(
                            appLabel = appLabelShown,
                            key = collator.getCollationKey(app.label.toString()),
                            appPackage = app.applicationInfo.packageName,
                            activityClassName = app.componentName.className,
                            isNew = false,
                            user = privateSpaceHandle,
                            category = category,
                        )
                    )
                }
            }
            SmartOrder.sort(prefs, appList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        appList
    }
}

fun getUserHandleFromString(context: Context, userHandleString: String): UserHandle {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    for (userHandle in userManager.userProfiles) {
        if (userHandle.toString() == userHandleString) {
            return userHandle
        }
    }
    return android.os.Process.myUserHandle()
}

fun isOlauncherDefault(context: Context): Boolean {
    val launcherPackageName = getDefaultLauncherPackage(context)
    return BuildConfig.APPLICATION_ID == launcherPackageName
}

fun getDefaultLauncherPackage(context: Context): String {
    val intent = Intent()
    intent.action = Intent.ACTION_MAIN
    intent.addCategory(Intent.CATEGORY_HOME)
    val packageManager = context.packageManager
    val result = packageManager.resolveActivity(intent, 0)
    return if (result?.activityInfo != null) {
        result.activityInfo.packageName
    } else "android"
}

fun getChangedAppTheme(context: Context, currentAppTheme: Int): Int {
    return when (currentAppTheme) {
        AppCompatDelegate.MODE_NIGHT_YES -> AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_YES
        else -> {
            if (context.isDarkThemeOn())
                AppCompatDelegate.MODE_NIGHT_NO
            else AppCompatDelegate.MODE_NIGHT_YES
        }
    }
}

fun openAppInfo(context: Context, userHandle: UserHandle, packageName: String) {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val component = launcher.getActivityList(packageName, userHandle).firstOrNull()?.componentName
    if (component != null)
        launcher.startAppDetailsActivity(component, userHandle, null, null)
    else
        context.showToast(context.getString(R.string.unable_to_open_app_info))
}

fun openSearch(context: Context) {
    val intent = Intent(Intent.ACTION_WEB_SEARCH)
    intent.putExtra(SearchManager.QUERY, "")
    context.startActivity(intent)
}

@SuppressLint("WrongConstant", "PrivateApi")
fun expandNotificationDrawer(context: Context) {
    try {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val method = statusBarManager.getMethod("expandNotificationsPanel")
        method.invoke(statusBarService)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun openDialerApp(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun openCameraApp(context: Context) {
    try {
        context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun openAlarmApp(context: Context) {
    try {
        context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
    } catch (e: Exception) {
        Log.d("TAG", e.toString())
    }
}

@SuppressLint("UnsafeImplicitIntentLaunch")
fun openCalendar(context: Context) {
    try {
        val calendarUri = CalendarContract.CONTENT_URI
            .buildUpon()
            .appendPath("time")
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, calendarUri))
    } catch (_: Exception) {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_APP_CALENDAR)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun isAccessServiceEnabled(context: Context): Boolean {
    val enabled = try {
        Settings.Secure.getInt(context.applicationContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
    } catch (_: Exception) {
        0
    }
    if (enabled == 1) {
        val enabledServicesString: String? = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServicesString?.contains(context.packageName + "/" + MyAccessibilityService::class.java.name) ?: false
    }
    return false
}

fun isTablet(context: Context): Boolean {
    val metrics = context.resources.displayMetrics
    val widthInches = metrics.widthPixels / metrics.xdpi
    val heightInches = metrics.heightPixels / metrics.ydpi
    val diagonalInches = sqrt(widthInches.toDouble().pow(2.0) + heightInches.toDouble().pow(2.0))
    return diagonalInches >= 7.0
}

fun Context.isDarkThemeOn(): Boolean {
    return resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES
}

fun Context.copyToClipboard(text: String) {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(getString(R.string.app_name), text)
    clipboardManager.setPrimaryClip(clipData)
    showToast("")
}

fun Context.openUrl(url: String) {
    if (url.isEmpty()) return
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = Uri.parse(url)
    startActivity(intent)
}

fun Context.isSystemApp(packageName: String, user: UserHandle? = null): Boolean {
    if (packageName.isBlank()) return true
    return try {
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val targetUser = user ?: android.os.Process.myUserHandle()
        val activityList = launcherApps.getActivityList(packageName, targetUser)
        if (activityList.isNotEmpty()) {
            val applicationInfo = activityList.first().applicationInfo
            ((applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                    || (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0))
        } else {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            ((applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                    || (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0))
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun Context.uninstall(packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE)
    intent.data = Uri.parse("package:$packageName")
    startActivity(intent)
}

@ColorInt
fun Context.getColorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true,
): Int {
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}

fun View.animateAlpha(alpha: Float = 1.0f) {
    this.animate().apply {
        interpolator = LinearInterpolator()
        duration = 200
        alpha(alpha)
        start()
    }
}

fun Context.deletePinnedShortcut(packageName: String, shortcutIdToDelete: String, user: UserHandle) {
    val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val query = LauncherApps.ShortcutQuery().apply {
        setPackage(packageName)
        setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
    }

    try {
        val pinnedShortcuts = launcherApps.getShortcuts(query, user)
        if (pinnedShortcuts != null) {
            val updatedPinnedIds = pinnedShortcuts
                .filter { it.id != shortcutIdToDelete }
                .map { it.id }
            launcherApps.pinShortcuts(packageName, updatedPinnedIds, user)
        }
    } catch (e: SecurityException) {
        Log.e("ShortcutHelper", "Permission denied to modify pinned shortcuts for $packageName", e)
    } catch (e: IllegalStateException) {
        Log.e("ShortcutHelper", "User profile unavailable for modifying pinned shortcuts for $packageName", e)
    } catch (e: Exception) {
        Log.e("ShortcutHelper", "Failed to modify pinned shortcuts for $packageName", e)
    }
}

fun Context.primaryDisplayRefreshRate(): Float {
    val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    return displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate
        ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate
}
