// SPDX-License-Identifier: MIT
package transport

import (
	"net/netip"
	"testing"

	"uk.co.traynor.privategallery/continuousvpn/gate"
)

// A registered tuple is control-flow metadata, never proof of packet origin.
// This uses the actual persistent reader and virtual TUN, not a second policy.
func TestRealTunCannotImpersonatePrivateProbe(t *testing.T) {
	f, real := fixture(t, 2)
	v := virtual(t, f, 1)
	if err := v.gate.MarkOuterProtected(1); err != nil {
		t.Fatal(err)
	}
	if err := v.gate.SetState(1, gate.Probe); err != nil {
		t.Fatal(err)
	}
	tuple := gate.Tuple{Protocol: 17, Source: netip.MustParseAddrPort("10.0.0.1:4660"), Destination: netip.MustParseAddrPort("10.0.0.2:53")}
	if _, err := v.gate.AddProbe(1, 9, tuple); err != nil {
		t.Fatal(err)
	}
	feed(t, real, packet(1))
	if len(v.queue) != 0 {
		t.Fatal("REAL packet with private readiness tuple entered WG during PROBE")
	}
	p := packet(1)
	copy(p[12:16], []byte{10, 0, 0, 2})
	copy(p[16:20], []byte{10, 0, 0, 1})
	copy(p[20:24], []byte{0, 53, 0x12, 0x34})
	if n, err := v.Write(p, 0); err != nil || n != len(p) {
		t.Fatal(n, err)
	}
	real.mu.Lock()
	writes := len(real.writes)
	real.mu.Unlock()
	if writes != 0 {
		t.Fatal("private readiness reply reached REAL Android TUN during PROBE")
	}
}
