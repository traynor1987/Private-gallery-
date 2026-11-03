#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
java com.sun.tools.javac.Main -d "$classes" \
 app/src/main/java/uk/co/traynor/privategallery/core/browser/v2/BrowserLaunchOwnership.java \
 scripts/tests/browser/BrowserLaunchOwnershipChecks.java
java -cp "$classes" BrowserLaunchOwnershipChecks
