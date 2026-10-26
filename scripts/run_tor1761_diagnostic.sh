#!/usr/bin/env bash
set -euo pipefail
platform=${1:?api37 required}
[[ "$GITHUB_ACTIONS" == true && "$platform" == api37 ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == "${platform#api}" ]]
evidence="app/build/tor1761-diagnostic/$platform"
mkdir -p "$evidence"
{ printf 'boot_completed='; adb shell getprop sys.boot_completed; printf 'airplane_mode='; adb shell settings get global airplane_mode_on; printf 'default_route_count='; adb shell ip route | awk '/^default / {n++} END {print n+0}'; } > "$evidence/emulator-network-state.txt"
git rev-parse HEAD > "$evidence/source-sha.txt"
adb shell getprop ro.build.version.sdk > "$evidence/sdk.txt"
sha256sum app/build/outputs/apk/debug/app-debug.apk > "$evidence/debug-apk-sha256.txt"
rm -rf app/build/outputs/androidTest-results/connected/debug
before=0
python3 scripts/ci_onion_probe.py --verify diagnostic-before > "$evidence/fixture-before-result.txt" 2>&1 || before=$?
printf '%s\n' "$before" > "$evidence/fixture-before-status.txt"
fixture="$RUNNER_TEMP/private-gallery-onion-probe"
for name in fixture-health.txt client-readiness.txt backend-requests.txt; do
  if [[ -f "$fixture/$name" ]]; then cp "$fixture/$name" "$evidence/before-$name"; fi
done
status=125
if [[ "$before" == 0 ]]; then
  status=0
  ./gradlew connectedDebugAndroidTest \
    '-Pandroid.testInstrumentationRunnerArguments.class=uk.co.traynor.privategallery.core.browser.hidden.HiddenTorBootstrapTest,uk.co.traynor.privategallery.core.browser.hidden.HiddenRuntimeFailureTest' \
    "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeUrl=${HIDDEN_ONION_PROBE_URL:?}" \
    "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeExpected=${HIDDEN_ONION_PROBE_EXPECTED:?}" \
    '-Pandroid.testInstrumentationRunnerArguments.hiddenTorStageDiagnostics=true' \
    '-Pandroid.testInstrumentationRunnerArguments.hiddenTorControlDiagnostics=true' \
    --stacktrace > "$evidence/gradle-raw.txt" 2>&1 || status=$?
fi
printf '%s\n' "$status" > "$evidence/original-command-status.txt"
after=0
python3 scripts/ci_onion_probe.py --verify diagnostic-after > "$evidence/fixture-after-result.txt" 2>&1 || after=$?
printf '%s\n' "$after" > "$evidence/fixture-after-status.txt"
for name in fixture-health.txt client-readiness.txt backend-requests.txt; do
  if [[ -f "$fixture/$name" ]]; then cp "$fixture/$name" "$evidence/after-$name"; fi
done
# Archive source-bound original XML and only numeric test observations, not URLs/relay identities.
archive_status=0
python3 - "$evidence" <<'PY' || archive_status=$?
from pathlib import Path
import sys,shutil,json,hashlib,xml.etree.ElementTree as E
out=Path(sys.argv[1]);root=Path('app/build/outputs/androidTest-results/connected/debug')
(out/'numeric-observations.txt').write_text('')
reports=list(root.rglob('*.xml'));summary={'diagnostic_only':True,'release_acceptance':False,'source':(out/'source-sha.txt').read_text().strip(),'reports':[]}
for i,p in enumerate(reports):
 shutil.copy2(p,out/('native-'+str(i)+'.xml'))
 cases=list(E.parse(p).getroot().iter('testcase'))
 summary['reports'].extend({'class':x.get('classname'),'name':x.get('name'),'failed':x.find('failure') is not None,'error':x.find('error') is not None,'skipped':x.find('skipped') is not None} for x in cases)
for p in root.rglob('logcat-*.txt'):
 raw=p.read_bytes();lines=raw.decode(errors='replace').splitlines()
 keep=[s for s in lines if 'System.out: Tor numeric bootstrap phase:' in s or 'System.out: Controlled live Tor observations:' in s or 'System.out: Tor control observation:' in s or 'System.out: Runtime failure control:' in s or 'System.out: Runtime failure category:' in s or 'System.out: Runtime failure postconditions:' in s]
 with (out/'numeric-observations.txt').open('a') as stream: stream.write('\n'.join(keep)+'\n')
 summary.setdefault('original_log_sha256',{})[p.name]=hashlib.sha256(raw).hexdigest()
(out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
if (out/'original-command-status.txt').read_text().strip()=='0':
 expected={
  ('uk.co.traynor.privategallery.core.browser.hidden.HiddenTorBootstrapTest','packagedTorBootstrapsInPrivateProcessAndStopsWhenReleased'),
  ('uk.co.traynor.privategallery.core.browser.hidden.HiddenRuntimeFailureTest','stalledConnectTimeoutDisconnectsRuntimeAndClosesSocket'),
  ('uk.co.traynor.privategallery.core.browser.hidden.HiddenRuntimeFailureTest','sessionRevocationDuringStalledConnectCancelsRuntimeAndClosesSocket')}
 assert len(summary['reports'])==3
 assert {(x['class'],x['name']) for x in summary['reports']}==expected
 assert all(not x['failed'] and not x['error'] and not x['skipped'] for x in summary['reports'])
 observations=(out/'numeric-observations.txt').read_text()
 assert 'phase=subscribed' in observations and 'readerAlive=false' in observations
 assert 'readerAlive=true' not in observations
print(json.dumps(summary,indent=2))
PY
printf '%s\n' "$archive_status" > "$evidence/original-report-status.txt"
# Raw console output may include routing details; diagnostic artifacts contain the filtered receipt.
rm -f "$evidence/gradle-raw.txt"
if [[ "$status" != 0 ]]; then exit "$status"; fi
if [[ "$archive_status" != 0 ]]; then exit "$archive_status"; fi
[[ "$before" == 0 && "$after" == 0 ]]
