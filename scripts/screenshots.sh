#!/bin/bash
# Automated Play Store screenshots — the native port of the Flutter
# fastlane screenshot lane. Runs the instrumented suite in
# app/src/androidTest for every locale × form factor × theme and stores
# full-resolution PNGs under app_store_assets/screenshots/<locale>/android/<phone|tablet>/<dark|light>/.
#
#   LGKA_LOGIN=user:pass scripts/screenshots.sh            # everything
#   LGKA_LOGIN=user:pass scripts/screenshots.sh dark phone  # one theme / form factor
#
# Needs ANDROID_HOME with emulator + a system image (see SYSTEM_IMAGE) and
# JDK 17+. AVDs lgka-shots-phone / lgka-shots-tablet are created on demand.
# One app session per theme × locale: the suite launches the app once, walks
# onboarding + login and captures every screen from there (8 shots per run).
#
# Headless Linux box (e.g. atlas, KVM):
#   ANDROID_HOME=~/android-sdk SYSTEM_IMAGE='system-images;android-37.0;google_apis;x86_64' \
#   LGKA_LOGIN=user:pass scripts/screenshots.sh
set -euo pipefail
cd "$(dirname "$0")/.."

: "${LGKA_LOGIN:?set LGKA_LOGIN=user:pass (the school website read-only login)}"
: "${ANDROID_HOME:=/opt/homebrew/share/android-commandlinetools}"
export ANDROID_HOME
SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
AVDM="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
EMU="$ANDROID_HOME/emulator/emulator"
ADB="$ANDROID_HOME/platform-tools/adb"
SYSTEM_IMAGE="${SYSTEM_IMAGE:-system-images;android-37.0;google_apis;arm64-v8a}"
MODES=(${1:-dark light})
FORMS=(${2:-phone tablet})
LOCALES=(de en)
device_for() { case "$1" in phone) echo pixel_9 ;; tablet) echo pixel_tablet ;; esac; }
# Clean status bar (same as the Flutter lane): 09:41, full battery, full signal, no notifications.
# SystemUI only honours this once it is fully up, and re-inflates on a uimode change, so it is
# (re)applied right before every run.
demo_mode() {
  "$ADB" shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
  "$ADB" shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941 >/dev/null
  "$ADB" shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
  "$ADB" shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true >/dev/null
  "$ADB" shell am broadcast -a com.android.systemui.demo -e command network -e mobile hide >/dev/null
  "$ADB" shell am broadcast -a com.android.systemui.demo -e command network -e airplane hide -e nosim hide >/dev/null
  "$ADB" shell am broadcast -a com.android.systemui.demo -e command status -e vpn hide -e volume hide -e bluetooth hide -e location hide -e alarm hide -e sync hide -e tty hide -e eri hide -e mute hide -e speakerphone hide >/dev/null
  "$ADB" shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null
}
ROOT="$PWD/app_store_assets/screenshots"

[ -d "$ANDROID_HOME/emulator" ] || "$SDKM" --install emulator
[ -d "$ANDROID_HOME/${SYSTEM_IMAGE//;//}" ] || "$SDKM" --install "$SYSTEM_IMAGE"

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -q

for form in "${FORMS[@]}"; do
  avd="lgka-shots-$form"
  if ! "$AVDM" list avd -c | grep -qx "$avd"; then
    echo no | "$AVDM" create avd -n "$avd" -k "$SYSTEM_IMAGE" -d "$(device_for "$form")" >/dev/null
  fi
  # Fixed port + ANDROID_SERIAL so a stray emulator can never make adb ambiguous.
  export ANDROID_SERIAL="emulator-${EMU_PORT:-5580}"
  EMU_FLAGS=(-no-snapshot -no-boot-anim -no-audio -gpu swiftshader_indirect)
  # headless Linux (no X server) → no window
  if [ "$(uname)" = Linux ] && [ -z "${DISPLAY:-}" ]; then EMU_FLAGS+=(-no-window); fi
  "$EMU" -avd "$avd" -port "${EMU_PORT:-5580}" "${EMU_FLAGS[@]}" >/dev/null 2>&1 &
  EMU_PID=$!
  "$ADB" wait-for-device
  until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
  "$ADB" shell settings put global animator_duration_scale 0 >/dev/null
  # The display must never blank mid-run (a blank frame fails the flat-frame guard).
  "$ADB" shell svc power stayon true >/dev/null
  "$ADB" shell settings put system screen_off_timeout 1800000 >/dev/null
  # Never let emulator ANR/crash dialogs (e.g. Pixel Launcher) appear in captures.
  "$ADB" shell settings put global hide_error_dialogs 1 >/dev/null
  "$ADB" shell settings put secure show_ime_with_hard_keyboard 0 >/dev/null 2>&1 || true
  "$ADB" shell settings put global sysui_demo_allowed 1 >/dev/null
  "$ADB" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
  "$ADB" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
  # Warm-up launch: the first Compose frames after a cold boot on the software GPU can take
  # many seconds and would otherwise be captured as an empty window.
  "$ADB" shell am start -W -n com.lgka/.MainActivity >/dev/null 2>&1 || true
  sleep 10
  "$ADB" shell am force-stop com.lgka
  for mode in "${MODES[@]}"; do
    "$ADB" shell cmd uimode night "$([ "$mode" = dark ] && echo yes || echo no)" >/dev/null
    sleep 3 # SystemUI re-inflates on the uimode change
    for locale in "${LOCALES[@]}"; do
      out="$ROOT/$locale/android/$form/$mode"
      rm -rf "$out"; mkdir -p "$out"
      echo "▶ $avd · $mode · $locale"
      demo_mode
      "$ADB" shell rm -rf "/sdcard/Android/data/com.lgka/files/screenshots" >/dev/null 2>&1 || true
      "$ADB" shell am instrument -w -r \
        -e class com.lgka.ScreenshotTest \
        -e login "$LGKA_LOGIN" -e theme "$mode" -e locale "$locale" -e cls "${LGKA_CLASS:-7b}" \
        com.lgka.test/androidx.test.runner.AndroidJUnitRunner | grep -E "^INSTRUMENTATION_STATUS: test=|FAILURES|OK \(|Error" || true
      "$ADB" pull "/sdcard/Android/data/com.lgka/files/screenshots/." "$out" >/dev/null
      ls -1 "$out"
    done
  done
  "$ADB" shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
  "$ADB" emu kill >/dev/null 2>&1 || kill "$EMU_PID" 2>/dev/null || true
  wait "$EMU_PID" 2>/dev/null || true
done
echo "done → $ROOT"
