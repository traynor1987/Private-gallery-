#!/usr/bin/env bash
set -euo pipefail
# A fresh emulator has no package until connectedDebugAndroidTest installs it.
# Clear only previous synthetic result files when a debuggable target already exists.
if native_evidence_package_path=$(adb shell pm path uk.co.traynor.privategallery) && [[ "$native_evidence_package_path" == package:* ]]; then
  adb shell run-as uk.co.traynor.privategallery rm -f files/native-tor1762-result.json files/vpn-native-revocation1762-result.json files/vpn-network-loss1762-result.json
fi
