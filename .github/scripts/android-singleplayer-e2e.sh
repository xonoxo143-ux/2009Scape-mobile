#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-bootstrap}"
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

dismiss_fullscreen_cling() {
  # Fresh emulators show Android's own immersive-mode education card over the
  # launcher. UIAutomator then sees only that system window, not our PLAY button.
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

copy_client_log() {
  adb shell cat "${CLIENT_LOG}" > "${ARTIFACT_DIR}/latestlog.txt" 2>/dev/null || true
}

copy_server_error() {
  adb shell run-as "${APP_ID}" cat files/singleplayer-server/.server-startup-error.txt     > "${ARTIFACT_DIR}/server-startup-error.txt" 2>/dev/null || true
}

collect_diagnostics() {
  set +e
  dump_ui
  capture_screen
  copy_client_log
  copy_server_error
  adb logcat -d > "${ARTIFACT_DIR}/logcat.txt" 2>&1
  adb shell dumpsys activity activities > "${ARTIFACT_DIR}/activities.txt" 2>&1
  adb shell dumpsys meminfo "${APP_ID}" > "${ARTIFACT_DIR}/meminfo.txt" 2>&1
  adb shell getprop > "${ARTIFACT_DIR}/getprop.txt" 2>&1
  adb shell run-as "${APP_ID}" sh -c 'find files -maxdepth 4 -type f -print 2>/dev/null | sort'     > "${ARTIFACT_DIR}/private-files.txt" 2>&1
  set -e
}
trap collect_diagnostics EXIT

wait_for_play_enabled() {
  local timeout="${1:-900}"
  local deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    dump_ui
    if [[ -f "${ARTIFACT_DIR}/window.xml" ]]; then
      if grep -q 'Setup: failed' "${ARTIFACT_DIR}/window.xml" ||          grep -q 'Setup failed:' "${ARTIFACT_DIR}/window.xml"; then
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
  local timeout="${2:-900}"
  local deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    if adb shell dumpsys activity activities | grep -q "${activity}"; then
      echo "Reached activity: ${activity}"
      return 0
    fi
    dump_ui
    if [[ -f "${ARTIFACT_DIR}/window.xml" ]] && grep -q 'Server: failed to start' "${ARTIFACT_DIR}/window.xml"; then
      echo "Server failed before client activity launched"
      return 1
    fi
    sleep 3
  done
  echo "Timed out waiting for activity: ${activity}"
  return 1
}

wait_for_client_login() {
  local timeout="${1:-900}"
  local deadline=$((SECONDS + timeout))
  local saw_login_screen=0
  while (( SECONDS < deadline )); do
    copy_client_log
    adb logcat -d > "${ARTIFACT_DIR}/logcat.txt" 2>&1 || true

    if grep -q 'SINGLEPLAYER_E2E: LOGIN_SCREEN' "${ARTIFACT_DIR}/latestlog.txt" 2>/dev/null ||        grep -q 'SINGLEPLAYER_E2E: LOGIN_SCREEN' "${ARTIFACT_DIR}/logcat.txt" 2>/dev/null; then
      if (( saw_login_screen == 0 )); then
        echo "RT4 login/character entry screen reached"
      fi
      saw_login_screen=1
    fi

    if grep -q 'SINGLEPLAYER_E2E: LOGGED_IN' "${ARTIFACT_DIR}/latestlog.txt" 2>/dev/null ||        grep -q 'SINGLEPLAYER_E2E: LOGGED_IN' "${ARTIFACT_DIR}/logcat.txt" 2>/dev/null; then
      echo "Local single-player client logged in successfully"
      return 0
    fi

    copy_server_error
    if [[ -s "${ARTIFACT_DIR}/server-startup-error.txt" ]]; then
      echo "Embedded server recorded a startup error:"
      cat "${ARTIFACT_DIR}/server-startup-error.txt"
      return 1
    fi
    sleep 5
  done

  if (( saw_login_screen == 1 )); then
    echo "Client reached the login screen but never completed local login"
  else
    echo "Client never reached the login/character entry screen"
  fi
  return 1
}

echo "=== Emulator ABI ==="
adb wait-for-device
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.product.cpu.abilist

# Reproduce the short landscape layout used on the phone rather than testing only
# a spacious portrait emulator.
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 1 || true
adb shell wm size 1768x884 || true
adb shell wm density 320 || true

echo "=== Install fresh APK ==="
adb install -r -t "${APK_PATH}"
adb shell pm clear "${APP_ID}" || true
adb logcat -c

adb shell am start -W -n "${APP_ID}/net.kdt.pojavlaunch.TestStorageActivity"
dismiss_fullscreen_cling

echo "=== Wait for first-run preparation ==="
wait_for_play_enabled 900
capture_screen

if [[ "${MODE}" == "bootstrap" ]]; then
  echo "Fresh-install bootstrap passed: PLAY became enabled."
  exit 0
fi

echo "=== Start local world ==="
tap_play
wait_for_activity 'net.kdt.pojavlaunch.JavaGUILauncherActivity' 900

echo "=== Wait for RT4 local login checkpoint ==="
wait_for_client_login 1200
capture_screen

echo "Full ARM64 single-player E2E passed."
