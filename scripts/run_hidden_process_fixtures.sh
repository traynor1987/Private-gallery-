#!/usr/bin/env bash
set -euo pipefail
# Actual process restart, using synthetic cookies in the TEST ONLY package.
[[ "${GITHUB_ACTIONS:-}" == true ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
mkdir -p app/build/acceptance-diagnostics/hidden-browser-preflight
# AGP's connected-test runner removes its installed APKs after completing.
# Reinstall the same built TEST ONLY artifacts before manual two-process instrumentation.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
fixture_package=uk.co.traynor.privategallery.hiddenprototype
adb shell pm list instrumentation | grep -F "$fixture_package.test/androidx.test.runner.AndroidJUnitRunner"
fixture_test=uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserIsolationTest#primaryAndHiddenProfilesKeepCookiesIndependentAndClearOnlyHiddenData
for phase in seed verify; do
  adb shell am instrument -w -e class "$fixture_test" -e hiddenProfilePhase "$phase" "$fixture_package.test/androidx.test.runner.AndroidJUnitRunner" | tee "app/build/acceptance-diagnostics/hidden-browser-preflight/process-$phase.txt"
  grep -q 'OK (1 test)' "app/build/acceptance-diagnostics/hidden-browser-preflight/process-$phase.txt"
  if [[ "$phase" == seed ]]; then adb shell am force-stop "$fixture_package"; fi
done

# Camera recovery is verified in a distinct process with only synthetic private files.
camera_fixture=uk.co.traynor.privategallery.core.camera.CameraProcessRecoveryTest#pendingCapturesSurviveIndependentProcessRestart
for phase in seed verify; do
  adb shell am instrument -w -e class "$camera_fixture" -e cameraJournalPhase "$phase" "$fixture_package.test/androidx.test.runner.AndroidJUnitRunner" | tee "app/build/acceptance-diagnostics/hidden-browser-preflight/camera-process-$phase.txt"
  grep -q 'OK (1 test)' "app/build/acceptance-diagnostics/hidden-browser-preflight/camera-process-$phase.txt"
  if [[ "$phase" == seed ]]; then adb shell am force-stop "$fixture_package"; fi
done
