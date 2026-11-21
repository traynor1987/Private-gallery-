#!/usr/bin/env bash
set -euo pipefail
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 37 ]]
adb shell pm path com.android.systemui | grep -q '^package:'
mkdir -p app/build/hidden-space
upgrade_status=0
bash scripts/run_hidden_upgrade94.sh || upgrade_status=$?
adb shell wm size 720x1600
adb shell wm density 320
package=uk.co.traynor.privategallery
classes="$package.HiddenSpaceAppsTest,$package.HiddenSpaceDashboardUiTest,$package.HiddenHomeNavigation67Test,$package.HiddenDiscoverabilityRegressionTest"
classes+=",$package.core.browser.hidden.HiddenBrowserIsolationTest,$package.core.browser.hidden.HiddenDownloadTest,$package.core.browser.hidden.HiddenSessionLibrary1762Test"
classes+=",$package.core.browser.hidden.HiddenVpnBoundary1762Test,$package.core.browser.hidden.HiddenRuntimeFailureTest"
classes+=",$package.core.hidden.HiddenRepositoryTest,$package.core.hidden.HiddenCredentialSeparationTest"
classes+=",$package.core.browser.v2.PrivateAppBrowserSeparationTest,$package.core.camera.VaultCaptureAccessTest"
classes+=",$package.core.camera.CameraProcessRecoveryTest,$package.core.backup.BackupRuntimeTest,$package.core.backup.BackupAppsRoundtrip62Test"
classes+=",$package.core.browser.v2.SanctuaryBrowserGateTest,$package.core.sanctuary.SanctuaryLauncherPolicy70Test"
classes+=",$package.core.home2.SpacePreferencesTest"
cover_status=0
./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$classes" --stacktrace || cover_status=$?
if [[ -d app/build/outputs/androidTest-results/connected ]]; then
    cp -R app/build/outputs/androidTest-results/connected app/build/hidden-space/cover
fi
python3 scripts/verify_selected_android1762.py app/build/hidden-space/cover "$classes" || cover_status=1
adb shell wm size 1768x2208
adb shell wm density 320
inner_status=0
./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$package.HiddenHomeNavigation67Test,$package.HiddenSpaceDashboardUiTest" --stacktrace || inner_status=$?
if [[ -d app/build/outputs/androidTest-results/connected ]]; then
    cp -R app/build/outputs/androidTest-results/connected app/build/hidden-space/inner
fi
python3 scripts/verify_selected_android1762.py app/build/hidden-space/inner "$package.HiddenHomeNavigation67Test,$package.HiddenSpaceDashboardUiTest" || inner_status=1
route_status=0
bash scripts/run_hidden_space_routes.sh || route_status=$?
# The Build 94 regression uses an emulator-only WhatsApp fixture and the unmodified listener.
adb shell wm size 1200x1800
adb shell wm density 160
adb shell settings put secure show_ime_with_hard_keyboard 1
guardian_status=0
bash scripts/run_guardian_notification_pipeline93.sh GuardianFoldNotificationTest guardian-fold-notification || guardian_status=1
bash scripts/run_guardian_notification_pipeline93.sh || guardian_status=1
for test in GuardianNotificationCompatibility77Test GuardianCaptureStore77Test GuardianEvidence90RuntimeTest; do
    bash scripts/run_visual_checkpoint.sh "$test" "$test" "$package.core.guardian" || guardian_status=1
done
bash scripts/run_visual_checkpoint.sh GuardianInbox77UiTest guardian-inbox "$package.launcher" || guardian_status=1
for test in SanctuaryGuardianUiTest SanctuaryDndUiTest; do
    bash scripts/run_visual_checkpoint.sh "$test" "$test" || guardian_status=1
done
cp -R app/build/home2-evidence app/build/hidden-space/guardian-sanctuary
(( cover_status == 0 && inner_status == 0 && route_status == 0 && guardian_status == 0 && upgrade_status == 0 ))
