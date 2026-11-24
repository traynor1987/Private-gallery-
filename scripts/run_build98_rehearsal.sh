#!/usr/bin/env bash
set -euo pipefail
umask 077
[[ "$GITHUB_ACTIONS" == true && "$GITHUB_EVENT_NAME" == push && "$GITHUB_REF" == refs/heads/main &&
   "$GITHUB_REPOSITORY" == traynor1987/Private-gallery- && "$GITHUB_ACTOR_ID" == 16270479 &&
   "$GITHUB_RUN_ATTEMPT" == 1 && "$PRODUCTION_UPGRADE" == 1 ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 37 ]]
[[ "$(adb shell getenforce | tr -d '\r')" == Enforcing ]]

root="$RUNNER_TEMP/novaos98"
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
    if ! adb shell am instrument -w -e restartCheck "$restart" -e baselineCode 97 -e candidateCode 98 \
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
if ! python3 scripts/rehearse_in_app_update85.py --apk "$root/candidate.apk" --output "$logs" \
    --source "$FROZEN_SOURCE" --candidate-code 98; then
    if [[ -s "$logs/in-app-update-instrumentation.txt" ]]; then
        tail -60 "$logs/in-app-update-instrumentation.txt" >&2
    fi
    exit 1
fi
run_phase verify verify false
adb shell am force-stop "$package"
run_phase verify after-restart true

PYTHONPATH=scripts python3 - "$root" "$FROZEN_SOURCE" <<'PY'
import json
from pathlib import Path
import sys
from novaos_build98_rehearsal import BASELINE_DIGEST, CANDIDATE, CERTIFICATE, digest, require, require_upgrade, write_json

root = Path(sys.argv[1])
upgrade = json.loads((root / 'runtime/in-app-update.json').read_text())
require(sys.argv[2] == CANDIDATE, 'Rehearsal source changed')
require(upgrade['apk_sha256'] == digest(root / 'candidate.apk'), 'Updater rehearse used different APK bytes')
require(digest(root / 'baseline.apk') == BASELINE_DIGEST, 'Installed baseline bytes changed')
upgrade.update(seed='PASS', verify='PASS', after_restart='PASS',
               certificate_sha256=CERTIFICATE, baseline_sha256=BASELINE_DIGEST)
require_upgrade(upgrade)
write_json(root / 'receipts/build98-upgrade.json', upgrade)
PY
echo 'PASS: original-key Build 97 to 98 populated in-app update and encrypted restart.'
