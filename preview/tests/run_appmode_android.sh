#!/usr/bin/env bash
set -euo pipefail
mkdir -p preview/artifacts
# Only synthetic pages and media are exercised. Never run account login in CI.
trap 'adb logcat -d -t 1500 > preview/artifacts/appmode-logcat.txt || true' EXIT
python3 preview/tests/wait_for_android.py --output preview/artifacts/emulator-preflight.json
# Pinned prior preview, same development certificate; exercise a real in-place update.
python3 - <<'PYUPGRADE'
import urllib.request,hashlib,pathlib
url='https://github.com/traynor1987/Private-gallery-/releases/download/novaos-home2-store-preview-0.3/NovaOS-Home2-Store-Preview.apk'
with urllib.request.urlopen(url,timeout=60) as r:raw=r.read(60_000_000)
assert hashlib.sha256(raw).hexdigest()=='a1b0c360ee77a6f1cf199bde0433b3b1e1adcad229476d6f999c07e61b3b5327'
pathlib.Path('/tmp/nova-preview-03.apk').write_bytes(raw)
PYUPGRADE
adb install /tmp/nova-preview-03.apk
adb shell run-as uk.co.traynor.novaos.preview mkdir -p files
adb shell "run-as uk.co.traynor.novaos.preview sh -c 'echo nova04-upgrade-probe > files/nova04-upgrade-probe'"
adb install -r preview/app/build/outputs/apk/debug/app-debug.apk
adb exec-out run-as uk.co.traynor.novaos.preview cat files/nova04-upgrade-probe > preview/artifacts/upgrade-data-probe.txt
python3 - <<'PYPROBE'
import pathlib
assert pathlib.Path('preview/artifacts/upgrade-data-probe.txt').read_text().strip()=='nova04-upgrade-probe'
PYPROBE
adb install -r preview/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
tests='uk.co.traynor.novaos.preview.MultitaskingAcceptanceTest,uk.co.traynor.novaos.preview.AppModeAcceptanceTest#appModeHomeRetainsAttachedUserStartedAudioAndActivityLifecycle,uk.co.traynor.novaos.preview.PreviewAcceptanceTest#primaryV1SettingsAndHomeContinuityUseSameWebView,uk.co.traynor.novaos.preview.PreviewAcceptanceTest#foldViewportLayoutsAndLargeTextRender'
timeout 420s adb shell am instrument -w -r -e class "$tests" uk.co.traynor.novaos.preview.test/androidx.test.runner.AndroidJUnitRunner | tee preview/artifacts/appmode-tests.txt
python3 - <<'PY'
import pathlib,re
s=pathlib.Path('preview/artifacts/appmode-tests.txt').read_text()
assert re.search(r'OK \(7 tests\)',s) and 'FAILURES!!!' not in s and 'INSTRUMENTATION_FAILED' not in s,s[-12000:]
PY

for name in home-cover home-inner home-landscape drawer-large-text; do
 adb exec-out run-as uk.co.traynor.novaos.preview cat "files/screenshots/$name.png" > "preview/artifacts/$name.png"
done
