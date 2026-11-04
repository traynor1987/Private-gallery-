#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
java com.sun.tools.javac.Main -Xlint:all -d "$classes" \
 app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenSession.java \
 app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenBrowserContinuation.java \
 app/src/test/java/uk/co/traynor/privategallery/core/hidden/{HiddenContinuationAuthenticationChecks,HiddenRevocationSignalChecks}.java \
 app/src/test/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenBrowserContinuationChecks.java
java -cp "$classes" uk.co.traynor.privategallery.core.hidden.HiddenContinuationAuthenticationChecks
java -cp "$classes" uk.co.traynor.privategallery.core.hidden.HiddenRevocationSignalChecks
java -cp "$classes" uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserContinuationChecks
