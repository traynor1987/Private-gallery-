#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && ( "${GITHUB_REF:-}" == refs/heads/release/1.7.6.2-ui-browser-polish || "${GITHUB_REF:-}" == refs/heads/release/1.7.7-recently-deleted ) ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
evidence=app/build/focused1762
mkdir -p "$evidence"
git rev-parse HEAD > "$evidence/source-sha.txt"
bash scripts/verify_android17_device.sh "$evidence"
sha256sum app/build/outputs/apk/debug/app-debug.apk > "$evidence/apk-sha256.txt"
# Gradle collects this runner output before uninstalling its disposable test target.
# Require exactly one actual producer per known synthetic result; never synthesize proof.
copy_native_result() {
  local result=$1 destination=$2
  local -a matches=()
  mapfile -d '' matches < <(find app/build/outputs/connected_android_test_additional_output -type f -name "$result" -print0)
  [[ ${#matches[@]} == 1 ]] || { echo "Expected one actual synthetic result: $result" >&2; return 1; }
  cp "${matches[0]}" "$destination"
}
native_evidence_package=$(python3 scripts/debug_apk_identity1762.py app/build/outputs/apk/debug/app-debug.apk)
printf '%s\n' "$native_evidence_package" > "$evidence/application-id.txt"
archive() {
  status=$?; trap - EXIT; set +e
  printf '%s\n' "$status" > "$evidence/exit-status.txt"
  adb logcat -d > "$evidence/logcat.txt" 2>&1
  # Preserve these existing synthetic proofs even if a different scenario fails.
  # The APK identity guard above rejects production packages; no owner files are read.
  for result in vpn-native-revocation1762-result.json vpn-network-loss1762-result.json native-tor1762-result.json; do
    if copy_native_result "$result" "$evidence/$result.tmp" 2>/dev/null; then
      mv "$evidence/$result.tmp" "$evidence/$result"
    else
      rm -f "$evidence/$result.tmp"
    fi
  done
  adb pull "/sdcard/Android/media/$native_evidence_package/additional_test_output" "$evidence/additional-test-output" >/dev/null 2>&1
  exit "$status"
}
trap archive EXIT
classes='uk.co.traynor.privategallery.core.hidden.HiddenRepositoryTest#corruptionOfThumbnailDoesNotDeleteAuthenticatedMedia,uk.co.traynor.privategallery.ui.RecentlyDeletedUi177Test,uk.co.traynor.privategallery.core.vault.RecentlyDeleted177Test,uk.co.traynor.privategallery.core.hidden.HiddenRecentlyDeleted177Test,uk.co.traynor.privategallery.PrimaryGalleryDeviceRepair1762Test,uk.co.traynor.privategallery.ui.ExternalEditingUi176Test#returnedWithoutResultExposesGuidedExportWithSeparateConsent,uk.co.traynor.privategallery.ExternalEditingLifecycle176Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenUploadPickerPaging1762Test#actualTransferredLastItemBeyondFiveHundredSurvivesCancelReopenAndTypedPages,uk.co.traynor.privategallery.core.browser.hidden.NativeTorIsolation1762Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnLive1762Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnNetworkLoss1762Test,uk.co.traynor.privategallery.HiddenMainBrowserLifecycle1762Test'
# A narrow reproducer can never become a successful release gate, even if it passes.
IFS= read -r verification_scope < .focused1762
if [[ "$verification_scope" == diagnostic-browser-result-owners ]]; then
  printf '%s\n' 'DIAGNOSTIC ONLY: not full-scope release evidence' > "$evidence/diagnostic-only.txt"
  diagnostic_classes="uk.co.traynor.privategallery.ui.BrowserPermissionOwnership1762Test,uk.co.traynor.privategallery.ui.BrowserDeviceSelection1762Test"
  adb shell wm size 1200x1800
  adb shell wm density 160
  ./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$diagnostic_classes" --stacktrace
  python3 scripts/verify_selected_android1762.py app/build/outputs/androidTest-results/connected/debug "$diagnostic_classes" > "$evidence/selected-cases-result.txt"
  echo 'Diagnostic completed; full exact-source release verification is still required.' >&2
  exit 17
fi
if [[ "$verification_scope" == diagnostic-runtime-fixes ]]; then
  printf '%s\n' 'DIAGNOSTIC ONLY: affected UI and complete native security suites; not full release evidence' > "$evidence/diagnostic-only.txt"
  classes="uk.co.traynor.privategallery.ExternalEditorRecovery1762Test,uk.co.traynor.privategallery.core.media.ExternalEditorRecoveryImport1762Test,uk.co.traynor.privategallery.ExternalEditingLifecycle176Test,uk.co.traynor.privategallery.core.media.ExternalPhotoExport1761Test,uk.co.traynor.privategallery.ui.ExternalEditingUi176Test,uk.co.traynor.privategallery.ui.HiddenNativeBrowserDialogs1762Test,uk.co.traynor.privategallery.HiddenVpnHandoff1762Test,uk.co.traynor.privategallery.ui.BrowserToolbarLayout1762Test,uk.co.traynor.privategallery.ui.BrowserProfessionalUiTest,uk.co.traynor.privategallery.ui.BrowserPolishTest,uk.co.traynor.privategallery.ui.BrowserSystemPicker1762Test,uk.co.traynor.privategallery.ui.BrowserPermissionOwnership1762Test,uk.co.traynor.privategallery.ui.BrowserDeviceSelection1762Test,uk.co.traynor.privategallery.ui.HiddenBrowserCapturePolicy1762Test,uk.co.traynor.privategallery.ui.BrowserConsent1752Test,uk.co.traynor.privategallery.ui.RemainingMediaModals1762Test,uk.co.traynor.privategallery.ui.BrowserModalSurfaces1762Test,uk.co.traynor.privategallery.ui.ModalCompletion1762Test,uk.co.traynor.privategallery.ui.AiEditorProfessionalTest,uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnBoundary1762Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenRuntimeFailureTest,uk.co.traynor.privategallery.core.browser.hidden.HiddenLibraryParcelPressure1762Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenUploadPickerPaging1762Test,uk.co.traynor.privategallery.core.browser.hidden.NativeTorIsolation1762Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnLive1762Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnNetworkLoss1762Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenNativePresentation1762Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnShutdown1762Test"
fi
if [[ "$verification_scope" == diagnostic-native-upload-fix ]]; then
  printf '%s\n' 'DIAGNOSTIC ONLY: remaining affected native browser protocol cases; not full release evidence' > "$evidence/diagnostic-only.txt"
  classes="uk.co.traynor.privategallery.core.browser.hidden.NativeTorIsolation1762Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnLive1762Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnNetworkLoss1762Test"
fi
test -s "$RUNNER_TEMP/private-gallery-vpn1762/client.conf"
adb push "$RUNNER_TEMP/private-gallery-vpn1762/client.conf" /data/local/tmp/hidden-vpn1762.conf
adb shell chmod 600 /data/local/tmp/hidden-vpn1762.conf
bash scripts/reset_native_browser_evidence1762.sh "$native_evidence_package"
adb shell wm size 1200x1800
adb shell wm density 160
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$classes"     -Pandroid.testInstrumentationRunnerArguments.hiddenVpnConfig=/data/local/tmp/hidden-vpn1762.conf     -Pandroid.testInstrumentationRunnerArguments.hiddenVpnOrigin=http://fixture.invalid \
    "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeUrl=${HIDDEN_ONION_PROBE_URL:?}" \
    "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeExpected=${HIDDEN_ONION_PROBE_EXPECTED:?}" --stacktrace
sudo chmod a+r app/build/hidden-vpn1762/events.jsonl app/build/hidden-vpn1762/traffic.pcap
sudo tcpdump -tttt -nn -vv -X -r app/build/hidden-vpn1762/traffic.pcap > app/build/hidden-vpn1762/packets.txt 2>&1
copy_native_result vpn-native-revocation1762-result.json app/build/hidden-vpn1762/vpn-native-revocation1762-result.json
copy_native_result vpn-network-loss1762-result.json app/build/hidden-vpn1762/vpn-network-loss1762-result.json
python3 scripts/verify_hidden_vpn_fixture1762.py app/build/hidden-vpn1762
copy_native_result native-tor1762-result.json app/build/hidden-vpn1762/native-tor1762-result.json
python3 scripts/verify_native_tor1762.py app/build/hidden-vpn1762 "$RUNNER_TEMP/private-gallery-onion-probe" > "$evidence/native-tor-result.txt"
if [[ "$verification_scope" == diagnostic-runtime-fixes || "$verification_scope" == diagnostic-native-upload-fix ]]; then
  python3 scripts/verify_selected_android1762.py app/build/outputs/androidTest-results/connected/debug "$classes" > "$evidence/selected-cases-result.txt"
  echo 'Affected runtime and packet verification completed; final enabled-source full gate remains required.' >&2
  exit 17
fi
python3 scripts/verify_focused1762.py app/build/outputs/androidTest-results/connected/debug
python3 scripts/verify_selected_android1762.py app/build/outputs/androidTest-results/connected/debug "$classes" > "$evidence/selected-cases-result.txt"
