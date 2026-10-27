#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REF:-}" == refs/heads/release/1.7.6.2-ui-browser-polish ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
out=app/build/picker1762
mkdir -p "$out"
git rev-parse HEAD > "$out/source-sha.txt"
bash scripts/verify_android17_device.sh "$out"
sha256sum app/build/outputs/apk/debug/app-debug.apk > "$out/apk-sha256.txt"
archive() { status=$?; trap - EXIT; set +e; printf '%s\n' "$status" > "$out/exit-status.txt"; adb logcat -d > "$out/logcat.txt" 2>&1; exit "$status"; }
trap archive EXIT
adb shell wm size 1200x1800
adb shell wm density 160
./gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=uk.co.traynor.privategallery.ui.BrowserConsent1752Test#extensionBasedUploadUsesThemedVaultPickerWithoutPrematureEgress' --stacktrace
python3 - <<'PY'
import xml.etree.ElementTree as E
from pathlib import Path
cases=[]
for p in Path('app/build/outputs/androidTest-results/connected/debug').rglob('*.xml'):
 cases.extend(E.parse(p).getroot().iter('testcase'))
expected=('uk.co.traynor.privategallery.ui.BrowserConsent1752Test','extensionBasedUploadUsesThemedVaultPickerWithoutPrematureEgress')
assert len(cases)==1 and (cases[0].get('classname'),cases[0].get('name'))==expected
assert all(cases[0].find(outcome) is None for outcome in ('failure','error','skipped'))
print('PASS: named picker interaction executed with no failures or skips')
PY
