#!/usr/bin/env bash
set -euo pipefail

out=app/build/novaos2
debug=app/build/outputs/apk/debug/app-debug.apk
tests=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
namespace=uk.co.traynor.privategallery
app_id="$namespace.hiddenprototype"
fixture_root="app/src/androidTest/java/${namespace//./\/}/novaos2"
mkdir -p "$out"
bash scripts/verify_android17_device.sh "$out"
sha256sum "$debug" "$tests" > "$out/debug-inputs.sha256"

# Wait for the package manager and compositor as well as sys.boot_completed.
stable=0
previous=''
for attempt in {1..30}; do
    compositor=$(adb shell pidof surfaceflinger | tr -d '\r' || true)
    service=$(adb shell service check package | tr -d '\r' || true)
    if [[ -n "$compositor" && "$service" == 'Service package: found' ]] &&
        adb shell cmd package path android | grep -q '^package:'; then
        if [[ "$compositor" == "$previous" ]]; then ((stable+=1)); else stable=1; fi
        if ((stable >= 5)); then break; fi
    else
        stable=0
    fi
    previous=$compositor
    sleep 3
done
((stable >= 5))
adb shell wm dismiss-keyguard
adb install --no-streaming -r "$debug" | tee "$out/install-app.txt"
adb install --no-streaming -r "$tests" | tee "$out/install-tests.txt"
grep -Fxq Success "$out/install-app.txt"
grep -Fxq Success "$out/install-tests.txt"
adb shell run-as "$app_id" rm -rf cache/novaos2-captures

classes=()
expected=0
while IFS= read -r test; do
    class=${test#app/src/androidTest/java/}
    class=${class%.kt}
    count=$(grep -Ec '@Test([[:space:]]|$)' "$test")
    classes+=("${class//\//.}")
    expected=$((expected + count))
done < <(
    find "$fixture_root" -type f -name '*Test.kt' | sort
    theme_test="app/src/androidTest/java/${namespace//./\/}/launcher/LauncherThemesStudioUiTest.kt"
    if [[ -f "$theme_test" ]]; then printf '%s\n' "$theme_test"; fi
)
((expected > 0))
selection=$(IFS=,; printf '%s' "${classes[*]}")
printf '%s\n' "${classes[@]}" > "$out/test-selection.txt"
adb shell am instrument -w -r -e class "$selection" "$app_id.test/androidx.test.runner.AndroidJUnitRunner" | tee "$out/instrumentation.txt"

if adb shell run-as "$app_id" test -d cache/novaos2-captures; then
    mkdir -p "$out/screenshots"
    while IFS= read -r name; do
        [[ "$name" =~ ^[a-z0-9-]+\.png$ ]]
        adb exec-out run-as "$app_id" cat "cache/novaos2-captures/$name" > "$out/screenshots/$name"
    done < <(adb shell run-as "$app_id" ls cache/novaos2-captures | tr -d '\r')
fi

grep -Eq "^OK \(${expected} tests?\)[[:space:]]*$" "$out/instrumentation.txt"
! grep -Eq '^INSTRUMENTATION_STATUS_CODE: -[1-4][[:space:]]*$' "$out/instrumentation.txt"
sha256sum --check "$out/debug-inputs.sha256"

# Wave B has a stateful cross-feature case. The ordinary run above always exercises it.
# Seed and reopen the same two actual encrypted records in distinct emulator processes.
if [[ -f "$fixture_root/WaveBNavigationTest.kt" ]]; then
    [[ $(adb shell getprop ro.kernel.qemu | tr -d '\r') == 1 ]]
    method="$namespace.novaos2.WaveBNavigationTest#desktopWorkspaceAndActiveShiftKeepIndependentStateDuringNavigation"
    restart="$out/wave-b-restart"
    mkdir -p "$restart"
    for phase in seed verify; do
        adb shell am force-stop "$app_id"
        [[ -z "$(adb shell pidof "$app_id" | tr -d '\r' || true)" ]]
        adb shell am instrument -w -r -e waveBProcess "$phase" -e class "$method" \
            "$app_id.test/androidx.test.runner.AndroidJUnitRunner" | tee "$restart/$phase.txt"
        grep -Eq '^OK \(1 test\)[[:space:]]*$' "$restart/$phase.txt"
        if grep -Eq '^INSTRUMENTATION_STATUS_CODE: -[1-4][[:space:]]*$' "$restart/$phase.txt"; then exit 1; fi
        marker=$(sed -n 's/^INSTRUMENTATION_STATUS: waveBMarker=//p' "$restart/$phase.txt" | tr -d '\r')
        [[ "$marker" =~ ^([1-9][0-9]*):([0-9a-f]{64})$ ]]
        if [[ "$phase" == seed ]]; then
            seed_pid=${BASH_REMATCH[1]}
            seed_digest=${BASH_REMATCH[2]}
        else
            verify_pid=${BASH_REMATCH[1]}
            verify_digest=${BASH_REMATCH[2]}
            [[ "$verify_pid" != "$seed_pid" && "$verify_digest" == "$seed_digest" ]]
        fi
    done
    printf 'source=%s\napi=37\nseed_pid=%s\nverify_pid=%s\nrecords_sha256=%s\nresult=PASS\n' \
        "$(git rev-parse HEAD)" "$seed_pid" "$verify_pid" "$seed_digest" > "$restart/receipt.txt"
fi
