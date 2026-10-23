#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_REPOSITORY:-}" == traynor1987/Private-gallery- ]]
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REF:-}" == refs/heads/release/1.7-encrypted-camera ]]
apk=app/build/outputs/apk/release/app-release.apk
sdk="$ANDROID_HOME/build-tools/36.0.0"
mkdir -p app/build/production-evidence
"$sdk/apksigner" verify --verbose --print-certs "$apk" > app/build/production-evidence/signature.txt
"$sdk/aapt" dump badging "$apk" > app/build/production-evidence/package.txt
grep -q "package: name='uk.co.traynor.privategallery' versionCode='33' versionName='1.7'" app/build/production-evidence/package.txt
grep -q "application-label:'Private Gallery'" app/build/production-evidence/package.txt
! grep -q 'application-debuggable' app/build/production-evidence/package.txt
grep -q 'Signer #1 certificate SHA-256 digest: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17' app/build/production-evidence/signature.txt
sha256sum "$apk" > app/build/production-evidence/apk-sha256.txt
git rev-parse HEAD > app/build/production-evidence/source-sha.txt
