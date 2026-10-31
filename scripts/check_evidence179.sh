#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
# Domain/authority checks use the JDK only. Archive BC crypto compiles and runs in
# the mandatory Android lane; its independent pure format/ZIP checks have a separate script.
evidence_sources=()
for name in EvidenceCatalog EvidenceCodec EvidenceEncryptedStore EvidenceAuthorities EvidenceTextHints EvidenceStrictJson; do
 evidence_sources+=("app/src/main/java/uk/co/traynor/privategallery/core/evidence/$name.java")
done
java com.sun.tools.javac.Main -Xlint:all -d "$classes" \
 app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenRecordStore.java \
 app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenSession.java \
 "${evidence_sources[@]}" scripts/tests/Evidence179Checks.java \
 scripts/tests/EvidenceAuthorities179Checks.java scripts/tests/EvidenceTextHints179Checks.java
java --add-opens java.base/java.io=ALL-UNNAMED -cp "$classes" Evidence179Checks
java -cp "$classes" EvidenceAuthorities179Checks
java -cp "$classes" EvidenceTextHints179Checks
