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
if [[ "${GITHUB_REF:-}" == refs/heads/release/1.7.6.1-editing-settings-polish ]]; then
  classes+=",uk.co.traynor.privategallery.core.browser.hidden.HiddenRuntimeFailureTest"
fi
probe_arguments=()
if [[ "${GITHUB_ACTIONS:-}" == true || -n "${HIDDEN_ONION_PROBE_URL:-}" || -n "${HIDDEN_ONION_PROBE_EXPECTED:-}" ]]; then
  probe_arguments+=("-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeUrl=${HIDDEN_ONION_PROBE_URL:?}")
  probe_arguments+=("-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeExpected=${HIDDEN_ONION_PROBE_EXPECTED:?}")
fi
./gradlew connectedDebugAndroidTest "${probe_arguments[@]}" "-Pandroid.testInstrumentationRunnerArguments.class=$classes"
mkdir -p app/build/acceptance-diagnostics/hidden-browser-preflight
cp -R app/build/outputs/androidTest-results/connected/debug/. app/build/acceptance-diagnostics/hidden-browser-preflight/

bash scripts/run_hidden_process_fixtures.sh
