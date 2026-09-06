package app.olauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.UserHandle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Filter
import android.widget.Filterable
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.databinding.AdapterAppDrawerBinding
import app.olauncher.databinding.AdapterPrivateSpaceHeaderBinding
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.showKeyboard
import java.text.Normalizer

class AppDrawerAdapter(
    private var flag: Int,
    private val appLabelGravity: Int,
    private val appClickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appDeleteListener: (AppModel) -> Unit,
    private val appHideListener: (AppModel, Int) -> Unit,
    private val appRenameListener: (AppModel, String) -> Unit,
    private val appCategoryListener: (AppModel) -> Unit,
    private val appEmphasisListener: (AppModel) -> Unit = {},
    private val privateSpaceToggleListener: () -> Unit = {},
    private val privateSpaceSettingsListener: () -> Unit = {},
) : ListAdapter<AppModel, RecyclerView.ViewHolder>(DIFF_CALLBACK), Filterable {

    companion object {
        const val VIEW_TYPE_APP = 0
        const val VIEW_TYPE_PRIVATE_HEADER = 1

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

                else -> false
            }

            override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean =
                oldItem == newItem
        }
    }

    private var autoLaunch = true
    private var isBangSearch = false
    var allowAutoLaunch = true
    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val separatorsRegex = Regex("[-_+,.`'\\s\\p{Z}]")
    private val appFilter = createAppFilter()
    private val myUserHandle = android.os.Process.myUserHandle()

    var appsList: MutableList<AppModel> = mutableListOf()
    var appFilteredList: MutableList<AppModel> = mutableListOf()

    override fun getItemViewType(position: Int): Int {
        return when (appFilteredList.getOrNull(position)) {
            is AppModel.PrivateSpaceHeader -> VIEW_TYPE_PRIVATE_HEADER
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

                is ViewHolder -> holder.bind(
                    flag,
                    appLabelGravity,
                    myUserHandle,
                    appModel,
                    appClickListener,
                    appDeleteListener,
                    appInfoListener,
                    appHideListener,
                    appRenameListener,
                    appCategoryListener,
                    appEmphasisListener,
                    groupHasEmphasis(appModel),
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
                    appsList
                } else {
                    // Dedupe multi-category duplicates so keyboard matching / auto-launch
                    // still treats each app as a single result.
                    dedupeAppsForSearch(
                        appsList.filter { app ->
                            app !is AppModel.PrivateSpaceHeader &&
                                appLabelMatches(app.appLabel, charSearch)
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
                && appFilteredList[0] !is AppModel.PrivateSpaceHeader
            ) appClickListener(appFilteredList[0])
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun dedupeAppsForSearch(apps: List<AppModel>): MutableList<AppModel> {
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<AppModel>()
        for (app in apps) {
            val key = when (app) {
                is AppModel.App -> "app:${app.appPackage}|${app.user}"
                is AppModel.PinnedShortcut -> "shortcut:${app.shortcutId}|${app.user}"
                is AppModel.PrivateSpaceHeader -> "private-space"
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
        this.appFilteredList = appsList
        submitList(appsList)
    }

    private fun groupHasEmphasis(appModel: AppModel): Boolean {
        val category = appModel.category ?: return false
        return appsList.any { it.category == category && it.emphasized }
    }

    fun launchFirstInList() {
        val first = appFilteredList.firstOrNull {
            it !is AppModel.PrivateSpaceHeader
        }
        if (first != null) appClickListener(first)
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
        fun bind(
            flag: Int,
            appLabelGravity: Int,
            myUserHandle: UserHandle,
            appModel: AppModel,
            clickListener: (AppModel) -> Unit,
            appDeleteListener: (AppModel) -> Unit,
            appInfoListener: (AppModel) -> Unit,
            appHideListener: (AppModel, Int) -> Unit,
            appRenameListener: (AppModel, String) -> Unit,
            appCategoryListener: (AppModel) -> Unit,
            appEmphasisListener: (AppModel) -> Unit,
            groupHasEmphasis: Boolean,
        ) = with(binding) {
            appHideLayout.visibility = View.GONE
            renameLayout.visibility = View.GONE
            appTitle.visibility = View.VISIBLE

            // Show indicators in title based on app type and state
            appTitle.text = buildString {
                append(appModel.appLabel)
                if (appModel.isNew) append(" ✦")
            }
            appTitle.gravity = appLabelGravity
            applyEmphasisStyle(appTitle, appModel.emphasized, groupHasEmphasis)
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
            categoryMarker.alpha = 1f
            appModel.category?.let { category ->
                categoryMarker.setImageResource(category.iconRes)
                categoryMarker.contentDescription = category.displayName
                categoryMarker.imageTintList = ColorStateList.valueOf(category.color)
                categoryMarker.alpha = when {
                    appModel.emphasized -> 1f
                    groupHasEmphasis -> 0.4f
                    else -> 1f
                }
            }
            categoryMarker.isLongClickable = flag == Constants.FLAG_LAUNCH_APP && appModel.appPackage.isNotEmpty()
            categoryMarker.setOnLongClickListener {
                if (appModel.appPackage.isNotEmpty() && flag == Constants.FLAG_LAUNCH_APP) {
                    appEmphasisListener(appModel)
                    true
                } else {
                    false
                }
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
                    appHide.text = if (flag == Constants.FLAG_HIDDEN_APPS)
                        root.context.getString(R.string.adapter_show)
                    else
                        root.context.getString(R.string.adapter_hide)
                    appTitle.visibility = View.INVISIBLE
                    categoryMarker.visibility = View.GONE
                    appHide.alpha = when (appModel is AppModel.PinnedShortcut) {
                        true -> 0.5f
                        false -> 1.0f
                    }
                    appHideLayout.visibility = View.VISIBLE
                    // Only allow renaming non hidden apps
                    appRename.isVisible = flag != Constants.FLAG_HIDDEN_APPS
                    appCategory.isVisible = flag != Constants.FLAG_HIDDEN_APPS
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
                    appHideLayout.visibility = View.GONE
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
                appHideLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
                categoryMarker.isVisible = showCategoryMarker
            }
            appRenameClose.setOnClickListener {
                closeRenameEditor()
            }
            appHide.setOnClickListener { appHideListener(appModel, bindingAdapterPosition) }
        }

        private fun applyEmphasisStyle(
            title: android.widget.TextView,
            emphasized: Boolean,
            groupHasEmphasis: Boolean,
        ) {
            val context = title.context
            if (emphasized) {
                title.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                title.setTextColor(colorAttr(context, R.attr.primaryColor))
            } else {
                title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                title.setTextColor(
                    colorAttr(
                        context,
                        if (groupHasEmphasis) R.attr.primaryColorTrans50 else R.attr.primaryColor,
                    )
                )
            }
        }

        @ColorInt
        private fun colorAttr(context: Context, @AttrRes attr: Int): Int {
            val value = TypedValue()
            context.theme.resolveAttribute(attr, value, true)
            return value.data
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
