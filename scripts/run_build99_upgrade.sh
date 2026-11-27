#!/usr/bin/env bash
set -euo pipefail
umask 077
[[ "$GITHUB_ACTIONS" == true && "$GITHUB_EVENT_NAME" == push && "$GITHUB_REF" == refs/heads/main &&
   "$GITHUB_REPOSITORY" == traynor1987/Private-gallery- && "$GITHUB_ACTOR_ID" == 16270479 &&
   "$GITHUB_RUN_ATTEMPT" == 1 && "$PRODUCTION_UPGRADE" == 1 ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 37 ]]
[[ "$(adb shell getenforce | tr -d '\r')" == Enforcing ]]

root="$RUNNER_TEMP/novaos99"
receipts="$root/receipts/from97"
logs="$root/runtime"
mkdir -p "$receipts" "$logs"
package=uk.co.traynor.privategallery
installed="$(adb shell pm list packages "$package" | tr -d '\r')"
if grep -Fxq "package:$package" <<< "$installed"; then
    echo 'The production package must initially be absent on the disposable device.' >&2
    exit 1
fi

adb install "$root/baseline.apk"
adb install "$root/instrumentation.apk"
adb shell svc power stayon true

run_phase() {
    local method=$1 phase=$2 restart=$3
    adb shell input keyevent KEYCODE_WAKEUP
    adb shell wm dismiss-keyguard
    if ! adb shell am instrument -w -e restartCheck "$restart" -e baselineCode 97 -e candidateCode 99 \
        -e class "$package.upgrade.UpgradeFixtureTest#$method" \
        "$package.test/androidx.test.runner.AndroidJUnitRunner" > "$logs/$phase.txt"; then
        cat "$logs/$phase.txt" >&2
        return 1
    fi
    if ! grep -Fxq 'OK (1 test)' "$logs/$phase.txt" || grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|FAILURE:' "$logs/$phase.txt"; then
        printf 'Failed mandatory populated upgraded-data phase: %s\n' "$phase" >&2
        cat "$logs/$phase.txt" >&2
        return 1
    fi
    printf 'OK (1 test)\n' > "$receipts/upgrade-$phase.txt"
}

run_phase seed seed false

# Exercise both opt-in and existing default behavior on the disposable owner-free emulator.
previous_home="$(adb shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN \
    -c android.intent.category.HOME | tr -d '\r' | tail -n 1)"
[[ "$previous_home" =~ ^[a-zA-Z0-9_.]+/[a-zA-Z0-9_.$]+$ && "$previous_home" != "$package/"* ]]
adb shell cmd package set-home-activity --user 0 "$package/.launcher.NovaLauncherActivity"
run_phase verifySelectedDefaultHome default-before false

if ! python3 scripts/rehearse_in_app_update85.py --apk "$root/candidate.apk" --output "$logs" \
    --source "$FROZEN_SOURCE" --candidate-code 99; then
    if [[ -s "$logs/in-app-update-instrumentation.txt" ]]; then
        tail -60 "$logs/in-app-update-instrumentation.txt" >&2
    fi
    exit 1
fi
run_phase verifySelectedDefaultHome default-upgrade false
adb shell am force-stop "$package"
run_phase verifySelectedDefaultHome default-restart true
adb shell cmd package set-home-activity --user 0 "$previous_home"
run_phase verify verify false
adb shell am force-stop "$package"
run_phase verify after-restart true

if adb install --no-streaming -r "$root/baseline.apk" > "$logs/downgrade.txt" 2>&1; then
    echo 'Android unexpectedly allowed the stable Build 97 APK to replace Build 99.' >&2
    exit 1
fi
grep -Fq 'INSTALL_FAILED_VERSION_DOWNGRADE' "$logs/downgrade.txt"
adb shell dumpsys package "$package" > "$logs/installed-after-downgrade.txt"
grep -Eq '^[[:space:]]+versionCode=99[[:space:]]' "$logs/installed-after-downgrade.txt"
printf 'INSTALL_FAILED_VERSION_DOWNGRADE; installed Build 99\n' > "$receipts/no-downgrade.txt"

PYTHONPATH=scripts python3 - "$root" "$FROZEN_SOURCE" <<'PY'
import json
from pathlib import Path
import sys
from novaos_build99_rehearsal import BASELINE_DIGEST, CERTIFICATE, digest, require, require_upgrade, sha, write_json

root = Path(sys.argv[1])
upgrade = json.loads((root / 'runtime/in-app-update.json').read_text())
source = sys.argv[2]
require(sha(source), 'Upgrade source is not a full SHA')
candidate_digest = digest(root / 'candidate.apk')
baseline_digest = digest(root / 'baseline.apk')
require(upgrade['apk_sha256'] == candidate_digest, 'Updater rehearse used different APK bytes')
require(baseline_digest == BASELINE_DIGEST, 'Installed baseline bytes changed')
upgrade.update(seed='PASS', verify='PASS', after_restart='PASS',
               certificate_sha256=CERTIFICATE, baseline_sha256=BASELINE_DIGEST, default_home_retention='PASS',
               anti_downgrade='PASS')
require_upgrade(upgrade, source)
write_json(root / 'receipts/build99-upgrade.json', upgrade)
report = root / 'receipts/from97'
write_json(report / 'in-app-update.json', json.loads((root / 'runtime/in-app-update.json').read_text()))
for name, value in {
    'upgrade-candidate-source-sha.txt': source,
    'upgrade-baseline-sha256.txt': baseline_digest,
    'upgrade-candidate-sha256.txt': candidate_digest,
    'upgrade-result.txt': 'PASS: authentic Build 97 to 99 populated in-app update and encrypted restart',
}.items():
    (report / name).write_text(value + '\n')
PY
echo 'PASS: original-key Build 97 to 99 populated in-app update and encrypted restart.'
