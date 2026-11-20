#!/usr/bin/env bash
set -euo pipefail

[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_EVENT_NAME:-}" == pull_request &&
    "${GITHUB_HEAD_REF:-}" == feature/novaos-hidden-space-apps && "${GITHUB_BASE_REF:-}" == main ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
evidence=app/build/hidden-space/routes
fixture_evidence=app/build/hidden-vpn1762
mkdir -p "$evidence"
git rev-parse HEAD > "$evidence/source-sha.txt"
bash scripts/verify_android17_device.sh "$evidence"
apk=app/build/outputs/apk/debug/app-debug.apk
package=$(python3 scripts/debug_apk_identity1762.py "$apk")
sha256sum "$apk" > "$evidence/apk-sha256.txt"
adb shell wm size 1200x1800
adb shell wm density 160
bash scripts/reset_native_browser_evidence1762.sh "$package"

adb_cfg=/data/local/tmp/hidden-vpn1762.conf
trap 'adb shell rm -f "$adb_cfg" >/dev/null 2>&1 || true' EXIT
if [[ "${HIDDEN_SPACE_VPN_READY:-}" == true ]]; then
    adb push "$RUNNER_TEMP/private-gallery-vpn1762/client.conf" "$adb_cfg"
    adb shell chmod 600 "$adb_cfg"
fi

copy_result() {
    local name=$1
    local -a matches
    mapfile -d '' matches < <(find app/build/outputs/connected_android_test_additional_output -type f -name "$name" -print0)
    [[ ${#matches[@]} == 1 ]] || { echo "Expected one fresh Android result for $name" >&2; return 1; }
    cp "${matches[0]}" "$fixture_evidence/$name"
}

run_cases() {
    local label=$1 selection=$2 status=0
    shift 2
    local reports=app/build/outputs/androidTest-results/connected
    # Only discard previous generated test reports, after the layout suite has archived its results.
    rm -rf "$reports" app/build/outputs/connected_android_test_additional_output
    ./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$selection" \
        -Pandroid.enableAdditionalTestOutput=true "$@" --stacktrace || status=$?
    if [[ -d "$reports" ]]; then
        cp -R "$reports" "$evidence/$label"
    fi
    local additional=app/build/outputs/connected_android_test_additional_output
    if [[ -d "$additional" ]]; then
        cp -R "$additional" "$evidence/$label-additional"
    fi
    python3 scripts/verify_selected_android1762.py "$evidence/$label" "$selection" | tee "$evidence/$label-cases.txt" || status=1
    return "$status"
}

read_packets() {
    sudo chmod a+r "$fixture_evidence/traffic.pcap"
    sudo tcpdump -tttt -nn -vv -X -r "$fixture_evidence/traffic.pcap" > "$fixture_evidence/packets.txt" 2>&1
}

namespace=uk.co.traynor.privategallery.core.browser.hidden
status=0
if [[ "${HIDDEN_SPACE_VPN_READY:-}" == true ]]; then
    vpn_cases="$namespace.HiddenVpnLive1762Test,$namespace.HiddenVpnNetworkLoss1762Test"
    if run_cases vpn "$vpn_cases" "-Pandroid.testInstrumentationRunnerArguments.hiddenVpnConfig=$adb_cfg" \
            -Pandroid.testInstrumentationRunnerArguments.hiddenVpnOrigin=http://fixture.invalid &&
        copy_result vpn-native-revocation1762-result.json && copy_result vpn-network-loss1762-result.json &&
        sudo chmod a+r "$fixture_evidence/events.jsonl" && read_packets; then
        python3 scripts/verify_hidden_vpn_fixture1762.py "$fixture_evidence" | tee "$evidence/vpn-packet-result.txt" || status=1
    else
        status=1
    fi
else
    echo 'Disposable WireGuard fixture was unavailable.' >&2
    status=1
fi

if [[ "${HIDDEN_SPACE_TOR_READY:-}" == true ]]; then
    if run_cases tor "$namespace.NativeTorIsolation1762Test" \
            "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeUrl=${HIDDEN_ONION_PROBE_URL:?}" \
            "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeExpected=${HIDDEN_ONION_PROBE_EXPECTED:?}" &&
        copy_result native-tor1762-result.json && read_packets; then
        python3 scripts/verify_native_tor1762.py "$fixture_evidence" "$RUNNER_TEMP/private-gallery-onion-probe" |
            tee "$evidence/tor-packet-result.txt" || status=1
    else
        status=1
    fi
else
    echo 'Controlled Tor onion fixture was unavailable.' >&2
    status=1
fi
exit "$status"
