#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
java com.sun.tools.javac.Main -Xlint:all -d "$work" \
  app/src/main/java/uk/co/traynor/privategallery/core/browser/v2/BrowserExternalResultGate.java \
  app/src/main/java/uk/co/traynor/privategallery/core/browser/v2/BrowserDeviceUriPolicy.java \
  app/src/test/java/uk/co/traynor/privategallery/core/browser/v2/BrowserPermissionResultGateChecks.java
java -cp "$work" uk.co.traynor.privategallery.core.browser.v2.BrowserPermissionResultGateChecks
