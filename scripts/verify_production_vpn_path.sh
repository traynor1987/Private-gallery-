#!/usr/bin/env bash
set -euo pipefail

# This APK is WireGuard-only. The future OpenVPN2 compatibility fork must not
# enter source, native, packaging, or dependency paths by accident.
production_paths=(app/build.gradle.kts gradle app/src/main .github/workflows)
if grep -RInE 'ics-openvpn|de\\.blinkt|openvpn3|openvpn 3|agpl|private-gallery-openvpn2' "${production_paths[@]}"; then
  echo 'FAIL: OpenVPN or AGPL material entered the Private Gallery production path' >&2
  exit 1
fi
grep -q 'com.wireguard.android:tunnel' gradle/libs.versions.toml
test -f NOTICE
test -f docs/WIREGUARD_DEPENDENCIES.md
echo 'PASS: WireGuard-only production path and notice audit'
