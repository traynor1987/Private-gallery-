#!/usr/bin/env bash
set -euo pipefail
# Disposable CI emulator only. Install the exact archived original-key code46 APK; never clear seeded data.
[[ "${GITHUB_ACTIONS:-}" == true ]]
source scripts/release_configuration.sh
[[ "${GITHUB_REF:-}" == refs/heads/release/1.7.8-private-apps && "${PRODUCTION_UPGRADE:-}" == 1 ]]
[[ "$baseline_code" == 46 && "$release_code" == 47 && "$baseline_sha" == 25aae6d59f79c455e47acc73a5c99f9c8f15242a ]]
archived_baseline="${PRIVATE_GALLERY_178_BASELINE_APK:?Exact archived signed 1.7.7 APK required}"
[[ -f "$archived_baseline" && -s "$archived_baseline" ]]
[[ "$(sha256sum "$archived_baseline" | cut -d ' ' -f 1)" == 696e80071497501d8da6cd8dd3e2c9728a63b0d4abc9e88c4ab46338fef97a2e ]] || { echo 'Archived baseline APK hash mismatch' >&2; exit 1; }
readonly archived_baseline
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
project_root=$(pwd)
production="${PRODUCTION_UPGRADE:-0}"
fixture_package=uk.co.traynor.privategallery.hiddenprototype
artifact_dir=app/build/acceptance-diagnostics
build_task=assembleDebug
baseline_variant=debug
if [[ "$production" == 1 ]]; then
  [[ "${GITHUB_REPOSITORY:-}" == traynor1987/Private-gallery- && "${GITHUB_EVENT_NAME:-}" == push ]]
  fixture_package=uk.co.traynor.privategallery
  artifact_dir=app/build/production-evidence
  build_task=assembleRelease
  baseline_variant=release
  ! adb shell pm path "$fixture_package" | grep -q '^package:'
fi
baseline_dir=$(mktemp -d)
trap 'rm -rf "$baseline_dir"' EXIT
candidate_apk="$project_root/app/build/outputs/apk/$baseline_variant/app-$baseline_variant.apk"
test -s "$candidate_apk"
git fetch --no-tags origin "$baseline_sha"
git archive "$baseline_sha" | tar -x -C "$baseline_dir"
fixture_dir="$baseline_dir/app/src/androidTest/java/uk/co/traynor/privategallery/upgrade"
mkdir -p "$fixture_dir"
cp scripts/upgrade/UpgradeFixtureTest.kt "$fixture_dir/"
if [[ "$production" == 1 ]]; then
python3 - "$baseline_dir/app/build.gradle.kts" "$fixture_dir/UpgradeFixtureTest.kt" <<'PYCODE'
import pathlib,sys
p=pathlib.Path(sys.argv[1]);s=p.read_text();needle='      applicationIdSuffix = ".hiddenprototype"'
assert s.count(needle)==1
p.write_text(s.replace(needle,''))
p=pathlib.Path(sys.argv[2]);p.write_text(p.read_text().replace('uk.co.traynor.privategallery.hiddenprototype','uk.co.traynor.privategallery'))
PYCODE
fi
# Build baseline-compatible instrumentation only; the application baseline is the archived APK.
(cd "$baseline_dir" && ./gradlew assembleDebugAndroidTest)
if [[ "$production" == 1 ]]; then
  "$ANDROID_HOME/build-tools/36.0.0/apksigner" sign --ks "$ORG_GRADLE_PROJECT_PRIVATE_GALLERY_STORE_FILE" --ks-key-alias "$KEY_ALIAS" --ks-pass env:STORE_PASSWORD --key-pass env:KEY_PASSWORD "$baseline_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
  mkdir -p "$artifact_dir"
  "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs "$baseline_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" > "$artifact_dir/upgrade-instrumentation-signature.txt"
  grep -Fq 'Signer #1 certificate SHA-256 digest: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17' "$artifact_dir/upgrade-instrumentation-signature.txt"
else
  adb uninstall "$fixture_package.test" >/dev/null 2>&1 || true
  adb uninstall "$fixture_package" >/dev/null 2>&1 || true
fi
mkdir -p "$artifact_dir"
sdk="$ANDROID_HOME/build-tools/36.0.0"
baseline_apk="$archived_baseline"
for phase in baseline candidate; do
  if [[ "$phase" == baseline ]]; then apk="$baseline_apk"; code="$baseline_code"; else apk="$candidate_apk"; code="$release_code"; fi
  "$sdk/aapt" dump badging "$apk" > "$artifact_dir/upgrade-$phase-package.txt"
  grep -Fq "package: name='$fixture_package' versionCode='$code'" "$artifact_dir/upgrade-$phase-package.txt"
  if [[ "$phase" == candidate ]]; then grep -Fxq "sdkVersion:'$release_min_sdk'" "$artifact_dir/upgrade-$phase-package.txt"; fi
  "$sdk/apksigner" verify --verbose --print-certs "$apk" > "$artifact_dir/upgrade-$phase-signature.txt"
  if [[ "$production" == 1 ]]; then
    grep -Fq 'Signer #1 certificate SHA-256 digest: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17' "$artifact_dir/upgrade-$phase-signature.txt"
  fi
  sha256sum "$apk" > "$artifact_dir/upgrade-$phase-sha256.txt"
done
printf '%s\n' "$baseline_sha" > "$artifact_dir/upgrade-baseline-source-sha.txt"
git rev-parse HEAD > "$artifact_dir/upgrade-candidate-source-sha.txt"
adb install "$baseline_apk"
adb install "$baseline_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
mkdir -p "$artifact_dir"
run_fixture() {
  local phase="$1"
  local label="${2:-$phase}"
  local restart_check=false
  if [[ "$label" == after-restart ]]; then restart_check=true; fi
  adb shell am instrument -w -e restartCheck "$restart_check" -e baselineCode "$baseline_code" -e candidateCode "$release_code" -e class "uk.co.traynor.privategallery.upgrade.UpgradeFixtureTest#$phase" "$fixture_package.test/androidx.test.runner.AndroidJUnitRunner" | tee "$artifact_dir/upgrade-$label.txt"
  grep -q 'OK (1 test)' "$artifact_dir/upgrade-$label.txt"
}
run_fixture seed
adb install -r "$candidate_apk"
run_fixture verify
adb shell am force-stop "$fixture_package"
run_fixture verify after-restart
printf '%s\n' "PASS: V$baseline_version code$baseline_code -> V$release_version code$release_code candidate in-place synthetic upgrade, independent PINs, Primary and Hidden encrypted media/collections/settings/VPN preserved; package=$fixture_package" > "$artifact_dir/upgrade-result.txt"
