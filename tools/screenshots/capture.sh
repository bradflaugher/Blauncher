#!/usr/bin/env bash
# Saves what the emulator shows right now as an RGB PNG (Play rejects alpha).
#
#   tools/screenshots/capture.sh <serial> tools/screenshots/raw/phone/1_home.png
set -euo pipefail
serial=${1:?serial}
out=${2:?output png}
mkdir -p "$(dirname "$out")"
adb -s "$serial" exec-out screencap -p > "$out"
magick "$out" -alpha off "PNG24:$out"
