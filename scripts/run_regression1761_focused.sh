#!/usr/bin/env bash
set -euo pipefail
mode=${1:?default or software}; avd=${2:?actual AVD name}
[[ "$GITHUB_ACTIONS" == true && "$GITHUB_REF" == refs/heads/release/1.7.6.1-editing-settings-polish ]]
[[ "$mode" == default || "$mode" == software ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
evidence="app/build/regression1761-focused/$mode"
mkdir -p "$evidence"
bash scripts/verify_android17_device.sh "$evidence"
[[ "$(adb shell getenforce | tr -d '\r')" == Enforcing ]]
adb shell getenforce > "$evidence/selinux.txt"
adb shell getprop ro.boot.qemu.hwcodec.avcdec > "$evidence/avcdec.txt"
git rev-parse HEAD > "$evidence/source-sha.txt"
sha256sum app/build/outputs/apk/debug/app-debug.apk | cut -d' ' -f1 > "$evidence/apk-sha256.txt"
python3 scripts/ci_emulator_storage.py "$evidence/storage-before.json" --avd "$avd" --require-budget
rm -rf app/build/outputs/androidTest-results/connected/debug
adb logcat -c
classes=uk.co.traynor.privategallery.core.media.PrivateVideoPlayerTest#protectedMemoryPlaybackAndCleanup
if [[ "$mode" == software ]]; then
  classes=uk.co.traynor.privategallery.core.media.PrivateVideoPlayerTest,uk.co.traynor.privategallery.PrivacyShield175Test,uk.co.traynor.privategallery.ui.MediaDetails174Test,uk.co.traynor.privategallery.EncryptedVideoContinuity1751Test,uk.co.traynor.privategallery.core.vault.VaultVideoDataSourceTest,uk.co.traynor.privategallery.core.media.AdaptiveVideoPlaybackTest
fi
status=0
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$classes" --stacktrace || status=$?
printf '%s\n' "$status" > "$evidence/command-exit-status.txt"
if [[ -d app/build/outputs/androidTest-results/connected/debug ]]; then cp -a app/build/outputs/androidTest-results/connected/debug/. "$evidence/"; fi
adb logcat -d > "$evidence/logcat.txt"
adb shell getenforce > "$evidence/selinux-after.txt"
[[ "$(tr -d '\r\n' < "$evidence/selinux-after.txt")" == Enforcing ]]
python3 scripts/ci_emulator_storage.py "$evidence/storage-after.json" --avd "$avd"
# Default is an explicit negative control, not an acceptance pass. The final verifier
# requires its exact failed case and SELinux/codec error chain, and all27 software passes.
if [[ "$mode" == software ]]; then exit "$status"; fi
