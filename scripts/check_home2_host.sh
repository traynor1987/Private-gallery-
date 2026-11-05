#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
javac -d "$work" app/src/main/java/uk/co/traynor/privategallery/home2/*.java tests/home2/*Checks.java
for check in PreviewRulesChecks ShellRulesChecks MindRulesChecks WidgetRulesChecks PolishRulesChecks CorrectionRulesChecks; do
  java -cp "$work" "$check"
done
python3 tests/home2/verify_space_preferences.py
