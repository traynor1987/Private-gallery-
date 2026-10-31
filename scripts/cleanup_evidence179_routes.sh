#!/usr/bin/env bash
set -u
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REF:-}" == refs/heads/release/1.7.9-evidence-locker ]] || exit 1
status=0
python3 scripts/archive_evidence179_routes.py || status=$?
python3 scripts/ci_onion_probe.py --stop || status=$?
bash scripts/cleanup_hidden_vpn_fixture1762.sh || status=$?
adb shell rm -f /data/local/tmp/hidden-vpn1762.conf >/dev/null 2>&1 || true
exit "$status"
