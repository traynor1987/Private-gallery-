// SPDX-License-Identifier: MIT
package bridge

import (
	"encoding/hex"
	"golang.zx2c4.com/wireguard/conn"
	"golang.zx2c4.com/wireguard/device"
	"net/netip"
	"strconv"
	"strings"
	"uk.co.traynor.privategallery/continuousvpn/transport"
)

// Config is a bounded exact single-peer owner configuration, never arbitrary IPC.
// Only numeric endpoints/prefixes are accepted. No DNS or fallback is performed.
type Config struct {
	PrivateKey, PeerKey, PresharedKey, Endpoint string
	AllowedIPs                                  []string
	Keepalive, ListenPort                       int
}

func key(s string, nonzero bool) bool {
	if len(s) != 64 {
		return false
	}
	b, e := hex.DecodeString(s)
	if e != nil {
		return false
	}
	if !nonzero {
		return true
	}
	for _, v := range b {
		if v != 0 {
			return true
		}
	}
	return false
}
func (c Config) options(g int64, b conn.Bind, p func(int) bool) (transport.Options, bool) {
	if g <= 0 || b == nil || p == nil || !key(c.PrivateKey, true) || !key(c.PeerKey, true) || (c.PresharedKey != "" && !key(c.PresharedKey, false)) || c.ListenPort < 0 || c.ListenPort > 65535 || c.Keepalive < 0 || c.Keepalive > 65535 || len(c.Endpoint) > 64 || len(c.AllowedIPs) < 1 || len(c.AllowedIPs) > 16 {
		return transport.Options{}, false
	}
	if _, ok := b.(conn.PeekLookAtSocketFd); !ok {
		return transport.Options{}, false
	}
	endpoint, e := netip.ParseAddrPort(c.Endpoint)
	if e != nil || endpoint.Port() == 0 || !concrete(endpoint.Addr()) {
		return transport.Options{}, false
	}
	var ipc strings.Builder
	ipc.WriteString("private_key=" + c.PrivateKey + "\nlisten_port=" + strconv.Itoa(c.ListenPort) + "\nreplace_peers=true\npublic_key=" + c.PeerKey + "\nendpoint=" + endpoint.String() + "\nreplace_allowed_ips=true\n")
	if c.PresharedKey != "" {
		ipc.WriteString("preshared_key=" + c.PresharedKey + "\n")
	}
	seen := make(map[netip.Prefix]bool)
	for _, s := range c.AllowedIPs {
		if len(s) > 64 {
			return transport.Options{}, false
		}
		prefix, e := netip.ParsePrefix(s)
		if e != nil || prefix.Addr().Is4In6() || prefix != prefix.Masked() || seen[prefix] {
			return transport.Options{}, false
		}
		seen[prefix] = true
		ipc.WriteString("allowed_ip=" + prefix.String() + "\n")
	}
	ipc.WriteString("persistent_keepalive_interval=" + strconv.Itoa(c.Keepalive) + "\n")
	var peer device.NoisePublicKey
	decoded, _ := hex.DecodeString(c.PeerKey)
	copy(peer[:], decoded)
	return transport.Options{Generation: uint64(g), Bind: b, Protect: p, IPCConfig: ipc.String(), PeerPublicKey: peer}, true
}
func concrete(a netip.Addr) bool {
	return a.IsValid() && a.Zone() == "" && !a.Is4In6() && !a.IsUnspecified() && !a.IsMulticast() && a.String() != "255.255.255.255"
}
