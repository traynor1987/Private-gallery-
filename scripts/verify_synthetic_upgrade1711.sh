#!/usr/bin/env bash
set -euo pipefail
# Production-only disposable emulator. Never uninstall or clear seeded application data.
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_EVENT_NAME:-}" == push && "${GITHUB_REPOSITORY:-}" == traynor1987/Private-gallery- && "${GITHUB_REF:-}" == refs/heads/feature/novaos-visual-foundation && "${PRODUCTION_UPGRADE:-}" == 1 ]] || { echo 'Unsupported upgrade invocation' >&2; exit 1; }
control_root=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "${PRIVATE_GALLERY_1711_ROOT:-$control_root/..}"
source "$control_root/release_configuration1711.sh"
[[ "$baseline_code" == 63 && "$release_code" == 64 && "$baseline_sha" == 4738bcb12abb91ed289a78fcdcfaec7bc631cf0f ]]
archived_baseline="${PRIVATE_GALLERY_1711_BASELINE_APK:?Exact archived signed 1.7.11.7 code63 APK required}"
[[ -f "$archived_baseline" && -s "$archived_baseline" ]] || { echo 'Archived baseline APK unavailable' >&2; exit 1; }
[[ "$(sha256sum "$archived_baseline" | cut -d ' ' -f 1)" == 84bbf1aab425c33a06d737614c6160f9daa0f5fc3dcdd74e7cb06e77441b5b6e ]] || { echo 'Archived baseline APK hash mismatch' >&2; exit 1; }
readonly archived_baseline
[[ "$(git rev-parse HEAD)" == "${FROZEN_SOURCE:?Exact tested source required}" ]]
python3 "$control_root/verify_release1711_gate.py" --receipt "${PRIVATE_GALLERY_1711_GATE_RECEIPT:?Exact-source reviewed pre-signer gate required}" --source "$FROZEN_SOURCE"
fixture_source="${PRIVATE_GALLERY_1711_UPGRADE_FIXTURE:?Reviewed baseline-compatible fixture required}"
[[ -s "$fixture_source" && "$(sha256sum "$fixture_source" | cut -d ' ' -f 1)" == "${PRIVATE_GALLERY_1711_UPGRADE_FIXTURE_SHA256:?Reviewed fixture digest required}" ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Disposable emulator required' >&2; exit 1; }
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 37 ]]
[[ "$(adb shell getenforce | tr -d '\r')" == Enforcing ]]
fixture_package=uk.co.traynor.privategallery
# pm list packages succeeds with empty output for an absent package; pm path does not.
installed_packages=$(adb shell pm list packages "$fixture_package" | tr -d '\r') || { echo 'Package presence query failed' >&2; exit 1; }
if grep -Fxq "package:$fixture_package" <<< "$installed_packages"; then
  echo 'Production package must be initially absent' >&2
  exit 1
fi
project_root=$(pwd)
artifact_dir="${PRIVATE_GALLERY_1711_EVIDENCE_DIR:-$project_root/app/build/production-evidence/from63}"
[[ ! -e "$artifact_dir" ]] || { echo "Fresh upgrade evidence directory required" >&2; exit 1; }
mkdir -p "$artifact_dir"
sdk="$ANDROID_HOME/build-tools/36.0.0"
candidate_apk="${PRIVATE_GALLERY_1711_CANDIDATE_APK:-$project_root/app/build/outputs/apk/release/app-release.apk}"
test -s "$candidate_apk"
# Verify immutable application identities before installing either application.
for phase in baseline candidate; do
  if [[ "$phase" == baseline ]]; then apk="$archived_baseline"; code="$baseline_code"; version="$baseline_version"; else apk="$candidate_apk"; code="$release_code"; version="$release_version"; fi
  "$sdk/aapt" dump badging "$apk" > "$artifact_dir/upgrade-$phase-package.txt"
  grep -Fq "package: name='$fixture_package' versionCode='$code' versionName='$version'" "$artifact_dir/upgrade-$phase-package.txt"
  grep -Fxq "sdkVersion:'37'" "$artifact_dir/upgrade-$phase-package.txt"
  grep -Fxq "targetSdkVersion:'37'" "$artifact_dir/upgrade-$phase-package.txt"
  if grep -q 'application-debuggable' "$artifact_dir/upgrade-$phase-package.txt"; then
    echo "Debuggable application rejected: $phase" >&2; exit 1
  fi
  "$sdk/apksigner" verify --verbose --print-certs "$apk" > "$artifact_dir/upgrade-$phase-signature.txt"
  grep -Fxq 'Verified using v2 scheme (APK Signature Scheme v2): true' "$artifact_dir/upgrade-$phase-signature.txt"
  grep -Fxq 'Number of signers: 1' "$artifact_dir/upgrade-$phase-signature.txt"
  grep -Fxq 'Signer #1 certificate SHA-256 digest: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17' "$artifact_dir/upgrade-$phase-signature.txt"
  sha256sum "$apk" > "$artifact_dir/upgrade-$phase-sha256.txt"
done
control_root=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
compiled="${PRIVATE_GALLERY_1711_COMPILED_DIR:-${RUNNER_TEMP:?}/upgrade1711-compiled}"
python3 "$control_root/verify_compiled_upgrade1711.py" --directory "$compiled" --source "$FROZEN_SOURCE" --fixture "$fixture_source" --compiler "$control_root/compile_upgrade1711_fixture.sh"
# Signing is a separate future workflow step after the exact-source review/runtime gate.
# This runner never reads signer credentials and never mutates the compiled or signed APK.
instrumentation_apk="${PRIVATE_GALLERY_1711_SIGNED_INSTRUMENTATION_APK:?Original-key signed reviewed instrumentation required}"
[[ -s "$instrumentation_apk" ]]
python3 "$control_root/verify_signed_upgrade1711.py" "$compiled/instrumentation.apk" "$instrumentation_apk"
cp "$compiled/compilation.json" "$artifact_dir/upgrade-compilation.json"
sha256sum "$fixture_source" > "$artifact_dir/upgrade-fixture-sha256.txt"
python3 - "$compiled/compilation.json" "$artifact_dir/upgrade-compiled-fixture-sha256.txt" <<'PYCOMPILED'
import json,sys
from pathlib import Path
proof=json.loads(Path(sys.argv[1]).read_text())
Path(sys.argv[2]).write_text(proof['transformed_fixture_sha256']+'  reviewed-production-fixture.kt\n')
PYCOMPILED
"$sdk/apksigner" verify --min-sdk-version 24 --verbose --print-certs "$instrumentation_apk" > "$artifact_dir/upgrade-instrumentation-signature.txt"
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
  adb shell am instrument -w -e restartCheck "$restart" -e baselineCode "$baseline_code" -e candidateCode "$release_code" -e class "uk.co.traynor.privategallery.upgrade.UpgradeFixtureTest#$phase" "$fixture_package.test/androidx.test.runner.AndroidJUnitRunner" | tee "$artifact_dir/upgrade-$label.txt"
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
printf '%s\n' 'PASS: original signed code63 -> code64 in-place upgrade/restart; all legacy encrypted data/configuration and additive owner-encrypted evidence preserved' > "$artifact_dir/upgrade-result.txt"
