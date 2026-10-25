#!/usr/bin/env bash
set -euo pipefail
out=app/build/refinement1751/storage-api37
mkdir -p "$out"
archive() {
  local primary=$?
  trap - EXIT
  set +e
  python3 scripts/ci_emulator_storage.py "$out/after.json" --avd storage1751-37
  local snapshot_status=$?
  printf '%s\n' "$primary" > "$out/command-exit-status.txt"
  if [[ "$primary" != 0 ]]; then exit "$primary"; fi
  exit "$snapshot_status"
}
trap archive EXIT
python3 scripts/ci_emulator_storage.py "$out/before.json" --avd storage1751-37 --require-budget
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = 37
selection=uk.co.traynor.privategallery.core.hidden.HiddenLargeMediaTest#largeVideoImportSeekThumbnailRestartAndRevocation,uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccessTest#largeVideoBothDestinationsPreserveCompleteBytesAndPlaybackSeek
bash scripts/run_repair1751_focused.sh storage-api37-cases "$selection"
python3 scripts/verify_professional_ui_results.py app/build/outputs/androidTest-results/connected/debug "$selection"
