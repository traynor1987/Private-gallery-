#!/usr/bin/env bash
set -euo pipefail
# Preserve original test exit status while collecting synthetic actual-Home observations.
capture() {
  local original=$?
  trap - EXIT
  set +e
  mkdir -p app/build/home2-evidence/actual-home
  adb pull /sdcard/Android/data/uk.co.traynor.privategallery.hiddenprototype/files/nova-status60 app/build/home2-evidence/actual-home/ > app/build/home2-evidence/actual-home/pull.txt 2>&1
  exit "$original"
}
trap capture EXIT
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="$(python3 scripts/verify_home2_runtime.py --classes)" -Pkotlin.incremental=false --no-daemon --console=plain
