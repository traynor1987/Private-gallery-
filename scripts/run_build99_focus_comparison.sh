#!/usr/bin/env bash
set -euo pipefail
[[ ${GITHUB_ACTIONS:-} == true ]]
[[ $(adb shell getprop ro.kernel.qemu | tr -d '\r') == 1 ]]
[[ $(adb shell getprop ro.build.version.sdk | tr -d '\r') == 37 ]]
out=app/build/build99-comparison
mkdir -p "$out"
git rev-parse HEAD HEAD^{tree} > "$out/source.txt"
adb shell wm size 1200x1800
adb shell wm density 160
adb shell wm dismiss-keyguard
if [[ ${PREPARE_FULLSCREEN:?} == true ]]; then
  adb shell settings put secure immersive_mode_confirmations confirmed
  [[ $(adb shell settings get secure immersive_mode_confirmations | tr -d '\r') == confirmed ]]
fi
adb shell settings get secure immersive_mode_confirmations > "$out/tutorial-state.txt"
# Observe window ownership on a disposable device without recording UI contents.
while true; do
  adb shell dumpsys window displays | grep -E 'mCurrentFocus=|mFocusedApp=' >> "$out/focus.txt" || true
  sleep 1
done &
observer=$!
trap 'kill "$observer" 2>/dev/null || true' EXIT
ns=uk.co.traynor.privategallery
cases="$ns.EncryptedVideoContinuity1751Test,$ns.core.vault.RecentlyDeleted177Test,$ns.core.vault.VaultDocumentRepositoryTest,$ns.core.hidden.HiddenRecentlyDeleted177Test#independentRetentionRestoreMembershipAndThumbnailRecovery,$ns.ui.BrowserProfessionalUiTest"
run_cases() {
    local label=$1 selection=$2 result=0
    local reports=app/build/outputs/androidTest-results/connected
    rm -rf "$reports"
    ./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$selection" --console=plain || result=$?
    if [[ -d "$reports" ]]; then
        mkdir -p "$out/$label"
        cp -a "$reports/." "$out/$label/"
        python3 scripts/verify_selected_android1762.py "$out/$label" "$selection" || result=1
    else
        result=1
    fi
    return "$result"
}
failed=0
if [[ ${VERIFY_FRESH_FIXTURES:-false} == true ]]; then
    run_cases notices "$ns.ui.BrowserV2ModernWebTest#nativeConfirmDialogCompletesWithoutReplacingParentPage,$ns.ui.BrowserBetaNoticeTest,$ns.ui.HiddenBrowser17LayoutTest,$ns.ui.HiddenRestoreDialogTest,$ns.ui.BrowserV2HomeRenderTest,$ns.ui.BrowserV2InitialPresentationTest,$ns.ui.BrowserPolishTest,$ns.ui.BrowserConsent1752Test,$ns.core.media.BrowserVideoAssistantTest" || failed=1
    run_cases hidden_vault "$ns.ui.HiddenVaultIntegrationTest#hiddenBottomNavigationFitsActualActivityWindowNotItsOwnLayoutBounds,$ns.ui.HiddenVaultIntegrationTest#normalVaultGridViewerAndFavouriteUsePersistedHiddenData,$ns.ui.HiddenVaultIntegrationTest#pickerResultSurvivesPrimaryLockButImportRequiresFreshHiddenPin" || failed=1
    run_cases diagnostics "$ns.ui.BrowserRuntimeDiagnosticsTest#clearingCurrentTraceRetainsClearlySeparatedPreviousFatalEvidence" || failed=1
    run_cases launcher "$ns.LauncherWorkspace70Test#directPrimaryVaultHidesOnlyInternalNavigationUntilReturningHome" || failed=1
    run_cases gallery "$ns.PrimaryGalleryDeviceRepair1762Test" || failed=1
    run_cases theme "$ns.ui.HiddenBrowserCapturePolicy1762Test#parentSelectedThemeIsAuthoritativeAndIndependentOfHiddenCaptureOptIn" || failed=1
fi
run_cases common "$cases" || failed=1
exit "$failed"
