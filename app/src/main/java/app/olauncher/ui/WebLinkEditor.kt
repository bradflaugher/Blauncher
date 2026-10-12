package app.olauncher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.TextViewCompat
import app.olauncher.R
import app.olauncher.data.AppCategory
import app.olauncher.data.Prefs
import app.olauncher.data.WebLink
import app.olauncher.databinding.DialogWebLinkBinding
import app.olauncher.databinding.ItemGroupChoiceBinding
import app.olauncher.helper.showToast

/**
 * The sheet that adds a web link, or edits [existing] one: its address, an optional name (the
 * site's address shows until one is given) and the groups it is listed under. Save keeps the
 * sheet open until the address is a web page; an existing link also gets Remove.
 * [onChanged] runs after anything was saved or removed, so the caller can reload the drawer.
 */
object WebLinkEditor {

    fun show(context: Context, prefs: Prefs, existing: WebLink? = null, onChanged: () -> Unit) {
        val builder = AlertDialog.Builder(context)
        // Inflate against the dialog's own theme so the sheet matches the dialog's colors.
        val inflater = LayoutInflater.from(builder.context)
        val sheet = DialogWebLinkBinding.inflate(inflater)
        val checked = existing?.categories.orEmpty().toMutableSet()

        existing?.let {
            sheet.webLinkUrl.setText(it.url)
            sheet.webLinkName.setText(it.label)
        }
        AppCategory.entries.forEach { category ->
            val row = ItemGroupChoiceBinding.inflate(inflater, sheet.webLinkGroups, true).root
            row.text = category.displayName
            row.isChecked = category in checked
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(category.iconRes, 0, 0, 0)
            TextViewCompat.setCompoundDrawableTintList(row, ColorStateList.valueOf(category.colorFor(builder.context)))
            row.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) checked.add(category) else checked.remove(category)
            }
        }

        builder
            .setTitle(if (existing == null) R.string.add_web_link else R.string.edit_web_link)
            .setView(sheet.root)
            // Replaced below, so an address that is no web page keeps the sheet open.
            .setPositiveButton(R.string.save_groups, null)
            .setNegativeButton(R.string.close, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.remove) { _, _ ->
                prefs.removeWebLink(existing.id)
                context.showToast(context.getString(R.string.web_link_removed, existing.displayLabel))
                onChanged()
            }
        }
        val dialog = builder.show()
        if (existing == null) sheet.webLinkUrl.requestFocus()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val url = WebLink.normalizeUrl(sheet.webLinkUrl.text.toString())
            if (url == null) {
                sheet.webLinkUrl.error = context.getString(R.string.web_link_invalid)
                sheet.webLinkUrl.requestFocus()
                return@setOnClickListener
            }
            val label = sheet.webLinkName.text.toString().trim()
            // In the order the groups are listed, not the order they were ticked.
            val categories = AppCategory.entries.filter { it in checked }
            val link = existing?.copy(label = label, url = url, categories = categories)
                ?: WebLink(label = label, url = url, categories = categories)
            prefs.saveWebLink(link)
            if (existing == null) context.showToast(context.getString(R.string.web_link_added, link.displayLabel))
            dialog.dismiss()
            onChanged()
        }
    }
}
