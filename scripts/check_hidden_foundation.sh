#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
java com.sun.tools.javac.Main -Xlint:all -d "$work" \
  app/src/main/java/uk/co/traynor/privategallery/core/hidden/*.java \
  app/src/test/java/uk/co/traynor/privategallery/core/hidden/HiddenFoundationChecks.java \
  app/src/test/java/uk/co/traynor/privategallery/core/hidden/SyntheticGalleryChecks.java \
  app/src/test/java/uk/co/traynor/privategallery/core/hidden/HiddenOperationChecks.java \
  app/src/test/java/uk/co/traynor/privategallery/core/hidden/HiddenTimeoutChecks.java \
  app/src/test/java/uk/co/traynor/privategallery/core/hidden/HiddenSyncChecks.java \
  app/src/test/java/uk/co/traynor/privategallery/core/hidden/HiddenRecordChecks.java \
  app/src/test/java/uk/co/traynor/privategallery/core/hidden/HiddenChunkChecks.java
java -cp "$work" uk.co.traynor.privategallery.core.hidden.HiddenFoundationChecks
java -cp "$work" uk.co.traynor.privategallery.core.hidden.SyntheticGalleryChecks

java -cp "$work" uk.co.traynor.privategallery.core.hidden.HiddenOperationChecks
java -cp "$work" uk.co.traynor.privategallery.core.hidden.HiddenRecordChecks

java -cp "$work" uk.co.traynor.privategallery.core.hidden.HiddenSyncChecks

java -cp "$work" uk.co.traynor.privategallery.core.hidden.HiddenTimeoutChecks

java -Xmx48m -cp "$work" uk.co.traynor.privategallery.core.hidden.HiddenChunkChecks
