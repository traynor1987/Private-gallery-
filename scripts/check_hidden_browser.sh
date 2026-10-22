#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
java com.sun.tools.javac.Main -Xlint:all -d "$work" app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/*.java app/src/test/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenBrowserPolicyChecks.java
java -cp "$work" uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserPolicyChecks
