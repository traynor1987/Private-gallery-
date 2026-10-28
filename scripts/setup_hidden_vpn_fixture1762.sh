#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REF:-}" == refs/heads/release/1.7.6.2-ui-browser-polish ]]
# Dedicated disposable runner only. No production VPN configuration or signing credentials.
umask 077
fixture_root="$RUNNER_TEMP/private-gallery-vpn1762"
evidence="$GITHUB_WORKSPACE/app/build/hidden-vpn1762"
mkdir -p "$fixture_root" "$evidence"
command -v wg tcpdump iptables tor >/dev/null
/usr/bin/python3 -c "import cryptography"
wg genkey > "$fixture_root/server.key"
wg genkey > "$fixture_root/client.key"
server_public=$(wg pubkey < "$fixture_root/server.key")
client_public=$(wg pubkey < "$fixture_root/client.key")
sudo ip link add pg1762 type wireguard
sudo ip address add 10.91.0.1/24 dev pg1762
sudo ip address add 203.0.113.10/32 dev lo
# Android17 correctly blocks private LAN endpoints without ACCESS_LOCAL_NETWORK.
# This reserved public-shaped underlay address stays local to the disposable host,
# models a public VPN server, and is separate from the fail-closed synthetic origin.
# No production LAN permission or platform protection is disabled for this fixture.
sudo ip address add 203.0.113.11/32 dev lo
sudo wg set pg1762 listen-port 51820 private-key "$fixture_root/server.key" peer "$client_public" allowed-ips 10.91.0.2/32
sudo ip link set pg1762 up
sudo sysctl -w net.ipv4.ip_forward=1 > "$evidence/ip-forward.txt"
# The synthetic origin is reachable ONLY through WireGuard, even if a browser tries direct fallback.
sudo iptables -I INPUT -d 203.0.113.10/32 ! -i pg1762 -j REJECT
sudo iptables -I FORWARD -i pg1762 -s 10.91.0.2/32 -j ACCEPT
sudo iptables -I FORWARD -o pg1762 -d 10.91.0.2/32 -j ACCEPT
sudo iptables -t nat -A POSTROUTING -s 10.91.0.2/32 ! -o pg1762 -j MASQUERADE
forward_dns=$(awk '/^nameserver / {print $2;exit}' /etc/resolv.conf)
[[ "$forward_dns" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]
/usr/bin/python3 scripts/create_hidden_vpn_fixture_tls1762.py "$fixture_root"
sudo python3 scripts/hidden_vpn_fixture1762.py --evidence "$evidence" --forward-dns "$forward_dns" --tls-cert "$fixture_root/fixture.crt" --tls-key "$fixture_root/fixture.key" > "$evidence/fixture.log" 2>&1 &
printf '%s\n' "$!" > "$fixture_root/server.pid"
sudo tcpdump -i any -nn -U -s 256 '(host 203.0.113.10 or port 53 or udp port 51820)' -w "$evidence/traffic.pcap" > "$evidence/capture.log" 2>&1 &
printf '%s\n' "$!" > "$fixture_root/capture.pid"
{
  printf '[Interface]\nPrivateKey = '
  cat "$fixture_root/client.key"
  printf 'Address = 10.91.0.2/32\nDNS = 10.91.0.1\n\n[Peer]\nPublicKey = %s\nEndpoint = 203.0.113.11:51820\nAllowedIPs = 0.0.0.0/0\nPersistentKeepalive = 5\n' "$server_public"
} > "$fixture_root/client.conf"
python3 - "$evidence/ready" <<'PY'
import pathlib,sys,time
p=pathlib.Path(sys.argv[1]);deadline=time.monotonic()+15
while not p.exists() and time.monotonic()<deadline:time.sleep(.1)
assert p.exists(),'Synthetic VPN origin/DNS failed to start'
PY
printf 'READY: isolated ephemeral WireGuard/DNS/origin fixture; no owner profile used\n'
