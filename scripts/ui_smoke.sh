#!/usr/bin/env bash
set -euo pipefail

mkdir -p ui-artifacts
APK="app/build/outputs/apk/debug/app-debug.apk"

adb wait-for-device
adb shell wm size 390x844
adb shell wm density 160
adb install -r "$APK"
adb shell am force-stop com.refit.app
adb shell am start -n com.refit.app/.MainActivity >/dev/null
sleep 3

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml /tmp/refit-window.xml >/dev/null
}

assert_text() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import sys
import xml.etree.ElementTree as ET

label = sys.argv[1]
root = ET.parse("/tmp/refit-window.xml").getroot()
values = []
for node in root.iter("node"):
    text = node.attrib.get("text", "")
    desc = node.attrib.get("content-desc", "")
    values.extend([text, desc])
if not any(label in value for value in values):
    raise SystemExit(f"Expected UI text not found: {label}")
PY
}

tap_text() {
  local label="$1"
  dump_ui
  local coords
  coords="$(python3 - "$label" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

label = sys.argv[1]
root = ET.parse("/tmp/refit-window.xml").getroot()
for node in root.iter("node"):
    text = node.attrib.get("text", "")
    desc = node.attrib.get("content-desc", "")
    if label in text or label in desc:
        bounds = node.attrib.get("bounds", "")
        nums = list(map(int, re.findall(r"\d+", bounds)))
        if len(nums) == 4:
            x = (nums[0] + nums[2]) // 2
            y = (nums[1] + nums[3]) // 2
            print(f"{x} {y}")
            raise SystemExit(0)
raise SystemExit(f"Tap target not found: {label}")
PY
)"
  adb shell input tap $coords
  sleep 2
}

shot() {
  local name="$1"
  adb exec-out screencap -p > "ui-artifacts/${name}.png"
}

assert_text "오늘의 재활"
shot "01-home"

tap_text "재활 시작"
assert_text "운동 방법"
shot "02-exercise-intro"

tap_text "운동 시작"
assert_text "실시간 EMG"
shot "03-live-rehab"

sleep 2
tap_text "운동 종료"
assert_text "재활 결과"
shot "04-result"

tap_text "확인"
assert_text "오늘의 재활"
tap_text "재활 기록 보기"
assert_text "최근 RMS 추이"
shot "05-history"

adb shell wm size reset || true
adb shell wm density reset || true
