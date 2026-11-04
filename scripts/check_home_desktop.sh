#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
java com.sun.tools.javac.Main -Xlint:all -d "$classes" \
 app/src/main/java/uk/co/traynor/privategallery/core/home/{HomeLayout,NovaNavigation,NovaReleaseFeatures}.java \
 app/src/main/java/uk/co/traynor/privategallery/core/home/desktop/HomeDesktop.java \
 scripts/tests/home/HomeDesktopChecks.java
java -cp "$classes" HomeDesktopChecks
