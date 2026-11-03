// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Private Gallery contributors.

// Package transport implements a persistent, draining TUN fence and disposable
// WireGuard generations. All control handles belong to a trusted owner. Android
// authentication, child lifetime descriptors and probe socket reservations must
// be implemented by the caller; this package is not an Android containment proof.
package transport

import (
	"errors"
	"fmt"
	"os"
	"sync"

	"golang.zx2c4.com/wireguard/tun"
	"uk.co.traynor.privategallery/continuousvpn/gate"
)

const maxPacketSize = 65575

var (
	ErrClosed = errors.New("fence or transport is closed")
	ErrBusy   = errors.New("retire the attached transport before releasing the fence")
	ErrConfig = errors.New("invalid transport configuration")
)

// Fence owns real exclusively after NewFence succeeds. No other component may
// read or close that device. Exactly one goroutine consumes it for this lifetime.
// Routine transport retirement never closes real. Do not copy a Fence.
type Fence struct {
	real       tun.Device
	mtu        int
	name       string
	capacity   int
	registry   gate.Registry
	owner      sync.Mutex // serializes replacement, retirement and explicit release
	mu         sync.Mutex // target dispatch, failure and release state; never held over I/O
	target     *virtualTun
	current    *Transport
	children   map[*virtualTun]struct{}
	failure    error
	released   bool
	errors     chan error
	readerDone chan struct{}
}

func NewFence(real tun.Device, queueCapacity int) (*Fence, error) {
	if real == nil || queueCapacity < 1 || queueCapacity > 1024 {
		return nil, ErrConfig
	}
	mtu, err := real.MTU()
	if err != nil {
		return nil, err
	}
	if mtu <= 0 || mtu > maxPacketSize {
		return nil, ErrConfig
	}
	name, err := real.Name()
	if err != nil {
		return nil, err
	}
	f := &Fence{real: real, mtu: mtu, name: name, capacity: queueCapacity, children: make(map[*virtualTun]struct{}), errors: make(chan error, 1), readerDone: make(chan struct{})}
	go f.readLoop()
	return f, nil
}

// Errors reports the first fatal lifetime TUN failure. Failure closes admission
// and retains the real device. The owner must retire children before Release.
func (f *Fence) Errors() <-chan error { return f.errors }

func (f *Fence) readLoop() {
	defer close(f.readerDone)
	buffer := make([]byte, maxPacketSize)
	defer zero(buffer)
	for {
		n, err := f.real.Read(buffer, 0)
		if err != nil {
			f.fail(fmt.Errorf("lifetime TUN read: %w", err))
			return
		}
		if n < 0 || n > len(buffer) {
			f.fail(errors.New("invalid lifetime TUN read length"))
			return
		}
		f.mu.Lock()
		if v := f.target; v != nil && n > 0 {
			v.enqueue(buffer[:n])
		}
		f.mu.Unlock()
		zero(buffer[:n])
	}
}

func (f *Fence) fail(err error) {
	f.mu.Lock()
	if f.released || f.failure != nil {
		f.mu.Unlock()
		return
	}
	f.failure = err
	v := f.target
	if v != nil {
		v.gate.Invalidate(v.generation)
	}
	f.mu.Unlock()
	if v != nil {
		v.Close()
	}
	f.errors <- err
}

func (f *Fence) attach(g *gate.Gate, generation uint64) (*virtualTun, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.released || f.failure != nil {
		return nil, ErrClosed
	}
	if f.target != nil {
		return nil, ErrBusy
	}
	v := &virtualTun{fence: f, gate: g, generation: generation, queue: make(chan []byte, f.capacity), ready: make(chan struct{}, 1), done: make(chan struct{}), events: make(chan tun.Event)}
	f.target = v
	f.children[v] = struct{}{}
	return v, nil
}

// Release is the only operation allowed to close the real TUN. The external
// lifetime owner must first prove sensitive child retirement and close its own
// keeper descriptors in the separately reviewed Android order. Release refuses
// an attached generation and waits for retired plaintext writes before closing.
// A stuck write leaves this call waiting and the fence retained, never falsely
// acknowledging denial. Call outside session monitors.
func (f *Fence) Release() error {
	f.owner.Lock()
	defer f.owner.Unlock()
	f.mu.Lock()
	if f.released {
		f.mu.Unlock()
		return nil
	}
	if f.target != nil {
		f.mu.Unlock()
		return ErrBusy
	}
	children := make([]*virtualTun, 0, len(f.children))
	for v := range f.children {
		children = append(children, v)
	}
	f.mu.Unlock()
	if f.current != nil {
		if err := f.current.closeLocked(); err != nil {
			// Retain the failed generation and lifetime TUN. Never turn an
			// unacknowledged transport barrier into a lifetime release receipt.
			return err
		}
	}
	for _, v := range children {
		v.gate.Invalidate(v.generation)
		if err := v.gate.Drain(v.generation); err != nil {
			return err
		}
	}
	f.mu.Lock()
	f.released = true
	f.mu.Unlock()
	err := f.real.Close()
	<-f.readerDone
	return err
}

func zero(b []byte) {
	for i := range b {
		b[i] = 0
	}
}

// virtualTun never exports or closes the lifetime descriptor. queue is never
// closed: enqueue, dequeue and disposal share mu, while done cancels Read.
// f.mu -> v.mu -> gate.mu is the only nested lock order.
type virtualTun struct {
	fence      *Fence
	gate       *gate.Gate
	generation uint64
	mu         sync.Mutex
	closed     bool
	queue      chan []byte
	ready      chan struct{}
	done       chan struct{}
	events     chan tun.Event
}

var _ tun.Device = (*virtualTun)(nil)

func (v *virtualTun) enqueue(packet []byte) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.closed || !v.gate.AdmitOutbound(v.generation, packet) {
		return
	}
	owned := append([]byte(nil), packet...)
	select {
	case v.queue <- owned:
		select {
		case v.ready <- struct{}{}:
		default:
		}
	default:
		zero(owned)
	}
}

func (v *virtualTun) File() *os.File           { return nil }
func (v *virtualTun) Name() (string, error)    { return v.fence.name, nil }
func (v *virtualTun) MTU() (int, error)        { return v.fence.mtu, nil }
func (v *virtualTun) Events() <-chan tun.Event { return v.events }
func (v *virtualTun) Flush() error             { return nil } // Matches pin; NOT a queue purge.

func (v *virtualTun) Read(b []byte, offset int) (int, error) {
	if offset < 0 || offset > len(b) {
		return 0, ErrConfig
	}
	for {
		select {
		case <-v.done:
			return 0, os.ErrClosed
		case <-v.ready:
		}
		v.mu.Lock()
		if v.closed {
			v.mu.Unlock()
			return 0, os.ErrClosed
		}
		for len(v.queue) > 0 {
			packet := <-v.queue
			if !v.gate.AdmitOutbound(v.generation, packet) {
				zero(packet)
				continue
			}
			// Never truncate a packet into a seemingly valid WireGuard payload.
			if len(packet) > len(b)-offset {
				zero(packet)
				continue
			}
			n := copy(b[offset:], packet)
			zero(packet)
			if len(v.queue) > 0 {
				select {
				case v.ready <- struct{}{}:
				default:
				}
			}
			v.mu.Unlock()
			return n, nil
		}
		v.mu.Unlock()
	}
}

func (v *virtualTun) Write(b []byte, offset int) (int, error) {
	if offset < 0 || offset > len(b) {
		return 0, ErrConfig
	}
	n := len(b) - offset
	_, err := v.gate.WriteInbound(v.generation, b[offset:], func() error {
		var err error
		n, err = v.fence.real.Write(b, offset)
		if err != nil {
			v.fence.fail(fmt.Errorf("lifetime TUN write: %w", err))
		}
		return err
	})
	return n, err // A denied packet is successfully consumed by this single-packet API.
}

func (v *virtualTun) Close() error {
	v.gate.Invalidate(v.generation)
	v.fence.mu.Lock()
	defer v.fence.mu.Unlock()
	if v.fence.target == v {
		v.fence.target = nil
	}
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.closed {
		return nil
	}
	v.closed = true
	for len(v.queue) > 0 {
		zero(<-v.queue)
	}
	close(v.done)
	close(v.events)
	return nil
}
