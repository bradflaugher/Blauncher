package app.olauncher.ui

import android.content.res.ColorStateList
import android.os.Process
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppCategory
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogAppGroupsBinding
import app.olauncher.databinding.ItemGroupChoiceBinding
import app.olauncher.helper.deletePinnedShortcut
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.showToast
import app.olauncher.helper.uninstall

/**
 * The app drawer's list, hosted in the home screen's sheet below the search bar: every app in
 * its groups, Private Space at the bottom, and each app's long-press menu (uninstall, rename,
 * group, info). [filter] narrows it to the apps the search text matches, best first.
 *
 * Nothing here opens an app by itself: a tap does, through [onLaunch]. [isActive] says whether
 * the sheet is still up to take a tap, so a double tap acts only once. [onLeave] asks the host
 * to put the sheet away after handing off to system settings.
 */
class AppDrawerList(
    private val fragment: Fragment,
    private val recyclerView: RecyclerView,
    private val viewModel: MainViewModel,
    private val prefs: Prefs,
    private val isActive: () -> Boolean,
    private val onLaunch: (AppModel) -> Unit,
    private val onLeave: () -> Unit,
    private val onAppMenuOpened: () -> Unit,
    private val beforeDialog: () -> Unit,
) {
    private val context get() = fragment.requireContext()
    private var appList: List<AppModel>? = null
    private var privateSpaceApps: List<AppModel>? = null
    private var privateSpaceLocked = true
    private var privateSpaceAvailable = false

    private val adapter = AppDrawerAdapter(
        Constants.FLAG_LAUNCH_APP,
        prefs.appLabelAlignment,
        appClickListener = { appModel -> if (isActive()) onLaunch(appModel) },
        appInfoListener = {
            if (isActive()) {
                openAppInfo(context, it.user, it.appPackage)
                onLeave()
            }
        },
        appDeleteListener = ::uninstall,
        appRenameListener = { appModel, renameLabel ->
            val identifier = when (appModel) {
                // Package-qualified: two apps may pin shortcuts with the same id.
                is AppModel.PinnedShortcut -> appModel.emphasisKey
                is AppModel.App -> appModel.appPackage
                else -> return@AppDrawerAdapter
            }
            prefs.setAppRenameLabel(identifier, renameLabel)
            viewModel.getAppList()
        },
        appCategoryListener = ::showCategoryChooser,
        appEmphasisListener = { appModel ->
            if (appModel.emphasisKey.isBlank()) return@AppDrawerAdapter
            val emphasized = prefs.toggleAppEmphasized(appModel.emphasisKey)
            context.showToast(
                context.getString(
                    if (emphasized) R.string.emphasized_toast else R.string.unemphasized_toast,
                    appModel.appLabel,
                )
            )
            viewModel.getAppList()
        },
        privateSpaceToggleListener = { viewModel.togglePrivateSpaceLock() },
        privateSpaceSettingsListener = {
            if (isActive()) {
                viewModel.openPrivateSpaceSettings()
                onLeave()
            }
        },
        appMenuOpenedListener = onAppMenuOpened,
    )

    /** The rows on screen right now, in order; the trailing blank padding row is left out. */
    val visibleApps: List<AppModel>
        get() = adapter.appFilteredList.filter { it.appLabel.isNotEmpty() || it !is AppModel.App }

    init {
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = null
        val owner = fragment.viewLifecycleOwner
        viewModel.appList.observe(owner) {
            appList = it
            update()
        }
        viewModel.privateSpaceAvailable.observe(owner) {
            privateSpaceAvailable = it
            update()
        }
        viewModel.privateSpaceLocked.observe(owner) {
            privateSpaceLocked = it
            update()
        }
        viewModel.privateSpaceApps.observe(owner) {
            privateSpaceApps = it
            update()
        }
    }

    /** Lists only the apps [text] matches, best first; blank text lists everything in groups. */
    fun filter(text: CharSequence) {
        adapter.search(text)
    }

    /** Back to the top with every group folded again, as each visit to the drawer starts. */
    fun reset() {
        adapter.collapseGroups()
        recyclerView.scrollToPosition(0)
    }

    private fun update() {
        val apps = appList ?: return
        val combined = apps.toMutableList()
        if (privateSpaceAvailable) {
            combined.add(AppModel.PrivateSpaceHeader(isLocked = privateSpaceLocked))
            if (!privateSpaceLocked) privateSpaceApps?.let(combined::addAll)
        }
        // Keeps whatever search text the list was last narrowed to.
        adapter.setAppList(combined)
    }

    private fun uninstall(appModel: AppModel) {
        when (appModel) {
            is AppModel.PrivateSpaceHeader, is AppModel.GroupToggle -> {}
            is AppModel.PinnedShortcut ->
                context.deletePinnedShortcut(
                    packageName = appModel.appPackage,
                    shortcutIdToDelete = appModel.shortcutId,
                    user = appModel.user,
                )

            is AppModel.App -> {
                if (appModel.user != Process.myUserHandle()) {
                    openAppInfo(context, appModel.user, appModel.appPackage)
                } else if (context.isSystemApp(appModel.appPackage, appModel.user)) {
                    context.showToast(context.getString(R.string.system_app_cannot_delete))
                    openAppInfo(context, appModel.user, appModel.appPackage)
                } else {
                    context.uninstall(appModel.appPackage)
                }
            }
        }
        viewModel.getAppList()
    }

    /**
     * The Group sheet for one app: an Emphasize switch on top, then the groups the app is listed
     * under. With no manual choice the current automatic group is pre-ticked so the sheet always
     * shows where the app actually is; saving an unchanged automatic selection stays automatic.
     */
    private fun showCategoryChooser(appModel: AppModel) {
        if (appModel.appPackage.isBlank()) return
        beforeDialog()
        val categories = AppCategory.entries
        val manual = prefs.getAppCategoryOverrides(appModel.appPackage)
        val automatic = currentGroupsOf(appModel)
        val checked = (manual ?: automatic).toMutableSet()
        val builder = AlertDialog.Builder(context)
        // Inflate against the dialog's own theme so the sheet matches the dialog's colors.
        val inflater = LayoutInflater.from(builder.context)
        val sheet = DialogAppGroupsBinding.inflate(inflater)

        sheet.emphasizeSwitch.isChecked = prefs.isAppEmphasized(appModel.emphasisKey)
        sheet.emphasizeRow.setOnClickListener { sheet.emphasizeSwitch.toggle() }
        sheet.groupsSummary.setText(
            if (manual == null) R.string.groups_automatic_summary else R.string.groups_manual_summary
        )
        categories.forEach { category ->
            val row = ItemGroupChoiceBinding.inflate(inflater, sheet.groupList, true).root
            row.text = category.displayName
            row.isChecked = category in checked
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(category.iconRes, 0, 0, 0)
            TextViewCompat.setCompoundDrawableTintList(row, ColorStateList.valueOf(category.colorFor(builder.context)))
            row.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) checked.add(category) else checked.remove(category)
            }
        }

        fun saveEmphasis() {
            if (appModel.emphasisKey.isNotBlank()) {
                prefs.setAppEmphasized(appModel.emphasisKey, sheet.emphasizeSwitch.isChecked)
            }
        }

        builder
            .setTitle(appModel.appLabel)
            .setView(sheet.root)
            .setPositiveButton(R.string.save_groups) { dialog, _ ->
                val keepAutomatic = manual == null && checked == automatic.toSet()
                if (checked.isEmpty() || keepAutomatic) prefs.clearAppCategoryOverride(appModel.appPackage)
                else prefs.setAppCategoryOverrides(appModel.appPackage, checked)
                saveEmphasis()
                dialog.dismiss()
                viewModel.getAppList()
            }
            .setNeutralButton(R.string.automatic) { dialog, _ ->
                prefs.clearAppCategoryOverride(appModel.appPackage)
                saveEmphasis()
                dialog.dismiss()
                viewModel.getAppList()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /** Every group this app (or pinned shortcut) is currently listed under in the drawer. */
    private fun currentGroupsOf(appModel: AppModel): List<AppCategory> {
        val groups = adapter.appsList
            .filter { it.emphasisKey == appModel.emphasisKey }
            .mapNotNull { it.category }
            .distinct()
        return groups.ifEmpty { listOfNotNull(appModel.category) }
    }
}
