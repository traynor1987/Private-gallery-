// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Private Gallery contributors.

// Package gate supplies a syscall-free, fail-closed admission boundary for the
// pinned WireGuard single-packet interfaces. The trusted owner alone may control
// it. A Gate belongs to exactly one transport/device, never to a reusable TUN.
package gate

import (
	"errors"
	"sync"
	"sync/atomic"
)

// State is an admission mode. Zero values deny traffic.
type State uint8

const (
	Closed State = iota
	Probe
	Open
	MaxProbeLeases = 128
)

var (
	ErrGeneration = errors.New("invalid or stale transport generation")
	ErrState      = errors.New("invalid, sealed, or unprotected admission transition")
	ErrLease      = errors.New("invalid, stale, or duplicate probe lease")
	ErrLimit      = errors.New("probe lease bound reached")
	ErrCallback   = errors.New("nil I/O callback")
)

// Registry is a zero-value-usable, owner-private generation namespace. Do not
// replace it while retaining any previous transport: its high-water mark must
// outlive all handles. NewGate retires and drains the previous handle before
// returning a new CLOSED one. Call outside Java/session/ownership monitors.
type Registry struct {
	mu      sync.Mutex
	high    uint64
	current *Gate
}

func (r *Registry) NewGate(generation uint64) (*Gate, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if generation == 0 || generation <= r.high {
		return nil, ErrGeneration
	}
	if r.current != nil {
		if err := r.current.Close(r.high); err != nil {
			return nil, err
		}
	}
	g := &Gate{generation: generation, leases: make(map[LeaseID]probeLease)}
	g.idle = sync.NewCond(&g.mu)
	g.current.Store(&admission{state: Closed, sequence: 1})
	r.high = generation
	r.current = g
	return g, nil
}

type admission struct {
	state    State
	sequence uint64
	sealed   bool
}
type probeLease struct {
	epoch uint64
	tuple Tuple
}

// LeaseID is opaque and bound to one Gate. Zero and foreign IDs are invalid.
type LeaseID struct {
	owner  *Gate
	serial uint64
}

// Gate must not be copied. Its zero value always denies. No method performs a
// network syscall or holds a lock over an I/O callback. The integrator must call
// AdmitOutbound only AFTER a blocking TUN Read has returned, and OuterSend at the
// actual encrypted UDP write (including handshake/keepalive/control packets).
type Gate struct {
	generation uint64
	current    atomic.Pointer[admission]
	active     atomic.Int64
	mu         sync.Mutex
	idle       *sync.Cond
	protected  bool
	nextLease  uint64
	leases     map[LeaseID]probeLease
}

func (g *Gate) matches(generation uint64) bool {
	return g != nil && generation != 0 && generation == g.generation && g.current.Load() != nil
}

// MarkOuterProtected records successful protection of BOTH outer sockets. Only
// the owner that checked those results may call it, before activating the peer.
func (g *Gate) MarkOuterProtected(generation uint64) error {
	if !g.matches(generation) {
		return ErrGeneration
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	s := g.current.Load()
	if s.sealed || s.state != Closed {
		return ErrState
	}
	g.protected = true
	return nil
}

// SetState accepts only initial CLOSED -> PROBE. OPEN requires Publish and its
// captured revocation sequence. CLOSED means permanent Invalidate+Drain, rather
// than a pause that could release old encrypted queues on this same handle.
func (g *Gate) SetState(generation uint64, state State) error {
	if !g.matches(generation) {
		return ErrGeneration
	}
	if state == Closed {
		return g.Close(generation)
	}
	if state != Probe {
		return ErrState
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	s := g.current.Load()
	if s.sealed || s.state != Closed || !g.protected {
		return ErrState
	}
	if !g.current.CompareAndSwap(s, &admission{state: Probe, sequence: s.sequence}) {
		return ErrState
	}
	return nil
}

// RevocationSequence captures the token needed for authenticated publication.
func (g *Gate) RevocationSequence(generation uint64) (uint64, error) {
	if !g.matches(generation) {
		return 0, ErrGeneration
	}
	s := g.current.Load()
	if s.sealed {
		return s.sequence, ErrState
	}
	return s.sequence, nil
}

// Publish atomically opens only the same still-live PROBE admission observed by
// the owner transaction. Invalidate wins against a stale captured sequence. This
// is not authentication: Java must also validate epoch/owner/presentation/grace.
func (g *Gate) Publish(generation, expectedSequence uint64) error {
	if !g.matches(generation) {
		return ErrGeneration
	}
	s := g.current.Load()
	if s.sealed || s.state != Probe || expectedSequence == 0 || s.sequence != expectedSequence {
		return ErrState
	}
	if !g.current.CompareAndSwap(s, &admission{state: Open, sequence: s.sequence}) {
		return ErrState
	}
	return nil
}

// Invalidate is lock-free and never waits for callbacks. It permanently closes
// admission and advances its revocation sequence. Calls already admitted may
// still be running: call Drain OUTSIDE session/ownership monitors before any
// close acknowledgement, device disposal, route release or tuple FD release.
func (g *Gate) Invalidate(generation uint64) (uint64, error) {
	if !g.matches(generation) {
		return 0, ErrGeneration
	}
	for {
		s := g.current.Load()
		if s.sealed {
			return s.sequence, nil
		}
		next := &admission{state: Closed, sequence: s.sequence + 1, sealed: true}
		if g.current.CompareAndSwap(s, next) {
			return next.sequence, nil
		}
	}
}

// Drain waits for all admitted encrypted sends AND inbound plaintext writes.
// It requires prior invalidation. A caller must not be an I/O callback itself.
// After acknowledgement no callback can begin or remain active on this handle.
func (g *Gate) Drain(generation uint64) error {
	if !g.matches(generation) {
		return ErrGeneration
	}
	if !g.current.Load().sealed {
		return ErrState
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	for g.active.Load() != 0 {
		g.idle.Wait()
	}
	clearLeases(g.leases)
	return nil
}
func clearLeases(leases map[LeaseID]probeLease) {
	for id := range leases {
		delete(leases, id)
	}
}

// Close is the synchronous Invalidate+Drain barrier, outside session locks.
func (g *Gate) Close(generation uint64) error {
	if _, err := g.Invalidate(generation); err != nil {
		return err
	}
	return g.Drain(generation)
}

// Retire permanently closes this transport; a new generation needs a new Gate
// and new WireGuard device/queues, with the old TUN reader stopped first.
func (g *Gate) Retire(generation uint64) error { return g.Close(generation) }

// AddProbe registers a concrete socket tuple in PROBE only. The trusted owner
// must bind and retain its real non-reusable socket before registration, and
// register before connect/send. Each DNS TCP fallback needs a separate lease.
func (g *Gate) AddProbe(generation, epoch uint64, tuple Tuple) (LeaseID, error) {
	if !g.matches(generation) {
		return LeaseID{}, ErrGeneration
	}
	if epoch == 0 || !tuple.valid() {
		return LeaseID{}, ErrLease
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	s := g.current.Load()
	if s.sealed || s.state != Probe {
		return LeaseID{}, ErrState
	}
	if len(g.leases) >= MaxProbeLeases || g.nextLease == ^uint64(0) {
		return LeaseID{}, ErrLimit
	}
	for _, lease := range g.leases {
		if lease.tuple == tuple || lease.tuple == tuple.reverse() {
			return LeaseID{}, ErrLease
		}
	}
	g.nextLease++
	id := LeaseID{g, g.nextLease}
	g.leases[id] = probeLease{epoch, tuple}
	if g.current.Load() != s {
		delete(g.leases, id)
		return LeaseID{}, ErrState
	}
	return id, nil
}

// RemoveProbe removes both directions before returning. It must precede closing
// or releasing the Java FD. It does not recall packets already admitted into WG
// queues or writes already executing: use Invalidate+Drain and device retirement
// when that stronger guarantee is required. Never use tuple secrecy as authority.
func (g *Gate) RemoveProbe(generation, epoch uint64, id LeaseID) error {
	if !g.matches(generation) {
		return ErrGeneration
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	lease, ok := g.leases[id]
	if !ok || id.owner != g || epoch == 0 || lease.epoch != epoch {
		return ErrLease
	}
	delete(g.leases, id)
	return nil
}
func (g *Gate) admitted(generation uint64, packet []byte, inbound bool) bool {
	if !g.matches(generation) {
		return false
	}
	s := g.current.Load()
	if s.sealed {
		return false
	}
	if s.state == Open {
		return validIPPacket(packet) && g.current.Load() == s
	}
	if s.state != Probe {
		return false
	}
	tuple, ok := parseProbePacket(packet)
	if !ok {
		return false
	}
	if inbound {
		tuple = tuple.reverse()
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.current.Load() != s {
		return false
	}
	for _, lease := range g.leases {
		if lease.tuple == tuple {
			return true
		}
	}
	return false
}

// AdmitOutbound is a post-Read plaintext decision, not a send barrier. Denied
// packets must be consumed/dropped without returning a fatal TUN Read error.
func (g *Gate) AdmitOutbound(generation uint64, packet []byte) bool {
	return g.admitted(generation, packet, false)
}
func (g *Gate) finish() {
	if g.active.Add(-1) == 0 {
		g.mu.Lock()
		g.idle.Broadcast()
		g.mu.Unlock()
	}
}

// WriteInbound brackets the actual TUN Write, separately from Read admission.
// False,nil means consumed/dropped. The wrapper translates this to the pinned
// single-packet Write byte count; callback errors pass through unchanged.
func (g *Gate) WriteInbound(generation uint64, packet []byte, write func() error) (bool, error) {
	if !g.matches(generation) {
		return false, nil
	}
	if write == nil {
		return false, ErrCallback
	}
	g.active.Add(1)
	defer g.finish()
	if !g.admitted(generation, packet, true) {
		return false, nil
	}
	return true, write()
}

// OuterSend brackets the ACTUAL conn.Bind.Send write. PROBE permits encrypted
// handshake/probe records; their inner tuples cannot be recovered here. Old WG
// queues must therefore never be rebound to a fresh Gate. False,nil is a drop.
func (g *Gate) OuterSend(generation uint64, send func() error) (bool, error) {
	if !g.matches(generation) {
		return false, nil
	}
	if send == nil {
		return false, ErrCallback
	}
	g.active.Add(1)
	defer g.finish()
	s := g.current.Load()
	if s.sealed || (s.state != Probe && s.state != Open) {
		return false, nil
	}
	return true, send()
}
