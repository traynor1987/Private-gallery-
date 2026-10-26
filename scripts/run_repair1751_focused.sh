#!/usr/bin/env bash
set -euo pipefail
platform=${1:?platform label required}
classes=${2:?focused classes required}
[[ "$platform" == api36 || "$platform" == api37 || "$platform" == api36-affected || "$platform" == api37-affected || "$platform" == storage-api37-cases ]]
evidence="app/build/refinement1751/$platform"
mkdir -p "$evidence"
rm -rf "$evidence/results" app/build/outputs/androidTest-results/connected/debug
git rev-parse HEAD > "$evidence/source-sha.txt"
git rev-parse HEAD:app/src/main > "$evidence/main-tree.txt"
adb shell getprop ro.build.fingerprint > "$evidence/build-fingerprint.txt"
adb shell getprop ro.build.version.sdk > "$evidence/sdk.txt"
adb shell wm size > "$evidence/display-size.txt"
adb shell wm density > "$evidence/display-density.txt"
adb logcat -c
status=0
probe_arguments=()
if [[ "${GITHUB_REF:-}" == refs/heads/release/1.7.6.1-editing-settings-polish && "$platform" == api37 ]]; then
  bash scripts/verify_android17_device.sh "$evidence"
  probe_arguments+=('-Pandroid.testInstrumentationRunnerArguments.hiddenTorControlDiagnostics=true')
fi
if [[ -n "${HIDDEN_ONION_PROBE_URL:-}" || -n "${HIDDEN_ONION_PROBE_EXPECTED:-}" ]]; then
  probe_arguments+=("-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeUrl=${HIDDEN_ONION_PROBE_URL:?}")
  probe_arguments+=("-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeExpected=${HIDDEN_ONION_PROBE_EXPECTED:?}")
fi
./gradlew connectedDebugAndroidTest "${probe_arguments[@]}" "-Pandroid.testInstrumentationRunnerArguments.class=$classes" --stacktrace || status=$?
# Keep native focus/lifecycle evidence from disposable synthetic fixtures, including failed runs.
if [[ -d app/build/outputs/androidTest-results/connected/debug ]]; then cp -a app/build/outputs/androidTest-results/connected/debug "$evidence/results"; fi
if [[ -d app/build/outputs/connected_android_test_additional_output ]]; then cp -a app/build/outputs/connected_android_test_additional_output "$evidence/additional-output"; fi
adb logcat -d > "$evidence/logcat.txt" 2>&1 || true
exit "$status"
