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
  adb shell dumpsys window windows | grep -E 'mCurrentFocus=|mFocusedApp=' >> "$out/focus.txt" || true
  sleep 1
done &
observer=$!
trap 'kill "$observer" 2>/dev/null || true' EXIT
ns=uk.co.traynor.privategallery
cases="$ns.EncryptedVideoContinuity1751Test,$ns.core.vault.RecentlyDeleted177Test,$ns.core.vault.VaultDocumentRepositoryTest,$ns.core.hidden.HiddenRecentlyDeleted177Test#independentRetentionRestoreMembershipAndThumbnailRecovery,$ns.ui.BrowserProfessionalUiTest"
./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$cases" --console=plain
python3 scripts/verify_selected_android1762.py app/build/outputs/androidTest-results/connected "$cases"
