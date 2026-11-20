#!/usr/bin/env bash
# Run the compiled debug APKs with the same AndroidJUnitRunner used by connectedDebugAndroidTest.
set -euo pipefail

out=app/build/intelligence
debug=app/build/outputs/apk/debug/app-debug.apk
instrumentation=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
app_id=uk.co.traynor.privategallery.hiddenprototype
package=uk.co.traynor.privategallery
runner="$app_id.test/androidx.test.runner.AndroidJUnitRunner"
classes=(
  "$package.intelligence.IntelligenceLauncherUiTest"
  "$package.intelligence.IntelligenceWorkspaceTest"
  "$package.launcher.LauncherHomeLibraryPager73UiTest"
  "$package.core.sanctuary.SanctuaryLauncherPolicy70Test"
)

mkdir -p "$out"
bash scripts/verify_android17_device.sh "$out"
sha256sum "$debug" "$instrumentation" > "$out/command-inputs.sha256"

save_crash_log() {
  local result=$?
  if (( result != 0 )); then
    adb logcat -d -b crash > "$out/emulator-crashes.txt" || true
    adb shell service check package > "$out/package-service.txt" || true
  fi
  exit "$result"
}
trap save_crash_log EXIT

# sys.boot_completed alone can precede system-server and Package Manager stabilisation.
# Require consecutive healthy checks of the same process before installing either APK.
stable=0
previous=''
for attempt in {1..30}; do
  server=$(adb shell pidof system_server | tr -d '\r' || true)
  system_ui=$(adb shell pidof com.android.systemui | tr -d '\r' || true)
  package_service=$(adb shell service check package | tr -d '\r' || true)
  printf 'attempt=%s system_server=%s system_ui=%s package=%s\n' "$attempt" "$server" "$system_ui" "$package_service" >> "$out/emulator-health.txt"
  if [[ -n "$server" && -n "$system_ui" && "$package_service" == "Service package: found" ]] &&
     adb shell cmd package path android | grep -q '^package:'; then
    if [[ "$server" == "$previous" ]]; then stable=$((stable + 1)); else stable=1; fi
    if (( stable >= 10 )); then break; fi
  else
    stable=0
  fi
  previous="$server"
  sleep 3
done
(( stable >= 10 ))

adb shell wm dismiss-keyguard
adb install --no-streaming -r "$debug" | tee "$out/install-app.txt"
adb install --no-streaming -r "$instrumentation" | tee "$out/install-instrumentation.txt"
grep -Fxq 'Success' "$out/install-app.txt"
grep -Fxq 'Success' "$out/install-instrumentation.txt"

# Read expectations from source so missing classes, crashes and skipped methods all fail.
expected=0
for class in "${classes[@]}"; do
  source="app/src/androidTest/java/${class//./\/}.kt"
  count=$(grep -Ec '@Test([[:space:]]|$)' "$source")
  expected=$((expected + count))
done
selection=$(IFS=,; printf '%s' "${classes[*]}")
adb shell am instrument -w -r -e class "$selection" "$runner" | tee "$out/instrumentation.txt"
grep -Eq "^OK \(${expected} tests\)[[:space:]]*$" "$out/instrumentation.txt"
if grep -Eq '^INSTRUMENTATION_STATUS_CODE: -[1-4][[:space:]]*$' "$out/instrumentation.txt"; then
  echo 'An instrumentation test failed, was ignored or violated an assumption.' >&2
  exit 1
fi
sha256sum --check "$out/command-inputs.sha256"

bash scripts/run_launcher70_restart.sh
