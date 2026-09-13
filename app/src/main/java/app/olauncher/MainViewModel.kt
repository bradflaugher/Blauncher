package app.olauncher

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.helper.SingleLiveEvent
import app.olauncher.helper.SmartOrder
import app.olauncher.helper.getAppsList
import app.olauncher.helper.getPrivateSpaceApps
import app.olauncher.helper.getPrivateSpaceUserHandle
import app.olauncher.helper.isOlauncherDefault
import app.olauncher.helper.isPrivateSpaceLocked
import app.olauncher.helper.showToast
import kotlinx.coroutines.launch


class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext by lazy { application.applicationContext }
    private val prefs = Prefs(appContext)

    val firstOpen = MutableLiveData<Boolean>()
    val refreshHome = MutableLiveData<Unit>()
    val updateSwipeApps = MutableLiveData<Any>()
    val appList = MutableLiveData<List<AppModel>?>()
    val hiddenApps = MutableLiveData<List<AppModel>?>()
    val isOlauncherDefault = MutableLiveData<Boolean>()
    val launcherResetFailed = MutableLiveData<Boolean>()
    val homeAppAlignment = MutableLiveData<Int>()
    val privateSpaceApps = MutableLiveData<List<AppModel>?>()
    val privateSpaceLocked = MutableLiveData<Boolean>()
    val privateSpaceAvailable = MutableLiveData<Boolean>()

    // Suppress backToHomeScreen during Private Space lock/unlock auth
    var isPrivateSpaceToggling = false

    val resetLauncherLiveData = SingleLiveEvent<Unit?>()
    // Home button for recents feature disabled
    // val showRecentApps = SingleLiveEvent<Unit?>()

    fun selectedApp(appModel: AppModel, flag: Int) {
        if (appModel is AppModel.PrivateSpaceHeader) return
        when (flag) {
            Constants.FLAG_LAUNCH_APP -> {
                SmartOrder.recordLaunch(prefs, appModel.category)
                when (appModel) {
                    is AppModel.PinnedShortcut -> launchShortcut(appModel)
                    is AppModel.App ->
                        launchApp(appModel.appPackage, appModel.activityClassName, appModel.user)

                    else -> {}
                }
            }

            Constants.FLAG_HIDDEN_APPS -> {
                if (appModel is AppModel.App) {
                    SmartOrder.recordLaunch(prefs, appModel.category)
                    launchApp(appModel.appPackage, appModel.activityClassName, appModel.user)
                }
            }

            Constants.FLAG_SET_SWIPE_LEFT_APP -> saveSwipeApp(appModel, isLeft = true)
            Constants.FLAG_SET_SWIPE_RIGHT_APP -> saveSwipeApp(appModel, isLeft = false)
            Constants.FLAG_SET_CALENDAR_APP -> saveCalendarApp(appModel)
            Constants.FLAG_SET_PASSWORD_APP -> savePasswordApp(appModel)
        }
    }

    private fun launchShortcut(appModel: AppModel.PinnedShortcut) {
        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val query = LauncherApps.ShortcutQuery().apply {
            setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        }
        launcher.getShortcuts(query, appModel.user)?.find { it.id == appModel.shortcutId }
            ?.let { shortcut ->
                launcher.startShortcut(shortcut, null, null)
            }
    }

    private fun saveSwipeApp(appModel: AppModel, isLeft: Boolean) {
        when (appModel) {
            is AppModel.PrivateSpaceHeader -> return
            is AppModel.App -> {
                if (isLeft) {
                    prefs.appNameSwipeLeft = appModel.appLabel
                    prefs.appPackageSwipeLeft = appModel.appPackage
                    prefs.appUserSwipeLeft = appModel.user.toString()
                    prefs.appActivityClassNameSwipeLeft = appModel.activityClassName
                    prefs.isShortcutSwipeLeft = false
                    prefs.shortcutIdSwipeLeft = ""
                } else {
                    prefs.appNameSwipeRight = appModel.appLabel
                    prefs.appPackageSwipeRight = appModel.appPackage
                    prefs.appUserSwipeRight = appModel.user.toString()
                    prefs.appActivityClassNameRight = appModel.activityClassName
                    prefs.isShortcutSwipeRight = false
                    prefs.shortcutIdSwipeRight = ""
                }
            }

            is AppModel.PinnedShortcut -> {
                if (isLeft) {
                    prefs.appNameSwipeLeft = appModel.appLabel
                    prefs.appPackageSwipeLeft = appModel.appPackage
                    prefs.appUserSwipeLeft = appModel.user.toString()
                    prefs.appActivityClassNameSwipeLeft = null
                    prefs.isShortcutSwipeLeft = true
                    prefs.shortcutIdSwipeLeft = appModel.shortcutId
                } else {
                    prefs.appNameSwipeRight = appModel.appLabel
                    prefs.appPackageSwipeRight = appModel.appPackage
                    prefs.appUserSwipeRight = appModel.user.toString()
                    prefs.appActivityClassNameRight = null
                    prefs.isShortcutSwipeRight = true
                    prefs.shortcutIdSwipeRight = appModel.shortcutId
                }
            }
        }
        updateSwipeApps()
    }

    private fun saveCalendarApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            prefs.calendarAppPackage = appModel.appPackage
            prefs.calendarAppUser = appModel.user.toString()
            prefs.calendarAppClassName = appModel.activityClassName
        }
    }

    private fun savePasswordApp(appModel: AppModel) {
        // The picker only hands over apps; anything else is a programming error, not a user one.
        if (appModel !is AppModel.App) return
        prefs.passwordAppName = appModel.appLabel
        prefs.passwordAppPackage = appModel.appPackage
        prefs.passwordAppUser = appModel.user.toString()
        prefs.passwordAppClassName = appModel.activityClassName
        refreshHome()
    }

    fun firstOpen(value: Boolean) {
        firstOpen.postValue(value)
    }

    fun refreshHome() {
        refreshHome.value = Unit
    }

    private fun updateSwipeApps() {
        updateSwipeApps.postValue(Unit)
    }

    private fun launchApp(packageName: String, activityClassName: String?, userHandle: UserHandle) {
        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityInfo = launcher.getActivityList(packageName, userHandle)

        val isActivityValid = activityClassName.isNullOrBlank().not()
                && activityInfo.any { it.componentName.className == activityClassName }

        val component = if (isActivityValid)
            ComponentName(packageName, activityClassName)
        else {
            when (activityInfo.size) {
                0 -> {
                    appContext.showToast(appContext.getString(R.string.app_not_found))
                    return
                }

                1 -> ComponentName(packageName, activityInfo[0].name)
                else -> ComponentName(packageName, activityInfo[activityInfo.size - 1].name)
            }.also { prefs.updateAppActivityClassName(packageName, it.className) }
        }

        try {
            launcher.startMainActivity(component, userHandle, null, null)
        } catch (e: SecurityException) {
            try {
                launcher.startMainActivity(component, android.os.Process.myUserHandle(), null, null)
            } catch (e: Exception) {
                appContext.showToast(appContext.getString(R.string.unable_to_open_app))
            }
        } catch (e: Exception) {
            appContext.showToast(appContext.getString(R.string.unable_to_open_app))
        }
    }

    // The smart order shifts through the day; re-sort cached lists when the drawer opens.
    fun refreshAppOrder() {
        appList.value?.let { apps ->
            val sorted = apps.toMutableList()
            SmartOrder.sort(prefs, sorted)
            if (sorted != apps) appList.value = sorted
        }
        privateSpaceApps.value?.let { apps ->
            val sorted = apps.toMutableList()
            SmartOrder.sort(prefs, sorted)
            if (sorted != apps) privateSpaceApps.value = sorted
        }
    }

    fun getAppList(includeHiddenApps: Boolean = false) {
        viewModelScope.launch {
            val apps = getAppsList(appContext, prefs, includeRegularApps = true, includeHiddenApps)
            appList.value = apps
        }
        getPrivateSpaceAppList()
    }

    fun getHiddenApps() {
        viewModelScope.launch {
            hiddenApps.value =
                getAppsList(appContext, prefs, includeRegularApps = false, includeHiddenApps = true)
        }
    }

    fun isOlauncherDefault() {
        isOlauncherDefault.value = isOlauncherDefault(appContext)
    }

    fun updateHomeAlignment(gravity: Int) {
        prefs.homeAlignment = gravity
        homeAppAlignment.value = prefs.homeAlignment
    }

    fun getPrivateSpaceAppList() {
        viewModelScope.launch {
            val handle = getPrivateSpaceUserHandle(appContext)
            privateSpaceAvailable.value = handle != null
            if (handle != null) {
                privateSpaceLocked.value = isPrivateSpaceLocked(appContext, handle)
                privateSpaceApps.value = getPrivateSpaceApps(appContext, prefs)
            } else {
                privateSpaceLocked.value = true
                privateSpaceApps.value = emptyList()
            }
        }
    }

    fun openPrivateSpaceSettings() {
        try {
            val intent = Intent("android.settings.PRIVATE_SPACE_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
            } catch (_: Exception) {
                appContext.showToast(appContext.getString(R.string.unable_to_open_app))
            }
        }
    }

    fun togglePrivateSpaceLock() {
        val handle = getPrivateSpaceUserHandle(appContext) ?: return
        try {
            isPrivateSpaceToggling = true
            val userManager = appContext.getSystemService(Context.USER_SERVICE) as UserManager
            val currentlyLocked = userManager.isQuietModeEnabled(handle)
            userManager.requestQuietModeEnabled(!currentlyLocked, handle)
        } catch (e: Exception) {
            isPrivateSpaceToggling = false
            e.printStackTrace()
        }
    }
}
