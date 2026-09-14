#!/usr/bin/env bash
# Runs the verified Kurswahl photo dataset through the real app path (ML Kit on the phone) with
# KurswahlDatasetDeviceTest. Needs a connected phone (no emulator); installs the debug app and its test APK.
#
#   scripts/kurswahl-dataset-device.sh [dataset folder]   (default ~/Documents/lgka-kurswahl-dataset)
#
# Results: build/kurswahl-dataset-device/dataset-out/results.txt and NN_shots.json (ML Kit boxes per shot).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATASET="${1:-$HOME/Documents/lgka-kurswahl-dataset}"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
FILES=/sdcard/Android/data/com.lgka/files
REMOTE=$FILES/dataset

if [ "$("$ADB" get-state 2>/dev/null || true)" != "device" ]; then
  echo "No phone connected (adb get-state)." >&2
  exit 1
fi
if "$ADB" shell getprop ro.kernel.qemu | grep -q 1; then
  echo "Refusing to run on an emulator." >&2
  exit 1
fi

cd "$ROOT"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

"$ADB" shell rm -rf "$REMOTE" "$FILES/dataset-out"
"$ADB" shell mkdir -p "$REMOTE"
"$ADB" push core/src/test/resources/plan/stufenplan_j11_words.json core/src/test/resources/plan/stufenplan_j12_words.json "$REMOTE/"
for case in "$DATASET"/[0-9][0-9]; do
  name="$(basename "$case")"
  "$ADB" shell mkdir -p "$REMOTE/$name"
  "$ADB" push "$case/truth.json" "$case/shots/1.jpg" "$case/shots/2.jpg" "$case/shots/3.jpg" "$REMOTE/$name/"
done

set +e
"$ADB" shell am instrument -w -r -e class com.lgka.KurswahlDatasetDeviceTest com.lgka.test/androidx.test.runner.AndroidJUnitRunner
status=$?
set -e

mkdir -p build/kurswahl-dataset-device
"$ADB" pull "$FILES/dataset-out" build/kurswahl-dataset-device/
cat build/kurswahl-dataset-device/dataset-out/results.txt
exit $status
