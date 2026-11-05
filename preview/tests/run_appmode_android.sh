#!/usr/bin/env bash
set -euo pipefail
mkdir -p preview/artifacts
# Only synthetic pages and media are exercised. Never run account login in CI.
trap 'adb logcat -d -t 1500 > preview/artifacts/appmode-logcat.txt || true' EXIT
python3 preview/tests/wait_for_android.py --output preview/artifacts/emulator-preflight.json
# Pinned prior preview, same development certificate; exercise a real in-place update.
python3 - <<'PYUPGRADE'
import urllib.request,hashlib,pathlib
url='https://github.com/traynor1987/Private-gallery-/releases/download/novaos-home2-store-preview-0.4/NovaOS-Home2-Store-Preview.apk'
with urllib.request.urlopen(url,timeout=60) as r:raw=r.read(60_000_000)
assert hashlib.sha256(raw).hexdigest()=='f96c952ce6d09cc2c714f24ab7c7a2f910d76bce52acd66dad11d8c22be43983'
pathlib.Path('/tmp/nova-preview-04.apk').write_bytes(raw)
PYUPGRADE
adb install /tmp/nova-preview-04.apk
adb shell run-as uk.co.traynor.novaos.preview mkdir -p files
adb shell "run-as uk.co.traynor.novaos.preview sh -c 'echo nova05-upgrade-probe > files/nova05-upgrade-probe'"
python3 preview/tests/make_upgrade_fixture.py
adb shell run-as uk.co.traynor.novaos.preview mkdir -p shared_prefs
adb push /tmp/nova05-upgrade-prefs.xml /data/local/tmp/nova05-upgrade-prefs.xml
adb shell "run-as uk.co.traynor.novaos.preview cp /data/local/tmp/nova05-upgrade-prefs.xml shared_prefs/nova_preview_v1.xml"
adb install -r preview/app/build/outputs/apk/debug/app-debug.apk
adb exec-out run-as uk.co.traynor.novaos.preview cat files/nova05-upgrade-probe > preview/artifacts/upgrade-data-probe.txt
python3 - <<'PYPROBE'
import pathlib
assert pathlib.Path('preview/artifacts/upgrade-data-probe.txt').read_text().strip()=='nova05-upgrade-probe'
PYPROBE
adb install -r preview/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
timeout 90s adb shell am instrument -w -r -e class uk.co.traynor.novaos.preview.UpgradeAcceptanceTest uk.co.traynor.novaos.preview.test/androidx.test.runner.AndroidJUnitRunner | tee preview/artifacts/upgrade-tests.txt
python3 - <<'PYUPGRADERESULT'
import pathlib
s=pathlib.Path('preview/artifacts/upgrade-tests.txt').read_text()
assert 'OK (1 test)' in s and 'FAILURES!!!' not in s and 'INSTRUMENTATION_FAILED' not in s,s[-12000:]
PYUPGRADERESULT
tests='uk.co.traynor.novaos.preview.ExperienceAcceptanceTest,uk.co.traynor.novaos.preview.PreviewAcceptanceTest#packageAndCatalogueArePreviewOnly,uk.co.traynor.novaos.preview.MultitaskingAcceptanceTest,uk.co.traynor.novaos.preview.AppModeAcceptanceTest#appModeHomeRetainsAttachedUserStartedAudioAndActivityLifecycle,uk.co.traynor.novaos.preview.PreviewAcceptanceTest#primaryV1SettingsAndHomeContinuityUseSameWebView,uk.co.traynor.novaos.preview.PreviewAcceptanceTest#foldViewportLayoutsAndLargeTextRender'
timeout 420s adb shell am instrument -w -r -e class "$tests" uk.co.traynor.novaos.preview.test/androidx.test.runner.AndroidJUnitRunner | tee preview/artifacts/appmode-tests.txt
python3 - <<'PY'
import pathlib,re
s=pathlib.Path('preview/artifacts/appmode-tests.txt').read_text()
assert re.search(r'OK \(14 tests\)',s) and 'FAILURES!!!' not in s and 'INSTRUMENTATION_FAILED' not in s,s[-12000:]
PY

for name in home-cover home-inner home-landscape drawer-large-text; do
 adb exec-out run-as uk.co.traynor.novaos.preview cat "files/screenshots/$name.png" > "preview/artifacts/$name.png"
done
