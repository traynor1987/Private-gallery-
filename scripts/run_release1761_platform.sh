#!/usr/bin/env bash
set -euo pipefail
platform=${1:?api37 required}
[[ "${GITHUB_ACTIONS:-}" == true ]]
case "$platform" in api37) expected_sdk=37 ;; *) exit 2 ;; esac
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == "$expected_sdk" ]]
evidence="app/build/platform-regression/$platform"
rm -rf "$evidence"
mkdir -p "$evidence"
bash scripts/verify_android17_device.sh "$evidence"
git rev-parse HEAD > "$evidence/source-sha.txt"
adb shell getprop ro.build.version.sdk > "$evidence/sdk.txt"
adb shell getprop ro.build.fingerprint > "$evidence/build-fingerprint.txt"
archive() {
  local primary_status=$?
  trap - EXIT
  set +e
  printf '%s\n' "$primary_status" > "$evidence/command-exit-status.txt"
  local archive_status=0
  python3 scripts/ci_emulator_storage.py "$evidence/storage-after.json" --avd "full1761-$expected_sdk" || archive_status=1
  cp -a app/build/acceptance-diagnostics "$evidence/diagnostics" || archive_status=1
  cp -a app/build/outputs/androidTest-results/connected/debug "$evidence/full-results" || archive_status=1
  adb shell wm size > "$evidence/display-size.txt" 2>&1
  adb shell wm density > "$evidence/display-density.txt" 2>&1
  if [[ "$primary_status" != 0 ]]; then exit "$primary_status"; fi
  exit "$archive_status"
}
trap archive EXIT
# Prior-platform evidence is already archived; stale raw reports cannot satisfy this SDK.
rm -rf app/build/outputs/androidTest-results/connected/debug
rm -rf app/build/acceptance-diagnostics/professional-ui app/build/acceptance-diagnostics/hidden-browser-preflight
rm -f app/build/acceptance-diagnostics/full-regression-*.json app/build/acceptance-diagnostics/full-regression-source-sha.txt app/build/acceptance-diagnostics/upgrade-*
python3 scripts/ci_emulator_storage.py "$evidence/storage-before-broad.json" --avd "full1761-$expected_sdk" --require-budget
bash scripts/run_professional_ui_tests.sh
rm -rf app/build/outputs/androidTest-results/connected/debug
python3 scripts/ci_emulator_storage.py "$evidence/storage-before-security.json" --avd "full1761-$expected_sdk" --require-budget
python3 scripts/ci_onion_probe.py --verify preflight
bash scripts/run_hidden_browser_preflight.sh
rm -rf app/build/outputs/androidTest-results/connected/debug
python3 scripts/ci_emulator_storage.py "$evidence/storage-before-full.json" --avd "full1761-$expected_sdk" --require-budget
python3 scripts/ci_onion_probe.py --verify full
bash scripts/run_android_instrumentation_with_diagnostics.sh
python3 scripts/ci_emulator_storage.py "$evidence/storage-before-upgrade.json" --avd "full1761-$expected_sdk" --require-budget
bash scripts/complete_android_regression.sh
