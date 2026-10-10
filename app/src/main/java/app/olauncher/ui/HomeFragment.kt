package app.olauncher.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.InputType
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
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
import app.olauncher.databinding.ItemSearchSuggestionBinding
import app.olauncher.helper.AppSearch
import app.olauncher.helper.Onboarding
import app.olauncher.helper.Tip
import app.olauncher.helper.Typefaces
import app.olauncher.helper.detectPasswordManager
import app.olauncher.helper.expandNotificationDrawer
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isPackageInstalled
import app.olauncher.helper.isPrivateSpaceProfile
import app.olauncher.helper.isProfileAvailable
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
import kotlin.math.max

/**
 * The home screen: the date on top, and along the bottom a unified search bar next to a key
 * glyph that opens the password manager. Everything else is gestures on the empty space.
 *
 * The search bar finds apps and the web in one place. The apps its text matches are listed
 * above it, best first, and open with a tap; enter (or the send button) always hands the text
 * to the chosen search engine. An app never opens without being tapped, unlike in the drawer:
 * "weather" may name an installed app and still be meant for the web.
 *
 * Until the user has found the drawer, settings, and what the date and key do, a tip card above
 * the search bar teaches them one at a time (see [Onboarding]).
 *
 * The search bar is a multi-line composer. Its text is treated as a draft: it survives
 * leaving the screen, the drawer, settings, rotation and a launcher restart, and is only
 * dropped once a browser has accepted it or the user taps clear.
 */
class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private companion object {
        const val MAX_KEYBOARD_ATTEMPTS = 3
        const val KEYBOARD_RETRY_DELAY_MS = 120L
        const val MAX_APP_SUGGESTIONS = 4
    }

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var coachAnimator: ObjectAnimator? = null
    private val accessibilityActionIds = mutableListOf<Int>()

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
        initPointerAndKeyInput()
        initClickListeners()
        initSearchBar()
        initCoachCard()
    }

    override fun onResume() {
        super.onResume()
        populateHomeScreen()
        restoreSearchDraft()
        populateCoachCard()
        updateAccessibilityActions()
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
            R.id.date -> {
                prefs.learnTip(Tip.HOME_SHORTCUTS)
                openCalendarApp()
            }

            R.id.passwordManager -> {
                prefs.learnTip(Tip.HOME_SHORTCUTS)
                openPasswordManager()
            }

            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.coachCard -> when (Onboarding.nextHomeTip(prefs.learnedTips)) {
                Tip.OPEN_DRAWER -> showAppList(Constants.FLAG_LAUNCH_APP)
                Tip.OPEN_SETTINGS -> openSettings()
                // Nothing to perform for this one: tapping the card says "got it".
                Tip.HOME_SHORTCUTS -> {
                    prefs.learnTip(Tip.HOME_SHORTCUTS)
                    populateCoachCard()
                }

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
            R.id.passwordManager -> if (showAppList(Constants.FLAG_SET_PASSWORD_APP))
                prefs.learnTip(Tip.HOME_SHORTCUTS)

            R.id.date -> if (showAppList(Constants.FLAG_SET_CALENDAR_APP)) {
                prefs.learnTip(Tip.HOME_SHORTCUTS)
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
                    navigateFromHome(R.id.action_mainFragment_to_settingsFragment)
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
        // The search bar matches the same apps the drawer lists, unlocked Private Space included.
        viewModel.appList.observe(viewLifecycleOwner) { updateSearchSuggestions() }
        viewModel.privateSpaceApps.observe(viewLifecycleOwner) { updateSearchSuggestions() }
        viewModel.privateSpaceLocked.observe(viewLifecycleOwner) { updateSearchSuggestions() }
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

    /**
     * Keyboard and mouse on Home (ChromeOS, desktop windowing): the wheel opens the drawer like a
     * swipe up, a right-click opens settings like a long press, and a letter, up or Enter key
     * opens the drawer while nothing else has focus.
     */
    private fun initPointerAndKeyInput() {
        binding.mainLayout.setOnGenericMotionListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_SCROLL &&
                event.isFromSource(InputDevice.SOURCE_CLASS_POINTER) &&
                event.getAxisValue(MotionEvent.AXIS_VSCROLL) != 0f
            ) {
                showAppList(Constants.FLAG_LAUNCH_APP)
                true
            } else false
        }
        binding.mainLayout.setOnContextClickListener {
            openSettings()
            true
        }
        binding.mainLayout.setOnKeyListener { _, keyCode, event ->
            val opensDrawer = keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ||
                    keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
            if (!opensDrawer || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed)
                return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                showAppList(Constants.FLAG_LAUNCH_APP)
            true
        }
    }

    /**
     * TalkBack users cannot swipe on the empty space, so every Home gesture is also offered as a
     * custom action on the root. Rebuilt on resume since the gesture settings may have changed.
     */
    private fun updateAccessibilityActions() {
        val root = binding.mainLayout
        accessibilityActionIds.forEach { ViewCompat.removeAccessibilityAction(root, it) }
        accessibilityActionIds.clear()
        fun add(label: String, action: () -> Unit) {
            accessibilityActionIds += ViewCompat.addAccessibilityAction(root, label) { _, _ ->
                action()
                true
            }
        }
        add(getString(R.string.tip_open_drawer_action)) { showAppList(Constants.FLAG_LAUNCH_APP) }
        add(getString(R.string.tip_open_settings_action)) { openSettings() }
        if (prefs.swipeLeftEnabled)
            add(swipeAppLabel(prefs.appNameSwipeLeft, R.string.swipe_left_app)) { openSwipeLeftApp() }
        if (prefs.swipeRightEnabled)
            add(swipeAppLabel(prefs.appNameSwipeRight, R.string.swipe_right_app)) { openSwipeRightApp() }
        val swipeDownLabel =
            if (prefs.swipeDownAction == Constants.SwipeDownAction.SEARCH) R.string.search else R.string.notifications
        add(getString(swipeDownLabel)) { swipeDownAction() }
    }

    private fun swipeAppLabel(appName: String, fallback: Int): String =
        if (appName.isBlank()) getString(fallback) else getString(R.string.open_app_named, appName)

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
        // Name what a tap and a long press do instead of TalkBack's generic "activate" and
        // "long press". The key's tap label names its app, so it is set in populatePasswordManager().
        ViewCompat.replaceAccessibilityAction(
            binding.date, AccessibilityActionCompat.ACTION_CLICK,
            getString(R.string.open_calendar), null
        )
        ViewCompat.replaceAccessibilityAction(
            binding.date, AccessibilityActionCompat.ACTION_LONG_CLICK,
            getString(R.string.choose_calendar_app), null
        )
        ViewCompat.replaceAccessibilityAction(
            binding.passwordManager, AccessibilityActionCompat.ACTION_LONG_CLICK,
            getString(R.string.choose_password_manager), null
        )
    }

    private fun initCoachCard() {
        binding.coachDismiss.setOnClickListener {
            prefs.learnAllTips()
            populateCoachCard()
            requireContext().showToast(R.string.tips_skipped, Toast.LENGTH_LONG)
        }
        binding.searchInput.setOnFocusChangeListener { _, hasFocus ->
            // While composing a search the card would only crowd the keyboard.
            populateCoachCard()
            // Pick up apps installed or renamed since the list was last loaded.
            if (hasFocus) viewModel.getAppList()
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
        val content = when (tip) {
            Tip.OPEN_SETTINGS -> CoachContent(
                R.drawable.ic_touch_hold, R.string.tip_open_settings_title,
                R.string.tip_open_settings_body, R.string.tip_open_settings_action,
            )

            Tip.HOME_SHORTCUTS -> CoachContent(
                R.drawable.ic_key, R.string.tip_home_shortcuts_title,
                R.string.tip_home_shortcuts_body, R.string.got_it,
            )

            else -> CoachContent(
                R.drawable.ic_swipe_up, R.string.tip_open_drawer_title,
                R.string.tip_open_drawer_body, R.string.tip_open_drawer_action,
            )
        }
        binding.coachIcon.setImageResource(content.icon)
        binding.coachTitle.setText(content.title)
        binding.coachBody.setText(content.body)
        // TalkBack reads "double-tap to open apps" rather than a bare "double-tap to activate".
        ViewCompat.replaceAccessibilityAction(
            binding.coachCard,
            AccessibilityActionCompat.ACTION_CLICK,
            getString(content.action),
            null,
        )
        // Focus changes can land here while paused; the glyph only moves on a visible screen.
        if (isResumed) startCoachAnimation(tip)
    }

    /** What the tip card shows for one tip, and what TalkBack calls tapping it. */
    private class CoachContent(
        @DrawableRes val icon: Int,
        @StringRes val title: Int,
        @StringRes val body: Int,
        @StringRes val action: Int,
    )

    /** A slow nudge on the tip's glyph: a lift for the swipe, a swell for a tap or long-press. */
    private fun startCoachAnimation(tip: Tip) {
        // Animations switched off in system settings leave the glyph still.
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val icon = binding.coachIcon
        val lift = -6f * resources.displayMetrics.density
        coachAnimator = when (tip) {
            Tip.OPEN_SETTINGS, Tip.HOME_SHORTCUTS -> ObjectAnimator.ofPropertyValuesHolder(
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
        if (!navigateFromHome(R.id.action_mainFragment_to_settingsFragment)) return
        // A tip counts as learned only once its screen has actually opened.
        prefs.learnTip(Tip.OPEN_SETTINGS)
        viewModel.firstOpen(false)
    }

    private fun initSearchBar() {
        // The window is edge to edge: pad the root clear of the system bars and display cutout,
        // and at the bottom clear of the keyboard too, so the search bar rises above it while typing.
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainLayout) { root, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            root.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, ime.bottom))
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
            updateSearchSuggestions()
        }
        // The field stays multi-line, so long text wraps and grows the bar, but the keyboard is
        // told it is a one-line field: its enter key becomes Go and submits instead of adding
        // a line break.
        binding.searchInput.setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submitSearch()
                true
            } else false
        }
        // Hardware keyboards: enter submits too, and Shift+Enter still starts a new line.
        binding.searchInput.setOnKeyListener { _, keyCode, event ->
            val enter = keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
            if (!enter || event.isShiftPressed) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) submitSearch()
            true
        }
    }

    /** Every app the drawer would list right now: the main list, plus Private Space while unlocked. */
    private fun searchableApps(): List<AppModel> {
        val apps = viewModel.appList.value.orEmpty()
        val privateApps =
            if (viewModel.privateSpaceLocked.value == false) viewModel.privateSpaceApps.value.orEmpty()
            else emptyList()
        return apps + privateApps
    }

    /** Lists the apps the search text matches above the bar, best first; a tap opens one. */
    private fun updateSearchSuggestions() {
        val binding = _binding ?: return
        val query = binding.searchInput.text?.toString()?.trim().orEmpty()
        val matches =
            if (query.isEmpty()) emptyList()
            else AppSearch.search(searchableApps(), query, label = { it.appLabel }, key = { it.emphasisKey })
                .take(MAX_APP_SUGGESTIONS)
        val list = binding.searchSuggestions
        list.removeAllViews()
        list.isVisible = matches.isNotEmpty()
        val inflater = layoutInflater
        matches.forEach { (app, _) ->
            suggestionRow(inflater, list).apply {
                text = suggestionText(app, textColors.defaultColor)
                setOnClickListener { openSearchedApp(app) }
            }
        }
    }

    /**
     * The app's name, then, for a work-profile or Private Space copy, a faded "· Work profile"
     * so two copies of one app can be told apart by sight and by TalkBack alike.
     */
    private fun suggestionText(app: AppModel, color: Int): CharSequence {
        if (app.user == Process.myUserHandle()) return app.appLabel
        val profile = getString(
            if (isPrivateSpaceProfile(requireContext(), app.user)) R.string.private_space else R.string.work_profile
        )
        return SpannableStringBuilder(app.appLabel).append(
            "  ·  $profile",
            ForegroundColorSpan(ColorUtils.setAlphaComponent(color, 0x99)),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    private fun suggestionRow(inflater: LayoutInflater, parent: ViewGroup): TextView =
        ItemSearchSuggestionBinding.inflate(inflater, parent, true).root

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

    private fun openSearchedApp(app: AppModel) {
        viewModel.selectedApp(app, Constants.FLAG_LAUNCH_APP)
        clearSearch()
    }

    /**
     * Enter and the send button: sends the composed text to the chosen search engine. The field
     * is emptied only after an app accepted the query, so a missing browser never eats the text.
     */
    private fun submitSearch() {
        val query = binding.searchInput.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) return
        if (sendSearch(requireContext(), prefs.searchEngine, query)) clearSearch()
        else requireContext().showToast(R.string.search_not_available)
    }

    private fun clearSearch() {
        binding.searchInput.text?.clear()
        prefs.searchDraft = ""
        binding.searchInput.hideKeyboard()
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
     * Keeps the password shortcut bound to an installed app: drops a binding whose app is gone
     * (not one whose profile is merely paused or locked), adopts a known password manager when
     * nothing is chosen, and names the glyph after it for accessibility. An unbound glyph is
     * drawn faded as the cue to pick an app.
     */
    private fun populatePasswordManager() {
        val context = requireContext()
        if (prefs.passwordAppPackage.isNotBlank() &&
            isProfileAvailable(context, prefs.passwordAppUser) &&
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
        // "Password manager, Bitwarden": what the glyph is, then which app it opens.
        binding.passwordManager.contentDescription =
            if (bound) getString(R.string.setting_value, getString(R.string.password_manager), prefs.passwordAppName)
            else getString(R.string.password_manager)
        ViewCompat.replaceAccessibilityAction(
            binding.passwordManager, AccessibilityActionCompat.ACTION_CLICK,
            if (bound) getString(R.string.open_app_named, prefs.passwordAppName)
            else getString(R.string.choose_password_manager),
            null
        )
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

    /** Opens the drawer for [flag]; returns whether it actually opened. */
    private fun showAppList(flag: Int): Boolean {
        if (!navigateFromHome(R.id.action_mainFragment_to_appListFragment, bundleOf(Constants.Key.FLAG to flag)))
            return false
        viewModel.getAppList()
        // Reached only once the drawer has actually opened.
        if (flag == Constants.FLAG_LAUNCH_APP) prefs.learnTip(Tip.OPEN_DRAWER)
        return true
    }

    /**
     * Follows one of Home's own actions, but only while Home is still the current destination.
     * Input arrives in bursts (a single wheel flick sends several scroll events, a swipe can end
     * on a tap target), and once the first event has left Home the rest must not open another
     * copy of the drawer or settings on top of it. Returns whether it navigated.
     */
    private fun navigateFromHome(actionId: Int, args: Bundle? = null): Boolean {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.mainFragment) return false
        return try {
            navController.navigate(actionId, args)
            true
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            false
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
        accessibilityActionIds.clear()
        _binding = null
    }
}
