#!/usr/bin/env bash
set -euo pipefail
mkdir -p preview/artifacts/screenshots
trap 'adb logcat -d -t 2000 > preview/artifacts/logcat.txt || true' EXIT
python3 preview/tests/wait_for_android.py --output preview/artifacts/emulator-preflight.json
adb install -r preview/app/build/outputs/apk/debug/app-debug.apk
adb install -r preview/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
# Package Manager health was established before either installation.
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell cmd package resolve-activity --brief uk.co.traynor.novaos.preview > preview/artifacts/activity-resolution.txt
adb shell am get-current-user > preview/artifacts/current-user.txt
adb logcat -c
timeout 600s adb shell am instrument -w -r -e class uk.co.traynor.novaos.preview.PreviewAcceptanceTest uk.co.traynor.novaos.preview.test/androidx.test.runner.AndroidJUnitRunner | tee preview/artifacts/android-tests.txt
python3 - <<'PY'
import pathlib,re
s=pathlib.Path('preview/artifacts/android-tests.txt').read_text()
assert re.search(r'OK \(8 tests\)',s),s[-10000:]
assert 'FAILURES!!!' not in s and 'INSTRUMENTATION_FAILED' not in s
PY
for name in home-cover home-inner home-landscape drawer-large-text; do
 adb exec-out run-as uk.co.traynor.novaos.preview cat "files/screenshots/$name.png" > "preview/artifacts/screenshots/$name.png"
 test -s "preview/artifacts/screenshots/$name.png"
done
adb logcat -d -t 1500 > preview/artifacts/logcat.txt
