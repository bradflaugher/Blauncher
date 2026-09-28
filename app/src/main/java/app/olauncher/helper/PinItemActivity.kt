package app.olauncher.helper

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Bundle
import app.olauncher.R

/** Invisible, content-free confirmation step for pin requests; a platform Activity is enough. */
class PinItemActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set window to be transparent
        window.setBackgroundDrawable(null)

        val launcherApps = getSystemService(LauncherApps::class.java)
        val pinItemRequest = launcherApps.getPinItemRequest(intent)

        when (pinItemRequest != null) {
            true -> handleRequestType(pinItemRequest)
            false -> showToast(R.string.pin_invalid_request)
        }

        finish()
    }

    private fun handleRequestType(pinItemRequest: LauncherApps.PinItemRequest) {
        when (pinItemRequest.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT ->
                handleShortcutRequest(pinItemRequest)

            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET ->
                showToast(R.string.pin_widgets_not_supported)

            else -> showToast(R.string.pin_unknown_request)
        }
    }

    private fun handleShortcutRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        val shortcutInfo = pinItemRequest.shortcutInfo
        if (shortcutInfo != null) {
            val success = pinItemRequest.accept()
            val message = when (success) {
                true -> R.string.pin_shortcut_pinned
                false -> R.string.pin_shortcut_failed
            }
            showToast(message)
        } else {
            showToast(R.string.pin_invalid_shortcut)
        }
    }
}