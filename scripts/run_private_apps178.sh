#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REF:-}" == refs/heads/release/1.7.8-private-apps ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
python3 scripts/debug_apk_identity1762.py app/build/outputs/apk/debug/app-debug.apk
adb push "$RUNNER_TEMP/private-gallery-vpn1762/client.conf" /data/local/tmp/hidden-vpn1762.conf
adb shell chmod 600 /data/local/tmp/hidden-vpn1762.conf
native_package=$(python3 scripts/debug_apk_identity1762.py app/build/outputs/apk/debug/app-debug.apk)
bash scripts/reset_native_browser_evidence1762.sh "$native_package"
adb shell wm size 1200x1800
adb shell wm density 160
classes='uk.co.traynor.privategallery.ui.PrivateAppCustomIconsTest,uk.co.traynor.privategallery.ui.BrowserVaultScreenshotTest,uk.co.traynor.privategallery.ui.PrivateAppSessionSettings178Test,uk.co.traynor.privategallery.core.browser.v2.PrivateAppSessionFence178Test,uk.co.traynor.privategallery.core.hidden.HiddenDocumentLifecycleTest,uk.co.traynor.privategallery.core.vault.VaultDocumentRepositoryTest,uk.co.traynor.privategallery.core.browser.hidden.HiddenDownloadTest,uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnLive1762Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnNetworkLoss1762Test,uk.co.traynor.privategallery.core.browser.hidden.NativeTorIsolation1762Test,uk.co.traynor.privategallery.ui.PrivateAppsUi178Test,uk.co.traynor.privategallery.ui.PrivateAppControls178Test,uk.co.traynor.privategallery.PrivateAppsAuthority178Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenPrivateAppsLive178Test'
./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$classes" \
 -Pandroid.testInstrumentationRunnerArguments.hiddenVpnConfig=/data/local/tmp/hidden-vpn1762.conf \
 -Pandroid.testInstrumentationRunnerArguments.hiddenVpnOrigin=http://fixture.invalid \
 "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeUrl=${HIDDEN_ONION_PROBE_URL:?}" \
 "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeExpected=${HIDDEN_ONION_PROBE_EXPECTED:?}" --stacktrace
python3 scripts/verify_selected_android1762.py app/build/outputs/androidTest-results/connected/debug "$classes"

copy_result() {
 local name=$1 destination=$2
 local -a matches=()
 mapfile -d '' matches < <(find app/build/outputs/connected_android_test_additional_output -type f -name "$name" -print0)
 [[ ${#matches[@]} == 1 ]]
 cp "${matches[0]}" "$destination"
}
sudo chmod a+r app/build/hidden-vpn1762/events.jsonl app/build/hidden-vpn1762/traffic.pcap
sudo tcpdump -tttt -nn -vv -X -r app/build/hidden-vpn1762/traffic.pcap > app/build/hidden-vpn1762/packets.txt 2>&1
for name in vpn-native-revocation1762-result.json vpn-network-loss1762-result.json native-tor1762-result.json; do
 copy_result "$name" "app/build/hidden-vpn1762/$name"
done
python3 scripts/verify_hidden_vpn_fixture1762.py app/build/hidden-vpn1762
python3 scripts/verify_native_tor1762.py app/build/hidden-vpn1762 "$RUNNER_TEMP/private-gallery-onion-probe"
python3 scripts/verify_private_apps178_cases.py app/build/outputs/androidTest-results/connected/debug
mkdir -p app/build/privateapps178
python3 - <<'PYPROOF'
import json,pathlib,subprocess
pathlib.Path('app/build/privateapps178/final-gates.json').write_text(json.dumps({'source_sha':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'decision':'PASS','original_exit_status':0,'network':{'owned_vpn':'PASS','native_tor':'PASS','dns_and_packet_observation':'PASS','revocation':'PASS'}},indent=2)+'\n')
PYPROOF
