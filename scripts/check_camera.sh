#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
java com.sun.tools.javac.Main -Xlint:all -d "$work" app/src/main/java/uk/co/traynor/privategallery/core/camera/*.java app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenChunkStore.java app/src/test/java/uk/co/traynor/privategallery/core/camera/*Checks.java
java -cp "$work" uk.co.traynor.privategallery.core.camera.CapturePolicyChecks
if [[ -f "$work/uk/co/traynor/privategallery/core/camera/CaptureJournalChecks.class" ]]; then java -cp "$work" uk.co.traynor.privategallery.core.camera.CaptureJournalChecks; fi
