#!/usr/bin/env bash
set -euo pipefail
# Disposable CI emulator only. Never clear data after seeding either fixture.
[[ "${GITHUB_ACTIONS:-}" == true ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
project_root=$(pwd)
production="${PRODUCTION_UPGRADE:-0}"
fixture_package=uk.co.traynor.privategallery.hiddenprototype
artifact_dir=app/build/acceptance-diagnostics
build_task=assembleDebug
baseline_variant=debug
if [[ "$production" == 1 ]]; then
  [[ "${GITHUB_REPOSITORY:-}" == traynor1987/Private-gallery- && "$GITHUB_REF" == refs/heads/release/1.6-hidden-tor ]]
  fixture_package=uk.co.traynor.privategallery
  artifact_dir=app/build/production-evidence
  build_task=assembleRelease
  baseline_variant=release
  ! adb shell pm path "$fixture_package" | grep -q '^package:'
fi
baseline_sha=a4cc3eecd5a2a1cf744ca0c23157d89254165632
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
(cd "$baseline_dir" && ./gradlew "$build_task" assembleDebugAndroidTest)
if [[ "$production" == 1 ]]; then
  "$ANDROID_HOME/build-tools/36.0.0/apksigner" sign --ks "$ORG_GRADLE_PROJECT_PRIVATE_GALLERY_STORE_FILE" --ks-key-alias "$KEY_ALIAS" --ks-pass env:STORE_PASSWORD --key-pass env:KEY_PASSWORD "$baseline_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
else
  adb uninstall "$fixture_package.test" >/dev/null 2>&1 || true
  adb uninstall "$fixture_package" >/dev/null 2>&1 || true
fi
adb install "$baseline_dir/app/build/outputs/apk/$baseline_variant/app-$baseline_variant.apk"
adb install "$baseline_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
mkdir -p "$artifact_dir"
run_fixture() {
  local phase="$1"
  local label="${2:-$phase}"
  adb shell am instrument -w -e class "uk.co.traynor.privategallery.upgrade.UpgradeFixtureTest#$phase" "$fixture_package.test/androidx.test.runner.AndroidJUnitRunner" | tee "$artifact_dir/upgrade-$label.txt"
  grep -q 'OK (1 test)' "$artifact_dir/upgrade-$label.txt"
}
run_fixture seed
adb install -r "$candidate_apk"
run_fixture verify
adb shell am force-stop "$fixture_package"
run_fixture verify after-restart
printf '%s\n' "PASS: V1.6 code30 -> V1.6 code31 candidate in-place synthetic upgrade, independent PINs, Primary and Hidden encrypted media/collections/settings/VPN preserved; package=$fixture_package" > "$artifact_dir/upgrade-result.txt"
