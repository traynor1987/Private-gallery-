// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Private Gallery contributors.

package gate

import (
	"encoding/binary"
	"net/netip"
)

// MaxPacketSize bounds a non-jumbo IPv6 packet (40-byte header + uint16 payload).
const MaxPacketSize = 65575

// Tuple identifies a held parent-owned TCP/UDP socket including its IP family.
// IPv4 and IPv6 addresses must be concrete, unzoned and from the same family.
type Tuple struct {
	Protocol    uint8
	Source      netip.AddrPort
	Destination netip.AddrPort
}

func (t Tuple) valid() bool {
	a, b := t.Source.Addr(), t.Destination.Addr()
	limitedBroadcast := netip.AddrFrom4([4]byte{255, 255, 255, 255})
	return (t.Protocol == 6 || t.Protocol == 17) && a.IsValid() && b.IsValid() && a.Zone() == "" && b.Zone() == "" &&
		!a.IsUnspecified() && !b.IsUnspecified() && !a.IsMulticast() && !b.IsMulticast() &&
		a != limitedBroadcast && b != limitedBroadcast &&
		!a.Is4In6() && !b.Is4In6() && a.Is4() == b.Is4() && t.Source.Port() != 0 && t.Destination.Port() != 0
}
func (t Tuple) reverse() Tuple {
	return Tuple{Protocol: t.Protocol, Source: t.Destination, Destination: t.Source}
}

// validIPPacket checks the bounded IP envelope only. OPEN deliberately preserves
// ordinary protocols, IPv4 options/fragments and IPv6 extension headers. It does
// not duplicate kernel/WireGuard checksum or transport validation.
func validIPPacket(p []byte) bool {
	if len(p) == 0 || len(p) > MaxPacketSize {
		return false
	}
	switch p[0] >> 4 {
	case 4:
		if len(p) < 20 {
			return false
		}
		header := int(p[0]&15) * 4
		return header >= 20 && header <= len(p) && int(binary.BigEndian.Uint16(p[2:4])) == len(p)
	case 6:
		return len(p) >= 40 && int(binary.BigEndian.Uint16(p[4:6]))+40 == len(p)
	default:
		return false
	}
}

// parseProbePacket permits a bounded, unfragmented, extension-free TCP/UDP
// packet only. IPv4 options are conservatively unsupported (e.g. source route).
// Checksums remain the stack's responsibility; packet buffers must be immutable
// for the duration of each decision/callback, as required by the pinned APIs.
func parseProbePacket(p []byte) (Tuple, bool) {
	if !validIPPacket(p) {
		return Tuple{}, false
	}
	var source, destination netip.Addr
	var protocol uint8
	offset := 0
	if p[0]>>4 == 4 {
		if p[0]&15 != 5 || binary.BigEndian.Uint16(p[6:8])&0xbfff != 0 {
			return Tuple{}, false
		}
		protocol = p[9]
		offset = 20
		source = netip.AddrFrom4([4]byte{p[12], p[13], p[14], p[15]})
		destination = netip.AddrFrom4([4]byte{p[16], p[17], p[18], p[19]})
	} else {
		protocol = p[6]
		offset = 40
		var a, b [16]byte
		copy(a[:], p[8:24])
		copy(b[:], p[24:40])
		source = netip.AddrFrom16(a)
		destination = netip.AddrFrom16(b)
	}
	payload := p[offset:]
	switch protocol {
	case 6:
		if len(payload) < 20 {
			return Tuple{}, false
		}
		header := int(payload[12]>>4) * 4
		if header < 20 || header > len(payload) {
			return Tuple{}, false
		}
	case 17:
		if len(payload) < 8 || int(binary.BigEndian.Uint16(payload[4:6])) != len(payload) {
			return Tuple{}, false
		}
	default:
		return Tuple{}, false
	}
	tuple := Tuple{protocol, netip.AddrPortFrom(source, binary.BigEndian.Uint16(payload[0:2])), netip.AddrPortFrom(destination, binary.BigEndian.Uint16(payload[2:4]))}
	return tuple, tuple.valid()
}
