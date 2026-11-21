#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true ]]
if [[ "${GITHUB_EVENT_NAME:-}" == push ]]; then
    [[ "${GITHUB_REPOSITORY:-}" == traynor1987/Private-gallery- &&
        "${GITHUB_WORKFLOW:-}" == "Hidden Space Android 17" && "${GITHUB_REF:-}" == refs/heads/main ]]
else
    [[ "${GITHUB_EVENT_NAME:-}" == pull_request && "${GITHUB_HEAD_REF:-}" == feature/novaos-hidden-space-apps &&
        "${GITHUB_BASE_REF:-}" == main ]]
fi
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 37 ]]
baseline=46b9c78c15bcbe18d129e28be8677d21c59ca838
git merge-base --is-ancestor "$baseline" HEAD
root=$PWD
baseline_dir="$RUNNER_TEMP/hidden-space-build94"
reports="$root/app/build/hidden-space/upgrade"
mkdir -p "$reports"
git worktree add --detach "$baseline_dir" "$baseline"
trap 'git worktree remove --force "$baseline_dir"' EXIT
test_dir="$baseline_dir/app/src/androidTest/java/uk/co/traynor/privategallery/core/hidden"
cp scripts/fixtures/HiddenUpgrade94Test.kt "$test_dir/"
(cd "$baseline_dir" && ./gradlew assembleDebug assembleDebugAndroidTest --console=plain)
baseline_apk="$baseline_dir/app/build/outputs/apk/debug/app-debug.apk"
candidate_apk="$root/app/build/outputs/apk/debug/app-debug.apk"
test_apk="$baseline_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
sha256sum "$baseline_apk" "$candidate_apk" "$test_apk" | tee "$reports/apks.sha256"
if cmp -s "$baseline_apk" "$candidate_apk"; then
    echo "Expected distinct baseline and candidate APKs" >&2
    exit 1
fi
pkg=uk.co.traynor.privategallery.hiddenprototype
# A fresh disposable emulator is required. No data clearing is allowed between phases.
packages=$(adb shell pm list packages "$pkg" | tr -d '\r')
[[ -z "$packages" ]]
adb install "$baseline_apk"
adb install -t "$test_apk"
run_case() {
    local phase=$1
    adb shell am instrument -w -e phase "$phase" -e class \
        uk.co.traynor.privategallery.core.hidden.HiddenUpgrade94Test \
        "$pkg.test/androidx.test.runner.AndroidJUnitRunner" | tee "$reports/$phase.txt"
    grep -Fxq 'OK (1 test)' <(tr -d '\r' < "$reports/$phase.txt")
}
run_case seed
adb install -r "$candidate_apk"
run_case verify
adb shell am force-stop "$pkg"
run_case restart
