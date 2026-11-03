#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REF:-}" == refs/heads/release/1.7.10-private-home ]]
[[ "$(git rev-parse HEAD)" == "${GITHUB_SHA:?}" ]]
root=app/build/nova17102
export NOVA17102_MATRIX=docs/release17102/ui-cases.json
if [[ "${NOVA17102_REPAIR:-0}" == 1 ]]; then export NOVA17102_MATRIX=docs/release17102/repair-cases.json; fi
if [[ "${NOVA17102_ENGINE:-0}" == 1 ]]; then export NOVA17102_MATRIX=docs/release17102/engine-cases.json; fi
if [[ "${NOVA17102_CLOSURE:-0}" == 1 ]]; then
 [[ "${NOVA17102_ENGINE:-0}" == 1 ]]
 export NOVA17102_MATRIX=docs/release17102/closure-cases.json
fi
if [[ "${NOVA17102_BETA:-0}" == 1 ]]; then
 [[ "${NOVA17102_ENGINE:-0}" == 1 ]]
 export NOVA17102_MATRIX=docs/release17102/beta-cases.json
fi
if [[ "${NOVA17103_POLISH:-0}" == 1 ]]; then
 [[ "${NOVA17102_ENGINE:-0}" == 1 ]]
 export NOVA17102_MATRIX=docs/release17103/ui-cases.json
fi
if [[ "${NOVA17103_CONTINUITY:-0}" == 1 ]]; then
 [[ "${NOVA17102_ENGINE:-0}" != 1 ]]
 export NOVA17102_MATRIX=docs/release17103/continuity-cases.json
fi
mkdir -p "$root"
git rev-parse HEAD > "$root/source-sha.txt"
bash scripts/home1710_emulator_preflight.sh
python3 scripts/debug_apk_identity1762.py app/build/outputs/apk/debug/app-debug.apk
selection=$(python3 - <<'PY'
import json,os
matrix=json.load(open(os.environ['NOVA17102_MATRIX']))
print(','.join(f'{cls}#{method}' for cls,methods in matrix.items() for method in methods) if (os.environ.get('NOVA17103_POLISH')=='1' or os.environ.get('NOVA17103_CONTINUITY')=='1') else ','.join(matrix))
PY
)
ui_status=0;ui_accounting_status=0
./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$selection" --stacktrace || ui_status=$?
python3 scripts/verify_selected_android1762.py app/build/outputs/androidTest-results/connected/debug "$selection" > "$root/ui-accounting.txt" || ui_accounting_status=$?
printf '%s\n' "$ui_status" > "$root/ui-original-exit-status.txt"
mkdir -p "$root/ui/outputs"
if [[ -d app/build/outputs/androidTest-results ]]; then cp -R app/build/outputs/androidTest-results "$root/ui/outputs/"; fi
if [[ -d app/build/outputs/connected_android_test_additional_output ]]; then cp -R app/build/outputs/connected_android_test_additional_output "$root/ui/outputs/"; fi
if [[ "${NOVA17102_ENGINE:-0}" == 1 ]]; then
 rm -rf app/build/outputs/androidTest-results/connected/debug app/build/outputs/connected_android_test_additional_output
 [[ "$ui_status" == 0 && "$ui_accounting_status" == 0 ]]
 printf '%s\n' "Selected UI matrix: $NOVA17102_MATRIX; selected presentation and popup ownership scope; no external Google OAuth or live route approval" > "$root/selection-scope.txt"
 printf '%s\n' 'PASS: selected focused UI evidence only; NO new route or signing approval' > "$root/runtime-result.txt"
 exit 0
fi
rm -rf app/build/outputs/androidTest-results/connected/debug app/build/outputs/connected_android_test_additional_output
adb push "$RUNNER_TEMP/private-gallery-vpn1762/client.conf" /data/local/tmp/hidden-vpn1762.conf
adb shell chmod 600 /data/local/tmp/hidden-vpn1762.conf
package=$(python3 scripts/debug_apk_identity1762.py app/build/outputs/apk/debug/app-debug.apk)
bash scripts/reset_native_browser_evidence1762.sh "$package"
run_route_tests() {
 ./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$1" \
  -Pandroid.testInstrumentationRunnerArguments.hiddenVpnConfig=/data/local/tmp/hidden-vpn1762.conf \
  -Pandroid.testInstrumentationRunnerArguments.hiddenVpnOrigin=http://fixture.invalid \
  "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeUrl=${HIDDEN_ONION_PROBE_URL:?}" \
  "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeExpected=${HIDDEN_ONION_PROBE_EXPECTED:?}" --stacktrace
}
collect_native_receipts() {
 for result in vpn-native-revocation1762-result.json vpn-network-loss1762-result.json native-tor1762-result.json; do
  mapfile -d '' matches < <(find app/build/outputs/connected_android_test_additional_output -type f -name "$result" -print0)
  [[ ${#matches[@]} == 1 ]]
  cp "${matches[0]}" "app/build/hidden-vpn1762/$result"
 done
}
baseline_status=0;baseline_accounting_status=0
if [[ "${NOVA17103_CONTINUITY:-0}" == 1 ]]; then
 # Preserve the shipped revocation check's complete observation window before a
 # later legitimate child emits new STUN. Never exempt later packets from its assertion.
 baseline_selection=$(python3 - <<'BASELINE'
import json
matrix=json.load(open('docs/release17102/route-cases.json'))
print(','.join(f'{cls}#{method}' for cls,methods in matrix.items() for method in methods))
BASELINE
 )
 run_route_tests "$baseline_selection" || baseline_status=$?
 python3 scripts/verify_selected_android1762.py app/build/outputs/androidTest-results/connected/debug "$baseline_selection" > "$root/baseline-route-accounting.txt" || baseline_accounting_status=$?
 printf '%s\n' "$baseline_status" > "$root/baseline-route-original-exit-status.txt"
 mkdir -p "$root/base-route/outputs" "$root/baseline-vpn"
 cp -R app/build/outputs/androidTest-results "$root/base-route/outputs/"
 cp -R app/build/outputs/connected_android_test_additional_output "$root/base-route/outputs/"
 collect_native_receipts
 sudo chmod a+r app/build/hidden-vpn1762/events.jsonl app/build/hidden-vpn1762/traffic.pcap
 cp app/build/hidden-vpn1762/events.jsonl app/build/hidden-vpn1762/traffic.pcap "$root/baseline-vpn/"
 cp app/build/hidden-vpn1762/{vpn-native-revocation1762-result.json,vpn-network-loss1762-result.json,native-tor1762-result.json} "$root/baseline-vpn/"
 sudo tcpdump -tttt -nn -vv -X -r "$root/baseline-vpn/traffic.pcap" > "$root/baseline-vpn/packets.txt" 2>&1
 python3 scripts/verify_hidden_vpn_fixture1762.py "$root/baseline-vpn"
 cp "$root/baseline-vpn/result.txt" app/build/hidden-vpn1762/result.txt
 python3 scripts/verify_native_tor1762.py "$root/baseline-vpn" "$RUNNER_TEMP/private-gallery-onion-probe" > "$root/tor-route-result.txt"
 [[ "$baseline_status" == 0 && "$baseline_accounting_status" == 0 ]]
 rm -rf app/build/outputs/androidTest-results/connected/debug app/build/outputs/connected_android_test_additional_output
fi
selection=$(python3 - <<'PY'
import json,os
matrix=json.load(open('docs/release17103/continuity-route-cases.json' if os.environ.get('NOVA17103_CONTINUITY')=='1' else 'docs/release17102/route-cases.json'))
if os.environ.get('NOVA17103_CONTINUITY')=='1':
 baseline=json.load(open('docs/release17102/route-cases.json'))
 matrix={cls:[method for method in methods if method not in baseline.get(cls,[])] for cls,methods in matrix.items()}
print(','.join(f'{cls}#{method}' for cls,methods in matrix.items() for method in methods) if os.environ.get('NOVA17103_CONTINUITY')=='1' else ','.join(matrix))
PY
)
route_status=0;route_accounting_status=0
run_route_tests "$selection" || route_status=$?
python3 scripts/verify_selected_android1762.py app/build/outputs/androidTest-results/connected/debug "$selection" > "$root/route-accounting.txt" || route_accounting_status=$?
printf '%s\n' "$route_status" > "$root/route-original-exit-status.txt"
sudo chmod a+r app/build/hidden-vpn1762/events.jsonl app/build/hidden-vpn1762/traffic.pcap
sudo tcpdump -tttt -nn -vv -X -r app/build/hidden-vpn1762/traffic.pcap > app/build/hidden-vpn1762/packets.txt 2>&1
if [[ "${NOVA17103_CONTINUITY:-0}" == 1 ]]; then
 # Freeze complete post-test observations: the owned capture remains alive until
 # workflow cleanup, so hashing its still-appending files would be unstable.
 final_packets=app/build/hidden-vpn1762/continuity-final
 mkdir -p "$final_packets"
 cp app/build/hidden-vpn1762/events.jsonl app/build/hidden-vpn1762/traffic.pcap "$final_packets/"
 sudo tcpdump -tttt -nn -vv -X -r "$final_packets/traffic.pcap" > "$final_packets/packets.txt" 2>&1
 mapfile -t continuity_receipts < <(PYTHONPATH=scripts python3 - <<'RECEIPTS'
from verify_hidden_continuity_packets17103 import RECEIPTS
print('\n'.join(RECEIPTS))
RECEIPTS
 )
 [[ ${#continuity_receipts[@]} == 8 ]]
 for result in "${continuity_receipts[@]}"; do
  mapfile -d '' matches < <(find app/build/outputs/connected_android_test_additional_output -type f -name "$result" -print0)
  [[ ${#matches[@]} == 1 ]]
  cp "${matches[0]}" "$final_packets/$result"
 done
 python3 scripts/verify_hidden_continuity_packets17103.py "$final_packets" > "$root/continuity-packet-result.txt"
else
 collect_native_receipts
 python3 scripts/verify_hidden_vpn_fixture1762.py app/build/hidden-vpn1762
 python3 scripts/verify_native_tor1762.py app/build/hidden-vpn1762 "$RUNNER_TEMP/private-gallery-onion-probe" > "$root/tor-route-result.txt"
fi
[[ "$ui_status" == 0 && "$ui_accounting_status" == 0 && "$route_status" == 0 && "$route_accounting_status" == 0 ]]
printf '%s\n' "Selected UI matrix: $NOVA17102_MATRIX" > "$root/selection-scope.txt"
printf '%s\n' 'PASS: focused UI and actual VPN/Tor route evidence; NOT signing approval' > "$root/runtime-result.txt"
