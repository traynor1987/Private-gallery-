#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_REPOSITORY:-}" == traynor1987/Private-gallery- ]]
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_EVENT_NAME:-}" == push ]]
control_root=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "${PRIVATE_GALLERY_1711_ROOT:-$control_root/..}"
source "$control_root/release_configuration1711.sh"
[[ "$(git rev-parse HEAD)" == "${FROZEN_SOURCE:?Exact reviewed source required}" ]]
python3 "$control_root/verify_release1711_gate.py" --receipt "${PRIVATE_GALLERY_1711_GATE_RECEIPT:?Reviewed signer gate required}" --source "$FROZEN_SOURCE"
apk="${PRIVATE_GALLERY_1711_CANDIDATE_APK:-app/build/outputs/apk/release/app-release.apk}"
sdk="$ANDROID_HOME/build-tools/36.0.0"
mkdir -p app/build/production-evidence
"$sdk/apksigner" verify --verbose --print-certs "$apk" > app/build/production-evidence/signature.txt
"$sdk/aapt" dump badging "$apk" > app/build/production-evidence/package.txt
grep -Fq "package: name='uk.co.traynor.privategallery' versionCode='$release_code' versionName='$release_version'" app/build/production-evidence/package.txt
grep -Fxq "sdkVersion:'$release_min_sdk'" app/build/production-evidence/package.txt
grep -q "application-label:'NovaOS'" app/build/production-evidence/package.txt
if grep -q 'application-debuggable' app/build/production-evidence/package.txt; then
  echo 'Debuggable application rejected: candidate' >&2; exit 1
fi
grep -Fq 'Signer #1 certificate SHA-256 digest: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17' app/build/production-evidence/signature.txt
sha256sum "$apk" > app/build/production-evidence/apk-sha256.txt
git rev-parse HEAD > app/build/production-evidence/source-sha.txt
grep -Fxq "targetSdkVersion:'37'" app/build/production-evidence/package.txt
grep -Fxq 'Number of signers: 1' app/build/production-evidence/signature.txt
grep -Fxq 'Verified using v2 scheme (APK Signature Scheme v2): true' app/build/production-evidence/signature.txt
