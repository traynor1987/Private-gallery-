#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REF:-}" == refs/heads/release/1.7.10-private-home ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
root=app/build/home1710
mkdir -p "$root"
health="$root/emulator-health.txt"
cat "${ANDROID_AVD_HOME:?}/test.ini" > "$health"
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 37 ]]
adb shell getprop ro.build.version.sdk >> "$health"
adb shell getconf PAGE_SIZE >> "$health"
grep -Fxq 'target=android-37.0' "$health"
initial_surface=$(adb shell pidof surfaceflinger | tr -d '\r')
[[ -n "$initial_surface" ]]
for observation in $(seq 1 15); do
  current_surface=$(adb shell pidof surfaceflinger | tr -d '\r')
  printf 'surfaceflinger[%s]=%s\n' "$observation" "$current_surface" >> "$health"
  [[ "$current_surface" == "$initial_surface" ]]
  sleep 2
done
adb logcat -d -b crash > "$root/emulator-crash-preflight.txt"
! grep -Fq 'hasReadColorBufferDma' "$root/emulator-crash-preflight.txt"
adb shell wm size 1200x1800
adb shell wm density 160
# Match AOSP CTS: acknowledge the emulator's one-time fullscreen tutorial.
# This changes no app permission or focus/privacy policy.
adb shell settings put secure immersive_mode_confirmations confirmed
[[ "$(adb shell settings get secure immersive_mode_confirmations | tr -d '\r')" == confirmed ]]

adb shell df -h /data /sdcard >> "$health"
adb shell 'mkdir -p /sdcard/Android/media && echo ready > /sdcard/home1710-ready.tmp && cat /sdcard/home1710-ready.tmp && rm /sdcard/home1710-ready.tmp' >> "$health"
