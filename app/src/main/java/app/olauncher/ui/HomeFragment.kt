package app.olauncher.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentHomeBinding
import app.olauncher.helper.Onboarding
import app.olauncher.helper.Tip
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
import app.olauncher.helper.sendSearch
import app.olauncher.helper.showToast
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.listener.ViewSwipeTouchListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * The home screen: the date on top, and along the bottom a search bar that hands the query
 * to the default browser's search engine next to a key glyph that opens the password
 * manager. Everything else is gestures on the empty space.
 *
 * Until the user has found the drawer and settings, a tip card above the search bar teaches
 * those two gestures one at a time (see [Onboarding]).
 *
 * The search bar is a multi-line composer. Its text is treated as a draft: it survives
 * leaving the screen, the drawer, settings, rotation and a launcher restart, and is only
 * dropped once a browser has accepted it or the user taps clear.
 */
class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private companion object {
        const val MAX_KEYBOARD_ATTEMPTS = 3
        const val KEYBOARD_RETRY_DELAY_MS = 120L
    }

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var coachAnimator: ObjectAnimator? = null

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
        initCoachCard()
        initAccessibilityActions()
    }

    override fun onResume() {
        super.onResume()
        populateHomeScreen()
        restoreSearchDraft()
        populateCoachCard()
        viewModel.isOlauncherDefault()
        showStatusBar()
    }

    override fun onPause() {
        super.onPause()
        saveSearchDraft()
        // Clearing focus re-runs populateCoachCard(), so stop the glyph only after it.
        _binding?.searchInput?.hideKeyboard()
        stopCoachAnimation()
    }

    override fun onClick(view: View) {
        when (view.id) {
            // Home button for recents feature disabled
            // R.id.recents -> {}
            R.id.date -> openCalendarApp()
            R.id.passwordManager -> openPasswordManager()
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.coachCard -> when (Onboarding.nextHomeTip(prefs.learnedTips)) {
                Tip.OPEN_DRAWER -> showAppList(Constants.FLAG_LAUNCH_APP)
                Tip.OPEN_SETTINGS -> openSettings()
                else -> populateCoachCard()
            }
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
            R.id.passwordManager -> showAppList(Constants.FLAG_SET_PASSWORD_APP)
            R.id.date -> {
                showAppList(Constants.FLAG_SET_CALENDAR_APP)
                prefs.calendarAppPackage = ""
                prefs.calendarAppClassName = ""
                prefs.calendarAppUser = ""
            }

            // Holding the card is holding the empty screen it floats on.
            R.id.coachCard -> openSettings()

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
        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateHomeScreen()
        }
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner) {
            binding.setDefaultLauncher.isVisible = it.not() && prefs.hideSetDefaultLauncher.not()
        }
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
        // The tip card sits where a thumb naturally starts the swipe it is teaching.
        binding.coachCard.setOnTouchListener(getViewSwipeTouchListener(context, binding.coachCard))
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
        binding.coachCard.setOnClickListener(this)
        binding.coachCard.setOnLongClickListener(this)
    }

    private fun initCoachCard() {
        binding.coachDismiss.setOnClickListener {
            prefs.learnAllTips()
            populateCoachCard()
            requireContext().showToast(R.string.tips_skipped, Toast.LENGTH_LONG)
        }
        // While composing a search the card would only crowd the keyboard.
        binding.searchInput.setOnFocusChangeListener { _, _ -> populateCoachCard() }
    }

    /**
     * Screen readers cannot perform the swipe and long-press gestures on empty space, so the
     * home screen offers them as named actions too.
     */
    private fun initAccessibilityActions() {
        ViewCompat.addAccessibilityAction(binding.mainLayout, getString(R.string.tip_open_drawer_action)) { _, _ ->
            showAppList(Constants.FLAG_LAUNCH_APP)
            true
        }
        ViewCompat.addAccessibilityAction(binding.mainLayout, getString(R.string.tip_open_settings_action)) { _, _ ->
            openSettings()
            true
        }
    }

    /** Shows the next unlearned home-screen tip, or hides the card once there is none. */
    private fun populateCoachCard() {
        val binding = _binding ?: return
        val tip = Onboarding.nextHomeTip(prefs.learnedTips)
        val show = tip != null && !binding.searchInput.hasFocus()
        binding.coachCard.isVisible = show
        stopCoachAnimation()
        if (!show || tip == null) return

        val step = Onboarding.homeStep(tip)
        val total = Onboarding.homeTips.size
        binding.coachStep.text =
            getString(if (step == 1) R.string.tip_welcome_step else R.string.tip_step, step, total)
        val settings = tip == Tip.OPEN_SETTINGS
        binding.coachIcon.setImageResource(if (settings) R.drawable.ic_touch_hold else R.drawable.ic_swipe_up)
        binding.coachTitle.setText(
            if (settings) R.string.tip_open_settings_title else R.string.tip_open_drawer_title
        )
        binding.coachBody.setText(
            if (settings) R.string.tip_open_settings_body else R.string.tip_open_drawer_body
        )
        // TalkBack reads "double-tap to open apps" rather than a bare "double-tap to activate".
        ViewCompat.replaceAccessibilityAction(
            binding.coachCard,
            AccessibilityActionCompat.ACTION_CLICK,
            getString(if (settings) R.string.tip_open_settings_action else R.string.tip_open_drawer_action),
            null,
        )
        // Focus changes can land here while paused; the glyph only moves on a visible screen.
        if (isResumed) startCoachAnimation(tip)
    }

    /** A slow nudge on the tip's glyph: a lift for the swipe, a swell for the long-press. */
    private fun startCoachAnimation(tip: Tip) {
        // Animations switched off in system settings leave the glyph still.
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val icon = binding.coachIcon
        val lift = -6f * resources.displayMetrics.density
        coachAnimator = when (tip) {
            Tip.OPEN_SETTINGS -> ObjectAnimator.ofPropertyValuesHolder(
                icon,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.8f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.8f),
            )

            else -> ObjectAnimator.ofFloat(icon, View.TRANSLATION_Y, 0f, lift)
        }.apply {
            duration = 700L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopCoachAnimation() {
        coachAnimator?.cancel()
        coachAnimator = null
        _binding?.coachIcon?.apply {
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
        }
    }

    private fun openSettings() {
        try {
            findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            // A tip counts as learned only once its screen has actually opened.
            prefs.learnTip(Tip.OPEN_SETTINGS)
            viewModel.firstOpen(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        // Accessibility ACTION_CLICK and keyboards land here.
        binding.searchInput.setOnClickListener { focusSearch() }
        // Every finger tap on the field lands here too, including the one that first gives it
        // focus. On that tap Android skips performClick() and relies on its own show request,
        // which is exactly the request that gets dropped after coming back from another app.
        binding.searchInput.setOnTouchListener(TapListener { focusSearch() })
        binding.searchSend.setOnClickListener { submitSearch() }
        binding.searchClear.setOnClickListener {
            binding.searchInput.text?.clear()
            prefs.searchDraft = ""
            focusSearch()
        }
        binding.searchInput.doAfterTextChanged { text ->
            val hasText = !text.isNullOrBlank()
            binding.searchClear.isVisible = hasText
            // While composing, the slot beside the bar becomes the send button.
            binding.searchSend.isVisible = hasText
            binding.passwordManager.isVisible = !hasText
        }
        // Enter adds a line, as in any composer. Ctrl+Enter or Shift+Enter sends, for hardware
        // keyboards; on-screen keyboards use the send button beside the bar.
        binding.searchInput.setOnKeyListener { _, keyCode, event ->
            val enter = keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
            if (enter && event.action == KeyEvent.ACTION_DOWN && (event.isCtrlPressed || event.isShiftPressed)) {
                submitSearch()
                true
            } else false
        }
    }

    /** Focuses the composer and raises the keyboard, verifying that it actually came up. */
    private fun focusSearch() {
        val input = binding.searchInput
        if (!input.hasFocus() && !input.requestFocus()) return
        input.setSelection(input.length())
        raiseKeyboard(attempt = 0)
    }

    /**
     * Asks for the keyboard through both entry points, then checks the window's IME inset a
     * moment later and asks again if it is still hidden. A single request is dropped often
     * enough to feel random, most reliably on the first tap after returning from another app,
     * when the input-method manager has not yet caught up with the field's new focus.
     */
    private fun raiseKeyboard(attempt: Int) {
        val input = _binding?.searchInput ?: return
        if (!input.hasFocus()) return
        input.post {
            val current = _binding?.searchInput ?: return@post
            if (!current.hasFocus()) return@post
            WindowCompat.getInsetsController(requireActivity().window, current)
                .show(WindowInsetsCompat.Type.ime())
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(current, InputMethodManager.SHOW_IMPLICIT)
            if (attempt < MAX_KEYBOARD_ATTEMPTS) {
                current.postDelayed({
                    if (_binding != null && !isKeyboardVisible()) raiseKeyboard(attempt + 1)
                }, KEYBOARD_RETRY_DELAY_MS * (attempt + 1))
            }
        }
    }

    private fun isKeyboardVisible(): Boolean {
        val root = _binding?.mainLayout ?: return true
        return ViewCompat.getRootWindowInsets(root)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    /** Calls [onTap] on a finger-up that did not travel; never consumes the event. */
    private inner class TapListener(private val onTap: () -> Unit) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var moved = false
        private val slop = ViewConfiguration.get(requireContext()).scaledTouchSlop

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    moved = false
                }

                MotionEvent.ACTION_MOVE ->
                    if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) moved = true

                MotionEvent.ACTION_UP -> if (!moved) view.post(onTap)
            }
            return false
        }
    }

    /**
     * Sends the composed text to the chosen search engine. The field is emptied only after an
     * app accepted the query, so a missing browser never eats the text.
     */
    private fun submitSearch() {
        val query = binding.searchInput.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) return
        if (sendSearch(requireContext(), prefs.searchEngine, query)) {
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
            showAppList(Constants.FLAG_SET_PASSWORD_APP)
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

    private fun showAppList(flag: Int) {
        viewModel.getAppList()
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
        // Reached only once one of the navigations above opened the drawer.
        if (flag == Constants.FLAG_LAUNCH_APP) prefs.learnTip(Tip.OPEN_DRAWER)
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
                openSettings()
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
        stopCoachAnimation()
        super.onDestroyView()
        _binding = null
    }
}
