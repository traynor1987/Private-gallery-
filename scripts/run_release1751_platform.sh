#!/usr/bin/env bash
set -euo pipefail
platform=${1:?api36 or api37 required}
[[ "${GITHUB_ACTIONS:-}" == true ]]
case "$platform" in api36) expected_sdk=36 ;; api37) expected_sdk=37 ;; *) exit 2 ;; esac
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == "$expected_sdk" ]]
evidence="app/build/platform-regression/$platform"
rm -rf "$evidence"
mkdir -p "$evidence"
git rev-parse HEAD > "$evidence/source-sha.txt"
adb shell getprop ro.build.version.sdk > "$evidence/sdk.txt"
adb shell getprop ro.build.fingerprint > "$evidence/build-fingerprint.txt"
archive() {
  local primary_status=$?
  trap - EXIT
  set +e
  printf '%s\n' "$primary_status" > "$evidence/command-exit-status.txt"
  local archive_status=0
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
bash scripts/run_professional_ui_tests.sh
rm -rf app/build/outputs/androidTest-results/connected/debug
bash scripts/run_hidden_browser_preflight.sh
rm -rf app/build/outputs/androidTest-results/connected/debug
bash scripts/run_android_instrumentation_with_diagnostics.sh
bash scripts/complete_android_regression.sh
