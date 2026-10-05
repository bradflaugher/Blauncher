#!/usr/bin/env bash
# Gets an emulator ready for Play screenshots of the debug build.
#
#   tools/screenshots/prepare-device.sh <serial> phone|seven|ten [landscape]
#
# Sizes the screen to 9:16 (or 16:9), installs the debug APK, makes Blauncher the home app,
# blacks out the wallpaper, tidies the status bar with System UI demo mode, and brings the
# first-run tips back so the tour can be captured from step one. Capture each screen with
# tools/screenshots/capture.sh, then caption them all with caption.py.
set -euo pipefail

serial=${1:?serial, e.g. emulator-5554}
device=${2:?phone, seven or ten}
orientation=${3:-portrait}
adb=(adb -s "$serial")
pkg=com.bradflaugher.blauncher.debug
root=$(cd "$(dirname "$0")/../.." && pwd)

case "$device" in
  phone) size=1080x1920 density=420 ;;
  seven) size=1080x1920 density=280 ;;
  ten) size=1620x2880 density=320 ;;
  *) echo "unknown device: $device" >&2; exit 1 ;;
esac
if [ "$orientation" = landscape ]; then
  # The forced size alone sets the orientation; keep the sensor from turning it back.
  size=$(echo "$size" | awk -Fx '{print $2 "x" $1}')
  "${adb[@]}" shell settings put system accelerometer_rotation 0
  "${adb[@]}" shell settings put system user_rotation 0
fi

"${adb[@]}" shell wm size "$size"
"${adb[@]}" shell wm density "$density"
"${adb[@]}" install -r "$root/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
"${adb[@]}" shell cmd role add-role-holder android.app.role.HOME "$pkg"
"${adb[@]}" shell cmd wallpaper set-dim-amount 1
# A dark keyboard and system dialogs, to match the black home screen.
"${adb[@]}" shell cmd uimode night yes >/dev/null

# A fresh emulator boots with every app installed "just now", so the drawer would mark them all
# new (✦). Move the clock a few hours on (needs a google_apis image, where adb root works).
"${adb[@]}" root >/dev/null && "${adb[@]}" wait-for-device
"${adb[@]}" shell settings put global auto_time 0
"${adb[@]}" shell 'date @$(( $(date +%s) + 3 * 3600 ))' >/dev/null

# Demo mode: 9:00, full battery and signal, no notification icons.
"${adb[@]}" shell settings put global sysui_demo_allowed 1
demo() { "${adb[@]}" shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 0900
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4
demo network -e mobile hide
demo notifications -e visible false

# A fresh tour: the debug build's reset_tips extra (see MainActivity) brings the tips back.
# On a first launch Blauncher also opens the default-apps screen; Home returns to it.
"${adb[@]}" shell am start -n "$pkg/app.olauncher.MainActivity" --ez reset_tips true >/dev/null
sleep 3
# Leaving that screen without a pick can leave Home unresolved, so claim the role once more.
"${adb[@]}" shell cmd role add-role-holder android.app.role.HOME "$pkg"
"${adb[@]}" shell input keyevent KEYCODE_HOME
