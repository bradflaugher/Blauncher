package app.olauncher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import app.olauncher.data.Prefs
import app.olauncher.databinding.ActivityMainBinding
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hasBeenHours
import app.olauncher.helper.isDefaultLauncher
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isTablet
import app.olauncher.helper.resetLauncherViaFakeActivity
import app.olauncher.helper.showLauncherSelector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        /**
         * Debug builds only, for test and screenshot runs:
         * `adb shell am start -n com.bradflaugher.blauncher.debug/app.olauncher.MainActivity --ez skip_tips true`
         * turns the first-run tips off, and `--ez reset_tips true` brings them back.
         */
        const val EXTRA_SKIP_TIPS = "skip_tips"
        const val EXTRA_RESET_TIPS = "reset_tips"
    }

    private lateinit var prefs: Prefs
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private var timerJob: Job? = null
    private var isResumed = false
    private var profileReceiver: BroadcastReceiver? = null

    private val launcherSelectorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            resetLauncherViaFakeActivity()
        }
    }

    override fun attachBaseContext(context: Context) {
        // Scales on top of the system font size rather than replacing it.
        applyOverrideConfiguration(Configuration().apply {
            fontScale = context.resources.configuration.fontScale * Prefs(context).textSizeScale
        })
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        applyDebugExtras(intent)
        if (isEinkDisplay()) prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = this.findNavController(R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Back does nothing on Home, like any launcher. Elsewhere the callback is disabled so the
        // NavHostFragment pops the stack itself and the system can animate predictive back.
        val swallowBackOnHome = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        }
        onBackPressedDispatcher.addCallback(this, swallowBackOnHome)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            swallowBackOnHome.isEnabled = destination.id == R.id.mainFragment
        }

        if (prefs.firstOpen) {
            viewModel.firstOpen(true)
            prefs.firstOpen = false
            viewModel.resetLauncherLiveData.call()
        }

        initObservers(viewModel)
        viewModel.getAppList()
        setupOrientation()

        profileReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.isPrivateSpaceToggling = false
                // Work-profile apps live in the main list, Private Space apps in their own; a
                // profile turning on or off changes one of them, and Home's search reads both.
                viewModel.getAppList()
            }
        }
        registerReceiver(
            profileReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            },
            RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStart() {
        super.onStart()
        restartLauncherOrCheckTheme()
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        viewModel.isPrivateSpaceToggling = false
    }

    override fun onStop() {
        isResumed = false
        // A rotation, fold or window resize recreates the activity; keep the user where they are.
        if (!isChangingConfigurations) backToHomeScreen()
        super.onStop()
    }

    override fun onUserLeaveHint() {
        backToHomeScreen()
        super.onUserLeaveHint()
    }

    override fun onNewIntent(intent: Intent) {
        applyDebugExtras(intent)
        backToHomeScreen()
        super.onNewIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
    }

    private fun applyDebugExtras(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        if (intent.getBooleanExtra(EXTRA_RESET_TIPS, false)) prefs.resetTips()
        if (intent.getBooleanExtra(EXTRA_SKIP_TIPS, false)) prefs.learnAllTips()
        // One-shot: the activity keeps its launch intent across recreation (rotation, resizing,
        // theme changes), so drop the extras once applied or they would run again each time.
        intent.removeExtra(EXTRA_RESET_TIPS)
        intent.removeExtra(EXTRA_SKIP_TIPS)
    }

    private fun initObservers(viewModel: MainViewModel) {
        viewModel.launcherResetFailed.observe(this) {
            openLauncherChooser(it)
        }
        viewModel.resetLauncherLiveData.observe(this) {
            if (isDefaultLauncher())
                resetLauncherViaFakeActivity()
            else
                showLauncherSelector(launcherSelectorLauncher)
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        // Phones stay portrait; large screens (tablets, unfolded foldables, desktop windows) rotate freely.
        requestedOrientation = if (isTablet(this)) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun backToHomeScreen() {
        if (viewModel.isPrivateSpaceToggling) return
        if (navController.currentDestination?.id != R.id.mainFragment)
            navController.popBackStack(R.id.mainFragment, false)
    }

    private fun openLauncherChooser(resetFailed: Boolean) {
        if (resetFailed) {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    private fun restartLauncherOrCheckTheme(forceRestart: Boolean = false) {
        if (forceRestart || prefs.launcherRestartTimestamp.hasBeenHours(4)) {
            prefs.launcherRestartTimestamp = System.currentTimeMillis()
            cacheDir.deleteRecursively()
            recreate()
        } else
            checkTheme()
    }

    private fun checkTheme() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            delay(200)
            if ((prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.white))
                || (prefs.appTheme == AppCompatDelegate.MODE_NIGHT_NO && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.black))
            )
                restartLauncherOrCheckTheme(true)
        }
    }

    override fun onDestroy() {
        profileReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }
}
