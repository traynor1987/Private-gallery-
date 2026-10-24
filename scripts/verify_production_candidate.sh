#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_REPOSITORY:-}" == traynor1987/Private-gallery- ]]
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_EVENT_NAME:-}" == push ]]
source scripts/release_configuration.sh
apk=app/build/outputs/apk/release/app-release.apk
sdk="$ANDROID_HOME/build-tools/36.0.0"
mkdir -p app/build/production-evidence
"$sdk/apksigner" verify --verbose --print-certs "$apk" > app/build/production-evidence/signature.txt
"$sdk/aapt" dump badging "$apk" > app/build/production-evidence/package.txt
grep -Fq "package: name='uk.co.traynor.privategallery' versionCode='$release_code' versionName='$release_version'" app/build/production-evidence/package.txt
grep -Fxq "sdkVersion:'$release_min_sdk'" app/build/production-evidence/package.txt
grep -q "application-label:'Private Gallery'" app/build/production-evidence/package.txt
! grep -q 'application-debuggable' app/build/production-evidence/package.txt
grep -Fq 'Signer #1 certificate SHA-256 digest: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17' app/build/production-evidence/signature.txt
sha256sum "$apk" > app/build/production-evidence/apk-sha256.txt
git rev-parse HEAD > app/build/production-evidence/source-sha.txt
