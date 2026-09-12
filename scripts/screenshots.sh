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
ROOT="$PWD/app_store_assets/screenshots"

[ -d "$ANDROID_HOME/emulator" ] || "$SDKM" --install emulator
[ -d "$ANDROID_HOME/${SYSTEM_IMAGE//;//}" ] || "$SDKM" --install "$SYSTEM_IMAGE"

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -q

for form in "${FORMS[@]}"; do
  avd="lgka-shots-$form"
  if ! "$AVDM" list avd -c | grep -qx "$avd"; then
    echo no | "$AVDM" create avd -n "$avd" -k "$SYSTEM_IMAGE" -d "$(device_for "$form")" >/dev/null
  fi
  "$EMU" -avd "$avd" -no-snapshot -no-boot-anim -no-audio -gpu swiftshader_indirect >/dev/null 2>&1 &
  EMU_PID=$!
  "$ADB" wait-for-device
  until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
  "$ADB" shell settings put global animator_duration_scale 0 >/dev/null
  "$ADB" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
  "$ADB" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
  for mode in "${MODES[@]}"; do
    "$ADB" shell cmd uimode night "$([ "$mode" = dark ] && echo yes || echo no)" >/dev/null
    for locale in "${LOCALES[@]}"; do
      out="$ROOT/$locale/android/$form/$mode"
      rm -rf "$out"; mkdir -p "$out"
      echo "▶ $avd · $mode · $locale"
      "$ADB" shell rm -rf "/sdcard/Android/data/com.lgka/files/screenshots" >/dev/null 2>&1 || true
      "$ADB" shell am instrument -w -r \
        -e class com.lgka.ScreenshotTest \
        -e login "$LGKA_LOGIN" -e theme "$mode" -e locale "$locale" -e cls "${LGKA_CLASS:-7b}" \
        com.lgka.test/androidx.test.runner.AndroidJUnitRunner | grep -E "^INSTRUMENTATION_STATUS: test=|FAILURES|OK \(|Error" || true
      "$ADB" pull "/sdcard/Android/data/com.lgka/files/screenshots/." "$out" >/dev/null
      ls -1 "$out"
    done
  done
  "$ADB" emu kill >/dev/null 2>&1 || kill "$EMU_PID" 2>/dev/null || true
  wait "$EMU_PID" 2>/dev/null || true
done
echo "done → $ROOT"
