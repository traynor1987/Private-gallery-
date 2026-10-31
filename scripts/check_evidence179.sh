#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
mapfile -t evidence_sources < <(find app/src/main/java/uk/co/traynor/privategallery/core/evidence -name '*.java' -print)
java com.sun.tools.javac.Main -Xlint:all -d "$classes" \
 app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenRecordStore.java \
 app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenSession.java \
 "${evidence_sources[@]}" scripts/tests/Evidence179Checks.java \
 scripts/tests/EvidenceAuthorities179Checks.java scripts/tests/EvidenceTextHints179Checks.java
java --add-opens java.base/java.io=ALL-UNNAMED -cp "$classes" Evidence179Checks
java -cp "$classes" EvidenceAuthorities179Checks
java -cp "$classes" EvidenceTextHints179Checks
