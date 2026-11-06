#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
javac -d "$work" app/src/main/java/uk/co/traynor/privategallery/core/calculator/*.java app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenPinThrottle.java tests/calculator/CalculatorChecks.java
java -cp "$work" CalculatorChecks
python3 tests/calculator/verify_entry_wiring.py
