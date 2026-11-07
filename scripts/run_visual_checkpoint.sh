#!/usr/bin/env bash
set -euo pipefail
test_class=${1:-VisualFoundationTest}
output=app/build/home2-evidence/${2:-visual-checkpoint}
export test_class
mkdir -p "$output"
capture() {
  local status=$?
  trap - EXIT
  local reports=app/build/outputs/androidTest-results/connected/debug
  if [[ -d "$reports" ]]; then cp -R "$reports" "$output/reports"; fi
  exit "$status"
}
trap capture EXIT
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=uk.co.traynor.privategallery.home2."$test_class" \
  -Pandroid.enableAdditionalTestOutput=true \
  -Pandroid.testInstrumentationRunnerArguments.additionalTestOutputDir=/sdcard/Android/media/uk.co.traynor.privategallery.hiddenprototype/additional_test_output \
  -Pkotlin.incremental=false --no-daemon --console=plain
python3 - <<'PY' | tee "$output/result.txt"
from pathlib import Path
import os
import re
import xml.etree.ElementTree as ET
test_class = os.environ['test_class']
file = Path(f'app/src/androidTest/java/uk/co/traynor/privategallery/home2/{test_class}.kt')
expected = set(re.findall(r'@Test\s+fun\s+(\w+)\s*\(', file.read_text()))
reports = Path('app/build/outputs/androidTest-results/connected/debug')
cases = [case for xml in reports.rglob('*.xml') for case in ET.parse(xml).iter('testcase')]
assert cases and len(cases) == len(expected) and {case.attrib['name'] for case in cases} == expected
assert all(case.attrib['classname'] == f'uk.co.traynor.privategallery.home2.{test_class}'
           and all(case.find(tag) is None for tag in ('failure', 'error', 'skipped')) for case in cases)
print(f'Android 17 visual checkpoint: {len(cases)}/{len(expected)} PASS')
PY
