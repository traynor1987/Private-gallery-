#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_REPOSITORY:-}" == traynor1987/Private-gallery- ]]
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_EVENT_NAME:-}" == push ]]
source scripts/release_configuration17103.sh
apk=app/build/outputs/apk/release/app-release.apk
sdk="$ANDROID_HOME/build-tools/36.0.0"
mkdir -p app/build/production-evidence
"$sdk/apksigner" verify --verbose --print-certs "$apk" > app/build/production-evidence/signature.txt
"$sdk/aapt" dump badging "$apk" > app/build/production-evidence/package.txt
grep -Fq "package: name='uk.co.traynor.privategallery' versionCode='$release_code' versionName='$release_version'" app/build/production-evidence/package.txt
grep -Fxq "sdkVersion:'$release_min_sdk'" app/build/production-evidence/package.txt
grep -q "application-label:'NovaOS'" app/build/production-evidence/package.txt
if grep -q 'application-debuggable' app/build/production-evidence/package.txt; then
  echo 'Debuggable production APK rejected' >&2
  exit 1
else
  debug_check_status=$?
  [[ "$debug_check_status" == 1 ]] || { echo 'Cannot inspect production APK debug flag' >&2; exit 1; }
fi
grep -Fq 'Signer #1 certificate SHA-256 digest: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17' app/build/production-evidence/signature.txt
sha256sum "$apk" > app/build/production-evidence/apk-sha256.txt
git rev-parse HEAD > app/build/production-evidence/source-sha.txt
grep -Fxq "targetSdkVersion:'37'" app/build/production-evidence/package.txt
grep -Fxq 'Number of signers: 1' app/build/production-evidence/signature.txt
grep -Fxq 'Verified using v2 scheme (APK Signature Scheme v2): true' app/build/production-evidence/signature.txt

# Test observer/transport barriers must never be packaged in the owner APK.
"$sdk/aapt" dump xmltree "$apk" AndroidManifest.xml > app/build/production-evidence/release-manifest.txt
python3 - "$apk" app/build/production-evidence/release-manifest.txt <<'PYBOUNDARY'
import pathlib,sys,zipfile
forbidden=(b'HiddenContinuityFixtureApplication',b'HiddenContinuityFixtureService',b'CiAccessibilityService178')
manifest=pathlib.Path(sys.argv[2]).read_bytes()
with zipfile.ZipFile(sys.argv[1]) as z:
    payloads=[manifest]+[z.read(n) for n in z.namelist() if n.endswith('.dex')]
    if any(name in data for name in forbidden for data in payloads):
        raise SystemExit('Debug continuity/accessibility fixture leaked into production APK')
print('PASS: no debug continuity observer, barriers or accessibility service in release manifest/dex')
PYBOUNDARY
