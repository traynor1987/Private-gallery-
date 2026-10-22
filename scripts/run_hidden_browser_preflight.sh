#!/usr/bin/env bash
set -euo pipefail
# Faster feedback on new 1.6 gates; the full unchanged regression suite runs afterwards.
adb shell wm size 1200x1800
adb shell wm density 160
classes="uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserIsolationTest"
classes+=",uk.co.traynor.privategallery.core.browser.hidden.HiddenSocksTransportTest"
classes+=",uk.co.traynor.privategallery.core.browser.hidden.HiddenDownloadTest"
classes+=",uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnProfileLoaderTest"
classes+=",uk.co.traynor.privategallery.core.browser.hidden.HiddenTorBootstrapTest"
classes+=",uk.co.traynor.privategallery.ui.HiddenHostRoutingTest#authenticatedHiddenTransitionToBrowserKeepsAuthorityAndNeverStartsUnavailableMode"
classes+=",uk.co.traynor.privategallery.ui.ProfessionalUiTest#diagnosticsRequireSevenTapsAndFreshAuthentication"
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$classes"
mkdir -p app/build/acceptance-diagnostics/hidden-browser-preflight
cp -R app/build/outputs/androidTest-results/connected/debug/. app/build/acceptance-diagnostics/hidden-browser-preflight/

# Actual process restart, using synthetic cookies in the TEST ONLY package.
[[ "${GITHUB_ACTIONS:-}" == true ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
fixture_package=uk.co.traynor.privategallery.hiddenprototype
fixture_test=uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserIsolationTest#primaryAndHiddenProfilesKeepCookiesIndependentAndClearOnlyHiddenData
for phase in seed verify; do
  adb shell am instrument -w -e class "$fixture_test" -e hiddenProfilePhase "$phase" "$fixture_package.test/androidx.test.runner.AndroidJUnitRunner" | tee "app/build/acceptance-diagnostics/hidden-browser-preflight/process-$phase.txt"
  grep -q 'OK (1 test)' "app/build/acceptance-diagnostics/hidden-browser-preflight/process-$phase.txt"
  if [[ "$phase" == seed ]]; then adb shell am force-stop "$fixture_package"; fi
done
