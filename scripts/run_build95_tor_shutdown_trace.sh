#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REPOSITORY:-}" == traynor1987/Private-gallery- ]]
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 37 ]]
evidence="app/build/tor-shutdown-trace"
onion="$RUNNER_TEMP/private-gallery-onion-probe"
mkdir -p "$evidence"
git rev-parse HEAD > "$evidence/source-sha.txt"
android_port=$(cat "$onion/http.port")
[[ "$android_port" =~ ^[0-9]+$ ]]
# Capture TCP headers only on the synthetic origin's loopback listener.
capture_pidfile="$RUNNER_TEMP/nova95-tor-origin-capture.pid"
sudo bash -c 'printf "%s\n" "$$" > "$1"; exec tcpdump -i lo -nn -U -s 54 -w "$2" tcp port "$3"' \
    _ "$capture_pidfile" "$evidence/origin.pcap" "$android_port" > "$evidence/capture.log" 2>&1 &
capture=$!
finish() {
    if [[ -s "$capture_pidfile" ]]; then
        sudo kill -INT "$(cat "$capture_pidfile")" 2>/dev/null || true
        wait "$capture" || true
    fi
    if sudo test -s "$evidence/origin.pcap"; then
        sudo tcpdump -tt -nn -r "$evidence/origin.pcap" > "$evidence/origin-packets.txt" 2> "$evidence/decode.log"
    fi
}
trap finish EXIT
for ((iteration=0; iteration<100; iteration++)); do
    if grep -q 'listening on' "$evidence/capture.log"; then break; fi
    kill -0 "$capture"
    sleep .05
done
grep -q 'listening on' "$evidence/capture.log"
adb shell wm size 1200x1800
adb shell wm density 160
python3 scripts/trace_build95_tor_shutdown.py --sample > "$evidence/clock-before.json"
selection=uk.co.traynor.privategallery.core.browser.hidden.NativeTorIsolation1762Test#productionHiddenLockRetiresAnInFlightTorRequest
if [[ "${1:-}" == --paired ]]; then
    selection="${selection%%#*}"
fi
status=0
./gradlew :app:connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=$selection" \
    -Pandroid.enableAdditionalTestOutput=true \
    "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeUrl=${HIDDEN_ONION_PROBE_URL:?}" \
    "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeExpected=${HIDDEN_ONION_PROBE_EXPECTED:?}" \
    --stacktrace || status=$?
adb logcat -d -v threadtime -s NovaTorShutdown:I Process:I Zygote:I > "$evidence/device-timeline.txt"
python3 scripts/trace_build95_tor_shutdown.py --sample > "$evidence/clock-after.json"
reports=app/build/outputs/androidTest-results/connected
python3 scripts/verify_selected_android1762.py "$reports" "$selection" || status=1
find app/build/outputs/connected_android_test_additional_output -name native-tor1762-lock.json -exec cp {} "$evidence/" \;
python3 scripts/trace_build95_tor_shutdown.py "$evidence" "$onion" "$evidence/native-tor1762-lock.json" || status=1
exit "$status"
