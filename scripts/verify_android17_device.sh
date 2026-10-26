#!/usr/bin/env bash
set -euo pipefail
out=${1:?evidence directory required}
mkdir -p "$out"
adb shell getprop ro.build.version.sdk | tr -d '\r' > "$out/sdk.txt"
adb shell getprop ro.build.version.release | tr -d '\r' > "$out/android-release.txt"
adb shell getprop ro.build.fingerprint > "$out/build-fingerprint.txt"
test "$(cat "$out/sdk.txt")" = 37
[[ "$(cat "$out/android-release.txt")" == 17 || "$(cat "$out/android-release.txt")" == 17.* ]]
