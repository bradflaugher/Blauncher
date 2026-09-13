package app.olauncher.data

object Constants {

    object Key {
        const val FLAG = "flag"
    }

    object UserState {
        const val START = "START"
    }

    object SwipeDownAction {
        const val SEARCH = 1
        const val NOTIFICATIONS = 2
    }

    object CharacterIndicator {
        const val SHOW = 102
        const val HIDE = 101
    }

    const val FLAG_LAUNCH_APP = 100
    const val FLAG_HIDDEN_APPS = 101

    const val FLAG_SET_SWIPE_LEFT_APP = 11
    const val FLAG_SET_SWIPE_RIGHT_APP = 12
    const val FLAG_SET_CALENDAR_APP = 13
    const val FLAG_SET_PASSWORD_APP = 14

    /** Flags whose drawer visit picks an app for a slot rather than launching one. */
    val APP_PICKER_FLAGS = FLAG_SET_SWIPE_LEFT_APP..FLAG_SET_PASSWORD_APP

    /**
     * Password managers the home-screen shortcut binds to on its own when none has been
     * chosen yet, tried in this order. The user can always pick a different app.
     */
    val KNOWN_PASSWORD_MANAGERS = listOf(
        "com.x8bit.bitwarden",
        "com.onepassword.android",
        "com.agilebits.onepassword",
        "proton.android.pass",
        "com.kunzisoft.keepass.free",
        "com.kunzisoft.keepass.libre",
        "keepass2android.keepass2android",
        "io.enpass.app",
        "com.callpod.android_apps.keeper",
        "com.lastpass.lpandroid",
        "com.dashlane",
        "com.nordpass.android.app.password.manager",
    )

    const val LONG_PRESS_DELAY_MS = 500L
    const val ONE_HOUR_IN_MILLIS = 3600000L

    const val MIN_ANIM_REFRESH_RATE = 30f

    const val URL_OLAUNCHER_GITHUB = "https://github.com/bradflaugher/Blauncher"

}
