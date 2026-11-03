// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Private Gallery contributors.
package transport

import (
	"bytes"
	"strconv"
)

// Statistics contains numeric fields only. The private raw UAPI never escapes
// this accessor or enters logs/errors. Values fit a positive Java signed long.
type Statistics struct{ HandshakeSeconds, HandshakeNanos, RxBytes, TxBytes int64 }
type boundedStatistics struct{ data []byte }

func (b *boundedStatistics) Write(p []byte) (int, error) {
	if len(b.data)+len(p) > 4096 {
		return 0, ErrConfig
	}
	b.data = append(b.data, p...)
	return len(p), nil
}

// Statistics may block on configuration and device locks. Never call it under
// an authentication/session monitor. The bridge bounds configuration to one
// peer and sixteen routes; the writer independently rejects oversized output.
func (t *Transport) Statistics() (Statistics, error) {
	t.fence.owner.Lock()
	defer t.fence.owner.Unlock()
	if t.closed || t.fence.current != t || t.device == nil {
		return Statistics{}, ErrClosed
	}
	b := new(boundedStatistics)
	defer func() { zero(b.data) }()
	if e := t.device.IpcGetOperation(b); e != nil {
		return Statistics{}, ErrConfig
	}
	return parseStatistics(b.data)
}
func parseStatistics(raw []byte) (Statistics, error) {
	var out Statistics
	seen := make(map[string]bool)
	peers := 0
	for _, line := range bytes.Split(raw, []byte{'\n'}) {
		pair := bytes.SplitN(line, []byte{'='}, 2)
		if len(pair) != 2 {
			continue
		}
		name := string(pair[0])
		var target *int64
		switch name {
		case "public_key":
			peers++
			continue
		case "last_handshake_time_sec":
			target = &out.HandshakeSeconds
		case "last_handshake_time_nsec":
			target = &out.HandshakeNanos
		case "rx_bytes":
			target = &out.RxBytes
		case "tx_bytes":
			target = &out.TxBytes
		default:
			continue
		}
		if seen[name] {
			return Statistics{}, ErrConfig
		}
		seen[name] = true
		v, e := strconv.ParseInt(string(pair[1]), 10, 64)
		if e != nil || v < 0 || (name == "last_handshake_time_nsec" && v >= 1e9) {
			return Statistics{}, ErrConfig
		}
		*target = v
	}
	if peers != 1 || len(seen) != 4 {
		return Statistics{}, ErrConfig
	}
	return out, nil
}
