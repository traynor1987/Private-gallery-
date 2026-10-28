#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
java com.sun.tools.javac.Main -Xlint:all -d "$work" app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenRecordStore.java app/src/main/java/uk/co/traynor/privategallery/core/media/ExternalEditRecoveryStore.java app/src/test/java/uk/co/traynor/privategallery/core/media/ExternalEditRecoveryChecks.java
java -cp "$work" uk.co.traynor.privategallery.core.media.ExternalEditRecoveryChecks
