#!/usr/bin/env bash
set -euo pipefail
# Never operate on an owner's device or a production package.
[[ "${GITHUB_ACTIONS:-}" == true ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
project_root=$(pwd)
fixture_package=uk.co.traynor.privategallery.hiddenprototype
baseline_sha=a19218479eb9b9bcdb35ff0035fc78522263c891
baseline_dir=$(mktemp -d)
trap 'rm -rf "$baseline_dir"' EXIT
candidate_apk="$project_root/app/build/outputs/apk/debug/app-debug.apk"
test -s "$candidate_apk"
git fetch --no-tags origin "$baseline_sha"
git archive "$baseline_sha" | tar -x -C "$baseline_dir"
fixture_dir="$baseline_dir/app/src/androidTest/java/uk/co/traynor/privategallery/upgrade"
mkdir -p "$fixture_dir"
cp scripts/upgrade/UpgradeFixtureTest.kt "$fixture_dir/"
python3 - "$baseline_dir/app/build.gradle.kts" <<'PY'
import pathlib,sys
p=pathlib.Path(sys.argv[1]);s=p.read_text()
needle='getByName("debug") {'
assert s.count(needle)==1
s=s.replace(needle,needle+'\n      applicationIdSuffix = ".hiddenprototype"')
p.write_text(s)
PY
(cd "$baseline_dir" && ./gradlew assembleDebug assembleDebugAndroidTest)
# Same runner-generated development signer; no permanent signing material involved.
# Delete only earlier disposable CI test state, BEFORE installing/seeding V1.
adb uninstall "$fixture_package.test" >/dev/null 2>&1 || true
adb uninstall "$fixture_package" >/dev/null 2>&1 || true
adb install "$baseline_dir/app/build/outputs/apk/debug/app-debug.apk"
adb install "$baseline_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
mkdir -p app/build/acceptance-diagnostics
run_fixture() {
  local phase="$1"
  local label="${2:-$phase}"
  adb shell am instrument -w -e class "uk.co.traynor.privategallery.upgrade.UpgradeFixtureTest#$phase" "$fixture_package.test/androidx.test.runner.AndroidJUnitRunner" | tee "app/build/acceptance-diagnostics/upgrade-$label.txt"
  grep -q 'OK (1 test)' "app/build/acceptance-diagnostics/upgrade-$label.txt"
}
run_fixture seed
# The actual upgrade: NO uninstall, clear-data, PIN reset, or reimport between these steps.
adb install -r "$candidate_apk"
run_fixture verify
adb shell am force-stop "$fixture_package"
run_fixture verify after-restart
printf '%s\n' "PASS: V1 code28 -> candidate in-place synthetic upgrade, original PIN/media/collections/settings/VPN preserved" > app/build/acceptance-diagnostics/upgrade-result.txt
