#!/usr/bin/env bash
set -euo pipefail
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 37 ]]
adb shell pm path com.android.systemui | grep -q '^package:'
mkdir -p app/build/hidden-space
adb shell wm size 720x1600
adb shell wm density 320
package=uk.co.traynor.privategallery
classes="$package.HiddenSpaceAppsTest,$package.HiddenSpaceDashboardUiTest,$package.HiddenHomeNavigation67Test,$package.HiddenDiscoverabilityRegressionTest"
classes+=",$package.core.browser.hidden.HiddenBrowserIsolationTest,$package.core.browser.hidden.HiddenDownloadTest,$package.core.browser.hidden.HiddenSessionLibrary1762Test"
classes+=",$package.core.browser.hidden.HiddenVpnBoundary1762Test,$package.core.browser.hidden.HiddenRuntimeFailureTest"
classes+=",$package.core.hidden.HiddenRepositoryTest,$package.core.hidden.HiddenCredentialSeparationTest"
classes+=",$package.core.browser.v2.PrivateAppBrowserSeparationTest,$package.core.camera.VaultCaptureAccessTest"
./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$classes" --stacktrace
cp -R app/build/outputs/androidTest-results/connected app/build/hidden-space/cover
adb shell wm size 1768x2208
adb shell wm density 320
./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$package.HiddenHomeNavigation67Test,$package.HiddenSpaceDashboardUiTest" --stacktrace
cp -R app/build/outputs/androidTest-results/connected app/build/hidden-space/inner
