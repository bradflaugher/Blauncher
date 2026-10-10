package app.olauncher.helper

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.UserHandle
import android.os.UserManager
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
import androidx.core.net.toUri
import app.olauncher.BuildConfig
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.data.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

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
): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val appList: MutableList<AppModel> = mutableListOf()

        try {
            val emphasizedApps = prefs.emphasizedApps

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
                        val model = AppModel.App(
                            appLabel = appLabelShown,
                            key = collator.getCollationKey(app.label.toString()),
                            appPackage = app.applicationInfo.packageName,
                            activityClassName = app.componentName.className,
                            isNew = (System.currentTimeMillis() - app.firstInstallTime) < Constants.ONE_HOUR_IN_MILLIS,
                            user = profile,
                            category = category,
                        )
                        model.copy(emphasized = model.emphasisKey in emphasizedApps)
                    }

                    if (app.applicationInfo.packageName != BuildConfig.APPLICATION_ID) {
                        appList.addAll(appModels)
                    }
                }
            }

            val pinned = try {
                getPinnedShortcuts(context, prefs, collator, emphasizedApps)
            } catch (_: Exception) {
                emptyList()
            }
            appList.addAll(pinned)

            SmartOrder.sort(prefs, appList)
            SmartOrder.applyGroupEmphasis(appList)
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
    emphasizedApps: Set<String>,
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
                        val listed = pinnedShortcuts.any {
                            it.shortcutId == shortcut.id && it.appPackage == shortcut.`package`
                        }
                        if (shortcut.isPinned && !listed) {
                            val identity = AppModel.PinnedShortcut(
                                appLabel = "",
                                key = null,
                                appPackage = shortcut.`package`,
                                shortcutId = shortcut.id,
                                user = profile,
                            )
                            val carriedEmphasis = prefs.migrateShortcutKeys(identity)
                            val label = prefs.getAppRenameLabel(identity.emphasisKey)
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
                                    ).let {
                                        it.copy(emphasized = carriedEmphasis || it.emphasisKey in emphasizedApps)
                                    }
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

fun isPackageInstalled(context: Context, packageName: String, userString: String): Boolean {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val activityInfo = launcher.getActivityList(packageName, getUserHandleFromString(context, userString))
    return activityInfo.isNotEmpty()
}

/**
 * False while the profile behind [userString] is paused or locked (a work profile switched off,
 * a locked Private Space): its apps cannot be listed then, so their absence proves nothing.
 */
fun isProfileAvailable(context: Context, userString: String): Boolean {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    val user = getUserHandleFromString(context, userString)
    return try {
        !userManager.isQuietModeEnabled(user) && userManager.isUserUnlocked(user)
    } catch (_: Exception) {
        false
    }
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
            val emphasizedApps = prefs.emphasizedApps

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
                        ).let { it.copy(emphasized = it.emphasisKey in emphasizedApps) }
                    )
                }
            }
            SmartOrder.sort(prefs, appList)
            SmartOrder.applyGroupEmphasis(appList)
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
    try {
        val component = launcher.getActivityList(packageName, userHandle).firstOrNull()?.componentName
        if (component != null) {
            launcher.startAppDetailsActivity(component, userHandle, null, null)
            return
        }
    } catch (e: Exception) {
        // A profile that is paused or being removed refuses the request.
        e.printStackTrace()
    }
    context.showToast(context.getString(R.string.unable_to_open_app_info))
}

fun openSearch(context: Context) {
    val intent = Intent(Intent.ACTION_WEB_SEARCH)
    intent.putExtra(SearchManager.QUERY, "")
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.showToast(R.string.search_not_available)
    }
}

/**
 * Opens the user's default browser on its own start page (Chrome on most phones), or lets the
 * system offer a choice when no browser is the default. Returns false when there is no browser.
 */
fun openBrowser(context: Context): Boolean {
    val intent = defaultBrowserPackage(context)?.let { context.packageManager.getLaunchIntentForPackage(it) }
        ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/**
 * The package the user has chosen to open web links, or null when no browser is set as
 * default (the system resolver would show a chooser instead).
 */
fun defaultBrowserPackage(context: Context): String? {
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    val flags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
    val packageName = context.packageManager.resolveActivity(probe, flags)?.activityInfo?.packageName
    return packageName?.takeIf { it != "android" }
}

/**
 * Sends [query] to [engine]. For a URL engine the results page opens straight in the default
 * browser, so one tap is enough. For [SearchEngine.BROWSER] the raw query goes to the default
 * browser as [Intent.ACTION_WEB_SEARCH], which uses whatever engine the user configured there,
 * falling back to the system-wide web-search handler and then to the default engine. The query is
 * passed as typed, line breaks included, so multi-sentence text arrives intact.
 *
 * @return true once some app accepted the query; false when nothing could take it, so the
 * caller can keep the text instead of discarding it.
 */
fun sendSearch(context: Context, engine: SearchEngine, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return false

    val url = engine.searchUrl(trimmed)
    if (url != null) return openSearchUrl(context, url)

    val webSearch = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, trimmed)
    val browser = defaultBrowserPackage(context)
    if (browser != null) {
        val targeted = Intent(webSearch).setPackage(browser)
        val flags = PackageManager.ResolveInfoFlags.of(0)
        if (context.packageManager.resolveActivity(targeted, flags) != null) {
            try {
                context.startActivity(targeted)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    try {
        context.startActivity(webSearch)
        return true
    } catch (_: ActivityNotFoundException) {
        // fall through to a plain URL
    }
    return openSearchUrl(context, SearchEngine.DEFAULT.searchUrl(trimmed)!!)
}

private fun openSearchUrl(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    true
} catch (e: Exception) {
    e.printStackTrace()
    false
}

/** The first installed app from [Constants.KNOWN_PASSWORD_MANAGERS] in the main profile. */
fun detectPasswordManager(context: Context): AppModel.App? {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val user = android.os.Process.myUserHandle()
    for (packageName in Constants.KNOWN_PASSWORD_MANAGERS) {
        val activity = try {
            launcher.getActivityList(packageName, user).firstOrNull()
        } catch (_: Exception) {
            null
        } ?: continue
        return AppModel.App(
            appLabel = activity.label.toString(),
            key = null,
            appPackage = packageName,
            activityClassName = activity.componentName.className,
            user = user,
        )
    }
    return null
}

// Hidden StatusBarManager API with no public equivalent; if a future Android blocks it, the gesture does nothing.
@SuppressLint("WrongConstant", "PrivateApi")
fun expandNotificationDrawer(context: Context) {
    try {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val method = statusBarManager.getMethod("expandNotificationsPanel")
        method.invoke(statusBarService)
    } catch (_: ReflectiveOperationException) {
    } catch (_: SecurityException) {
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

/** A large screen (tablet, unfolded foldable, desktop window): smallest width of at least 600dp. */
fun isTablet(context: Context): Boolean {
    val metrics = context.getSystemService(WindowManager::class.java).maximumWindowMetrics
    val bounds = metrics.bounds
    return minOf(bounds.width(), bounds.height()) / metrics.density >= 600f
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
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        showToast(R.string.no_app_for_link)
    }
}

/**
 * Opens Blauncher's Play listing so it can be rated: in the Play Store app when there is one,
 * else the web page in the browser. Only ever on a tap; the launcher never asks for a rating.
 */
fun Context.rateApp() {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Constants.URL_PLAY_STORE_APP.toUri()))
    } catch (_: ActivityNotFoundException) {
        openUrl(Constants.URL_PLAY_STORE)
    }
}

/** Opens the system share sheet with a line about Blauncher and its store link. */
fun Context.shareApp() {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_text, Constants.URL_PLAY_STORE))
    try {
        startActivity(Intent.createChooser(send, getString(R.string.share_app)))
    } catch (_: ActivityNotFoundException) {
        showToast(R.string.nothing_to_share_with)
    }
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
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        showToast(R.string.unable_to_open_app)
    }
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
    return displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 60f
}
