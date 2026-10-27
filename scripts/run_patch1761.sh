#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REF:-}" == refs/heads/release/1.7.6.1-editing-settings-polish ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
evidence=app/build/patch1761
mkdir -p "$evidence"
git rev-parse HEAD > "$evidence/source-sha.txt"
bash scripts/verify_android17_device.sh "$evidence"
python3 scripts/verify_android17_media_environment.py "$evidence/media-before.json"
python3 scripts/ci_emulator_storage.py "$evidence/storage-before.json" --avd patch1761-37 --require-budget
sha256sum app/build/outputs/apk/debug/app-debug.apk > "$evidence/apk-sha256.txt"
archive() {
  status=$?; trap - EXIT; set +e
  printf '%s\n' "$status" > "$evidence/command-exit-status.txt"
  python3 scripts/verify_android17_media_environment.py "$evidence/media-after.json"; after=$?
  adb logcat -d > "$evidence/logcat.txt" 2>&1
  python3 scripts/ci_emulator_storage.py "$evidence/storage-after.json" --avd patch1761-37
  if [[ "$status" != 0 ]]; then exit "$status"; fi
  exit "$after"
}
trap archive EXIT
classes=$(python3 scripts/release_policy.py selectors)
rm -rf app/build/outputs/androidTest-results/connected/debug
adb shell wm size 1200x1800
adb shell wm density 160
adb logcat -c
status=0
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$classes" --stacktrace || status=$?
if [[ -d app/build/outputs/androidTest-results/connected/debug ]]; then cp -a app/build/outputs/androidTest-results/connected/debug "$evidence/native"; fi
[[ "$status" == 0 ]]
python3 - <<'PY'
import sys,json
sys.path.insert(0,'scripts')
from release_policy import load_approved,verify_native
scope,_=load_approved();verify_native('app/build/patch1761/native',scope['required_methods'])
PY
# Independent app-process profile isolation, without a live Tor endpoint.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for phase in seed verify; do
  adb shell am instrument -w -e class uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserIsolationTest#primaryAndHiddenProfilesKeepCookiesIndependentAndClearOnlyHiddenData -e hiddenProfilePhase "$phase" uk.co.traynor.privategallery.hiddenprototype.test/androidx.test.runner.AndroidJUnitRunner | tee "$evidence/browser-process-$phase.txt"
  grep -Fq 'OK (1 test)' "$evidence/browser-process-$phase.txt"
  if [[ "$phase" == seed ]]; then adb shell am force-stop uk.co.traynor.privategallery.hiddenprototype; fi
done
# Fresh disposable emulator only; fixture owns synthetic credentials/media.
bash scripts/verify_synthetic_upgrade.sh
