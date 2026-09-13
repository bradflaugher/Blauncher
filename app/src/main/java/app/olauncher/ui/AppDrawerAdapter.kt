package app.olauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.content.res.ColorStateList
import android.os.UserHandle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Filter
import android.widget.Filterable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.databinding.AdapterAppDrawerBinding
import app.olauncher.databinding.AdapterGroupToggleBinding
import app.olauncher.databinding.AdapterPrivateSpaceHeaderBinding
import app.olauncher.helper.GroupCollapse
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.showKeyboard
import app.olauncher.helper.Typefaces
import java.text.Normalizer

class AppDrawerAdapter(
    private var flag: Int,
    private val appLabelGravity: Int,
    private val appClickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appDeleteListener: (AppModel) -> Unit,
    private val appRenameListener: (AppModel, String) -> Unit,
    private val appCategoryListener: (AppModel) -> Unit,
    private val appEmphasisListener: (AppModel) -> Unit = {},
    private val privateSpaceToggleListener: () -> Unit = {},
    private val privateSpaceSettingsListener: () -> Unit = {},
) : ListAdapter<AppModel, RecyclerView.ViewHolder>(DIFF_CALLBACK), Filterable {

    companion object {
        const val VIEW_TYPE_APP = 0
        const val VIEW_TYPE_PRIVATE_HEADER = 1
        const val VIEW_TYPE_GROUP_TOGGLE = 2

        /**
         * Dimmed rows fade to half strength. Applied through the text color and the drawable
         * alpha, never View.alpha: the row layout animates visibility changes, and that
         * transition drives View.alpha back to 1 whenever the title reappears after the menu.
         */
        private const val DIMMED_ALPHA_255 = 128

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppModel>() {
            override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean = when {
                oldItem is AppModel.App && newItem is AppModel.App ->
                    oldItem.appPackage == newItem.appPackage &&
                        oldItem.user == newItem.user &&
                        oldItem.category == newItem.category

                oldItem is AppModel.PinnedShortcut && newItem is AppModel.PinnedShortcut ->
                    oldItem.shortcutId == newItem.shortcutId &&
                        oldItem.user == newItem.user &&
                        oldItem.category == newItem.category

                oldItem is AppModel.PrivateSpaceHeader && newItem is AppModel.PrivateSpaceHeader -> true

                oldItem is AppModel.GroupToggle && newItem is AppModel.GroupToggle ->
                    oldItem.toggleKey == newItem.toggleKey

                else -> false
            }

            override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean =
                oldItem == newItem
        }
    }

    private var autoLaunch = true
    private var isBangSearch = false
    var allowAutoLaunch = true

    /**
     * Groups the user has expanded during this drawer visit (see [GroupCollapse.toggleKey]).
     * Lives with the adapter, which the drawer recreates on every open, so it resets by itself.
     */
    private val expandedGroups = mutableSetOf<String>()
    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val separatorsRegex = Regex("[-_+,.`'\\s\\p{Z}]")
    private val appFilter = createAppFilter()
    private val myUserHandle = android.os.Process.myUserHandle()

    var appsList: MutableList<AppModel> = mutableListOf()
    var appFilteredList: MutableList<AppModel> = mutableListOf()

    override fun getItemViewType(position: Int): Int {
        return when (appFilteredList.getOrNull(position)) {
            is AppModel.PrivateSpaceHeader -> VIEW_TYPE_PRIVATE_HEADER
            is AppModel.GroupToggle -> VIEW_TYPE_GROUP_TOGGLE
            else -> VIEW_TYPE_APP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_PRIVATE_HEADER -> PrivateSpaceHeaderViewHolder(
                AdapterPrivateSpaceHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            VIEW_TYPE_GROUP_TOGGLE -> GroupToggleViewHolder(
                AdapterGroupToggleBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            else -> ViewHolder(
                AdapterAppDrawerBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        try {
            if (appFilteredList.isEmpty() || position == RecyclerView.NO_POSITION) return
            val appModel = appFilteredList[holder.bindingAdapterPosition]
            when (holder) {
                is PrivateSpaceHeaderViewHolder -> {
                    holder.bind(
                        appLabelGravity,
                        privateSpaceToggleListener,
                        privateSpaceSettingsListener,
                    )
                }

                is GroupToggleViewHolder -> {
                    if (appModel is AppModel.GroupToggle) {
                        holder.bind(appLabelGravity, appModel, ::toggleGroup)
                    }
                }

                is ViewHolder -> holder.bind(
                    flag,
                    appLabelGravity,
                    myUserHandle,
                    appModel,
                    appClickListener,
                    appDeleteListener,
                    appInfoListener,
                    appRenameListener,
                    appCategoryListener,
                    appEmphasisListener,
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                isBangSearch = charSearch?.startsWith("!") ?: false
                autoLaunch = allowAutoLaunch && (charSearch?.startsWith(" ")?.not() ?: true)

                val appFilteredList = if (charSearch.isNullOrBlank()) {
                    displayRows()
                } else {
                    // Dedupe multi-category duplicates so keyboard matching / auto-launch
                    // still treats each app as a single result.
                    dedupeAppsForSearch(
                        appsList.filter { app ->
                            app.isLaunchable() && appLabelMatches(app.appLabel, charSearch)
                        }
                    )
                }

                val filterResults = FilterResults()
                filterResults.values = appFilteredList
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                results?.values?.let {
                    val items = it as MutableList<AppModel>
                    appFilteredList = items
                    submitList(appFilteredList) {
                        autoLaunch()
                    }
                }
            }
        }
    }

    private fun autoLaunch() {
        try {
            if (itemCount == 1
                && autoLaunch
                && isBangSearch.not()
                && flag == Constants.FLAG_LAUNCH_APP
                && appFilteredList.isNotEmpty()
                && appFilteredList[0].isLaunchable()
            ) appClickListener(appFilteredList[0])
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun AppModel.isLaunchable(): Boolean =
        this is AppModel.App || this is AppModel.PinnedShortcut

    /**
     * The rows shown with an empty search: the full list, with each emphasized group's faded
     * apps folded behind a toggle row. Pickers list every app plainly. Search always filters the
     * full list, so collapsed apps stay one keystroke away.
     */
    private fun displayRows(): MutableList<AppModel> {
        if (flag != Constants.FLAG_LAUNCH_APP) return appsList
        // Snapshot: the filter calls this from its worker thread while taps edit the set.
        val expanded = expandedGroups.toSet()
        return GroupCollapse.collapse(
            appsList,
            expanded,
            describe = { row ->
                GroupCollapse.Row(
                    group = row.category,
                    dimmed = row.dimmed,
                    isNew = row.isNew,
                    startsSection = row is AppModel.PrivateSpaceHeader,
                )
            },
            toggle = { key, group, collapsedApps, isExpanded ->
                AppModel.GroupToggle(key, group, collapsedApps, isExpanded, collapsedApps.first().user)
            },
        ).toMutableList()
    }

    private fun toggleGroup(toggle: AppModel.GroupToggle) {
        if (!expandedGroups.remove(toggle.toggleKey)) expandedGroups.add(toggle.toggleKey)
        appFilteredList = displayRows()
        submitList(appFilteredList)
    }


    private fun dedupeAppsForSearch(apps: List<AppModel>): MutableList<AppModel> {
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<AppModel>()
        for (app in apps) {
            val key = when (app) {
                is AppModel.App -> "app:${app.appPackage}|${app.user}"
                is AppModel.PinnedShortcut -> "shortcut:${app.shortcutId}|${app.user}"
                is AppModel.PrivateSpaceHeader -> "private-space"
                is AppModel.GroupToggle -> "toggle:${app.toggleKey}"
            }
            if (seen.add(key)) result.add(app)
        }
        return result
    }

    private fun appLabelMatches(appLabel: String, charSearch: CharSequence): Boolean {
        if (appLabel.contains(charSearch.trim(), true)) return true
        val query = charSearch.normalizeForSearch()
        return query.isNotEmpty() && appLabel.normalizeForSearch().contains(query, true)
    }

    private fun CharSequence.normalizeForSearch(): String =
        Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(diacriticsRegex, "")
            .replace(separatorsRegex, "")

    fun setAppList(appsList: MutableList<AppModel>) {
        // Add empty app for bottom padding in recyclerview and assign to list
        appsList.add(
            AppModel.App(
                appLabel = "",
                key = null,
                appPackage = "",
                activityClassName = "",
                isNew = false,
                user = android.os.Process.myUserHandle()
            )
        )
        this.appsList = appsList
        this.appFilteredList = displayRows()
        submitList(appFilteredList)
    }

    fun launchFirstInList() {
        val first = appFilteredList.firstOrNull { it.isLaunchable() }
        if (first != null) appClickListener(first)
    }

    /**
     * The toggle row for a collapsed group. Collapsed it reads "+N · App · App · …", the count
     * in the group's color and the names faded like the rows they stand for; expanded it offers
     * "fewer" in the same spot, so the finger that opened the group can close it.
     */
    class GroupToggleViewHolder(private val binding: AdapterGroupToggleBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val dimmedColors: ColorStateList = binding.root.textColors.withAlpha(DIMMED_ALPHA_255)

        fun bind(
            appLabelGravity: Int,
            toggle: AppModel.GroupToggle,
            toggleListener: (AppModel.GroupToggle) -> Unit,
        ) = with(binding.root) {
            gravity = appLabelGravity
            val density = resources.displayMetrics.density
            val basePadding = (24 * density).toInt()
            val markerPadding = (48 * density).toInt()
            setPaddingRelative(
                if (appLabelGravity == android.view.Gravity.START) markerPadding else basePadding,
                paddingTop,
                basePadding,
                paddingBottom,
            )
            setTextColor(dimmedColors)
            val count = toggle.collapsedApps.size
            text = if (toggle.expanded) {
                context.getString(R.string.group_show_fewer)
            } else {
                val names = toggle.collapsedApps.joinToString(" · ") { it.appLabel }
                val summary = context.getString(R.string.group_collapsed_summary, count, names)
                val countLabel = "+$count"
                SpannableString(summary).apply {
                    val start = summary.indexOf(countLabel)
                    if (start >= 0) setSpan(
                        ForegroundColorSpan(toggle.group.color),
                        start,
                        start + countLabel.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
            contentDescription = if (toggle.expanded)
                context.getString(R.string.group_collapse_description, toggle.group.displayName)
            else
                resources.getQuantityString(
                    R.plurals.group_expand_description, count, count, toggle.group.displayName
                )
            setOnClickListener { toggleListener(toggle) }
        }
    }

    class PrivateSpaceHeaderViewHolder(private val binding: AdapterPrivateSpaceHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            appLabelGravity: Int,
            toggleListener: () -> Unit,
            settingsListener: () -> Unit,
        ) = with(binding) {
            privateSpaceTitle.gravity = appLabelGravity
            privateSpaceTitle.setOnClickListener { toggleListener() }
            privateSpaceTitle.setOnLongClickListener {
                settingsListener()
                true
            }
        }
    }

    class ViewHolder(private val binding: AdapterAppDrawerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        /** The style's color state list (keeps pressed feedback) and its half-strength twin. */
        private val titleColors: ColorStateList = binding.appTitle.textColors
        private val dimmedTitleColors: ColorStateList = titleColors.withAlpha(DIMMED_ALPHA_255)

        fun bind(
            flag: Int,
            appLabelGravity: Int,
            myUserHandle: UserHandle,
            appModel: AppModel,
            clickListener: (AppModel) -> Unit,
            appDeleteListener: (AppModel) -> Unit,
            appInfoListener: (AppModel) -> Unit,
            appRenameListener: (AppModel, String) -> Unit,
            appCategoryListener: (AppModel) -> Unit,
            appEmphasisListener: (AppModel) -> Unit,
        ) = with(binding) {
            appMenuLayout.visibility = View.GONE
            renameLayout.visibility = View.GONE
            appTitle.visibility = View.VISIBLE

            // Show indicators in title based on app type and state
            appTitle.text = buildString {
                append(appModel.appLabel)
                if (appModel.isNew) append(" ✦")
            }
            appTitle.gravity = appLabelGravity
            appTitle.typeface = Typefaces.forEmphasis(appModel.emphasized)
            appTitle.setTextColor(if (appModel.dimmed) dimmedTitleColors else titleColors)
            val basePadding = (24 * appTitle.resources.displayMetrics.density).toInt()
            val markerPadding = (48 * appTitle.resources.displayMetrics.density).toInt()
            appTitle.setPaddingRelative(
                if (appLabelGravity == android.view.Gravity.START) markerPadding else basePadding,
                appTitle.paddingTop,
                basePadding,
                appTitle.paddingBottom,
            )
            val showProfileIndicator = appModel.user != myUserHandle
            val showCategoryMarker =
                flag == Constants.FLAG_LAUNCH_APP && appModel.appPackage.isNotEmpty()
            otherProfileIndicator.isVisible = showProfileIndicator
            categoryMarker.isVisible = showCategoryMarker
            categoryMarker.imageAlpha = if (appModel.dimmed) DIMMED_ALPHA_255 else 255
            appModel.category?.let { category ->
                categoryMarker.setImageResource(category.iconRes)
                categoryMarker.contentDescription = category.displayName
                categoryMarker.imageTintList = ColorStateList.valueOf(category.color)
            }
            // The glyph sits on top of the title, so it must keep behaving like the row on tap.
            // Long-press is the quick emphasis toggle.
            if (showCategoryMarker) {
                categoryMarker.setOnClickListener { clickListener(appModel) }
                categoryMarker.setOnLongClickListener {
                    appEmphasisListener(appModel)
                    true
                }
            } else {
                categoryMarker.setOnClickListener(null)
                categoryMarker.setOnLongClickListener(null)
                categoryMarker.isClickable = false
                categoryMarker.isLongClickable = false
            }
            fun closeRenameEditor() {
                renameLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
                categoryMarker.isVisible = showCategoryMarker
                otherProfileIndicator.isVisible = showProfileIndicator
            }

            appTitle.setOnClickListener { clickListener(appModel) }

            appTitle.setOnLongClickListener {
                if (appModel.appPackage.isNotEmpty()) {
                    appDelete.alpha = when (
                        appModel is AppModel.PinnedShortcut || !root.context.isSystemApp(appModel.appPackage, appModel.user)
                    ) {
                        true -> 1.0f
                        false -> 0.5f
                    }
                    appTitle.visibility = View.INVISIBLE
                    categoryMarker.visibility = View.GONE
                    appMenuLayout.visibility = View.VISIBLE
                }
                true
            }

            // Configure rename behavior
            appRename.setOnClickListener {
                if (appModel.appPackage.isNotEmpty()) {
                    etAppRename.hint = getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                    etAppRename.setText(appModel.appLabel)
                    etAppRename.setSelectAllOnFocus(true)
                    renameLayout.visibility = View.VISIBLE
                    appMenuLayout.visibility = View.GONE
                    categoryMarker.visibility = View.GONE
                    otherProfileIndicator.visibility = View.GONE
                    etAppRename.showKeyboard()
                    etAppRename.imeOptions = EditorInfo.IME_ACTION_DONE
                }
            }
            etAppRename.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    etAppRename.hint = getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    etAppRename.hint = ""
                }
            })
            etAppRename.setOnEditorActionListener { _, actionCode, _ ->
                if (actionCode == EditorInfo.IME_ACTION_DONE) {
                    val renameLabel = etAppRename.text.toString().trim()
                    if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                        appRenameListener(appModel, renameLabel)
                        closeRenameEditor()
                    }
                    true
                }
                false
            }
            tvSaveRename.setOnClickListener {
                etAppRename.hideKeyboard()
                val renameLabel = etAppRename.text.toString().trim()
                if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                    appRenameListener(appModel, renameLabel)
                } else {
                    appRenameListener(
                        appModel,
                        getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                    )
                }
                closeRenameEditor()
            }
            appInfo.setOnClickListener { appInfoListener(appModel) }
            appCategory.setOnClickListener { appCategoryListener(appModel) }
            appDelete.setOnClickListener { appDeleteListener(appModel) }
            appMenuClose.setOnClickListener {
                appMenuLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
                categoryMarker.isVisible = showCategoryMarker
            }
            appRenameClose.setOnClickListener {
                closeRenameEditor()
            }
        }

        private fun getAppName(context: Context, appPackage: String, user: UserHandle): String {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            return try {
                val activityList = launcherApps.getActivityList(appPackage, user)
                if (activityList.isNotEmpty()) {
                    activityList.first().label.toString()
                } else {
                    val packageManager = context.packageManager
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(appPackage, 0)
                    ).toString()
                }
            } catch (_: Exception) {
                "" // As a fallback, display an empty string.
            }
        }
    }
}
