#!/usr/bin/env bash
set -euo pipefail
# Production-only disposable emulator. Never uninstall or clear seeded application data.
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_EVENT_NAME:-}" == push && "${GITHUB_REPOSITORY:-}" == traynor1987/Private-gallery- && "${GITHUB_REF:-}" == refs/heads/release/1.7.9-evidence-locker && "${PRODUCTION_UPGRADE:-}" == 1 ]] || { echo 'Unsupported upgrade invocation' >&2; exit 1; }
control_root=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
source "$control_root/release_configuration.sh"
[[ "$baseline_code" == 50 && "$release_code" == 51 && "$baseline_sha" == 4820e176dcb60a3aa2f1bfd7b36e2de19fafee11 ]]
archived_baseline="${PRIVATE_GALLERY_179_BASELINE_APK:?Exact archived signed 1.7.8 code50 APK required}"
[[ -f "$archived_baseline" && -s "$archived_baseline" ]] || { echo 'Archived baseline APK unavailable' >&2; exit 1; }
[[ "$(sha256sum "$archived_baseline" | cut -d ' ' -f 1)" == df958e447ce1c5a9bc9b515d16de03c0d11a961ae6efb030b305b2dfe733ccf3 ]] || { echo 'Archived baseline APK hash mismatch' >&2; exit 1; }
readonly archived_baseline
[[ "$(git rev-parse HEAD)" == "${FROZEN_SOURCE:?Exact tested source required}" ]]
fixture_source="${PRIVATE_GALLERY_179_UPGRADE_FIXTURE:?Reviewed baseline-compatible fixture required}"
[[ -s "$fixture_source" && "$(sha256sum "$fixture_source" | cut -d ' ' -f 1)" == "${PRIVATE_GALLERY_179_UPGRADE_FIXTURE_SHA256:?Reviewed fixture digest required}" ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Disposable emulator required' >&2; exit 1; }
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 37 ]]
[[ "$(adb shell getenforce | tr -d '\r')" == Enforcing ]]
fixture_package=uk.co.traynor.privategallery
installed_package=$(adb shell pm path "$fixture_package" | tr -d '\r')
[[ -z "$installed_package" ]] || { echo 'Production package must be initially absent' >&2; exit 1; }
project_root=$(pwd)
artifact_dir="$project_root/app/build/production-evidence/from50"
mkdir -p "$artifact_dir"
sdk="$ANDROID_HOME/build-tools/36.0.0"
candidate_apk="$project_root/app/build/outputs/apk/release/app-release.apk"
test -s "$candidate_apk"
# Verify immutable application identities before installing either application.
for phase in baseline candidate; do
  if [[ "$phase" == baseline ]]; then apk="$archived_baseline"; code=50; version=1.7.8; else apk="$candidate_apk"; code=51; version=1.7.9; fi
  "$sdk/aapt" dump badging "$apk" > "$artifact_dir/upgrade-$phase-package.txt"
  grep -Fq "package: name='$fixture_package' versionCode='$code' versionName='$version'" "$artifact_dir/upgrade-$phase-package.txt"
  grep -Fxq "sdkVersion:'37'" "$artifact_dir/upgrade-$phase-package.txt"
  grep -Fxq "targetSdkVersion:'37'" "$artifact_dir/upgrade-$phase-package.txt"
  ! grep -q 'application-debuggable' "$artifact_dir/upgrade-$phase-package.txt"
  "$sdk/apksigner" verify --verbose --print-certs "$apk" > "$artifact_dir/upgrade-$phase-signature.txt"
  grep -Fxq 'Verified using v2 scheme (APK Signature Scheme v2): true' "$artifact_dir/upgrade-$phase-signature.txt"
  grep -Fxq 'Number of signers: 1' "$artifact_dir/upgrade-$phase-signature.txt"
  grep -Fxq 'Signer #1 certificate SHA-256 digest: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17' "$artifact_dir/upgrade-$phase-signature.txt"
  sha256sum "$apk" > "$artifact_dir/upgrade-$phase-sha256.txt"
done
control_root=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
compiled="${PRIVATE_GALLERY_179_COMPILED_DIR:-${RUNNER_TEMP:?}/upgrade179-compiled}"
python3 "$control_root/verify_compiled_upgrade179.py" --directory "$compiled" --source "$FROZEN_SOURCE" --fixture "$fixture_source" --compiler "$control_root/compile_upgrade179_fixture.sh"
# Keep the original compilation bytes immutable; resign only a private disposable copy.
signed_dir=$(mktemp -d "${RUNNER_TEMP:?}/upgrade179-signed.XXXXXX")
trap 'rm -rf "$signed_dir"' EXIT
instrumentation_apk="$signed_dir/instrumentation.apk"
cp "$compiled/instrumentation.apk" "$instrumentation_apk"
cp "$compiled/compilation.json" "$artifact_dir/upgrade-compilation.json"
sha256sum "$fixture_source" > "$artifact_dir/upgrade-fixture-sha256.txt"
python3 - "$compiled/compilation.json" "$artifact_dir/upgrade-compiled-fixture-sha256.txt" <<'PYCOMPILED'
import json,sys
from pathlib import Path
proof=json.loads(Path(sys.argv[1]).read_text())
Path(sys.argv[2]).write_text(proof['transformed_fixture_sha256']+'  reviewed-production-fixture.kt\n')
PYCOMPILED
"$sdk/apksigner" sign --ks "${ORG_GRADLE_PROJECT_PRIVATE_GALLERY_STORE_FILE:?}" --ks-key-alias "${KEY_ALIAS:?}" --ks-pass env:STORE_PASSWORD --key-pass env:KEY_PASSWORD "$instrumentation_apk"
"$sdk/apksigner" verify --verbose --print-certs "$instrumentation_apk" > "$artifact_dir/upgrade-instrumentation-signature.txt"
grep -Fxq 'Signer #1 certificate SHA-256 digest: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17' "$artifact_dir/upgrade-instrumentation-signature.txt"
grep -Fxq 'Verified using v2 scheme (APK Signature Scheme v2): true' "$artifact_dir/upgrade-instrumentation-signature.txt"
grep -Fxq 'Number of signers: 1' "$artifact_dir/upgrade-instrumentation-signature.txt"
sha256sum "$instrumentation_apk" > "$artifact_dir/upgrade-instrumentation-sha256.txt"
printf '%s\n' "$baseline_sha" > "$artifact_dir/upgrade-baseline-source-sha.txt"
git rev-parse HEAD > "$artifact_dir/upgrade-candidate-source-sha.txt"
adb install "$archived_baseline"
adb install "$instrumentation_apk"
run_fixture() {
  local phase=$1 label=$2 restart=false
  if [[ "$label" == after-restart ]]; then restart=true; fi
  adb shell am instrument -w -e restartCheck "$restart" -e baselineCode 50 -e candidateCode 51 -e class "uk.co.traynor.privategallery.upgrade.UpgradeFixtureTest#$phase" "$fixture_package.test/androidx.test.runner.AndroidJUnitRunner" | tee "$artifact_dir/upgrade-$label.txt"
  python3 - "$artifact_dir/upgrade-$label.txt" <<'PY'
from pathlib import Path
import re,sys
text=Path(sys.argv[1]).read_text()
assert len(re.findall(r'^OK \(1 test\)\s*$',text,re.M))==1
assert not any(word in text for word in ('FAILURES!!!','INSTRUMENTATION_FAILED','Process crashed','FAILURE:'))
PY
}
run_fixture seed seed
adb install -r "$candidate_apk"
run_fixture verify verify
adb shell am force-stop "$fixture_package"
run_fixture verify after-restart
printf '%s\n' 'PASS: original signed code50 -> code51 in-place upgrade/restart; all legacy encrypted data/configuration and additive owner-encrypted evidence preserved' > "$artifact_dir/upgrade-result.txt"
