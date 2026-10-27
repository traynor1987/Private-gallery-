#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REF:-}" == refs/heads/release/1.7.6.2-ui-browser-polish ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
evidence=app/build/focused1762
mkdir -p "$evidence"
git rev-parse HEAD > "$evidence/source-sha.txt"
bash scripts/verify_android17_device.sh "$evidence"
sha256sum app/build/outputs/apk/debug/app-debug.apk > "$evidence/apk-sha256.txt"
archive() { status=$?; trap - EXIT; set +e; printf '%s\n' "$status" > "$evidence/exit-status.txt"; adb logcat -d > "$evidence/logcat.txt" 2>&1; exit "$status"; }
trap archive EXIT
classes='uk.co.traynor.privategallery.ui.BrowserConsent1752Test#extensionBasedUploadUsesThemedVaultPickerWithoutPrematureEgress,uk.co.traynor.privategallery.CollectionDialogs1762Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenSessionLibrary1762Test,uk.co.traynor.privategallery.ui.HiddenRestoreDialogTest,uk.co.traynor.privategallery.ui.PrimaryCopyDialogTest,uk.co.traynor.privategallery.ui.PasscodeKeypadTest,uk.co.traynor.privategallery.ui.BrowserUploadPolicyTest,uk.co.traynor.privategallery.ui.ExternalEditingUi176Test,uk.co.traynor.privategallery.core.media.ExternalEditorIntents1761Test,uk.co.traynor.privategallery.core.media.ExternalPhotoExport1761Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserIsolationTest,uk.co.traynor.privategallery.core.browser.hidden.HiddenSocksTransportTest,uk.co.traynor.privategallery.core.browser.hidden.HiddenNavigationTransportTest,uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccessTest#transferredEligibleItemsAreImmediatelyVisibleToFreshPrimaryPickerQuery,uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccessTest#primaryMoveOnlyTrashesVerifiedSourceAndCanBeRecovered,uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccessTest#missingAndWrongPrimaryAuthenticationNeverPublishesOrDeletes,uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccessTest#restrictionsSurvivePrimaryTransferAndPreventPhonePublication'
adb shell wm size 1200x1800
adb shell wm density 160
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$classes" --stacktrace
python3 scripts/verify_focused1762.py app/build/outputs/androidTest-results/connected/debug
