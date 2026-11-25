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
  "$package.intelligence.OwnerPreviewIsolationTest"
  "$package.core.hidden.HiddenCredentialSeparationTest"
  "$package.core.hidden.HiddenRestoreAccessTest"
  "$package.launcher.LauncherHomeLibraryPager73UiTest"
  "$package.core.sanctuary.SanctuaryLauncherPolicy70Test"
)

if [[ ${NOVA_INTELLIGENCE_VISUALS:-false} == true ]]; then
  classes+=(
    "$package.intelligence.IntelligenceBuild98VisualTest"
    "$package.CapturePolicy98Test"
    "$package.intelligence.OwnerPreviewSetup98Test"
  )
fi

mkdir -p "$out"
bash scripts/verify_android17_device.sh "$out"
sha256sum "$debug" "$instrumentation" > "$out/command-inputs.sha256"
cp "${ANDROID_AVD_HOME:-$HOME/.android/avd}/test.ini" "$out/avd.ini"
grep -Fxq 'target=android-37.0' "$out/avd.ini"

save_crash_log() {
  local result=$?
  if [[ -v previous_ime_setting ]]; then
    if [[ $previous_ime_setting == null ]]; then
      adb shell settings delete secure show_ime_with_hard_keyboard || true
    else
      adb shell settings put secure show_ime_with_hard_keyboard "$previous_ime_setting" || true
    fi
  fi
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
  compositor=$(adb shell pidof surfaceflinger | tr -d '\r' || true)
  server=$(adb shell pidof system_server | tr -d '\r' || true)
  system_ui=$(adb shell pidof com.android.systemui | tr -d '\r' || true)
  package_service=$(adb shell service check package | tr -d '\r' || true)
  printf 'attempt=%s system_server=%s system_ui=%s surfaceflinger=%s package=%s\n' "$attempt" "$server" "$system_ui" "$compositor" "$package_service" >> "$out/emulator-health.txt"
  processes="$server $system_ui $compositor"
  if [[ -n "$server" && -n "$system_ui" && -n "$compositor" && "$package_service" == "Service package: found" ]] &&
     adb shell cmd package path android | grep -q '^package:'; then
    if [[ "$processes" == "$previous" ]]; then stable=$((stable + 1)); else stable=1; fi
    if (( stable >= 10 )); then break; fi
  else
    stable=0
  fi
  previous="$processes"
  sleep 3
done
if (( stable < 10 )); then
  echo 'Android 17 did not stabilise before installation.' >&2
  exit 1
fi

adb logcat -d -b crash > "$out/emulator-crash-preflight.txt"
if grep -Fq 'hasReadColorBufferDma' "$out/emulator-crash-preflight.txt"; then
  echo 'The emulator hit the color-buffer DMA crash during startup.' >&2
  exit 1
fi

adb shell wm dismiss-keyguard
adb install --no-streaming -r "$debug" | tee "$out/install-app.txt"
adb install --no-streaming -r "$instrumentation" | tee "$out/install-instrumentation.txt"
grep -Fxq 'Success' "$out/install-app.txt"
grep -Fxq 'Success' "$out/install-instrumentation.txt"

if [[ ${NOVA_INTELLIGENCE_VISUALS:-false} == true ]]; then
  previous_ime_setting=$(adb shell settings get secure show_ime_with_hard_keyboard | tr -d '\r')
  adb shell settings put secure show_ime_with_hard_keyboard 1
  adb shell run-as "$app_id" rm -rf cache/nova-intelligence-build98
fi

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

if [[ ${NOVA_INTELLIGENCE_VISUALS:-false} == true ]]; then
  mkdir -p "$out/screenshots"
  screenshots=(
    cover-dashboard cover-chat inner-dashboard inner-chat cover-keyboard-open cover-cloud-loading
    cover-synthetic-cloud-reply cover-offline-fallback guardian-owner-preview-disconnected
    owner-preview-cover-setup owner-preview-inner-diagnostics
  )
  for name in "${screenshots[@]}"; do
    adb exec-out run-as "$app_id" cat "cache/nova-intelligence-build98/$name.png" > "$out/screenshots/$name.png"
  done
  python3 - "$out/screenshots" "${screenshots[@]}" <<'PY'
from hashlib import sha256
from pathlib import Path
import sys

root = Path(sys.argv[1])
captured = {}
for name in sys.argv[2:]:
    image = root / f"{name}.png"
    pixels = image.read_bytes()
    assert pixels.startswith(b"\x89PNG\r\n\x1a\n"), f"Missing valid emulator screenshot: {name}"
    digest = sha256(pixels).digest()
    assert digest not in captured, f"Emulator frames are identical: {captured.get(digest)} and {name}"
    captured[digest] = name
PY
fi

bash scripts/run_launcher70_restart.sh
