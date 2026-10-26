#!/usr/bin/env bash
set -euo pipefail
platform=${1:?api36 or api37 required}
[[ "$GITHUB_ACTIONS" == true && "$platform" =~ ^api(36|37)$ ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == "${platform#api}" ]]
evidence="app/build/tor1761-diagnostic/$platform"
mkdir -p "$evidence"
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
    '-Pandroid.testInstrumentationRunnerArguments.class=uk.co.traynor.privategallery.core.browser.hidden.HiddenTorBootstrapTest' \
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
reports=list(root.rglob('*.xml'));summary={'diagnostic_only':True,'release_acceptance':False,'source':(out/'source-sha.txt').read_text().strip(),'reports':[]}
for i,p in enumerate(reports):
 shutil.copy2(p,out/('native-'+str(i)+'.xml'))
 cases=list(E.parse(p).getroot().iter('testcase'))
 summary['reports'].extend({'class':x.get('classname'),'name':x.get('name'),'failed':x.find('failure') is not None,'error':x.find('error') is not None,'skipped':x.find('skipped') is not None} for x in cases)
for p in root.rglob('logcat-*HiddenTorBootstrapTest-*.txt'):
 raw=p.read_bytes();lines=raw.decode(errors='replace').splitlines()
 keep=[s for s in lines if 'System.out: Tor numeric bootstrap phase:' in s or 'System.out: Controlled live Tor observations:' in s or 'System.out: Tor control observation:' in s]
 (out/'numeric-observations.txt').write_text('\n'.join(keep)+'\n')
 summary['original_log_sha256']=hashlib.sha256(raw).hexdigest()
(out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
if (out/'original-command-status.txt').read_text().strip()=='0':
 assert len(summary['reports'])==1
 case=summary['reports'][0]
 assert case['class']=='uk.co.traynor.privategallery.core.browser.hidden.HiddenTorBootstrapTest'
 assert case['name']=='packagedTorBootstrapsInPrivateProcessAndStopsWhenReleased'
 assert not case['failed'] and not case['error'] and not case['skipped']
print(json.dumps(summary,indent=2))
PY
printf '%s\n' "$archive_status" > "$evidence/original-report-status.txt"
# Raw console output may include routing details; diagnostic artifacts contain the filtered receipt.
rm -f "$evidence/gradle-raw.txt"
if [[ "$status" != 0 ]]; then exit "$status"; fi
if [[ "$archive_status" != 0 ]]; then exit "$archive_status"; fi
[[ "$before" == 0 && "$after" == 0 ]]
