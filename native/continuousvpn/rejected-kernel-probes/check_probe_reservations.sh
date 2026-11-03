#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
java com.sun.tools.javac.Main --release 17 -Xlint:all -Werror -d "$classes" \
 app/src/main/java/uk/co/traynor/privategallery/core/vpn/continuity/ProbeReservations.java \
 scripts/tests/vpn/ProbeReservationsChecks.java
java -ea -cp "$classes" ProbeReservationsChecks
