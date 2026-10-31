#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
task4_classes=$(mktemp -d)
trap 'rm -rf "$task4_classes"' EXIT
java com.sun.tools.javac.Main -Xlint:all -d "$task4_classes" \
 app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceStrictJson.java \
 scripts/tests/EvidenceStrictJson179Checks.java
java -cp "$task4_classes" EvidenceStrictJson179Checks
