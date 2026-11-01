#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REF:-}" == refs/heads/release/1.7.9-evidence-locker ]]
[[ "${GITHUB_SHA:-}" =~ ^[a-f0-9]{40}$ ]]
[[ "${EVIDENCE179_ONION_HTTPS_URL:-}" =~ ^https://[a-z2-7]{56}\.onion$ ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 37 ]]
root=app/build/evidence179
mkdir -p "$root/android-results"
rm -f "$root/route-proof.json" "$root/focused-gates.json"
[[ ! -e "$root/source-sha.txt" ]]
printf '%s\n' "$GITHUB_SHA" > "$root/source-sha.txt"
trap 'python3 scripts/archive_evidence179_routes.py; adb logcat -d -b crash > app/build/evidence179/emulator-crash-final.txt || true' EXIT
python3 scripts/debug_apk_identity1762.py app/build/outputs/apk/debug/app-debug.apk
adb push "$RUNNER_TEMP/private-gallery-vpn1762/client.conf" /data/local/tmp/hidden-vpn1762.conf
adb shell chmod 600 /data/local/tmp/hidden-vpn1762.conf
python3 scripts/ci_onion_probe.py --verify preflight
selection=$(python3 - <<'PY'
import json
m=json.load(open('scripts/evidence179_cases.json'))
print(','.join(x['classname']+'#'+x['name'] for x in m if not x['classname'].endswith('.EvidenceOwnedRoute179Test')))
PY
)
run_selection() {
 local identity=$1 label=$2 gradle_status=0
 # Keep device execution and evidence collection distinct for every caller/route.
 selection_original_status=0
 selection_collection_status=0
 selection_clock_status=0
 # Clear only disposable generated instrumentation outputs, so stale XML/receipts cannot satisfy accounting.
 rm -rf app/build/outputs/androidTest-results/connected/debug app/build/outputs/connected_android_test_additional_output
 local command=(timeout --signal=TERM --kill-after=30s 15m ./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$identity"
  -Pandroid.testInstrumentationRunnerArguments.hiddenVpnConfig=/data/local/tmp/hidden-vpn1762.conf
  "-Pandroid.testInstrumentationRunnerArguments.evidence179OnionHttpsUrl=$EVIDENCE179_ONION_HTTPS_URL"
  "-Pandroid.testInstrumentationRunnerArguments.evidence179SourceSha=$GITHUB_SHA" --stacktrace)
 if [[ "$label" =~ ^(vpn_revoke|vpn_loss|tor_revoke|tor_death)$ ]]; then
  local wrapper_status=0
  python3 scripts/evidence179_clock.py --case "$label" --source-sha "$GITHUB_SHA" --output "$root/clock/$label" -- "${command[@]}" || wrapper_status=$?
  gradle_status=2;selection_clock_status=1
  if [[ -f "$root/clock/$label/original-exit-status.txt" ]]; then gradle_status=$(cat "$root/clock/$label/original-exit-status.txt"); fi
  if [[ -f "$root/clock/$label/clock-exit-status.txt" ]]; then selection_clock_status=$(cat "$root/clock/$label/clock-exit-status.txt"); fi
  [[ "$gradle_status" =~ ^-?[0-9]+$ && "$selection_clock_status" =~ ^[01]$ ]] || return 2
  # Wrapper/infrastructure failure must never be masked by zero status files.
  if [[ "$wrapper_status" != 0 && "$gradle_status" == 0 && "$selection_clock_status" == 0 ]]; then selection_clock_status=1; fi
 else
  "${command[@]}" || gradle_status=$?
 fi
 selection_original_status=$gradle_status
 if [[ "$gradle_status" == 124 || "$gradle_status" == 137 ]]; then
  # A hung invocation fails closed before the job deadline, preserving original test artifacts.
  timeout 15s adb logcat -d -b main -b system -b crash > "$root/timeout-$label-logcat.txt" 2>&1 || true
  timeout 15s adb shell dumpsys activity lastanr > "$root/timeout-$label-lastanr.txt" 2>&1 || true
 fi
 python3 - "$label" "$gradle_status" <<'PY' || selection_collection_status=$?
import pathlib,shutil,sys
source=pathlib.Path('app/build/outputs/androidTest-results/connected/debug')
dest=pathlib.Path('app/build/evidence179/android-results')/sys.argv[1]
if source.is_dir() and list(source.rglob('*.xml')):shutil.copytree(source,dest)
elif sys.argv[2]=='0':raise RuntimeError('Missing original XML')
additional=pathlib.Path('app/build/outputs/connected_android_test_additional_output')
if additional.is_dir():shutil.copytree(additional,pathlib.Path('app/build/evidence179/android-output')/sys.argv[1])
for path in list(additional.rglob('evidence179-route-*.json'))+list(additional.rglob('evidence179-clock-lifecycle.json')):
 target=pathlib.Path('app/build/evidence179')/path.name
 assert not target.exists(),'Duplicate route output'
 shutil.copyfile(path,target)
PY
 if [[ "$gradle_status" != 0 ]]; then return "$gradle_status"; fi
 if [[ "$selection_collection_status" != 0 ]]; then return "$selection_collection_status"; fi
 return "$selection_clock_status"
}
# BEGIN NONROUTE DIAGNOSTIC CONTINUATION
selection_status=0
run_selection "$selection" focused || selection_status=$?
nonroute_status=$selection_original_status
nonroute_collection_status=$selection_collection_status
printf '%s\n' "$nonroute_status" > "$root/nonroute-exit-status.txt"
printf '%s\n' "$nonroute_collection_status" > "$root/nonroute-collection-exit-status.txt"
# A partial archive must stop before continuation validation, even when its XML is complete.
[[ "$nonroute_collection_status" == 0 ]] || exit "$selection_status"
if [[ "$nonroute_status" != 0 ]]; then
 # Only a complete, normally failed test selection may continue independent observations.
 # Missing/truncated/crashed/timed-out runs stop; this is not a passing-evidence validator.
 [[ "$nonroute_status" == 1 ]] || exit "$nonroute_status"
 python3 - "$root/android-results/focused" scripts/evidence179_cases.json <<'PY_COMPLETE'
import json,pathlib,sys,xml.etree.ElementTree as ET
manifest=json.loads(pathlib.Path(sys.argv[2]).read_text())
expected=[(x['classname'],x['name']) for x in manifest if not x['classname'].endswith('.EvidenceOwnedRoute179Test')]
assert expected and len(expected)==len(set(expected)),'Invalid non-route scope'
cases=[c for path in pathlib.Path(sys.argv[1]).rglob('*.xml') for c in ET.parse(path).iter('testcase')]
actual=[(c.get('classname'),c.get('name')) for c in cases]
assert len(actual)==len(expected) and len(actual)==len(set(actual)) and set(actual)==set(expected),'Incomplete non-route selection'
assert not any(c.find('skipped') is not None for c in cases),'Skipped required non-route test'
assert any(c.find('failure') is not None or c.find('error') is not None for c in cases),'Unexplained original non-route failure'
print('Complete failed non-route selection: independent routes may run; final runtime gates remain blocked.')
PY_COMPLETE
fi
# END NONROUTE DIAGNOSTIC CONTINUATION
python3 scripts/ci_onion_probe.py --verify preflight
# Sequential host barriers separate actual VPN packets from Tor sink-negative windows.
while IFS=$'\t' read -r case method; do
 python3 - "$case" start <<'PY'
import json,os,sys,time
with open('app/build/evidence179/host-phases.jsonl','a') as out:out.write(json.dumps(dict(case=sys.argv[1],kind=sys.argv[2],epoch=time.time(),source_sha=os.environ['GITHUB_SHA']))+'\n')
PY
 set +e
 run_selection "uk.co.traynor.privategallery.core.evidence.EvidenceOwnedRoute179Test#$method" "$case"
 original_status=$selection_original_status
 collection_status=$selection_collection_status
 clock_status=$selection_clock_status
 peer_close_status=0
 if [[ "$original_status" == 0 && "$collection_status" == 0 && "$clock_status" == 0 ]]; then python3 scripts/wait_evidence179_peer_close.py --case "$case" || peer_close_status=$?; fi
 set -e
 python3 - "$case" "$original_status" "$peer_close_status" "$collection_status" "$clock_status" <<'PY'
import json,os,sys,time
with open('app/build/evidence179/host-phases.jsonl','a') as out:out.write(json.dumps(dict(case=sys.argv[1],kind='end',epoch=time.time(),source_sha=os.environ['GITHUB_SHA'],original_exit_status=int(sys.argv[2]),peer_close_exit_status=int(sys.argv[3]),collection_exit_status=int(sys.argv[4]),clock_exit_status=int(sys.argv[5])))+'\n')
PY
 [[ "$original_status" == 0 ]] || exit "$original_status"
 [[ "$collection_status" == 0 ]] || exit "$collection_status"
 [[ "$clock_status" == 0 ]] || exit "$clock_status"
 [[ "$peer_close_status" == 0 ]] || exit "$peer_close_status"
 if [[ "$case" == vpn_loss ]]; then python3 scripts/drain_evidence179_vpn.py; fi
done < <(python3 - <<'PY'
from scripts.verify_evidence179_routes import CASES
for case,value in CASES.items():print(case+'\t'+value[0])
PY
)
python3 scripts/archive_evidence179_routes.py
# BEGIN NONROUTE FAILURE BARRIER
# All mandatory non-route tests must pass before strict route/full-case validation or final gates.
[[ "$nonroute_status" == 0 ]] || exit "$nonroute_status"
# END NONROUTE FAILURE BARRIER
python3 scripts/verify_evidence179_routes.py --android-results "$root/android-results" --vpn-root "$root/vpn" --onion-root "$root/onion" --results-root "$root" --source-sha "$GITHUB_SHA" --cases scripts/evidence179_cases.json
# This is only the focused implementation proof. Root release controls still require upgrade/signing gates.
python3 - <<'PY'
import json,os
from pathlib import Path
root=Path('app/build/evidence179')
proof=json.loads((root/'route-proof.json').read_text());assert proof['decision']=='PASS'
(root/'focused-gates.json').write_text(json.dumps(dict(source_sha=os.environ['GITHUB_SHA'],decision='PASS',original_exit_status=0,network={k:'PASS' for k in ('owned_vpn','native_tor','dns_and_packet_observation','revocation')}),indent=2)+'\n')
PY
