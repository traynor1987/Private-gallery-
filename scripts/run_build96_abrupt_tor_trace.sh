#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REPOSITORY:-}" == traynor1987/Private-gallery- &&
   "${GITHUB_REF:-}" == refs/heads/diagnostics/build96-abrupt-tor ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 37 ]]
evidence=app/build/tor-shutdown-trace
onion="$RUNNER_TEMP/private-gallery-onion-probe"
mkdir -p "$evidence"
git rev-parse HEAD > "$evidence/source-sha.txt"
adb shell wm size 1200x1800
adb shell wm density 160
python3 scripts/tor_clock1762.py > "$evidence/tor-clock-before.json"
selection=uk.co.traynor.privategallery.core.browser.hidden.NativeTorIsolation1762Test#packagedTorNativeFormsUploadsDownloadsAndNetworkSink
status=0
./gradlew :app:connectedDebugAndroidTest     "-Pandroid.testInstrumentationRunnerArguments.class=$selection"     -Pandroid.enableAdditionalTestOutput=true     "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeUrl=${HIDDEN_ONION_PROBE_URL:?}"     "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeExpected=${HIDDEN_ONION_PROBE_EXPECTED:?}"     --no-daemon --console=plain --stacktrace || status=$?
adb logcat -d -v threadtime -s NovaTorShutdown:I > "$evidence/device-timeline.txt"
python3 scripts/tor_clock1762.py > "$evidence/tor-clock-after.json"
python3 scripts/verify_selected_android1762.py app/build/outputs/androidTest-results/connected "$selection" || status=1
find app/build/outputs/connected_android_test_additional_output -name native-tor1762-result.json -exec cp {} "$evidence/" \;
PYTHONPATH=scripts python3 - "$evidence" "$onion" <<'PY' || status=1
import json, sys, time
from pathlib import Path
from tor_clock1762 import bounds
from verify_native_tor1762 import CLOSE_WINDOW_MS, close_observation

evidence, onion = map(Path, sys.argv[1:])
marker = json.loads((evidence / "native-tor1762-result.json").read_bytes())
interval = bounds(evidence, [marker])
deadline = marker["trigger_elapsed_ns"] + interval[1] + CLOSE_WINDOW_MS * 1_000_000
source = onion / "interactive-events.jsonl"
events = []
while True:
    events = [json.loads(line) for line in source.read_bytes().splitlines()]
    closing = any(e.get("case") == marker["case"] and e.get("kind") == "slow-closed" for e in events)
    if time.monotonic_ns() >= deadline or closing:
        break
    time.sleep(.1)
(evidence / "origin-at-deadline.jsonl").write_bytes(source.read_bytes())
print("Remote closure bound in ms", close_observation(events, marker, "Abrupt Tor transport loss", interval))
PY
exit "$status"
