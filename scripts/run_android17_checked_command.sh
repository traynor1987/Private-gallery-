#!/usr/bin/env bash
# Retry one unchanged instrumentation command only for a confirmed external AVD launcher ANR.
set -euo pipefail
output=${1:?Evidence directory required}
shift
[[ ${1:-} == -- ]]
shift
[[ $# -gt 0 ]]
mkdir -p "$output"
apk=app/build/outputs/apk/debug/app-debug.apk
test_apk=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
known_inputs=false
if [[ -f "$apk" && -f "$test_apk" ]]; then
  sha256sum "$apk" "$test_apk" > "$output/command-inputs.sha256"
  known_inputs=true
fi
if "$@"; then
  if [[ "$known_inputs" == true ]]; then sha256sum --check "$output/command-inputs.sha256"; fi
  exit 0
else first_status=$?; fi
[[ "$known_inputs" == true ]] || exit "$first_status"
sha256sum --check "$output/command-inputs.sha256"
[[ ${GITHUB_ACTIONS:-} == true ]] || exit "$first_status"
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit "$first_status"
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 37 ]] || exit "$first_status"
attempt="$output/infrastructure-attempt-1"
mkdir -p "$attempt"
adb shell dumpsys window > "$attempt/window.txt"
reports=app/build/outputs/androidTest-results/connected/debug
if [[ -d "$reports" ]]; then cp -R "$reports" "$attempt/reports"; fi
pattern='Application Not Responding: com\.google\.android\.apps\.nexuslauncher'
if ! grep -Eq "$pattern" "$attempt/window.txt" &&
   ! grep -REq "$pattern" "$attempt/reports" 2>/dev/null; then
  exit "$first_status"
fi
cp "$output/command-inputs.sha256" "$attempt/apk-sha256.txt"
printf 'first_exit=%s\nreason=external-google-launcher-anr\nmaximum_retries=1\n' "$first_status" > "$attempt/receipt.txt"
# Disposable AVD process recovery only. No package disable, role change, uninstall or data clear.
adb shell am force-stop com.google.android.apps.nexuslauncher
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
for observation in $(seq 1 15); do
  adb shell dumpsys window > "$attempt/window-after-recovery.txt"
  if ! grep -Eq "$pattern" "$attempt/window-after-recovery.txt"; then break; fi
  [[ "$observation" != 15 ]] || exit "$first_status"
  sleep 1
done
sha256sum --check "$attempt/apk-sha256.txt"
if "$@"; then retry_status=0; else retry_status=$?; fi
sha256sum --check "$attempt/apk-sha256.txt"
printf 'retry_exit=%s\n' "$retry_status" >> "$attempt/receipt.txt"
exit "$retry_status"
