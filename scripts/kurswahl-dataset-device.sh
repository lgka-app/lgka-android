#!/usr/bin/env bash
# Runs the verified Kurswahl photo dataset through the real app path (ML Kit on the phone) with
# KurswahlDatasetDeviceTest. Needs the Pixel 7 connected (addressed by adb transport id, never an emulator); installs the
# debug app and its test APK (install -r only), removes the pushed dataset afterwards and opens the app once.
#
#   scripts/kurswahl-dataset-device.sh [dataset folder]   (default ~/Documents/lgka-kurswahl-dataset)
#
# Results: build/kurswahl-dataset-device/dataset-out/results.txt and NN_shots.json (ML Kit boxes per shot).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATASET="${1:-$HOME/Documents/lgka-kurswahl-dataset}"
ADB_BIN="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
FILES=/sdcard/Android/data/com.lgka/files
REMOTE=$FILES/dataset

# the Pixel 7 by its transport id (never the serial); ADB_TRANSPORT overrides
TRANSPORT="${ADB_TRANSPORT:-$("$ADB_BIN" devices -l | awk '/model:Pixel_7/ { for (i = 1; i <= NF; i++) if ($i ~ /^transport_id:/) { sub("transport_id:", "", $i); print $i } }' | head -1)}"
if [ -z "$TRANSPORT" ]; then
  echo "Pixel 7 not connected (adb devices -l)." >&2
  exit 1
fi
adb() { "$ADB_BIN" -t "$TRANSPORT" "$@"; }
if adb shell getprop ro.kernel.qemu | grep -q 1; then
  echo "Refusing to run on an emulator." >&2
  exit 1
fi

cd "$ROOT"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
# install only: a failed install (e.g. a signature mismatch) stops here; never uninstall or clear app data
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb shell rm -rf "$REMOTE" "$FILES/dataset-out"
adb shell mkdir -p "$REMOTE"
adb push core/src/test/resources/plan/stufenplan_j11_words.json core/src/test/resources/plan/stufenplan_j12_words.json "$REMOTE/"
for case in "$DATASET"/[0-9][0-9]; do
  name="$(basename "$case")"
  adb shell mkdir -p "$REMOTE/$name"
  adb push "$case/truth.json" "$case/shots/1.jpg" "$case/shots/2.jpg" "$case/shots/3.jpg" "$REMOTE/$name/"
done

set +e
adb shell am instrument -w -r -e class com.lgka.KurswahlDatasetDeviceTest com.lgka.test/androidx.test.runner.AndroidJUnitRunner
status=$?
set -e

mkdir -p build/kurswahl-dataset-device
adb pull "$FILES/dataset-out" build/kurswahl-dataset-device/
# the dataset leaves the phone again; the debug app stays installed and is opened once
adb shell rm -rf "$REMOTE" "$FILES/dataset-out"
adb shell am start -n com.lgka/.MainActivity >/dev/null
cat build/kurswahl-dataset-device/dataset-out/results.txt
exit $status
