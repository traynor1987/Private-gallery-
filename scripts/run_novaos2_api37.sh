#!/usr/bin/env bash
set -euo pipefail

wave_a=false
if (($#)); then
    [[ "$#" == 1 && "$1" == --wave-a ]]
    wave_a=true
fi

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
if $wave_a; then adb shell run-as "$app_id" rm -f cache/novaos2-wave-a-timings.txt; fi

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
    if $wave_a; then
        fixtures="app/src/androidTest/java/${namespace//./\/}"
        printf '%s\n' "$fixtures/HiddenDiscoverabilityRegressionTest.kt" \
            "$fixtures/core/hidden/HiddenCredentialSeparationTest.kt" \
            "$fixtures/core/sanctuary/SanctuaryLauncherPolicy70Test.kt" \
            "$fixtures/core/update/Updater85RuntimeTest.kt" "$fixtures/core/update/Updater86UiTest.kt"
    fi
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
