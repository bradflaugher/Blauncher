package app.olauncher.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.graphics.Typeface
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import android.widget.ArrayAdapter
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.olauncher.BuildConfig
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppCategory
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.data.SearchEngine
import app.olauncher.data.ShortcutGlyph
import app.olauncher.databinding.FragmentSettingsBinding
import app.olauncher.helper.SmartOrder
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.isTablet
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openUrl
import app.olauncher.helper.rateApp
import app.olauncher.helper.shareApp
import app.olauncher.helper.showToast
import java.util.Locale

class SettingsFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            root.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        viewModel.isOlauncherDefault()

        // Home button for recents feature disabled
        // populateHomeButtonRecents()
        populateAppThemeText()
        populateTextSize()
        populateAlignment()
        populateDateBold()
        populatePasswordApp()
        populateShortcutGlyph()
        populateSearchEngine()
        populateSmartOrdering()
        populateSwipeApps()
        populateSwipeDownAction()
        initClickListeners()
        initObservers()
    }

    override fun onClick(view: View) {
        binding.appThemeSelectLayout.visibility = View.GONE
        binding.swipeDownSelectLayout.visibility = View.GONE
        if (view.id != R.id.textSizeMinus && view.id != R.id.textSizePlus) {
            if (binding.textSizesLayout.isVisible) {
                binding.textSizesLayout.visibility = View.GONE
                applyTextSizeScale()
            }
        }
        binding.alignmentSelectLayout.visibility = View.GONE

        when (view.id) {
            R.id.appInfo -> openAppInfo(requireContext(), Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.howItWorks -> showQuickGuide()
            R.id.sendFeedback -> requireContext().openUrl(Constants.URL_FEEDBACK)
            R.id.shareApp -> requireContext().shareApp()
            R.id.rateApp -> requireContext().rateApp()
            // Home button for recents feature disabled
            // R.id.homeButtonRecents -> toggleHomeButtonRecents()
            R.id.passwordApp -> showAppList(Constants.FLAG_SET_PASSWORD_APP)
            R.id.shortcutGlyph -> showShortcutGlyphChooser()
            R.id.searchEngine -> showSearchEngineChooser()
            R.id.alignment -> binding.alignmentSelectLayout.visibility = View.VISIBLE
            R.id.alignmentLeft -> viewModel.updateHomeAlignment(Gravity.START)
            R.id.alignmentCenter -> viewModel.updateHomeAlignment(Gravity.CENTER)
            R.id.alignmentRight -> viewModel.updateHomeAlignment(Gravity.END)
            R.id.dateBold -> toggleDateBold()
            R.id.appThemeText -> binding.appThemeSelectLayout.visibility = View.VISIBLE
            R.id.themeLight -> updateTheme(AppCompatDelegate.MODE_NIGHT_NO)
            R.id.themeDark -> updateTheme(AppCompatDelegate.MODE_NIGHT_YES)
            R.id.themeSystem -> updateTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            R.id.textSizeValue -> binding.textSizesLayout.visibility = View.VISIBLE
            R.id.tvGestures -> binding.flSwipeDown.visibility = View.VISIBLE

            R.id.textSizeMinus -> adjustTextSizePreview(-0.1f)
            R.id.textSizePlus -> adjustTextSizePreview(0.1f)

            R.id.swipeUpApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_UP_APP)
            R.id.swipeLeftApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_LEFT_APP)
            R.id.swipeRightApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_RIGHT_APP)
            R.id.swipeDownAction -> binding.swipeDownSelectLayout.visibility = View.VISIBLE
            R.id.notifications -> updateSwipeDownAction(Constants.SwipeDownAction.NOTIFICATIONS)
            R.id.search -> updateSwipeDownAction(Constants.SwipeDownAction.SEARCH)

            R.id.github -> requireContext().openUrl(Constants.URL_OLAUNCHER_GITHUB)
            R.id.privacyPolicy -> requireContext().openUrl(Constants.URL_PRIVACY_POLICY)
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.alignment -> {
                prefs.appLabelAlignment = prefs.homeAlignment
                requireContext().showToast(getString(R.string.alignment_changed))
            }

            R.id.appThemeText -> {
                binding.appThemeSelectLayout.visibility = View.VISIBLE
                binding.themeSystem.visibility = View.VISIBLE
            }

            R.id.swipeUpApp -> toggleSwipeUp()
            R.id.swipeLeftApp -> toggleSwipeLeft()
            R.id.swipeRightApp -> toggleSwipeRight()
        }
        return true
    }

    private fun initClickListeners() {
        binding.scrollLayout.setOnClickListener(this)
        binding.appInfo.setOnClickListener(this)
        binding.setLauncher.setOnClickListener(this)
        binding.howItWorks.setOnClickListener(this)
        binding.sendFeedback.setOnClickListener(this)
        binding.shareApp.setOnClickListener(this)
        binding.rateApp.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.homeButtonRecents.setOnClickListener(this)
        binding.passwordApp.setOnClickListener(this)
        binding.shortcutGlyph.setOnClickListener(this)
        binding.searchEngine.setOnClickListener(this)
        binding.alignment.setOnClickListener(this)
        binding.alignmentLeft.setOnClickListener(this)
        binding.alignmentCenter.setOnClickListener(this)
        binding.alignmentRight.setOnClickListener(this)
        binding.dateBold.setOnClickListener(this)
        binding.swipeUpApp.setOnClickListener(this)
        binding.swipeLeftApp.setOnClickListener(this)
        binding.swipeRightApp.setOnClickListener(this)
        binding.swipeDownAction.setOnClickListener(this)
        binding.search.setOnClickListener(this)
        binding.notifications.setOnClickListener(this)
        binding.appThemeText.setOnClickListener(this)
        binding.themeLight.setOnClickListener(this)
        binding.themeDark.setOnClickListener(this)
        binding.themeSystem.setOnClickListener(this)
        binding.textSizeValue.setOnClickListener(this)
        binding.github.setOnClickListener(this)
        binding.privacyPolicy.setOnClickListener(this)

        binding.smartOrderSettings.refreshCategories.setOnClickListener {
            confirmSmartOrderAction(R.string.app_groups, R.string.confirm_refresh_categories, R.string.recategorize) {
                prefs.clearAppCategoryOverrides()
                viewModel.getAppList()
                populateSmartOrdering()
                requireContext().showToast(R.string.categories_refreshed)
            }
        }
        binding.smartOrderSettings.resetLearning.setOnClickListener {
            confirmSmartOrderAction(R.string.usage_learning, R.string.confirm_reset_learning, R.string.reset) {
                prefs.clearCategoryUsageData()
                viewModel.getAppList()
                populateSmartOrdering()
                requireContext().showToast(R.string.learning_reset)
            }
        }
        binding.smartOrderSettings.pinnedGroups.setOnClickListener { showPinnedGroupsChooser() }

        binding.textSizeMinus.setOnClickListener(this)
        binding.textSizePlus.setOnClickListener(this)

        binding.alignment.setOnLongClickListener(this)
        binding.appThemeText.setOnLongClickListener(this)
        binding.swipeUpApp.setOnLongClickListener(this)
        binding.swipeLeftApp.setOnLongClickListener(this)
        binding.swipeRightApp.setOnLongClickListener(this)
    }

    private fun initObservers() {
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner) {
            if (it) {
                binding.setLauncher.text = getString(R.string.change_default_launcher)
            }
        }
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            populateAlignment()
        }
        viewModel.updateSwipeApps.observe(viewLifecycleOwner) {
            populateSwipeApps()
        }
        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populatePasswordApp()
        }
    }

    private fun toggleSwipeUp() {
        prefs.swipeUpEnabled = !prefs.swipeUpEnabled
        showSwipeAppState(binding.swipeUpApp, prefs.swipeUpEnabled)
        requireContext().showToast(
            getString(if (prefs.swipeUpEnabled) R.string.swipe_up_app_enabled else R.string.swipe_up_app_disabled)
        )
    }

    private fun toggleSwipeLeft() {
        prefs.swipeLeftEnabled = !prefs.swipeLeftEnabled
        showSwipeAppState(binding.swipeLeftApp, prefs.swipeLeftEnabled)
        requireContext().showToast(
            getString(if (prefs.swipeLeftEnabled) R.string.swipe_left_app_enabled else R.string.swipe_left_app_disabled)
        )
    }

    private fun toggleSwipeRight() {
        prefs.swipeRightEnabled = !prefs.swipeRightEnabled
        showSwipeAppState(binding.swipeRightApp, prefs.swipeRightEnabled)
        requireContext().showToast(
            getString(if (prefs.swipeRightEnabled) R.string.swipe_right_app_enabled else R.string.swipe_right_app_disabled)
        )
    }

    /** A disabled swipe app is faded; the state is also spoken, so it is not shown by color alone. */
    private fun showSwipeAppState(view: TextView, enabled: Boolean) {
        view.setTextColor(
            requireContext().getColorFromAttr(if (enabled) R.attr.primaryColor else R.attr.primaryColorTrans50)
        )
        describeSwipeAppState(view, enabled)
    }

    private fun describeSwipeAppState(view: TextView, enabled: Boolean) {
        ViewCompat.setStateDescription(view, getString(if (enabled) R.string.enabled else R.string.disabled))
    }

    /** Reads a value view as "Label, value" so TalkBack names the setting it belongs to. */
    private fun TextView.describeAs(@StringRes label: Int) {
        contentDescription = getString(R.string.setting_value, getString(label), text)
    }

    private fun toggleDateBold() {
        prefs.dateBold = !prefs.dateBold
        populateDateBold()
        viewModel.refreshHome()
    }

    /**
     * Help: every gesture on one page, for anyone who skipped the tips or forgot one, then the
     * questions people ask about a launcher with nothing on it. The swipe lines name what the
     * user has actually set, so the guide never describes a different phone.
     */
    private fun showQuickGuide() {
        val off = getString(R.string.guide_gesture_off)
        val swipeDown = getString(
            if (prefs.swipeDownAction == Constants.SwipeDownAction.SEARCH) R.string.search
            else R.string.notifications
        )
        val swipeUp = if (prefs.swipeUpEnabled) prefs.appNameSwipeUp.ifBlank { getString(R.string.browser) } else off
        val swipeLeft = if (prefs.swipeLeftEnabled) prefs.appNameSwipeLeft else off
        val swipeRight = if (prefs.swipeRightEnabled) prefs.appNameSwipeRight else off
        val guide = SpannableStringBuilder()
        fun heading(res: Int) {
            if (guide.isNotEmpty()) guide.append("\n\n")
            val start = guide.length
            guide.append(getString(res))
            guide.setSpan(StyleSpan(Typeface.BOLD), start, guide.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            guide.setSpan(RelativeSizeSpan(1.15f), start, guide.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        fun line(text: String) {
            guide.append("\n")
            val start = guide.length
            guide.append(text)
            guide.setSpan(BulletSpan(16), start, guide.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        heading(R.string.guide_home_heading)
        line(getString(R.string.guide_search_apps))
        line(getString(R.string.guide_swipe_up, swipeUp))
        line(getString(R.string.guide_long_press))
        line(getString(R.string.guide_swipe_down, swipeDown))
        line(getString(R.string.guide_swipe_left, swipeLeft))
        line(getString(R.string.guide_swipe_right, swipeRight))
        line(getString(R.string.guide_date))
        line(getString(R.string.guide_key))
        line(getString(R.string.guide_search))
        heading(R.string.guide_drawer_heading)
        line(getString(R.string.guide_type))
        line(getString(R.string.guide_enter))
        line(getString(R.string.guide_app_menu))
        line(getString(R.string.guide_emphasize))
        line(getString(R.string.guide_close_drawer))
        heading(R.string.guide_faq_heading)
        fun question(q: Int, a: Int) {
            guide.append("\n\n")
            val start = guide.length
            guide.append(getString(q))
            guide.setSpan(StyleSpan(Typeface.BOLD), start, guide.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            guide.append("\n").append(getString(a))
        }
        question(R.string.faq_old_launcher_q, R.string.faq_old_launcher_a)
        question(R.string.faq_icons_q, R.string.faq_icons_a)
        question(R.string.faq_wrong_group_q, R.string.faq_wrong_group_a)
        question(R.string.faq_order_q, R.string.faq_order_a)
        question(R.string.faq_fewer_apps_q, R.string.faq_fewer_apps_a)
        question(R.string.faq_online_q, R.string.faq_online_a)
        question(R.string.faq_feedback_q, R.string.faq_feedback_a)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.how_it_works_title)
            .setMessage(guide)
            .setPositiveButton(R.string.got_it, null)
            .setNeutralButton(R.string.show_tips_again) { _, _ ->
                prefs.resetTips()
                requireContext().showToast(R.string.tips_reset)
            }
            .show()
    }

    private fun populateSearchEngine() {
        binding.searchEngine.text = prefs.searchEngine.displayName(requireContext())
        binding.searchEngine.describeAs(R.string.search_engine)
    }

    private fun showSearchEngineChooser() {
        val engines = SearchEngine.entries
        val checked = engines.indexOf(prefs.searchEngine)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.search_engine)
            .setSingleChoiceItems(engines.map { it.displayName(requireContext()) }.toTypedArray(), checked) { dialog, which ->
                prefs.searchEngine = engines[which]
                populateSearchEngine()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /** Shows the shortcut button's glyph, drawn as it is on Home, with its name. */
    private fun populateShortcutGlyph() {
        val glyph = prefs.shortcutGlyph
        binding.shortcutGlyph.text = glyph.label
        binding.shortcutGlyph.setCompoundDrawablesRelativeWithIntrinsicBounds(glyph.icon, 0, 0, 0)
        TextViewCompat.setCompoundDrawableTintList(
            binding.shortcutGlyph,
            ColorStateList.valueOf(requireContext().getColorFromAttr(R.attr.primaryColor)),
        )
        binding.shortcutGlyph.describeAs(R.string.shortcut_glyph)
    }

    /** Every glyph on a grid, the current one ringed; a tap picks it. */
    private fun showShortcutGlyphChooser() {
        val context = requireContext()
        val density = resources.displayMetrics.density
        val cell = (56 * density).toInt()
        val padding = (16 * density).toInt()
        val tint = ColorStateList.valueOf(context.getColorFromAttr(R.attr.primaryColor))
        val grid = GridLayout(context).apply {
            columnCount = 5
            setPadding(padding, padding, padding, 0)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.shortcut_glyph)
            .setView(ScrollView(context).apply { addView(grid) })
            .setNegativeButton(R.string.close, null)
            .create()
        ShortcutGlyph.entries.forEach { glyph ->
            grid.addView(ImageView(context).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cell
                    height = cell
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                val inset = (14 * density).toInt()
                setPadding(inset, inset, inset, inset)
                setImageResource(glyph.icon)
                imageTintList = tint
                contentDescription = glyph.label
                isSelected = glyph == prefs.shortcutGlyph
                if (isSelected) setBackgroundResource(R.drawable.bg_quick_action)
                else setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener {
                    prefs.shortcutGlyph = glyph
                    populateShortcutGlyph()
                    dialog.dismiss()
                }
            })
        }
        dialog.show()
    }

    /** Names the app behind the shortcut button beside the search bar, or invites picking one. */
    private fun populatePasswordApp() {
        binding.passwordApp.text = prefs.passwordAppName.ifBlank { getString(R.string.none) }
        binding.passwordApp.describeAs(R.string.password_manager)
    }

    private fun populateDateBold() {
        binding.dateBold.text = getString(if (prefs.dateBold) R.string.on else R.string.off)
        binding.dateBold.describeAs(R.string.bold_date)
    }

    private fun confirmSmartOrderAction(titleRes: Int, messageRes: Int, actionRes: Int, action: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(actionRes) { dialog, _ ->
                action()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private var pendingTextSizeScale: Float = -1f

    private fun adjustTextSizePreview(delta: Float) {
        val maxScale = if (isTablet(requireContext())) 2.0f else 1.5f
        val current = if (pendingTextSizeScale > 0) pendingTextSizeScale else prefs.textSizeScale
        val newScale = Math.round((current + delta) * 10f) / 10f
        val clamped = newScale.coerceIn(0.5f, maxScale)
        if (clamped == current) return
        pendingTextSizeScale = clamped
        val formatted = String.format(Locale.getDefault(), "%.1f", clamped)
        binding.textSizeValue.text = formatted
        binding.textSizeValue.describeAs(R.string.text_size)
        binding.textSizeCurrent.text = formatted
    }

    private fun applyTextSizeScale() {
        if (pendingTextSizeScale < 0 || prefs.textSizeScale == pendingTextSizeScale) {
            pendingTextSizeScale = -1f
            return
        }
        prefs.textSizeScale = pendingTextSizeScale
        pendingTextSizeScale = -1f
        requireActivity().recreate()
    }

    private fun updateTheme(appTheme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == appTheme) return
        prefs.appTheme = appTheme
        populateAppThemeText(appTheme)
        setAppTheme(appTheme)
    }

    private fun setAppTheme(theme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == theme) return
        requireActivity().recreate()
    }

    private fun populateAppThemeText(appTheme: Int = prefs.appTheme) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> binding.appThemeText.text = getString(R.string.dark)
            AppCompatDelegate.MODE_NIGHT_NO -> binding.appThemeText.text = getString(R.string.light)
            else -> binding.appThemeText.text = getString(R.string.system_default)
        }
        binding.appThemeText.describeAs(R.string.theme_mode)
    }

    private fun populateTextSize() {
        val formatted = String.format(Locale.getDefault(), "%.1f", prefs.textSizeScale)
        binding.textSizeValue.text = formatted
        binding.textSizeValue.describeAs(R.string.text_size)
        binding.textSizeCurrent.text = formatted
    }

    private fun populateSmartOrdering() = with(binding.smartOrderSettings) {
        // The row shows a compact summary; the chooser dialog holds the full ordered list.
        val pinned = prefs.pinnedCategories
        pinnedGroups.text = when {
            pinned.isEmpty() -> getString(R.string.none)
            pinned.size == 1 -> pinned.first().displayName
            else -> getString(R.string.pinned_count, pinned.size)
        }
        pinnedGroups.describeAs(R.string.pinned_groups)
        refreshCategories.describeAs(R.string.app_groups)
        resetLearning.describeAs(R.string.usage_learning)
        // What the model would surface right now, past the pins,
        // each group name tinted with its category color.
        val preview = SpannableStringBuilder(getString(R.string.up_next_label)).append(' ')
        SmartOrder.currentOrder(prefs)
            .filterNot { it in pinned }
            .take(3)
            .forEachIndexed { index, category ->
                if (index > 0) preview.append("  ·  ")
                val start = preview.length
                preview.append(category.displayName)
                preview.setSpan(
                    ForegroundColorSpan(category.colorFor(requireContext())),
                    start,
                    preview.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        topGroups.text = preview
    }

    // Tapping pins a group at the end of the list; tapping again unpins it. The
    // number in front of each pinned group shows the order the drawer will use.
    private fun showPinnedGroupsChooser() {
        val categories = AppCategory.entries
        val pinned = prefs.pinnedCategories.toMutableList()
        val adapter = object : ArrayAdapter<AppCategory>(
            requireContext(),
            android.R.layout.simple_list_item_1,
            categories,
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val category = categories[position]
                val index = pinned.indexOf(category)
                (view as TextView).text =
                    if (index >= 0) getString(R.string.pinned_group_order, index + 1, category.displayName)
                    else category.displayName
                view.alpha = if (index >= 0) 1f else 0.5f
                ViewCompat.setStateDescription(
                    view,
                    getString(if (index >= 0) R.string.pinned else R.string.not_pinned),
                )
                return view
            }
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.pin_groups_in_order)
            .setAdapter(adapter, null)
            .setPositiveButton(R.string.done) { d, _ ->
                prefs.pinnedCategories = pinned
                d.dismiss()
                populateSmartOrdering()
                viewModel.getAppList()
            }
            .setNegativeButton(R.string.close, null)
            .create()
        dialog.listView.setOnItemClickListener { _, _, position, _ ->
            val category = categories[position]
            if (!pinned.remove(category)) pinned.add(category)
            adapter.notifyDataSetChanged()
        }
        dialog.show()
    }

    private fun populateAlignment() {
        when (prefs.homeAlignment) {
            Gravity.START -> binding.alignment.text = getString(R.string.left)
            Gravity.CENTER -> binding.alignment.text = getString(R.string.center)
            Gravity.END -> binding.alignment.text = getString(R.string.right)
        }
        binding.alignment.describeAs(R.string.home_layout_alignment)
    }

    private fun populateSwipeDownAction() {
        binding.swipeDownAction.text = when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.NOTIFICATIONS -> getString(R.string.notifications)
            else -> getString(R.string.search)
        }
        binding.swipeDownAction.describeAs(R.string.swipe_down_for)
    }

    private fun updateSwipeDownAction(swipeDownFor: Int) {
        if (prefs.swipeDownAction == swipeDownFor) return
        prefs.swipeDownAction = swipeDownFor
        populateSwipeDownAction()
    }

    private fun populateSwipeApps() {
        // Until an app is picked, swipe up opens the default browser.
        binding.swipeUpApp.text = prefs.appNameSwipeUp.ifBlank { getString(R.string.browser) }
        binding.swipeUpApp.describeAs(R.string.swipe_up_app)
        if (!prefs.swipeUpEnabled) showSwipeAppState(binding.swipeUpApp, false)
        else describeSwipeAppState(binding.swipeUpApp, true)
        binding.swipeLeftApp.text = prefs.appNameSwipeLeft
        binding.swipeRightApp.text = prefs.appNameSwipeRight
        binding.swipeLeftApp.describeAs(R.string.swipe_left_app)
        binding.swipeRightApp.describeAs(R.string.swipe_right_app)
        // Enabled keeps the style's own colors; only a disabled app is recolored (faded).
        if (!prefs.swipeLeftEnabled) showSwipeAppState(binding.swipeLeftApp, false)
        else describeSwipeAppState(binding.swipeLeftApp, true)
        if (!prefs.swipeRightEnabled) showSwipeAppState(binding.swipeRightApp, false)
        else describeSwipeAppState(binding.swipeRightApp, true)
    }

    private fun showAppListIfEnabled(flag: Int) {
        if ((flag == Constants.FLAG_SET_SWIPE_UP_APP) and !prefs.swipeUpEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        if ((flag == Constants.FLAG_SET_SWIPE_LEFT_APP) and !prefs.swipeLeftEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        if ((flag == Constants.FLAG_SET_SWIPE_RIGHT_APP) and !prefs.swipeRightEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        showAppList(flag)
    }

    private fun showAppList(flag: Int) {
        if (navigateFromSettings(R.id.action_settingsFragment_to_appListFragment, bundleOf(Constants.Key.FLAG to flag)))
            viewModel.getAppList()
    }

    /**
     * Follows one of Settings' own actions only while Settings is still on screen. A second tap
     * that lands before the drawer has replaced it would otherwise ask the drawer for an action
     * it does not have, which throws.
     */
    private fun navigateFromSettings(actionId: Int, args: Bundle? = null): Boolean {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.settingsFragment) return false
        navController.navigate(actionId, args)
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
