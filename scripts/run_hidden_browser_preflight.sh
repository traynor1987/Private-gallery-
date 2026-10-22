#!/usr/bin/env bash
set -euo pipefail
# Faster feedback on new 1.6 gates; the full unchanged regression suite runs afterwards.
adb shell wm size 1200x1800
adb shell wm density 160
classes="uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserIsolationTest"
classes+=",uk.co.traynor.privategallery.core.browser.hidden.HiddenSocksTransportTest"
classes+=",uk.co.traynor.privategallery.core.browser.hidden.HiddenDownloadTest"
classes+=",uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnProfileLoaderTest"
classes+=",uk.co.traynor.privategallery.ui.HiddenHostRoutingTest#authenticatedHiddenTransitionToBrowserKeepsAuthorityAndNeverStartsUnavailableMode"
classes+=",uk.co.traynor.privategallery.ui.ProfessionalUiTest#diagnosticsRequireSevenTapsAndFreshAuthentication"
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$classes"
mkdir -p app/build/acceptance-diagnostics/hidden-browser-preflight
cp -R app/build/outputs/androidTest-results/connected/debug/. app/build/acceptance-diagnostics/hidden-browser-preflight/
