#!/usr/bin/env bash
# Drive the app on a device/emulator without retyping the adb preamble.
#   tools/dev.sh install            build debug + install
#   tools/dev.sh share URL          open the app as if URL was shared from another app
#   tools/dev.sh shot OUT.png       screenshot, downscaled to 1000px (cheap to read)
#   tools/dev.sh tap X Y | swipe X1 Y1 X2 Y2 | text "abc" | key BACK
#   tools/dev.sh ui                 dump the UI tree (text, content-desc, bounds) compactly
#   tools/dev.sh files              list downloaded videos
# Device: $ANDROID_SERIAL (default: the first emulator).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
SERIAL="${ANDROID_SERIAL:-$("$ADB" devices | awk '/^emulator-/{print $1; exit}')}"
APP=com.carlospinan.guardavideo
adb() { "$ADB" -s "$SERIAL" "$@"; }

case "${1:-}" in
  install)
    (cd "$ROOT" && ./gradlew -q :androidApp:assembleDebug)
    adb install -r "$ROOT/androidApp/build/outputs/apk/debug/androidApp-debug.apk" | tail -1
    adb shell pm grant "$APP" android.permission.POST_NOTIFICATIONS 2>/dev/null || true ;;
  share)
    adb shell am start -a android.intent.action.SEND -t text/plain \
      --es android.intent.extra.TEXT "'$2'" -n "$APP/$APP.app.MainActivity" | tail -1 ;;
  shot)
    adb exec-out screencap -p > "$2.raw.png" && sips -Z 1000 "$2.raw.png" --out "$2" >/dev/null && rm "$2.raw.png" ;;
  tap) adb shell input tap "$2" "$3" ;;
  swipe) adb shell input swipe "$2" "$3" "$4" "$5" "${6:-300}" ;;  # x1 y1 x2 y2 [ms]
  text) adb shell input text "$(printf %q "$2")" ;;
  key) adb shell input keyevent "KEYCODE_$2" ;;
  ui)
    adb shell rm -f /sdcard/ui.xml; adb shell uiautomator dump /sdcard/ui.xml >/dev/null && adb exec-out cat /sdcard/ui.xml |
      grep -o '<node [^>]*>' | sed -E 's/.*text="([^"]*)".*content-desc="([^"]*)".*bounds="([^"]*)".*/\1|\2|\3/' |
      grep -v '^||' ;;
  files) adb shell ls -l "/sdcard/Android/data/$APP/files/Movies" ;;
  *) sed -n '2,10p' "$0"; exit 1 ;;
esac
