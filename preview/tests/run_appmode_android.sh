#!/usr/bin/env bash
set -euo pipefail
mkdir -p preview/artifacts
# Only synthetic pages and media are exercised. Never run account login in CI.
trap 'adb logcat -d -t 1500 > preview/artifacts/appmode-logcat.txt || true' EXIT
python3 preview/tests/wait_for_android.py --output preview/artifacts/emulator-preflight.json
adb install -r preview/app/build/outputs/apk/debug/app-debug.apk
adb install -r preview/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
tests='uk.co.traynor.novaos.preview.AppModeAcceptanceTest,uk.co.traynor.novaos.preview.PreviewAcceptanceTest#primaryV1SettingsAndHomeContinuityUseSameWebView,uk.co.traynor.novaos.preview.PreviewAcceptanceTest#storeSearchAndConfirmedInstallWork,uk.co.traynor.novaos.preview.PreviewAcceptanceTest#installIsIdempotentProfileLocalAndPersistent,uk.co.traynor.novaos.preview.PreviewAcceptanceTest#uninstallCleansHomeDockFolderAndDrawer,uk.co.traynor.novaos.preview.PreviewAcceptanceTest#foldViewportLayoutsAndLargeTextRender'
timeout 420s adb shell am instrument -w -r -e class "$tests" uk.co.traynor.novaos.preview.test/androidx.test.runner.AndroidJUnitRunner | tee preview/artifacts/appmode-tests.txt
python3 - <<'PY'
import pathlib,re
s=pathlib.Path('preview/artifacts/appmode-tests.txt').read_text()
assert re.search(r'OK \(7 tests\)',s) and 'FAILURES!!!' not in s and 'INSTRUMENTATION_FAILED' not in s,s[-12000:]
PY
for name in home-cover home-inner home-landscape drawer-large-text; do
 adb exec-out run-as uk.co.traynor.novaos.preview cat "files/screenshots/$name.png" > "preview/artifacts/$name.png"
done
