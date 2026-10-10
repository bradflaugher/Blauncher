package app.olauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.LauncherApps
import android.content.res.ColorStateList
import android.os.UserHandle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import androidx.core.view.ViewCompat
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.databinding.AdapterAppDrawerBinding
import app.olauncher.databinding.AdapterGroupHeaderBinding
import app.olauncher.databinding.AdapterPrivateSpaceHeaderBinding
import app.olauncher.helper.AppSearch
import app.olauncher.helper.GroupSections
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isPrivateSpaceProfile
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.showKeyboard
import app.olauncher.helper.Typefaces

class AppDrawerAdapter(
    private var flag: Int,
    private val appLabelGravity: Int,
    private val appClickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appDeleteListener: (AppModel) -> Unit,
    private val appRenameListener: (AppModel, String) -> Unit,
    private val appCategoryListener: (AppModel) -> Unit,
    private val privateSpaceToggleListener: () -> Unit = {},
    private val privateSpaceSettingsListener: () -> Unit = {},
    private val appMenuOpenedListener: () -> Unit = {},
) : ListAdapter<AppModel, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        const val VIEW_TYPE_APP = 0
        const val VIEW_TYPE_PRIVATE_HEADER = 1
        const val VIEW_TYPE_GROUP_HEADER = 2

        /** The app count beside a category's name: smaller, and faded to about two-thirds. */
        private const val COUNT_ALPHA_255 = 166
        private const val COUNT_SIZE = 0.6f

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppModel>() {
            override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean = when {
                oldItem is AppModel.App && newItem is AppModel.App ->
                    oldItem.appPackage == newItem.appPackage &&
                        oldItem.user == newItem.user &&
                        oldItem.category == newItem.category

                oldItem is AppModel.PinnedShortcut && newItem is AppModel.PinnedShortcut ->
                    oldItem.shortcutId == newItem.shortcutId &&
                        oldItem.appPackage == newItem.appPackage &&
                        oldItem.user == newItem.user &&
                        oldItem.category == newItem.category

                oldItem is AppModel.PrivateSpaceHeader && newItem is AppModel.PrivateSpaceHeader -> true

                oldItem is AppModel.GroupHeader && newItem is AppModel.GroupHeader ->
                    oldItem.sectionKey == newItem.sectionKey

                else -> false
            }

            override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean =
                oldItem == newItem
        }
    }

    /** The search text the list is narrowed to; blank lists every app in its groups. */
    private var query: String = ""

    /** Whether the list on screen (not the one being diffed in) holds search results. */
    private var showingSearchResults = false

    /**
     * The one category open in the drawer (see [GroupSections.key]), or null with all closed.
     * Opening another closes it, so the list never outgrows the room above the keyboard. The
     * drawer calls [collapseGroups] whenever it is put away, so each visit starts closed.
     */
    private var expandedGroup: String? = null
    private val myUserHandle = android.os.Process.myUserHandle()

    /** Spoken name of each other profile ("Work profile" / "Private space"), looked up once. */
    private val profileLabels = mutableMapOf<UserHandle, String>()

    private fun profileLabel(context: Context, user: UserHandle): String? {
        if (user == myUserHandle) return null
        return profileLabels.getOrPut(user) {
            context.getString(
                if (isPrivateSpaceProfile(context, user)) R.string.private_space else R.string.work_profile
            )
        }
    }

    var appsList: MutableList<AppModel> = mutableListOf()
    var appFilteredList: MutableList<AppModel> = mutableListOf()

    // Types and binds read the list the adapter is showing, which the diff swaps in a moment after
    // [appFilteredList] is replaced: between the two they can be different lengths and kinds.
    override fun getItemViewType(position: Int): Int {
        return when (currentList.getOrNull(position)) {
            is AppModel.PrivateSpaceHeader -> VIEW_TYPE_PRIVATE_HEADER
            is AppModel.GroupHeader -> VIEW_TYPE_GROUP_HEADER
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

            VIEW_TYPE_GROUP_HEADER -> GroupHeaderViewHolder(
                AdapterGroupHeaderBinding.inflate(
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
            if (position == RecyclerView.NO_POSITION) return
            val appModel = currentList.getOrNull(position) ?: return
            when (holder) {
                is PrivateSpaceHeaderViewHolder -> {
                    holder.bind(
                        appLabelGravity,
                        (appModel as? AppModel.PrivateSpaceHeader)?.isLocked ?: true,
                        privateSpaceToggleListener,
                        privateSpaceSettingsListener,
                    )
                }

                is GroupHeaderViewHolder -> {
                    if (appModel is AppModel.GroupHeader) {
                        holder.bind(appLabelGravity, appModel, ::toggleGroup)
                    }
                }

                is ViewHolder -> holder.bind(
                    flag,
                    // Search results name their category with its glyph; under an open
                    // category's header the glyph would only repeat it.
                    showCategoryMarker = showingSearchResults,
                    appLabelGravity,
                    myUserHandle,
                    profileLabel(holder.itemView.context, appModel.user),
                    appModel,
                    appClickListener,
                    appDeleteListener,
                    appInfoListener,
                    appRenameListener,
                    appCategoryListener,
                    appMenuOpenedListener,
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Narrows the list to the apps [text] matches, best first (each app once, even when it is
     * listed under several groups), or lists everything in its groups when [text] is blank.
     * Computed right away: matching a few hundred labels is cheap, and the rows a caller reads
     * back are then always the ones for the text it just set. Nothing opens by itself; an app
     * opens only when tapped.
     */
    fun search(text: CharSequence) {
        query = text.trim().toString()
        refresh()
    }

    /** Closes the open category, if any. */
    fun collapseGroups() {
        if (expandedGroup == null) return
        expandedGroup = null
        refresh()
    }

    private fun refresh() {
        appFilteredList = if (query.isEmpty()) {
            displayRows()
        } else {
            AppSearch.search(
                appsList.filter { it.isLaunchable() },
                query,
                label = { it.appLabel },
                key = { it.searchKey() },
            ).mapTo(mutableListOf()) { it.first }
        }
        val searching = query.isNotEmpty()
        submitList(appFilteredList) {
            if (showingSearchResults == searching) return@submitList
            showingSearchResults = searching
            // A row listed in both modes is the same item to the diff and keeps its old look.
            notifyItemRangeChanged(0, itemCount)
        }
    }

    private fun AppModel.isLaunchable(): Boolean =
        this is AppModel.App || this is AppModel.PinnedShortcut

    /**
     * The rows shown with an empty search: one header per category, the open one's apps under
     * it (see [GroupSections]). Pickers list every app plainly. Search always covers every app.
     */
    private fun displayRows(): MutableList<AppModel> {
        if (flag != Constants.FLAG_LAUNCH_APP) return appsList
        // The blank row that pads the list's end belongs to no category.
        val padding = appsList.filter { it is AppModel.App && it.appPackage.isEmpty() }
        return GroupSections.build(
            appsList - padding.toSet(),
            expandedGroup,
            describe = { row ->
                GroupSections.Input(
                    group = row.category,
                    isNew = row.isNew,
                    startsSection = row is AppModel.PrivateSpaceHeader,
                )
            },
            header = { h -> AppModel.GroupHeader(h.group, h.key, h.appCount, h.hasNewApp, h.expanded) },
        ).plus(padding).toMutableList()
    }

    private fun toggleGroup(header: AppModel.GroupHeader) {
        expandedGroup = if (header.expanded) null else header.sectionKey
        refresh()
    }

    private fun AppModel.searchKey(): String = when (this) {
        is AppModel.App -> "app:$appPackage|$user"
        is AppModel.PinnedShortcut -> "shortcut:$appPackage/$shortcutId|$user"
        is AppModel.PrivateSpaceHeader -> "private-space"
        is AppModel.GroupHeader -> "group:$sectionKey"
    }

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
        refresh()
    }

    /**
     * A category's row: its colored glyph in the gutter the app glyphs use, its name, and a
     * small faded count (with ✦ while it holds a newly installed app). The open category's name
     * turns medium weight; its apps follow it, indented to its name.
     */
    class GroupHeaderViewHolder(private val binding: AdapterGroupHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            appLabelGravity: Int,
            header: AppModel.GroupHeader,
            toggleListener: (AppModel.GroupHeader) -> Unit,
        ) = with(binding.groupTitle) {
            gravity = appLabelGravity
            val name = header.group.displayName
            val count = buildString {
                append("  ").append(header.appCount)
                if (header.hasNewApp) append(" ✦")
            }
            text = SpannableString(name + count).apply {
                val faded = textColors.defaultColor.let { (it and 0x00FFFFFF) or (COUNT_ALPHA_255 shl 24) }
                setSpan(ForegroundColorSpan(faded), name.length, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(COUNT_SIZE), name.length, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            typeface = Typefaces.forWeight(header.expanded)
            // 20dp, like the app rows' glyph, so 24 + 20 + 4dp puts the name where theirs start.
            val glyphSize = (20 * resources.displayMetrics.density).toInt()
            val glyph = AppCompatResources.getDrawable(context, header.group.iconRes)?.mutate()
            glyph?.setBounds(0, 0, glyphSize, glyphSize)
            setCompoundDrawablesRelative(glyph, null, null, null)
            TextViewCompat.setCompoundDrawableTintList(
                this, ColorStateList.valueOf(header.group.colorFor(context))
            )
            contentDescription = listOfNotNull(
                resources.getQuantityString(R.plurals.group_header_description, header.appCount, name, header.appCount),
                context.getString(R.string.group_has_new_app).takeIf { header.hasNewApp },
            ).joinToString(", ")
            ViewCompat.setStateDescription(
                this, context.getString(if (header.expanded) R.string.expanded else R.string.collapsed)
            )
            setOnClickListener { toggleListener(header) }
        }
    }

    class PrivateSpaceHeaderViewHolder(private val binding: AdapterPrivateSpaceHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            appLabelGravity: Int,
            isLocked: Boolean,
            toggleListener: () -> Unit,
            settingsListener: () -> Unit,
        ) = with(binding) {
            privateSpaceTitle.gravity = appLabelGravity
            ViewCompat.setStateDescription(
                privateSpaceTitle,
                privateSpaceTitle.context.getString(if (isLocked) R.string.locked else R.string.unlocked),
            )
            privateSpaceTitle.setOnClickListener { toggleListener() }
            privateSpaceTitle.setOnLongClickListener {
                settingsListener()
                true
            }
        }
    }

    class ViewHolder(private val binding: AdapterAppDrawerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        /** TalkBack actions standing in for the long-press menu; replaced on every bind. */
        private val accessibilityActionIds = mutableListOf<Int>()

        fun bind(
            flag: Int,
            showCategoryMarker: Boolean,
            appLabelGravity: Int,
            myUserHandle: UserHandle,
            profileLabel: String?,
            appModel: AppModel,
            clickListener: (AppModel) -> Unit,
            appDeleteListener: (AppModel) -> Unit,
            appInfoListener: (AppModel) -> Unit,
            appRenameListener: (AppModel, String) -> Unit,
            appCategoryListener: (AppModel) -> Unit,
            appMenuOpenedListener: () -> Unit,
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
            appTitle.typeface = Typefaces.LIGHT
            val basePadding = (24 * appTitle.resources.displayMetrics.density).toInt()
            val markerPadding = (48 * appTitle.resources.displayMetrics.density).toInt()
            appTitle.setPaddingRelative(
                if (appLabelGravity == android.view.Gravity.START) markerPadding else basePadding,
                appTitle.paddingTop,
                basePadding,
                appTitle.paddingBottom,
            )
            val showProfileIndicator = appModel.user != myUserHandle
            val showCategoryMarker = showCategoryMarker &&
                    flag == Constants.FLAG_LAUNCH_APP && appModel.appPackage.isNotEmpty()
            otherProfileIndicator.isVisible = showProfileIndicator
            categoryMarker.isVisible = showCategoryMarker
            appModel.category?.let { category ->
                categoryMarker.setImageResource(category.iconRes)
                categoryMarker.contentDescription = category.displayName
                categoryMarker.imageTintList = ColorStateList.valueOf(category.colorFor(root.context))
            }
            // The glyph and profile dot are hidden from TalkBack; the title speaks for them.
            val context = root.context
            appTitle.contentDescription = listOfNotNull(
                appModel.appLabel,
                appModel.category?.displayName?.takeIf { showCategoryMarker },
                context.getString(R.string.app_new).takeIf { appModel.isNew },
                profileLabel?.takeIf { showProfileIndicator },
            ).joinToString(", ")
            // The empty bottom-padding row has nothing to announce.
            appTitle.importantForAccessibility =
                if (appModel.appPackage.isEmpty()) View.IMPORTANT_FOR_ACCESSIBILITY_NO
                else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            // The glyph sits on top of the title, so it must keep behaving like the row on tap.
            if (showCategoryMarker) {
                categoryMarker.setOnClickListener { clickListener(appModel) }
            } else {
                categoryMarker.setOnClickListener(null)
                categoryMarker.isClickable = false
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
                    val canDelete = appModel is AppModel.PinnedShortcut ||
                        !root.context.isSystemApp(appModel.appPackage, appModel.user)
                    appDelete.alpha = if (canDelete) 1.0f else 0.5f
                    // Stays enabled: tapping it still explains why and opens App info.
                    ViewCompat.setStateDescription(
                        appDelete,
                        if (canDelete) null else root.context.getString(R.string.system_app_cannot_delete),
                    )
                    appTitle.visibility = View.INVISIBLE
                    categoryMarker.visibility = View.GONE
                    appMenuLayout.visibility = View.VISIBLE
                    appMenuOpenedListener()
                    moveAccessibilityFocus(appDelete)
                }
                true
            }

            // Configure rename behavior
            fun openRenameEditor() {
                if (appModel.appPackage.isNotEmpty()) {
                    etAppRename.hint = getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                    etAppRename.setText(appModel.appLabel)
                    etAppRename.setSelectAllOnFocus(true)
                    renameLayout.visibility = View.VISIBLE
                    appMenuLayout.visibility = View.GONE
                    appTitle.visibility = View.INVISIBLE
                    categoryMarker.visibility = View.GONE
                    otherProfileIndicator.visibility = View.GONE
                    etAppRename.showKeyboard()
                    etAppRename.imeOptions = EditorInfo.IME_ACTION_DONE
                }
            }
            // The hint (the app's original name) only shows while the field is empty and is
            // also the field's accessible label, so it stays set while typing.
            appRename.setOnClickListener { openRenameEditor() }
            // A blank name drops the rename, so the row goes back to the app's (or the
            // shortcut's) own label rather than borrowing the parent app's name.
            fun saveRename() {
                etAppRename.hideKeyboard()
                if (appModel.appPackage.isNotBlank())
                    appRenameListener(appModel, etAppRename.text.toString().trim())
                closeRenameEditor()
            }
            etAppRename.setOnEditorActionListener { _, actionCode, _ ->
                if (actionCode == EditorInfo.IME_ACTION_DONE) {
                    saveRename()
                    true
                } else false
            }
            tvSaveRename.setOnClickListener { saveRename() }
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

            // TalkBack users get the long-press menu as actions.
            accessibilityActionIds.forEach { ViewCompat.removeAccessibilityAction(appTitle, it) }
            accessibilityActionIds.clear()
            if (appModel.appPackage.isNotEmpty()) {
                fun addAction(label: Int, action: () -> Unit) {
                    accessibilityActionIds += ViewCompat.addAccessibilityAction(
                        appTitle, context.getString(label)
                    ) { _, _ ->
                        action()
                        true
                    }
                }
                addAction(R.string.delete) { appDeleteListener(appModel) }
                addAction(R.string.rename) { openRenameEditor() }
                addAction(R.string.category) { appCategoryListener(appModel) }
                addAction(R.string.info) { appInfoListener(appModel) }
            }
        }

        /**
         * The long-pressed title is hidden behind the menu, so a screen reader would be left
         * focused on nothing; hand its focus to the menu's first button instead.
         */
        @SuppressLint("AccessibilityFocus")
        private fun moveAccessibilityFocus(view: View) {
            view.post { view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) }
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
