#!/usr/bin/env bash
set -euo pipefail
scratch_dir=$(mktemp -d)
trap 'rm -rf "$scratch_dir"' EXIT
java com.sun.tools.javac.Main -Xlint:all -d "$scratch_dir" app/src/androidTest/java/uk/co/traynor/privategallery/core/browser/hidden/TorControlEventSummary.java scripts/tests/TorControlEventChecks.java
java -cp "$scratch_dir" TorControlEventChecks
