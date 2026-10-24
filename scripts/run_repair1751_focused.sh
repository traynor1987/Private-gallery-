#!/usr/bin/env bash
set -euo pipefail
platform=${1:?platform label required}
classes=${2:?focused classes required}
[[ "$platform" == api32 || "$platform" == api36 ]]
evidence="app/build/refinement1751/$platform"
mkdir -p "$evidence"
adb logcat -c
status=0
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$classes" --stacktrace || status=$?
# Keep native focus/lifecycle evidence from disposable synthetic fixtures, including failed runs.
adb logcat -d > "$evidence/logcat.txt" 2>&1 || true
exit "$status"
