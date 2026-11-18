#!/usr/bin/env bash
set -euo pipefail
test_class=${1:-VisualFoundationTest}
output=app/build/home2-evidence/${2:-visual-checkpoint}
test_package=${3:-uk.co.traynor.privategallery.home2}
test_method=${4:-}
selected_test_identity="$test_package.$test_class"
if [[ -n "$test_method" ]]; then
  [[ "$test_method" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || exit 2
  selected_test_identity+="#$test_method"
fi
export test_class test_package test_method
mkdir -p "$output"
test_app=uk.co.traynor.privategallery.hiddenprototype
if [[ ${ORG_GRADLE_PROJECT_novaGuardianProbe:-false} == true ]]; then
  test_app=uk.co.traynor.privategallery.capture77.guardianprobe
fi
capture() {
  local status=$?
  trap - EXIT
  local reports=app/build/outputs/androidTest-results/connected/debug
  if [[ -d "$reports" ]]; then cp -R "$reports" "$output/reports"; fi
  exit "$status"
}
trap capture EXIT
bash scripts/run_android17_checked_command.sh "$output" -- ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="$selected_test_identity" \
  -Pandroid.enableAdditionalTestOutput=true \
  -Pandroid.testInstrumentationRunnerArguments.additionalTestOutputDir="/sdcard/Android/media/$test_app/additional_test_output" \
  -Pkotlin.incremental=false --no-daemon --console=plain
python3 - <<'PY' | tee "$output/result.txt"
from pathlib import Path
import os
import re
import xml.etree.ElementTree as ET
test_class = os.environ['test_class']
test_package = os.environ['test_package']
file = Path('app/src/androidTest/java', *test_package.split('.'), f'{test_class}.kt')
expected = set(re.findall(r'@Test\s+fun\s+(\w+)\s*\(', file.read_text()))
method = os.environ['test_method']
if method:
    assert method in expected, 'Unknown focused method'
    expected = {method}
reports = Path('app/build/outputs/androidTest-results/connected/debug')
cases = [case for xml in reports.rglob('*.xml') for case in ET.parse(xml).iter('testcase')]
assert cases and len(cases) == len(expected) and {case.attrib['name'] for case in cases} == expected
assert all(case.attrib['classname'] == f'{test_package}.{test_class}'
           and all(case.find(tag) is None for tag in ('failure', 'error', 'skipped')) for case in cases)
print(f'Android 17 visual checkpoint: {len(cases)}/{len(expected)} PASS')
PY
