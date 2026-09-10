#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-full}"
APP_ID="net.kdt.pojavlaunch.debug"
APK_PATH="${APK_PATH:-out/2009scape-mobile.apk}"
ARTIFACT_DIR="out/android-e2e-${MODE}"
UI_REMOTE="/sdcard/2009scape-window.xml"
CLIENT_LOG="/sdcard/Android/data/${APP_ID}/files/latestlog.txt"

mkdir -p "${ARTIFACT_DIR}"

dump_ui() {
  adb shell uiautomator dump "${UI_REMOTE}" >/dev/null 2>&1 || true
  adb pull "${UI_REMOTE}" "${ARTIFACT_DIR}/window.xml" >/dev/null 2>&1 || true
}

capture_screen() {
  adb exec-out screencap -p > "${ARTIFACT_DIR}/screen.png" 2>/dev/null || true
}

copy_client_log() {
  adb shell cat "${CLIENT_LOG}" > "${ARTIFACT_DIR}/latestlog.txt" 2>/dev/null || true
}

dismiss_fullscreen_cling() {
  adb shell settings put secure immersive_mode_confirmations confirmed >/dev/null 2>&1 || true

  for _ in $(seq 1 10); do
    dump_ui
    if [[ ! -f "${ARTIFACT_DIR}/window.xml" ]]; then
      sleep 1
      continue
    fi

    local coords
    coords="$(python3 - "${ARTIFACT_DIR}/window.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    raise SystemExit(1)
for node in root.iter('node'):
    if node.attrib.get('resource-id') == 'android:id/ok' or node.attrib.get('text') == 'Got it':
        m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        raise SystemExit(0)
raise SystemExit(1)
PY
)" || true

    if [[ -n "${coords}" ]]; then
      read -r x y <<< "${coords}"
      echo "Dismissing Android fullscreen education at ${x},${y}"
      adb shell input tap "${x}" "${y}"
      sleep 1
      continue
    fi
    return 0
  done
}

collect_diagnostics() {
  set +e
  dump_ui
  capture_screen
  copy_client_log
  adb logcat -d > "${ARTIFACT_DIR}/logcat.txt" 2>&1
  adb shell dumpsys activity activities > "${ARTIFACT_DIR}/activities.txt" 2>&1
  adb shell dumpsys meminfo "${APP_ID}" > "${ARTIFACT_DIR}/meminfo.txt" 2>&1
  adb shell getprop > "${ARTIFACT_DIR}/getprop.txt" 2>&1
  adb shell run-as "${APP_ID}" sh -c 'find files -maxdepth 5 -type f -print 2>/dev/null | sort'     > "${ARTIFACT_DIR}/private-files.txt" 2>&1
  set -e
}
trap collect_diagnostics EXIT

POST_INSTALL_DEADLINE=0

remaining_post_install() {
  local remaining=$((POST_INSTALL_DEADLINE - SECONDS))
  if (( remaining <= 0 )); then
    echo "Post-install E2E budget exhausted" >&2
    return 1
  fi
  echo "$remaining"
}

phase_timeout() {
  local cap="$1"
  local remaining
  remaining="$(remaining_post_install)" || return 1
  if (( remaining < cap )); then
    echo "$remaining"
  else
    echo "$cap"
  fi
}

wait_for_play_enabled() {
  local timeout="${1:-120}"
  local deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    dump_ui
    if [[ -f "${ARTIFACT_DIR}/window.xml" ]]; then
      if grep -q 'Game: setup failed' "${ARTIFACT_DIR}/window.xml" ||          grep -q 'Setup failed:' "${ARTIFACT_DIR}/window.xml"; then
        echo "Launcher reported setup failure"
        cat "${ARTIFACT_DIR}/window.xml"
        return 1
      fi

      if python3 - "${ARTIFACT_DIR}/window.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    rid = node.attrib.get('resource-id', '')
    if rid.endswith(':id/playSinglePlayer') or rid.endswith('/id/playSinglePlayer'):
        if node.attrib.get('enabled') == 'true':
            print('PLAY is enabled')
            raise SystemExit(0)
raise SystemExit(1)
PY
      then
        return 0
      fi
    fi
    sleep 3
  done
  echo "PLAY never became enabled"
  return 1
}

assert_launcher_controls_visible() {
  dump_ui
  python3 - "${ARTIFACT_DIR}/window.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET

screen_w, screen_h = 1768, 884
wanted = {
    'playSinglePlayer',
    'settings',
    'serverFiles',
    'updateFromGitHub',
}
seen = {}

root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    rid = node.attrib.get('resource-id', '')
    short = rid.rsplit('/', 1)[-1].rsplit(':id/', 1)[-1]
    if short not in wanted:
        continue
    m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
    if not m:
        raise SystemExit('Could not parse bounds for ' + short)
    x1, y1, x2, y2 = map(int, m.groups())
    if x2 <= x1 or y2 <= y1:
        raise SystemExit(short + ' has zero-size bounds')
    if x1 < 0 or y1 < 0 or x2 > screen_w or y2 > screen_h:
        raise SystemExit(f'{short} is clipped/off-screen: {(x1,y1,x2,y2)}')
    seen[short] = (x1, y1, x2, y2)

missing = wanted - set(seen)
if missing:
    raise SystemExit('Launcher controls missing from visible UI: ' + ', '.join(sorted(missing)))

print('Launcher controls are all visible:', seen)
PY
}

tap_play() {
  dump_ui
  local coords
  coords="$(python3 - "${ARTIFACT_DIR}/window.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    rid = node.attrib.get('resource-id', '')
    if rid.endswith(':id/playSinglePlayer') or rid.endswith('/id/playSinglePlayer'):
        if node.attrib.get('enabled') != 'true':
            raise SystemExit('PLAY exists but is disabled')
        m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
        if not m:
            raise SystemExit('Could not parse PLAY bounds')
        x1, y1, x2, y2 = map(int, m.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        raise SystemExit(0)
raise SystemExit('PLAY button not found')
PY
)"
  read -r x y <<< "${coords}"
  echo "Tapping PLAY at ${x},${y}"
  adb shell input tap "${x}" "${y}"
}

wait_for_activity() {
  local activity="${1}"
  local timeout="${2:-30}"
  local deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    if adb shell dumpsys activity activities | grep -q "${activity}"; then
      echo "Reached activity: ${activity}"
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for activity: ${activity}"
  return 1
}

wait_for_touch_marker() {
  local marker="$1"
  local timeout="${2:-12}"
  local deadline=$((SECONDS + timeout))

  while (( SECONDS < deadline )); do
    copy_client_log
    adb logcat -d > "${ARTIFACT_DIR}/logcat.txt" 2>&1 || true
    cat "${ARTIFACT_DIR}/latestlog.txt" "${ARTIFACT_DIR}/logcat.txt" \
      > "${ARTIFACT_DIR}/combined-log.txt" 2>/dev/null || true

    if grep -F -q "$marker" "${ARTIFACT_DIR}/combined-log.txt" 2>/dev/null; then
      echo "Touch milestone: $marker"
      return 0
    fi
    sleep 1
  done

  echo "Timed out waiting for touch milestone: $marker"
  return 1
}

smoke_test_touch_controls() {
  local timeout
  timeout="$(phase_timeout 35)"
  local deadline=$((SECONDS + timeout))

  # The canvas is 765x503 letterboxed into a 1768x884 landscape viewport.
  # These screen coordinates map safely into the central 3D scene.
  local x=884
  local y=350

  echo "=== Touch: tap ==="
  adb shell input tap "$x" "$y"
  wait_for_touch_marker 'SINGLEPLAYER_TOUCH: TAP' "$((deadline - SECONDS))"

  echo "=== Touch: long press ==="
  adb shell input swipe "$x" "$y" "$x" "$y" 750
  wait_for_touch_marker 'SINGLEPLAYER_TOUCH: LONG_PRESS' "$((deadline - SECONDS))"

  # Close the resulting context menu before testing camera drag.
  adb shell input keyevent KEYCODE_BACK
  sleep 1

  echo "=== Touch: world drag ==="
  adb shell input swipe "$x" "$y" "$((x + 180))" "$y" 500
  wait_for_touch_marker 'SINGLEPLAYER_TOUCH: DRAG_BEGIN:CAMERA' "$((deadline - SECONDS))"
  wait_for_touch_marker 'SINGLEPLAYER_TOUCH: DRAG_END' "$((deadline - SECONDS))"
}

wait_for_combined_game() {
  local timeout="${1:-180}"
  local deadline=$((SECONDS + timeout))
  local saw_vm=0
  local saw_sqlite=0
  local saw_world=0
  local saw_login=0

  while (( SECONDS < deadline )); do
    copy_client_log
    adb logcat -d > "${ARTIFACT_DIR}/logcat.txt" 2>&1 || true

    local combined
    combined="${ARTIFACT_DIR}/combined-log.txt"
    cat "${ARTIFACT_DIR}/latestlog.txt" "${ARTIFACT_DIR}/logcat.txt" > "${combined}" 2>/dev/null || true

    if grep -q 'SINGLEPLAYER_E2E: COMBINED_JVM_START' "${combined}" 2>/dev/null; then
      if (( saw_vm == 0 )); then echo "Combined Java 17 VM started"; fi
      saw_vm=1
    fi

    if grep -q 'SINGLEPLAYER_E2E: SQLITE_READY' "${combined}" 2>/dev/null; then
      if (( saw_sqlite == 0 )); then echo "Android SQLite JNI probe passed"; fi
      saw_sqlite=1
    fi

    if grep -q 'SINGLEPLAYER_E2E: WORLD_READY' "${combined}" 2>/dev/null; then
      if (( saw_world == 0 )); then echo "Embedded world engine is ready in the same VM"; fi
      saw_world=1
    fi

    if grep -q 'SINGLEPLAYER_E2E: LOGIN_SCREEN' "${combined}" 2>/dev/null; then
      if (( saw_login == 0 )); then echo "RT4 login/character entry screen reached"; fi
      saw_login=1
    fi

    if grep -q 'SINGLEPLAYER_E2E: LOGGED_IN' "${combined}" 2>/dev/null; then
      echo "Local player logged in successfully"
      if (( saw_vm == 0 || saw_sqlite == 0 || saw_world == 0 )); then
        echo "Login succeeded without expected combined-runtime milestones"
        return 1
      fi
      adb shell run-as "${APP_ID}" test -f singleplayer-game-ready.flag || {
        echo "Game-ready marker was not created"
        return 1
      }
      return 0
    fi

    if grep -E -q 'Combined single-player launch failed|FATAL EXCEPTION|UnsatisfiedLinkError|OutOfMemoryError' "${combined}" 2>/dev/null; then
      echo "Combined runtime reported a fatal error"
      tail -n 300 "${combined}" || true
      return 1
    fi

    sleep 5
  done

  echo "Combined game timed out. Milestones: vm=${saw_vm}, sqlite=${saw_sqlite}, world=${saw_world}, login=${saw_login}"
  return 1
}

echo "=== Emulator ABI ==="
adb wait-for-device
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.product.cpu.abilist

# Match the Fold-like short landscape view that previously clipped the bottom controls.
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 1 || true
adb shell wm size 1768x884 || true
adb shell wm density 320 || true

echo "=== Install clean APK ==="
adb install -r -t "${APK_PATH}"
adb shell pm clear "${APP_ID}" || true
adb logcat -c

# Everything after installation gets one hard five-minute budget. A dead
# loading/login state must fail quickly instead of occupying a runner for 15–20 minutes.
POST_INSTALL_DEADLINE=$((SECONDS + 300))

adb shell am start -W -n "${APP_ID}/net.kdt.pojavlaunch.TestStorageActivity"
dismiss_fullscreen_cling

echo "=== Wait for unified runtime preparation ==="
wait_for_play_enabled "$(phase_timeout 120)"
assert_launcher_controls_visible
capture_screen

if [[ "${MODE}" == "bootstrap" ]]; then
  echo "Fresh-install bootstrap passed."
  exit 0
fi

echo "=== Launch combined world + client ==="
tap_play
wait_for_activity 'net.kdt.pojavlaunch.JavaGUILauncherActivity' "$(phase_timeout 30)"

echo "=== Wait for combined Java 17 game milestones ==="
wait_for_combined_game "$(phase_timeout 180)"

echo "=== Verify native mobile controls ==="
smoke_test_touch_controls
capture_screen

echo "Combined single-player Android E2E passed."
