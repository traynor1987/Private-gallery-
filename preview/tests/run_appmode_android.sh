#!/usr/bin/env bash
set -euo pipefail
mkdir -p preview/artifacts
# Only synthetic pages and media are exercised. Never run account login in CI.
collect_receipts(){
 adb logcat -d > preview/artifacts/appmode-logcat.txt || true
 for kind in quick widgets; do
  for name in cover inner landscape; do
   if [ "$kind" = quick ]; then source="files/polish-screenshots/quick-$name.png"; else source="files/widget-screenshots/widgets-$name.png"; fi
   if adb shell "run-as uk.co.traynor.novaos.preview test -f $source"; then adb exec-out run-as uk.co.traynor.novaos.preview cat "$source" > "preview/artifacts/$kind-$name.png"; fi
  done
 done
}
trap collect_receipts EXIT
python3 preview/tests/wait_for_android.py --output preview/artifacts/emulator-preflight.json
# Pinned prior preview, same development certificate; exercise a real in-place update.
python3 - <<'PYUPGRADE'
import urllib.request,hashlib,pathlib
url='https://github.com/traynor1987/Private-gallery-/releases/download/novaos-home2-store-preview-0.6.1/NovaOS-Home2-Store-Preview.apk'
with urllib.request.urlopen(url,timeout=60) as r:raw=r.read(70_000_000)
assert hashlib.sha256(raw).hexdigest()=='c78729054b1cda2b319d1ac63ebcfe760f2eb16013a538e820c3982c98d7409a'
pathlib.Path('/tmp/nova-preview-05.apk').write_bytes(raw)
PYUPGRADE
adb install /tmp/nova-preview-05.apk
adb shell run-as uk.co.traynor.novaos.preview mkdir -p files
adb shell "run-as uk.co.traynor.novaos.preview sh -c 'echo nova06-upgrade-probe > files/nova06-upgrade-probe'"
python3 preview/tests/make_upgrade_fixture.py
adb shell run-as uk.co.traynor.novaos.preview mkdir -p shared_prefs
adb push /tmp/nova05-upgrade-prefs.xml /data/local/tmp/nova05-upgrade-prefs.xml
adb shell "run-as uk.co.traynor.novaos.preview cp /data/local/tmp/nova05-upgrade-prefs.xml shared_prefs/nova_preview_v1.xml"
adb push /tmp/nova06-upgrade-mind.xml /data/local/tmp/nova06-upgrade-mind.xml
adb shell "run-as uk.co.traynor.novaos.preview cp /data/local/tmp/nova06-upgrade-mind.xml shared_prefs/nova_mind_v1.xml"
adb push /tmp/nova06-upgrade-widgets.xml /data/local/tmp/nova06-upgrade-widgets.xml
adb shell "run-as uk.co.traynor.novaos.preview cp /data/local/tmp/nova06-upgrade-widgets.xml shared_prefs/nova_widgets_v1.xml"
adb install -r preview/app/build/outputs/apk/debug/app-debug.apk
adb exec-out run-as uk.co.traynor.novaos.preview cat files/nova06-upgrade-probe > preview/artifacts/upgrade-data-probe.txt
python3 - <<'PYPROBE'
import pathlib
assert pathlib.Path('preview/artifacts/upgrade-data-probe.txt').read_text().strip()=='nova06-upgrade-probe'
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
tests='uk.co.traynor.novaos.preview.PolishAcceptanceTest#ownPackageMediaSessionControlsAreRealAndAmbiguityFailsClosed,uk.co.traynor.novaos.preview.MediaContinuityAcceptanceTest'
timeout 420s adb shell am instrument -w -r -e class "$tests" uk.co.traynor.novaos.preview.test/androidx.test.runner.AndroidJUnitRunner | tee preview/artifacts/appmode-tests.txt
python3 - <<'PY'
import pathlib,re
s=pathlib.Path('preview/artifacts/appmode-tests.txt').read_text()
assert re.search(r'OK \(4 tests\)',s) and 'FAILURES!!!' not in s and 'INSTRUMENTATION_FAILED' not in s,s[-12000:]
PY
