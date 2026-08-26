#!/usr/bin/env bash
set -euo pipefail

APK_PATH="${1:?APK path is required}"
QUALITY="${2:-HD}"
OUTPUT_DIR="${3:-out/emulator-${QUALITY,,}}"
PACKAGE=net.kdt.pojavlaunch.debug

mkdir -p "$OUTPUT_DIR"

capture() {
  local name="$1"
  adb exec-out screencap -p > "$OUTPUT_DIR/${name}.png"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUTPUT_DIR/${name}.xml" >/dev/null 2>&1 || true
}

tap_resource() {
  local resource_name="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml /tmp/window.xml >/dev/null

  local coordinates
  if ! coordinates="$(python3 - "$resource_name" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

needle = sys.argv[1]
root = ET.parse('/tmp/window.xml').getroot()
for node in root.iter('node'):
    resource_id = node.attrib.get('resource-id', '')
    if resource_id.endswith(':id/' + needle):
        values = [int(value) for value in re.findall(r'\d+', node.attrib['bounds'])]
        print((values[0] + values[2]) // 2, (values[1] + values[3]) // 2)
        break
else:
    raise SystemExit('Could not find Android resource ' + needle)
PY
)"; then
    return 1
  fi
  read -r tap_x tap_y <<< "$coordinates"
  echo "Tapping $resource_name at $tap_x,$tap_y"
  adb shell input tap "$tap_x" "$tap_y"
}

dismiss_system_overlays() {
  local attempts=0
  while (( attempts < 6 )); do
    if tap_resource ok; then
      sleep 2
    elif tap_resource aerr_close; then
      sleep 2
    elif tap_resource aerr_wait; then
      sleep 2
    else
      return 0
    fi
    attempts=$((attempts + 1))
  done
}

collect_diagnostics() {
  adb logcat -d -v threadtime > "$OUTPUT_DIR/logcat.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$OUTPUT_DIR/activities.txt" 2>&1 || true
  adb shell dumpsys meminfo "$PACKAGE" > "$OUTPUT_DIR/meminfo.txt" 2>&1 || true
  adb shell run-as "$PACKAGE" find . -maxdepth 4 -type f \
    > "$OUTPUT_DIR/app-files.txt" 2>&1 || true
  adb shell run-as "$PACKAGE" cat latestcrash.txt \
    > "$OUTPUT_DIR/latestcrash.txt" 2>&1 || true
  adb shell run-as "$PACKAGE" cat files/latestlog.txt \
    > "$OUTPUT_DIR/latestlog.txt" 2>&1 || true
}

trap collect_diagnostics EXIT

adb wait-for-device
adb install -r "$APK_PATH"
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
adb shell settings put secure immersive_mode_confirmations confirmed || true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb shell am force-stop com.google.android.apps.nexuslauncher || true
adb logcat -c

adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/net.kdt.pojavlaunch.TestStorageActivity" \
  | tee "$OUTPUT_DIR/activity-start.txt"

sleep 8
dismiss_system_overlays
capture 00-quality-picker-loading

# The embedded runtime is unpacked asynchronously on first launch. Waiting here
# prevents a quality-button tap from being rejected while that task is active.
sleep 55
dismiss_system_overlays
capture 01-quality-picker-ready

if [[ "$QUALITY" == "HD" ]]; then
  tap_resource playHD
else
  tap_resource playSD
fi

sleep 5
capture 02-after-quality-5s
sleep 25
capture 03-after-quality-30s
sleep 60
capture 04-after-quality-90s

collect_diagnostics
trap - EXIT

test -s "$OUTPUT_DIR/01-quality-picker-ready.png"
test -s "$OUTPUT_DIR/04-after-quality-90s.png"
