#!/usr/bin/env bash
set -u

diagnostics_dir="app/build/acceptance-diagnostics"
mkdir -p "$diagnostics_dir"

test_status=0
./gradlew connectedDebugAndroidTest --stacktrace --info || test_status=$?

if [[ "$test_status" -ne 0 ]]; then
  raw_log="$diagnostics_dir/android-crash-buffer.raw"
  adb logcat -b crash -d -v threadtime > "$raw_log" 2>&1 || true
  python3 scripts/sanitize_android_crash_log.py "$raw_log" "$diagnostics_dir/android-crash-buffer.txt" || true
  rm -f "$raw_log"

  if adb shell run-as uk.co.traynor.privategallery cat files/browser-v2-fatal-report.txt \
      > "$diagnostics_dir/browser-v2-fatal-report.raw" 2>/dev/null; then
    python3 scripts/sanitize_android_crash_log.py \
      "$diagnostics_dir/browser-v2-fatal-report.raw" \
      "$diagnostics_dir/browser-v2-fatal-report.txt" || true
  fi
  rm -f "$diagnostics_dir/browser-v2-fatal-report.raw"

  echo "===== Sanitized Android crash buffer ====="
  cat "$diagnostics_dir/android-crash-buffer.txt" 2>/dev/null || true
  echo "===== Sanitized saved Browser fatal report (if present) ====="
  cat "$diagnostics_dir/browser-v2-fatal-report.txt" 2>/dev/null || true
fi

exit "$test_status"
