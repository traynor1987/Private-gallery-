#!/usr/bin/env bash
set -u
[[ "${GITHUB_ACTIONS:-}" == true && ( "${GITHUB_REF:-}" == refs/heads/release/1.7.6.2-ui-browser-polish || "${GITHUB_REF:-}" == refs/heads/release/1.7.7-recently-deleted ) ]] || exit 1
fixture_root="$RUNNER_TEMP/private-gallery-vpn1762"
evidence="$GITHUB_WORKSPACE/app/build/hidden-vpn1762"
mkdir -p "$evidence"
# Public counters only: `wg show` may include endpoint/peer public keys but never private keys.
sudo wg show pg1762 latest-handshakes > "$evidence/handshakes.txt" 2>/dev/null || true
sudo iptables -L INPUT -nv > "$evidence/input-firewall.txt" 2>/dev/null || true
for kind in server capture; do
  if [[ -f "$fixture_root/$kind.pid" ]]; then
    fixture_pid=$(cat "$fixture_root/$kind.pid")
    [[ "$fixture_pid" =~ ^[0-9]+$ ]] && sudo kill "$fixture_pid" 2>/dev/null || true
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
