#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
archive_classes=$(mktemp -d)
trap 'rm -rf "$archive_classes"' EXIT
java com.sun.tools.javac.Main -Xlint:all -d "$archive_classes" \
 app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenRecordStore.java \
 app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceEncryptedStore.java \
 app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceArchiveFormat.java \
 app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceCatalog.java \
 app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceCodec.java \
 app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceWipingBuffer.java \
 tests/evidence179/EvidenceArchiveFormatHostTest.java \
 tests/evidence179/EvidenceHistory179HostTest.java \
 tests/evidence179/EvidenceWipingBufferHostTest.java \
 tests/evidence179/EvidenceMigration179HostTest.java
java -cp "$archive_classes" uk.co.traynor.privategallery.core.evidence.EvidenceArchiveFormatHostTest
java -cp "$archive_classes" uk.co.traynor.privategallery.core.evidence.EvidenceHistory179HostTest
java -cp "$archive_classes" uk.co.traynor.privategallery.core.evidence.EvidenceWipingBufferHostTest
java -cp "$archive_classes" uk.co.traynor.privategallery.core.evidence.EvidenceMigration179HostTest
