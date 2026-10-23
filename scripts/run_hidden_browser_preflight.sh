#!/usr/bin/env bash
set -euo pipefail
# Faster feedback on new 1.6 gates; the full unchanged regression suite runs afterwards.
adb shell wm size 1200x1800
adb shell wm density 160
classes="uk.co.traynor.privategallery.core.browser.hidden.HiddenNavigationTransportTest"
classes+=",uk.co.traynor.privategallery.core.browser.hidden.HiddenRedirectRenderingTest"
classes+=",uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserIsolationTest"
classes+=",uk.co.traynor.privategallery.core.browser.hidden.HiddenSocksTransportTest"
classes+=",uk.co.traynor.privategallery.core.browser.hidden.HiddenDownloadTest"
classes+=",uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnProfileLoaderTest"
classes+=",uk.co.traynor.privategallery.core.browser.hidden.HiddenTorBootstrapTest"
classes+=",uk.co.traynor.privategallery.ui.HiddenHostRoutingTest#authenticatedHiddenTransitionToBrowserKeepsAuthorityAndNeverStartsUnavailableMode"
classes+=",uk.co.traynor.privategallery.ui.ProfessionalUiTest#diagnosticsRequireSevenTapsAndFreshAuthentication"
classes+=",uk.co.traynor.privategallery.ui.HiddenHostRoutingTest#foregroundCreatesFreshBrowserOwnerWithoutRestoringTabs"
classes+=",uk.co.traynor.privategallery.ui.DiscreetPinUnlockTest"
classes+=",uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccessTest#copiesMoreThanOneHundredRealPhotosWithoutLosingOriginals"
classes+=",uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccessTest#hundredsOfSelectionsReportActualProgressAndSafeCancellation"
classes+=",uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccessTest#phoneCopyAndMoveReceiptsRequireVerifiedDestinationAndUnchangedSource"
classes+=",uk.co.traynor.privategallery.core.hidden.HiddenRepositoryTest"
classes+=",uk.co.traynor.privategallery.core.camera.VaultCaptureAccessTest"
classes+=",uk.co.traynor.privategallery.core.camera.CameraHardwareTest"
classes+=",uk.co.traynor.privategallery.ui.HiddenBrowser17LayoutTest"
classes+=",uk.co.traynor.privategallery.ui.VaultCameraUiTest"
probe_arguments=()
if [[ "${GITHUB_ACTIONS:-}" == true || -n "${HIDDEN_ONION_PROBE_URL:-}" || -n "${HIDDEN_ONION_PROBE_EXPECTED:-}" ]]; then
  probe_arguments+=("-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeUrl=${HIDDEN_ONION_PROBE_URL:?}")
  probe_arguments+=("-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeExpected=${HIDDEN_ONION_PROBE_EXPECTED:?}")
fi
./gradlew connectedDebugAndroidTest "${probe_arguments[@]}" "-Pandroid.testInstrumentationRunnerArguments.class=$classes"
mkdir -p app/build/acceptance-diagnostics/hidden-browser-preflight
cp -R app/build/outputs/androidTest-results/connected/debug/. app/build/acceptance-diagnostics/hidden-browser-preflight/

# Actual process restart, using synthetic cookies in the TEST ONLY package.
[[ "${GITHUB_ACTIONS:-}" == true ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
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
