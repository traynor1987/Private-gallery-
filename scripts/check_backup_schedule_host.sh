#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
javac -Xlint:all -d "$work" app/src/main/java/uk/co/traynor/privategallery/core/backup/AutomaticBackupPolicy.java tests/backup/AutomaticBackupChecks.java
java -cp "$work" AutomaticBackupChecks
