#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
java com.sun.tools.javac.Main -Xlint:all -d "$work" \
  app/src/main/java/uk/co/traynor/privategallery/core/browser/v2/IsoBmffCompleteness.java \
  app/src/test/java/uk/co/traynor/privategallery/core/browser/v2/IsoBmffCompletenessChecks.java
java -cp "$work" uk.co.traynor.privategallery.core.browser.v2.IsoBmffCompletenessChecks \
  app/src/androidTest/assets/browser-media/valid.mp4
