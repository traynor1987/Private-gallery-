// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Private Gallery contributors.

package transport

import (
	"errors"
	"fmt"
	"sync"
	"sync/atomic"
	"syscall"

	"golang.zx2c4.com/wireguard/conn"
	"uk.co.traynor.privategallery/continuousvpn/gate"
)

var (
	ErrProtection = errors.New("outer socket protection failed")
	ErrReopen     = errors.New("outer socket reopen requires a fresh transport generation")
)

// protectedConnBind owns raw exclusively. Nobody may call raw.Open/Close/Send
// separately. lifecycle protects actual FD lifetime through both protect calls.
// It is never held over Send; closing sockets must be able to cancel that I/O.
type protectedConnBind struct {
	raw        conn.Bind
	fds        conn.PeekLookAtSocketFd
	protect    func(int) bool
	gate       *gate.Gate
	generation uint64
	lifecycle  sync.Mutex
	attempted  atomic.Bool
	ready      atomic.Bool
}

var _ conn.Bind = (*protectedConnBind)(nil)

func newProtectedConnBind(raw conn.Bind, protect func(int) bool, g *gate.Gate, generation uint64) (*protectedConnBind, error) {
	fds, ok := raw.(conn.PeekLookAtSocketFd)
	if !ok || protect == nil {
		return nil, ErrConfig
	}
	return &protectedConnBind{raw: raw, fds: fds, protect: protect, gate: g, generation: generation}, nil
}

func (b *protectedConnBind) Open(port uint16) (fns []conn.ReceiveFunc, actualPort uint16, err error) {
	b.lifecycle.Lock()
	defer b.lifecycle.Unlock()
	// The pin may request BindUpdate at any time. Never reopen old WG queues,
	// even if this generation has only reached PROBE. A new device must do it.
	if b.attempted.Swap(true) {
		b.ready.Store(false)
		b.gate.Invalidate(b.generation)
		b.raw.Close()
		return nil, 0, ErrReopen
	}
	success := false
	defer func() {
		if !success {
			b.ready.Store(false)
			b.gate.Invalidate(b.generation)
			b.raw.Close()
		}
	}()
	fns, actualPort, err = b.raw.Open(port)
	if err != nil {
		return nil, 0, err
	}
	if len(fns) == 0 {
		return nil, 0, ErrProtection
	}
	present := 0
	for _, peek := range []func() (int, error){b.fds.PeekLookAtSocketFd4, b.fds.PeekLookAtSocketFd6} {
		fd, peekErr := peek()
		// Only positive unsupported-family evidence permits absence. In
		// particular EINVAL from the pin's nil UDPConn is NOT such evidence.
		if errors.Is(peekErr, syscall.EAFNOSUPPORT) && fd < 0 {
			continue
		}
		if peekErr != nil || fd < 0 {
			return nil, 0, fmt.Errorf("%w: descriptor lookup: %v", ErrProtection, peekErr)
		}
		if !b.protect(fd) {
			return nil, 0, ErrProtection
		}
		present++
	}
	if present == 0 {
		return nil, 0, ErrProtection
	}
	if err = b.gate.MarkOuterProtected(b.generation); err != nil {
		return nil, 0, err
	}
	b.ready.Store(true)
	success = true
	return fns, actualPort, nil
}

func (b *protectedConnBind) Close() error {
	// Up's initial BindUpdate calls Close before the very first Open. That
	// empty close is allowed; any close after an attempt permanently revokes.
	if b.attempted.Load() {
		b.gate.Invalidate(b.generation)
	}
	b.lifecycle.Lock()
	defer b.lifecycle.Unlock()
	// Open may have won lifecycle after the optimistic check above.
	if b.attempted.Load() {
		b.gate.Invalidate(b.generation)
	}
	b.ready.Store(false)
	return b.raw.Close()
}

func (b *protectedConnBind) Send(packet []byte, endpoint conn.Endpoint) error {
	_, err := b.gate.OuterSend(b.generation, func() error {
		if !b.ready.Load() {
			return ErrProtection
		}
		return b.raw.Send(packet, endpoint)
	})
	return err
}

func (b *protectedConnBind) SetMark(mark uint32) error {
	b.lifecycle.Lock()
	defer b.lifecycle.Unlock()
	return b.raw.SetMark(mark)
}
func (b *protectedConnBind) ParseEndpoint(s string) (conn.Endpoint, error) {
	return b.raw.ParseEndpoint(s)
}
