#!/usr/bin/env bash
set -euo pipefail
scratch_dir=$(mktemp -d)
trap 'rm -rf "$scratch_dir"' EXIT
java com.sun.tools.javac.Main -d "$scratch_dir" app/src/androidTest/java/uk/co/traynor/privategallery/core/browser/hidden/SocksStageDiagnostics.java scripts/tests/SocksStageDiagnosticsChecks.java
java -cp "$scratch_dir" SocksStageDiagnosticsChecks
