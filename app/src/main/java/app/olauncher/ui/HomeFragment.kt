package app.olauncher.ui

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentHomeBinding
import app.olauncher.helper.Typefaces
import app.olauncher.helper.detectPasswordManager
import app.olauncher.helper.expandNotificationDrawer
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isPackageInstalled
import app.olauncher.helper.openCalendar
import app.olauncher.helper.openCameraApp
import app.olauncher.helper.openDialerApp
import app.olauncher.helper.openSearch
import app.olauncher.helper.searchWithDefaultBrowser
import app.olauncher.helper.showToast
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.listener.ViewSwipeTouchListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The home screen: the date on top, and along the bottom a search bar that hands the query
 * to the default browser's search engine next to a key glyph that opens the password
 * manager. Everything else is gestures on the empty space.
 *
 * The search bar is a multi-line composer. Its text is treated as a draft: it survives
 * leaving the screen, the drawer, settings, rotation and a launcher restart, and is only
 * dropped once a browser has accepted it or the user taps clear.
 */
class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListener()
        initClickListeners()
        initSearchBar()
    }

    override fun onResume() {
        super.onResume()
        populateHomeScreen()
        restoreSearchDraft()
        viewModel.isOlauncherDefault()
        showStatusBar()
    }

    override fun onPause() {
        super.onPause()
        saveSearchDraft()
        _binding?.searchInput?.hideKeyboard()
    }

    override fun onClick(view: View) {
        when (view.id) {
            // Home button for recents feature disabled
            // R.id.recents -> {}
            R.id.date -> openCalendarApp()
            R.id.passwordManager -> openPasswordManager()
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
        }
    }

    private fun openCalendarApp() {
        if (prefs.calendarAppPackage.isBlank())
            openCalendar(requireContext())
        else
            launchApp(
                "Calendar",
                prefs.calendarAppPackage,
                prefs.calendarAppClassName,
                prefs.calendarAppUser
            )
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.passwordManager -> showAppList(Constants.FLAG_SET_PASSWORD_APP, includeHiddenApps = true)
            R.id.date -> {
                showAppList(Constants.FLAG_SET_CALENDAR_APP)
                prefs.calendarAppPackage = ""
                prefs.calendarAppClassName = ""
                prefs.calendarAppUser = ""
            }

            R.id.setDefaultLauncher -> {
                prefs.hideSetDefaultLauncher = true
                binding.setDefaultLauncher.visibility = View.GONE
                if (viewModel.isOlauncherDefault.value != true) {
                    requireContext().showToast(R.string.set_as_default_launcher)
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                }
            }
        }
        return true
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            binding.firstRunTips.visibility = View.VISIBLE
            binding.setDefaultLauncher.visibility = View.GONE
        } else binding.firstRunTips.visibility = View.GONE

        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateHomeScreen()
        }
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner, Observer {
            if (binding.firstRunTips.isVisible) return@Observer
            binding.setDefaultLauncher.isVisible = it.not() && prefs.hideSetDefaultLauncher.not()
        })
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            setHomeAlignment(it)
        }
        // Home button for recents feature disabled
        // viewModel.showRecentApps.observe(viewLifecycleOwner) {
        //     binding.recents.performClick()
        // }
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        binding.mainLayout.setOnTouchListener(getSwipeGestureListener(context))
        // Tappable views route their taps through the swipe listener so a swipe that starts
        // on them still works as a gesture.
        binding.date.setOnTouchListener(getViewSwipeTouchListener(context, binding.date))
        binding.passwordManager.setOnTouchListener(getViewSwipeTouchListener(context, binding.passwordManager))
    }

    private fun initClickListeners() {
        // Home button for recents feature disabled
        // binding.recents.setOnClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
        binding.setDefaultLauncher.setOnLongClickListener(this)
        // Touch goes through the swipe listeners above, which consume it before the view's own
        // click handling; these listeners serve keyboards and accessibility ACTION_CLICK instead.
        binding.date.setOnClickListener(this)
        binding.date.setOnLongClickListener(this)
        binding.passwordManager.setOnClickListener(this)
        binding.passwordManager.setOnLongClickListener(this)
    }

    private fun initSearchBar() {
        // The keyboard would otherwise cover a bottom-aligned search bar; pad the root so the
        // content block rises above it while typing.
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainLayout) { root, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            root.setPadding(0, 0, 0, ime.bottom)
            insets
        }
        binding.searchBar.setOnClickListener { focusSearch() }
        binding.searchIcon.setOnClickListener { focusSearch() }
        binding.searchGo.setOnClickListener { submitSearch() }
        binding.searchClear.setOnClickListener {
            binding.searchInput.text?.clear()
            prefs.searchDraft = ""
            focusSearch()
        }
        binding.searchInput.doAfterTextChanged { text ->
            val hasText = !text.isNullOrBlank()
            binding.searchGo.isVisible = hasText
            binding.searchClear.isVisible = hasText
        }
        // Enter adds a line, as in any composer. Ctrl+Enter or Shift+Enter sends, for hardware
        // keyboards; on-screen keyboards use the arrow button.
        binding.searchInput.setOnKeyListener { _, keyCode, event ->
            val enter = keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
            if (enter && event.action == KeyEvent.ACTION_DOWN && (event.isCtrlPressed || event.isShiftPressed)) {
                submitSearch()
                true
            } else false
        }
    }

    private fun focusSearch() {
        val input = binding.searchInput
        if (input.requestFocus()) {
            input.setSelection(input.length())
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, 0)
        }
    }

    /**
     * Hands the composed text to the default browser's search engine. The field is emptied
     * only after an app accepted the query, so a missing browser never eats the text.
     */
    private fun submitSearch() {
        val query = binding.searchInput.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) return
        if (searchWithDefaultBrowser(requireContext(), query)) {
            binding.searchInput.text?.clear()
            prefs.searchDraft = ""
            binding.searchInput.hideKeyboard()
        } else {
            requireContext().showToast(R.string.search_not_available)
        }
    }

    private fun saveSearchDraft() {
        val input = _binding?.searchInput ?: return
        prefs.searchDraft = input.text?.toString().orEmpty()
    }

    /** Puts an unsent draft back into the field, cursor at the end, without raising the keyboard. */
    private fun restoreSearchDraft() {
        val draft = prefs.searchDraft
        if (draft.isBlank() || binding.searchInput.text?.isNotEmpty() == true) return
        binding.searchInput.setText(draft)
        binding.searchInput.setSelection(draft.length)
    }

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        binding.date.gravity = horizontalGravity
    }

    private fun populateHomeScreen() {
        populateDate()
        populatePasswordManager()
    }

    private fun populateDate() {
        val dateText = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        binding.date.text = dateText.replace(".,", ",")
        binding.date.typeface = Typefaces.forEmphasis(prefs.dateBold)
    }

    /**
     * Keeps the password shortcut bound to an installed app: drops a binding whose app is gone,
     * adopts a known password manager when nothing is chosen, and names the glyph after it for
     * accessibility. An unbound glyph is drawn faded as the cue to pick an app.
     */
    private fun populatePasswordManager() {
        val context = requireContext()
        if (prefs.passwordAppPackage.isNotBlank() &&
            !isPackageInstalled(context, prefs.passwordAppPackage, prefs.passwordAppUser)
        ) {
            prefs.clearPasswordApp()
        }
        if (prefs.passwordAppPackage.isBlank()) {
            detectPasswordManager(context)?.let { app ->
                prefs.passwordAppName = app.appLabel
                prefs.passwordAppPackage = app.appPackage
                prefs.passwordAppUser = app.user.toString()
                prefs.passwordAppClassName = app.activityClassName
            }
        }
        val bound = prefs.passwordAppPackage.isNotBlank()
        binding.passwordManager.contentDescription =
            prefs.passwordAppName.ifBlank { getString(R.string.password_manager) }
        binding.passwordManager.alpha = if (bound) 1f else 0.5f
    }

    private fun openPasswordManager() {
        if (prefs.passwordAppPackage.isBlank()) {
            requireContext().showToast(R.string.choose_password_manager)
            showAppList(Constants.FLAG_SET_PASSWORD_APP, includeHiddenApps = true)
            return
        }
        launchApp(
            appName = prefs.passwordAppName,
            packageName = prefs.passwordAppPackage,
            activityClassName = prefs.passwordAppClassName,
            userString = prefs.passwordAppUser
        )
    }

    private fun launchAppOrShortcut(
        appName: String,
        packageName: String,
        activityClassName: String?,
        shortcutId: String?,
        isShortcut: Boolean,
        userString: String,
        fallback: (() -> Unit)? = null,
    ) {
        if (appName.isEmpty()) {
            requireContext().showToast(R.string.long_press_to_change_app)
            return
        }
        if (isShortcut && !shortcutId.isNullOrEmpty()) {
            launchShortcut(
                packageName = packageName,
                shortcutId = shortcutId,
                shortcutLabel = appName,
                userString = userString
            )
        } else if (packageName.isNotEmpty()) {
            launchApp(
                appName = appName,
                packageName = packageName,
                activityClassName = activityClassName,
                userString = userString
            )
        } else {
            fallback?.invoke()
        }
    }

    private fun launchShortcut(shortcutId: String, packageName: String, shortcutLabel: String, userString: String) {
        viewModel.selectedApp(
            AppModel.PinnedShortcut(
                shortcutId = shortcutId,
                appLabel = shortcutLabel,
                user = getUserHandleFromString(requireContext(), userString),
                key = null,
                appPackage = packageName,
                isNew = false,
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun launchApp(appName: String, packageName: String, activityClassName: String?, userString: String) {
        viewModel.selectedApp(
            AppModel.App(
                appLabel = appName,
                key = null,
                appPackage = packageName,
                activityClassName = activityClassName,
                isNew = false,
                user = getUserHandleFromString(requireContext(), userString)
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun openSwipeRightApp() {
        if (!prefs.swipeRightEnabled) return
        launchAppOrShortcut(
            appName = prefs.appNameSwipeRight,
            packageName = prefs.appPackageSwipeRight,
            activityClassName = prefs.appActivityClassNameRight,
            shortcutId = prefs.shortcutIdSwipeRight,
            isShortcut = prefs.isShortcutSwipeRight,
            userString = prefs.appUserSwipeRight,
            fallback = { openDialerApp(requireContext()) }
        )
    }

    private fun openSwipeLeftApp() {
        if (!prefs.swipeLeftEnabled) return
        launchAppOrShortcut(
            appName = prefs.appNameSwipeLeft,
            packageName = prefs.appPackageSwipeLeft,
            activityClassName = prefs.appActivityClassNameSwipeLeft,
            shortcutId = prefs.shortcutIdSwipeLeft,
            isShortcut = prefs.isShortcutSwipeLeft,
            userString = prefs.appUserSwipeLeft,
            fallback = { openCameraApp(requireContext()) }
        )
    }

    private fun showAppList(flag: Int, includeHiddenApps: Boolean = false) {
        viewModel.getAppList(includeHiddenApps)
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf(Constants.Key.FLAG to flag)
            )
        } catch (e: Exception) {
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf(Constants.Key.FLAG to flag)
            )
            e.printStackTrace()
        }
    }

    private fun swipeDownAction() {
        when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.SEARCH -> openSearch(requireContext())
            else -> expandNotificationDrawer(requireContext())
        }
    }

    private fun showStatusBar() {
        requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
    }

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)

    private fun getSwipeGestureListener(context: Context): View.OnTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onLongClick() {
                super.onLongClick()
                try {
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                    viewModel.firstOpen(false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onClick() {
                super.onClick()
                // Tapping empty space only dismisses the keyboard; the draft stays put.
                _binding?.searchInput?.hideKeyboard()
            }
        }
    }

    private fun getViewSwipeTouchListener(context: Context, view: View): View.OnTouchListener {
        return object : ViewSwipeTouchListener(context, view) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                textOnLongClick(view)
            }

            override fun onClick(view: View) {
                super.onClick(view)
                textOnClick(view)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
