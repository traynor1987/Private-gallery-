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
 # Clear only disposable generated instrumentation outputs, so stale XML/receipts cannot satisfy accounting.
 rm -rf app/build/outputs/androidTest-results/connected/debug app/build/outputs/connected_android_test_additional_output
 ./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$identity" \
  -Pandroid.testInstrumentationRunnerArguments.hiddenVpnConfig=/data/local/tmp/hidden-vpn1762.conf \
  "-Pandroid.testInstrumentationRunnerArguments.evidence179OnionHttpsUrl=$EVIDENCE179_ONION_HTTPS_URL" \
  "-Pandroid.testInstrumentationRunnerArguments.evidence179SourceSha=$GITHUB_SHA" --stacktrace || gradle_status=$?
 python3 - "$label" "$gradle_status" <<'PY' || return $?
import pathlib,shutil,sys
source=pathlib.Path('app/build/outputs/androidTest-results/connected/debug')
dest=pathlib.Path('app/build/evidence179/android-results')/sys.argv[1]
if source.is_dir() and list(source.rglob('*.xml')):shutil.copytree(source,dest)
elif sys.argv[2]=='0':raise RuntimeError('Missing original XML')
additional=pathlib.Path('app/build/outputs/connected_android_test_additional_output')
if additional.is_dir():shutil.copytree(additional,pathlib.Path('app/build/evidence179/android-output')/sys.argv[1])
for path in additional.rglob('evidence179-route-*.json'):
 target=pathlib.Path('app/build/evidence179')/path.name
 assert not target.exists(),'Duplicate route output'
 shutil.copyfile(path,target)
PY
 return "$gradle_status"
}
run_selection "$selection" focused
python3 scripts/ci_onion_probe.py --verify preflight
# Sequential host barriers separate actual VPN packets from Tor sink-negative windows.
while IFS=$'\t' read -r case method; do
 python3 - "$case" start <<'PY'
import json,os,sys,time
with open('app/build/evidence179/host-phases.jsonl','a') as out:out.write(json.dumps(dict(case=sys.argv[1],kind=sys.argv[2],epoch=time.time(),source_sha=os.environ['GITHUB_SHA']))+'\n')
PY
 set +e
 run_selection "uk.co.traynor.privategallery.core.evidence.EvidenceOwnedRoute179Test#$method" "$case"
 original_status=$?
 peer_close_status=0
 if [[ "$original_status" == 0 ]]; then python3 scripts/wait_evidence179_peer_close.py --case "$case" || peer_close_status=$?; fi
 set -e
 python3 - "$case" "$original_status" "$peer_close_status" <<'PY'
import json,os,sys,time
with open('app/build/evidence179/host-phases.jsonl','a') as out:out.write(json.dumps(dict(case=sys.argv[1],kind='end',epoch=time.time(),source_sha=os.environ['GITHUB_SHA'],original_exit_status=int(sys.argv[2]),peer_close_exit_status=int(sys.argv[3])))+'\n')
PY
 [[ "$original_status" == 0 ]] || exit "$original_status"
 [[ "$peer_close_status" == 0 ]] || exit "$peer_close_status"
 if [[ "$case" == vpn_loss ]]; then python3 scripts/drain_evidence179_vpn.py; fi
done < <(python3 - <<'PY'
from scripts.verify_evidence179_routes import CASES
for case,value in CASES.items():print(case+'\t'+value[0])
PY
)
python3 scripts/archive_evidence179_routes.py
python3 scripts/verify_evidence179_routes.py --android-results "$root/android-results" --vpn-root "$root/vpn" --onion-root "$root/onion" --results-root "$root" --source-sha "$GITHUB_SHA" --cases scripts/evidence179_cases.json
# This is only the focused implementation proof. Root release controls still require upgrade/signing gates.
python3 - <<'PY'
import json,os
from pathlib import Path
root=Path('app/build/evidence179')
proof=json.loads((root/'route-proof.json').read_text());assert proof['decision']=='PASS'
(root/'focused-gates.json').write_text(json.dumps(dict(source_sha=os.environ['GITHUB_SHA'],decision='PASS',original_exit_status=0,network={k:'PASS' for k in ('owned_vpn','native_tor','dns_and_packet_observation','revocation')}),indent=2)+'\n')
PY
