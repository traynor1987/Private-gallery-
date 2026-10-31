#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
archive_classes=$(mktemp -d)
trap 'rm -rf "$archive_classes"' EXIT
java com.sun.tools.javac.Main -Xlint:all -d "$archive_classes" \
 app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceArchiveFormat.java \
 tests/evidence179/EvidenceArchiveFormatHostTest.java
java -cp "$archive_classes" uk.co.traynor.privategallery.core.evidence.EvidenceArchiveFormatHostTest
