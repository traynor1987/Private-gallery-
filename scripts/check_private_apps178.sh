#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
java com.sun.tools.javac.Main -d "$classes" \
 app/src/main/java/uk/co/traynor/privategallery/core/privateapps/*.java \
 app/src/main/java/uk/co/traynor/privategallery/core/browser/v2/BrowserScreenshotPolicy.java \
 app/src/main/java/uk/co/traynor/privategallery/core/browser/v2/BrowserScreenshotBuffer.java \
 scripts/tests/browser/BrowserScreenshotChecks.java \
 app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenSession.java \
 app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenRecordStore.java \
 scripts/tests/privateapps/*.java
java -cp "$classes" PrivateAppPolicyChecks
java -cp "$classes" PrivateAppCatalogChecks
java -cp "$classes" HiddenPrivateAppStoreChecks
java -cp "$classes" PrivateAppCommandChecks
java -cp "$classes" PrivateAppIconSourceChecks
java -cp "$classes" BrowserScreenshotChecks
