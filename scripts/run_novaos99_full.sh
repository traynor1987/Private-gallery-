#!/usr/bin/env bash
set -euo pipefail
bash scripts/verify_novaos99_full_context.sh
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]

out=app/build/full99
debug=app/build/outputs/apk/debug/app-debug.apk
tests=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
app_id=$(python3 scripts/debug_apk_identity1762.py "$debug")
runner=$app_id.test/androidx.test.runner.AndroidJUnitRunner
config=/data/local/tmp/hidden-vpn1762.conf
reports=app/build/outputs/androidTest-results/connected/debug
additional=app/build/outputs/connected_android_test_additional_output

cleanup() {
    status=$?
    adb shell rm -f "$config" >/dev/null 2>&1 || true
    if ((status != 0)); then
        adb logcat -d -b crash > "$out/runtime-crash.raw" || true
        python3 scripts/sanitize_android_crash_log.py "$out/runtime-crash.raw" "$out/runtime-crash.txt" || true
        rm -f "$out/runtime-crash.raw"
    fi
    printf '%s\n' "$status" > "$out/runtime-exit-status.txt"
    exit "$status"
}
trap cleanup EXIT

bash scripts/verify_android17_device.sh "$out"
adb shell wm size 1200x1800
adb shell wm density 160
adb shell wm dismiss-keyguard
# The Android SystemUI fullscreen tutorial legitimately revokes protected playback focus.
# Match the acknowledged first-use state of the API 37 comparison on this disposable AVD.
adb shell settings put secure immersive_mode_confirmations confirmed
[[ $(adb shell settings get secure immersive_mode_confirmations | tr -d '\r') == confirmed ]]
python3 scripts/verify_android17_media_environment.py "$out/media-before.json"
python3 scripts/ci_emulator_storage.py "$out/storage-before.json" --avd build99-full --require-budget
sha256sum --check "$out/debug-inputs.sha256"
adb install --no-streaming -r "$debug"
adb install --no-streaming -r "$tests"
adb shell am instrument -w -r -e log true "$runner" > "$out/discovery.txt"

# The route suites need controlled traffic/clock boundaries. The remaining
# discovery cases below run without class selection, except these independently verified fixtures.
python3 scripts/ci_onion_probe.py --verify preflight
HIDDEN_SPACE_VPN_READY=true HIDDEN_SPACE_TOR_READY=true bash scripts/run_hidden_space_routes.sh

adb push "$RUNNER_TEMP/private-gallery-vpn1762/client.conf" "$config"
adb shell chmod 600 "$config"
rm -rf "$reports" "$additional"
python3 scripts/ci_emulator_storage.py "$out/storage-before-broad.json" --avd build99-full --require-budget
python3 scripts/ci_onion_probe.py --verify full
python3 scripts/build99_broad_batches.py "$out/discovery.txt" > "$out/broad-batches.txt"
[[ -s "$out/broad-batches.txt" && "$app_id" == uk.co.traynor.privategallery.hiddenprototype ]]
broad_status=0
batch=0
mapfile -t selections < "$out/broad-batches.txt"
for selection in "${selections[@]}"; do
    batch=$((batch + 1))
    dest="$out/broad-results/batch-$batch"
    mkdir -p "$dest"
    # AGP uninstalls its packages after a connected run. Prove a fresh debug
    # sandbox even after interrupted or previously installed fixture sessions.
    adb install --no-streaming -r "$debug"
    [[ $(adb shell pm clear "$app_id" | tr -d '\r') == Success ]]
    rm -rf "$reports" "$additional"
    status=0
    bash scripts/run_android_instrumentation_with_diagnostics.sh \
        "-Pandroid.testInstrumentationRunnerArguments.class=$selection" \
        "-Pandroid.testInstrumentationRunnerArguments.hiddenVpnConfig=$config" \
        -Pandroid.testInstrumentationRunnerArguments.hiddenVpnOrigin=http://fixture.invalid \
        -Pandroid.testInstrumentationRunnerArguments.hiddenTorControlDiagnostics=true \
        -Pandroid.enableAdditionalTestOutput=true || status=$?
    if [[ -d "$reports" ]]; then cp -a "$reports/." "$dest/"; else status=1; fi
    printf '%s\n' "$status" > "$dest/gradle-status.txt"
    if ((status != 0)); then broad_status=1; fi
done
((broad_status == 0))

# Evidence Locker's packet proof must start with a fresh owned WireGuard
# capture. Its runner later drains that exact server before Tor negative phases.
bash scripts/cleanup_hidden_vpn_fixture1762.sh
mv -T app/build/hidden-vpn1762 "$out/native-vpn-observations"
test ! -e "$RUNNER_TEMP/build99-native-vpn-state"
mv -T "$RUNNER_TEMP/private-gallery-vpn1762" "$RUNNER_TEMP/build99-native-vpn-state"
mkdir -p app/build/evidence179
python3 - <<'PY_CAPTURE'
import json
from pathlib import Path
import time
with Path("app/build/evidence179/capture-start.json").open("x") as output:
    json.dump({"epoch": time.time()}, output)
PY_CAPTURE
bash scripts/setup_hidden_vpn_fixture1762.sh
bash scripts/run_evidence179.sh


bash scripts/run_hidden_process_fixtures.sh
bash scripts/run_launcher70_restart.sh
python3 scripts/run_backup_restart62.py
bash scripts/run_guardian_notification_pipeline93.sh
adb shell wm size 1200x1800
adb shell wm density 160
bash scripts/run_guardian_notification_pipeline93.sh GuardianFoldNotificationTest guardian-fold-notification

python3 scripts/verify_android17_media_environment.py "$out/media-after.json"
python3 scripts/ci_emulator_storage.py "$out/storage-after.json" --avd build99-full
sha256sum --check "$out/debug-inputs.sha256"
