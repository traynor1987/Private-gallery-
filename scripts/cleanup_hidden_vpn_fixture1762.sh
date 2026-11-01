#!/usr/bin/env bash
set -u
[[ "${GITHUB_ACTIONS:-}" == true && ( "${GITHUB_REF:-}" == refs/heads/release/1.7.6.2-ui-browser-polish || "${GITHUB_REF:-}" == refs/heads/release/1.7.7-recently-deleted || "${GITHUB_REF:-}" == refs/heads/release/1.7.8-private-apps || "${GITHUB_REF:-}" == refs/heads/release/1.7.9-evidence-locker || "${GITHUB_REF:-}" == refs/heads/release/1.7.10-private-home ) ]] || exit 1
fixture_root="$RUNNER_TEMP/private-gallery-vpn1762"
evidence="$GITHUB_WORKSPACE/app/build/hidden-vpn1762"
mkdir -p "$evidence"
# Public counters only: `wg show` may include endpoint/peer public keys but never private keys.
sudo wg show pg1762 latest-handshakes > "$evidence/handshakes.txt" 2>/dev/null || true
sudo iptables -L INPUT -nv > "$evidence/input-firewall.txt" 2>/dev/null || true
cleanup_status=0
for kind in server capture; do
  identity="$fixture_root/$kind.identity.json"
  if [[ -f "$identity" ]]; then
    sudo python3 scripts/evidence179_processes.py stop --identity "$identity" --signal "$([[ "$kind" == capture ]] && printf INT || printf TERM)" || cleanup_status=1
  elif [[ -f "$fixture_root/$kind.pid" ]]; then
    # A numeric PID without verified start/argv/output identity is never signal authority.
    printf 'Refusing unverified %s PID during fixture cleanup\n' "$kind" >&2
    cleanup_status=1
  fi
done
sudo iptables -D INPUT -d 203.0.113.10/32 ! -i pg1762 -j REJECT 2>/dev/null || true
sudo iptables -D FORWARD -i pg1762 -s 10.91.0.2/32 -j ACCEPT 2>/dev/null || true
sudo iptables -D FORWARD -o pg1762 -d 10.91.0.2/32 -j ACCEPT 2>/dev/null || true
sudo iptables -t nat -D POSTROUTING -s 10.91.0.2/32 ! -o pg1762 -j MASQUERADE 2>/dev/null || true
sudo ip link delete pg1762 2>/dev/null || true
sudo ip address delete 203.0.113.10/32 dev lo 2>/dev/null || true
sudo ip address delete 203.0.113.11/32 dev lo 2>/dev/null || true
# Exactly these generated disposable keys/configuration; never touch production signing material.
python3 - "$fixture_root" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1])
for name in ('server.key','client.key','client.conf','fixture.key','fixture.crt','fixture-ca.crt'):
 p=root/name
 if p.exists():p.unlink()
PY
sudo chmod -R a+rX "$evidence" 2>/dev/null || true

exit "$cleanup_status"
