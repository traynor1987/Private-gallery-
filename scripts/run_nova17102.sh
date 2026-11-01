#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REF:-}" == refs/heads/release/1.7.10-private-home ]]
[[ "$(git rev-parse HEAD)" == "${GITHUB_SHA:?}" ]]
root=app/build/nova17102
export NOVA17102_MATRIX=docs/release17102/ui-cases.json
if [[ "${NOVA17102_REPAIR:-0}" == 1 ]]; then export NOVA17102_MATRIX=docs/release17102/repair-cases.json; fi
mkdir -p "$root"
git rev-parse HEAD > "$root/source-sha.txt"
bash scripts/home1710_emulator_preflight.sh
python3 scripts/debug_apk_identity1762.py app/build/outputs/apk/debug/app-debug.apk
selection=$(python3 - <<'PY'
import json,os
print(','.join(json.load(open(os.environ['NOVA17102_MATRIX']))))
PY
)
ui_status=0;ui_accounting_status=0
./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$selection" --stacktrace || ui_status=$?
python3 scripts/verify_selected_android1762.py app/build/outputs/androidTest-results/connected/debug "$selection" > "$root/ui-accounting.txt" || ui_accounting_status=$?
printf '%s\n' "$ui_status" > "$root/ui-original-exit-status.txt"
mkdir -p "$root/ui/outputs"
if [[ -d app/build/outputs/androidTest-results ]]; then cp -R app/build/outputs/androidTest-results "$root/ui/outputs/"; fi
if [[ -d app/build/outputs/connected_android_test_additional_output ]]; then cp -R app/build/outputs/connected_android_test_additional_output "$root/ui/outputs/"; fi
rm -rf app/build/outputs/androidTest-results/connected/debug app/build/outputs/connected_android_test_additional_output
adb push "$RUNNER_TEMP/private-gallery-vpn1762/client.conf" /data/local/tmp/hidden-vpn1762.conf
adb shell chmod 600 /data/local/tmp/hidden-vpn1762.conf
package=$(python3 scripts/debug_apk_identity1762.py app/build/outputs/apk/debug/app-debug.apk)
bash scripts/reset_native_browser_evidence1762.sh "$package"
selection=$(python3 - <<'PY'
import json
print(','.join(json.load(open('docs/release17102/route-cases.json'))))
PY
)
route_status=0;route_accounting_status=0
./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$selection" \
 -Pandroid.testInstrumentationRunnerArguments.hiddenVpnConfig=/data/local/tmp/hidden-vpn1762.conf \
 -Pandroid.testInstrumentationRunnerArguments.hiddenVpnOrigin=http://fixture.invalid \
 "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeUrl=${HIDDEN_ONION_PROBE_URL:?}" \
 "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeExpected=${HIDDEN_ONION_PROBE_EXPECTED:?}" --stacktrace || route_status=$?
python3 scripts/verify_selected_android1762.py app/build/outputs/androidTest-results/connected/debug "$selection" > "$root/route-accounting.txt" || route_accounting_status=$?
printf '%s\n' "$route_status" > "$root/route-original-exit-status.txt"
sudo chmod a+r app/build/hidden-vpn1762/events.jsonl app/build/hidden-vpn1762/traffic.pcap
sudo tcpdump -tttt -nn -vv -X -r app/build/hidden-vpn1762/traffic.pcap > app/build/hidden-vpn1762/packets.txt 2>&1
for result in vpn-native-revocation1762-result.json vpn-network-loss1762-result.json native-tor1762-result.json; do
 mapfile -d '' matches < <(find app/build/outputs/connected_android_test_additional_output -type f -name "$result" -print0)
 [[ ${#matches[@]} == 1 ]]
 cp "${matches[0]}" "app/build/hidden-vpn1762/$result"
done
python3 scripts/verify_hidden_vpn_fixture1762.py app/build/hidden-vpn1762
python3 scripts/verify_native_tor1762.py app/build/hidden-vpn1762 "$RUNNER_TEMP/private-gallery-onion-probe" > "$root/tor-route-result.txt"
[[ "$ui_status" == 0 && "$ui_accounting_status" == 0 && "$route_status" == 0 && "$route_accounting_status" == 0 ]]
printf '%s\n' "Selected UI matrix: $NOVA17102_MATRIX" > "$root/selection-scope.txt"
printf '%s\n' 'PASS: focused UI and actual VPN/Tor route evidence; NOT signing approval' > "$root/runtime-result.txt"
