#!/usr/bin/env bash
set -euo pipefail
native_evidence_package=${1:?Expected verified debug APK application ID}
[[ "$native_evidence_package" == uk.co.traynor.privategallery.hiddenprototype ]] || { echo 'Refusing evidence operations on a non-test package' >&2; exit 2; }
# A fresh emulator has no package until connectedDebugAndroidTest installs it.
# Clear only previous synthetic result files when a debuggable target already exists.
if native_evidence_package_path=$(adb shell pm path "$native_evidence_package") && [[ "$native_evidence_package_path" == package:* ]]; then
  adb shell run-as "$native_evidence_package" rm -f files/native-tor1762-result.json files/vpn-native-revocation1762-result.json files/vpn-network-loss1762-result.json
fi
