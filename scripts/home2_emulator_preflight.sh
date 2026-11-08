#!/usr/bin/env bash
# Full API37/SystemUI health only. Never changes application or routing policy.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true ]]
case "${GITHUB_REF:-}" in
  refs/heads/integration/novaos-1.7-home2|refs/heads/feature/novaos-visual-foundation|refs/heads/feature/novaos-sanctuary|refs/heads/feature/novaos-sanctuary-integration) ;;
  *) exit 1 ;;
esac
root=${1:-app/build/home2-evidence}
mkdir -p "$root"
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 37 ]]
adb shell pm path com.android.systemui | tee "$root/systemui-package.txt"
grep -q '^package:' "$root/systemui-package.txt"
cat "${ANDROID_AVD_HOME:-$HOME/.android/avd}/test.ini" > "$root/avd.ini"
grep -Fxq 'target=android-37.0' "$root/avd.ini"
initial_surface=$(adb shell pidof surfaceflinger | tr -d '\r')
[[ -n "$initial_surface" ]]
for observation in $(seq 1 15); do
  current_surface=$(adb shell pidof surfaceflinger | tr -d '\r')
  printf 'surfaceflinger[%s]=%s\n' "$observation" "$current_surface" >> "$root/emulator-health.txt"
  [[ "$current_surface" == "$initial_surface" ]]
  sleep 2
done
adb logcat -d -b crash > "$root/emulator-crash-preflight.txt"
if grep -Fq 'hasReadColorBufferDma' "$root/emulator-crash-preflight.txt"; then exit 1; fi
