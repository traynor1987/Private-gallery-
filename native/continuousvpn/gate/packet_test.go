// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Private Gallery contributors.

package gate

import (
	"encoding/binary"
	"fmt"
	"testing"
)

func packet4(proto uint8) []byte {
	n := 28
	if proto == 6 {
		n = 40
	}
	p := make([]byte, n)
	p[0] = 0x45
	binary.BigEndian.PutUint16(p[2:4], uint16(n))
	p[8] = 64
	p[9] = proto
	copy(p[12:16], []byte{10, 0, 0, 2})
	copy(p[16:20], []byte{1, 1, 1, 1})
	binary.BigEndian.PutUint16(p[20:22], 32001)
	binary.BigEndian.PutUint16(p[22:24], 443)
	if proto == 17 {
		binary.BigEndian.PutUint16(p[24:26], 8)
	} else if proto == 6 {
		p[32] = 0x50
	}
	return p
}
func packet4Options(proto uint8) []byte {
	base := packet4(proto)
	p := append([]byte(nil), base[:20]...)
	p = append(p, 1, 1, 1, 1) // Four NOP options precede the unchanged transport.
	p = append(p, base[20:]...)
	p[0] = 0x46
	binary.BigEndian.PutUint16(p[2:4], uint16(len(p)))
	return p
}
func packet6(proto uint8) []byte {
	v4 := packet4(proto)
	p := make([]byte, len(v4)+20)
	p[0] = 0x60
	p[6] = proto
	p[7] = 64
	binary.BigEndian.PutUint16(p[4:6], uint16(len(p)-40))
	copy(p[8:24], []byte{0xfd, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2})
	copy(p[24:40], []byte{0x26, 6, 0x47, 0, 0x47, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x11, 0x11})
	copy(p[40:], v4[20:])
	return p
}
func reversePacket(original []byte) []byte {
	p := append([]byte(nil), original...)
	off := 20
	if p[0]>>4 == 4 {
		copy(p[12:16], original[16:20])
		copy(p[16:20], original[12:16])
	} else {
		off = 40
		copy(p[8:24], original[24:40])
		copy(p[24:40], original[8:24])
	}
	copy(p[off:off+2], original[off+2:off+4])
	copy(p[off+2:off+4], original[off:off+2])
	return p
}
func tupleOffsets(ipv6 bool) []int {
	if ipv6 {
		return []int{8, 23, 24, 39, 40, 41, 42, 43}
	}
	return []int{12, 15, 16, 19, 20, 21, 22, 23}
}
func TestMalformedProbePacketsDenied(t *testing.T) {
	cases := map[string][]byte{"nil": nil, "oversized": make([]byte, 65576), "empty": {}, "short4": {0x45}, "short6": {0x60}}
	mutate := func(name string, p []byte, off int, val byte) { p[off] = val; cases[name] = p }
	mutate("unknown version", packet4(17), 0, 0x75)
	mutate("short ihl", packet4(17), 0, 0x44)
	cases["ipv4 options"] = packet4Options(17)
	mutate("v4 length short", packet4(17), 3, 27)
	mutate("v4 length long", packet4(17), 3, 29)
	mutate("more fragments", packet4(17), 6, 0x20)
	mutate("fragment offset", packet4(17), 7, 1)
	mutate("reserved fragment flag", packet4(17), 6, 0x80)
	mutate("udp length short", packet4(17), 25, 7)
	mutate("udp length mismatch", packet4(17), 25, 9)
	mutate("udp zero length", packet6(17), 45, 0)
	mutate("tcp short header", packet4(6), 32, 0x40)
	mutate("tcp overrun", packet6(6), 52, 0xf0)
	mutate("zero source port", packet4(17), 20, 0)
	cases["zero source port"][21] = 0
	mutate("v6 length short", packet6(17), 5, 7)
	mutate("v6 length long", packet6(17), 5, 9)
	for _, next := range []byte{0, 43, 44, 50, 51, 60, 59, 1} {
		mutate(fmt.Sprintf("unsupported ipv6 next header %d", next), packet6(17), 6, next)
	}
	for name, p := range cases {
		t.Run(name, func(t *testing.T) {
			if _, ok := parseProbePacket(p); ok {
				t.Fatal("malformed/unsupported packet parsed")
			}
		})
	}
	for _, p := range [][]byte{packet4(17), packet4(6), packet6(17), packet6(6)} {
		for n := 0; n < len(p); n++ {
			if _, ok := parseProbePacket(p[:n]); ok {
				t.Fatalf("truncation %d parsed", n)
			}
		}
	}
}
func TestOpenPreservesOrdinaryIPProtocolsFragmentsAndExtensions(t *testing.T) {
	_, g := newGate(t, 1)
	openGate(t, g, 1)
	ordinary4 := packet4(17)
	ordinary4[9] = 1
	fragment4 := packet4(17)
	fragment4[6] = 0x20
	extension6 := packet6(17)
	extension6[6] = 0
	ordinary6 := packet6(17)
	ordinary6[6] = 58
	for _, p := range [][]byte{packet4(6), packet4(17), packet6(6), packet6(17), ordinary4, fragment4, extension6, ordinary6, packet4Options(6), packet4Options(17)} {
		if !g.AdmitOutbound(1, p) {
			t.Fatal("ordinary connected traffic denied")
		}
		called := false
		if ok, err := g.WriteInbound(1, p, func() error { called = true; return nil }); !ok || !called || err != nil {
			t.Fatal("ordinary inbound denied", err)
		}
	}
	for _, p := range [][]byte{nil, {0x45}, make([]byte, 65576)} {
		if g.AdmitOutbound(1, p) {
			t.Fatal("invalid IP admitted open")
		}
	}
}
func FuzzProbeParserNeverPanics(f *testing.F) {
	for _, p := range [][]byte{packet4(6), packet4(17), packet6(6), packet6(17), nil} {
		f.Add(p)
	}
	f.Fuzz(func(t *testing.T, p []byte) {
		tuple, ok := parseProbePacket(p)
		if ok && (!tuple.valid() || !validIPPacket(p)) {
			t.Fatal("accepted invalid tuple or IP envelope")
		}
	})
}
