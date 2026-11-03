// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Private Gallery contributors.

// Package bridge is owner-private. It does not authenticate callers or prove
// child death. Epoch, Sequence, Publish and the two Invalidate methods use only
// immutable registry snapshots and atomic payload/current pointers: no mutex,
// Java callback, TUN I/O or blocking close on those monitor-safe paths.
package bridge

import (
	"math"
	"net/netip"
	"sync/atomic"

	"golang.zx2c4.com/wireguard/conn"
	"golang.zx2c4.com/wireguard/tun"
	"uk.co.traynor.privategallery/continuousvpn/gate"
	"uk.co.traynor.privategallery/continuousvpn/transport"
)

const (
	OK        int64 = 0
	Invalid   int64 = -1
	Denied    int64 = -2
	Busy      int64 = -3
	Failed    int64 = -4
	Exhausted int64 = -5
	// Active entries, pending reservations and small retired/removal receipts all
	// count. Receipts last until their fence's successful final release. Capacity
	// exhaustion rejects new work before touching an existing transport; handles
	// are never recycled, including after all metadata for a fence is reclaimed.
	MaxRegistryEntries = 4096
)

// Protector ownership transfers to Replace on every result. Release follows the
// actual corresponding retirement barrier. Protect may only protect the FD;
// it must not reenter the bridge, owner or authentication/session operations.
type Protector interface {
	Protect(int) bool
	Release()
}
type snapshot struct{ entries map[int64]any }
type reservation struct{ fenceID int64 }
type Registry struct {
	next    atomic.Int64
	entries atomic.Pointer[snapshot]
}
type fence struct {
	id            int64
	core          atomic.Pointer[transport.Fence]
	current       atomic.Pointer[instance]
	epoch         atomic.Int64
	exhausted     atomic.Bool
	released      atomic.Bool
	busy          atomic.Bool // Nonwaiting control exclusion; never blocks monitor invalidation.
	high          int64       // Only the successful busy CAS owner accesses this value.
	releaseFailed bool        // A failed final close cannot be converted into a receipt by retry.
}

// Instance/lease authority is numeric only. Reclaimed receipts cannot retain a
// fence/device/key/protector graph. Readers load one immutable payload into a
// local Go reference before use; Swap(nil) never mutates a reader's payload.
type instance struct {
	id, fenceID, generation, epoch int64
	payload                        atomic.Pointer[transportPayload]
}
type transportPayload struct {
	core      *transport.Transport
	protector Protector
}
type lease struct {
	fenceID, transportID, generation, epoch int64
	payload                                 atomic.Pointer[leasePayload]
}
type leasePayload struct {
	core  gate.LeaseID
	tuple gate.Tuple
}

func (r *Registry) allocate() int64 {
	for {
		old := r.next.Load()
		if old == math.MaxInt64 {
			return Exhausted
		}
		if r.next.CompareAndSwap(old, old+1) {
			return old + 1
		}
	}
}

// reserve publishes only a numeric, non-authority placeholder and checks the
// practical global bound atomically. Replace reserves BEFORE destructive work.
func (r *Registry) reserve(fh int64) int64 {
	id := r.allocate()
	if id <= 0 {
		return id
	}
	if fh == 0 {
		fh = id
	}
	for {
		old := r.entries.Load()
		if old != nil && len(old.entries) >= MaxRegistryEntries {
			return Exhausted
		}
		next := copySnapshot(old)
		next.entries[id] = reservation{fenceID: fh}
		if r.entries.CompareAndSwap(old, next) {
			return id
		}
	}
}
func copySnapshot(old *snapshot) *snapshot {
	next := &snapshot{entries: make(map[int64]any)}
	if old != nil {
		for id, v := range old.entries {
			next.entries[id] = v
		}
	}
	return next
}

// commit cannot resurrect a reservation pruned by a concurrent final release.
func (r *Registry) commit(id int64, v any) bool {
	for {
		old := r.entries.Load()
		if old == nil {
			return false
		}
		if _, ok := old.entries[id].(reservation); !ok {
			return false
		}
		next := copySnapshot(old)
		next.entries[id] = v
		if r.entries.CompareAndSwap(old, next) {
			return true
		}
	}
}
func (r *Registry) drop(id int64) {
	for {
		old := r.entries.Load()
		if old == nil || old.entries[id] == nil {
			return
		}
		next := copySnapshot(old)
		delete(next.entries, id)
		if r.entries.CompareAndSwap(old, next) {
			return
		}
	}
}
func belongs(v any, fh int64) bool {
	switch v := v.(type) {
	case *fence:
		return v.id == fh
	case *instance:
		return v.fenceID == fh
	case *lease:
		return v.fenceID == fh
	case reservation:
		return v.fenceID == fh
	}
	return false
}
func (r *Registry) dropFence(fh int64) {
	for {
		old := r.entries.Load()
		if old == nil {
			return
		}
		next := &snapshot{entries: make(map[int64]any)}
		for id, v := range old.entries {
			if !belongs(v, fh) {
				next.entries[id] = v
			}
		}
		if r.entries.CompareAndSwap(old, next) {
			return
		}
	}
}
func (r *Registry) get(id int64) any {
	if id <= 0 {
		return nil
	}
	s := r.entries.Load()
	if s == nil {
		return nil
	}
	return s.entries[id]
}
func (r *Registry) fence(id int64) *fence {
	f, _ := r.get(id).(*fence)
	if f == nil || f.released.Load() {
		return nil
	}
	return f
}
func (r *Registry) instance(fh, th int64) *instance {
	if r.fence(fh) == nil {
		return nil
	}
	t, _ := r.get(th).(*instance)
	if t == nil || t.fenceID != fh {
		return nil
	}
	return t
}
func (r *Registry) current(fh, th, e int64) (*fence, *instance, *transportPayload) {
	f := r.fence(fh)
	t, _ := r.get(th).(*instance)
	if f == nil || t == nil || t.fenceID != fh || e <= 0 || t.epoch != e || f.exhausted.Load() || f.epoch.Load() != e || f.current.Load() != t {
		return nil, nil, nil
	}
	p := t.payload.Load()
	if p == nil || p.core == nil {
		return nil, nil, nil
	}
	return f, t, p
}

// create consumes device on every result. Exactly one reader owns the lifetime
// TUN after NewFence. Even creation reserves registry capacity before adoption.
func (r *Registry) create(d tun.Device) int64 {
	if d == nil {
		return Invalid
	}
	id := r.reserve(0)
	if id <= 0 {
		d.Close()
		return id
	}
	core, err := transport.NewFence(d, 64)
	if err != nil {
		r.drop(id)
		d.Close()
		return Failed
	}
	f := &fence{id: id}
	f.core.Store(core)
	f.epoch.Store(1)
	r.commit(id, f)
	return id
}

// Capture Epoch inside authenticated replacement admission; never refresh it in
// a delayed worker. This module cannot provide that external authentication.
func (r *Registry) Epoch(fh int64) int64 {
	f := r.fence(fh)
	if f == nil {
		return Invalid
	}
	if f.exhausted.Load() {
		return Exhausted
	}
	return f.epoch.Load()
}

// Replace may block and is forbidden inside authentication/session monitors.
// Malformed/stale/capacity-rejected requests preserve the unrelated current.
func (r *Registry) Replace(fh, g, e int64, c Config, b conn.Bind, p Protector) int64 {
	transferred := false
	if p == nil {
		return Invalid
	}
	defer func() {
		if !transferred {
			p.Release()
		}
	}()
	opts, ok := c.options(g, b, p.Protect)
	if !ok || e <= 0 {
		return Invalid
	}
	f := r.fence(fh)
	if f == nil {
		return Invalid
	}
	if !f.busy.CompareAndSwap(false, true) {
		return Busy
	}
	defer f.busy.Store(false)
	if f.released.Load() || f.exhausted.Load() || f.epoch.Load() != e {
		return Denied
	}
	if g <= f.high {
		return Invalid
	}
	id := r.reserve(fh)
	if id <= 0 {
		return id
	}
	f.high = g
	previous := f.current.Load()
	core, err := f.core.Load().Replace(opts)
	t := &instance{id: id, fenceID: fh, generation: g, epoch: e}
	t.payload.Store(&transportPayload{core: core, protector: p})
	transferred = true
	// The control CAS excludes release; this reserved slot cannot disappear.
	r.commit(id, t)
	r.install(f, t)
	if previous != nil {
		r.retirePayload(f, previous)
	} // Failed/blocked ownership is retained.
	if err != nil {
		if core != nil {
			core.Invalidate()
		}
		return Failed
	}
	return r.finishReplacement(f, t)
}
func (r *Registry) install(f *fence, t *instance) { f.current.Store(t) }
func (r *Registry) finishReplacement(f *fence, t *instance) int64 {
	// Invalidate increments epoch before reading current; replacement publishes
	// CLOSED current before this comparison. One side must observe revocation.
	if f.exhausted.Load() || f.epoch.Load() != t.epoch || f.released.Load() {
		if p := t.payload.Load(); p != nil && p.core != nil {
			p.core.Invalidate()
		}
		return Denied
	}
	return t.id
}

// Only call with f.busy owned. Swap follows the actual successful barrier; an
// error leaves the exact heavy graph and callback reference reachable. Existing
// readers may keep their immutable local payload alive until their call ends.
func (r *Registry) retirePayload(f *fence, t *instance) int64 {
	p := t.payload.Load()
	if p == nil {
		return OK
	}
	if p.core != nil && p.core.Close() != nil {
		return Failed
	}
	t.payload.Store(nil)
	f.current.CompareAndSwap(t, nil)
	if entries := r.entries.Load(); entries != nil {
		for _, v := range entries.entries {
			if l, ok := v.(*lease); ok && l.fenceID == f.id && l.transportID == t.id {
				l.payload.Store(nil)
			}
		}
	}
	p.protector.Release()
	return OK
}
func (r *Registry) BeginProbe(fh, th, e int64) int64 {
	_, t, p := r.current(fh, th, e)
	if p == nil {
		return Denied
	}
	if p.core.BeginProbe() != nil {
		return Denied
	}
	_, again, live := r.current(fh, th, e)
	if again != t || live != p {
		p.core.Invalidate()
		return Denied
	}
	return OK
}
func (r *Registry) Register(fh, th, e int64, protocol int, source, destination string) int64 {
	_, t, p := r.current(fh, th, e)
	if p == nil {
		return Denied
	}
	if (protocol != 6 && protocol != 17) || len(source) > 64 || len(destination) > 64 {
		return Invalid
	}
	a, err := netip.ParseAddrPort(source)
	if err != nil {
		return Invalid
	}
	b, err := netip.ParseAddrPort(destination)
	if err != nil {
		return Invalid
	}
	tuple := gate.Tuple{Protocol: uint8(protocol), Source: a, Destination: b}
	id := r.reserve(fh)
	if id <= 0 {
		return id
	}
	committed := false
	defer func() {
		if !committed {
			r.drop(id)
		}
	}()
	core, err := p.core.AddProbe(uint64(e), tuple)
	if err != nil {
		return Denied
	}
	_, again, live := r.current(fh, th, e)
	if again != t || live != p {
		p.core.RemoveProbe(uint64(e), core)
		return Denied
	}
	l := &lease{fenceID: fh, transportID: th, generation: t.generation, epoch: e}
	l.payload.Store(&leasePayload{core: core, tuple: tuple})
	if !r.commit(id, l) {
		p.core.RemoveProbe(uint64(e), core)
		return Denied
	}
	// Retirement may have pruned leases before this insertion. This final check
	// ensures it cannot leave an unpublished heavy lease payload rooted afterward.
	_, again, live = r.current(fh, th, e)
	if again != t || live != p {
		l.payload.Store(nil)
		p.core.RemoveProbe(uint64(e), core)
		return Denied
	}
	committed = true
	return id
}

// Known numeric receipts retain idempotence until successful fence release.
// Unknown/foreign handles never acquire removal success. This is not a drain.
func (r *Registry) Remove(fh, th, e, lh int64) int64 {
	t := r.instance(fh, th)
	l, _ := r.get(lh).(*lease)
	if t == nil || l == nil || l.fenceID != fh || l.transportID != th || l.generation != t.generation || e <= 0 || l.epoch != e {
		return Invalid
	}
	lp := l.payload.Load()
	if lp == nil {
		return OK
	}
	p := t.payload.Load()
	if p == nil {
		l.payload.Store(nil)
		return OK
	}
	if p.core == nil {
		return Denied
	}
	if err := p.core.RemoveProbe(uint64(e), lp.core); err != nil {
		if l.payload.Load() == nil {
			return OK
		}
		if _, err = p.core.RevocationSequence(); err != gate.ErrState {
			return Denied
		}
	}
	l.payload.Store(nil)
	return OK
}
func (r *Registry) Sequence(fh, th, e int64) int64 {
	_, _, p := r.current(fh, th, e)
	if p == nil {
		return Denied
	}
	s, err := p.core.RevocationSequence()
	if err != nil {
		return Denied
	}
	if s == 0 || s > math.MaxInt64 {
		p.core.Invalidate()
		return Exhausted
	}
	return int64(s)
}
func (r *Registry) Publish(fh, th, e, s int64) int64 {
	_, _, p := r.current(fh, th, e)
	if p == nil || s <= 0 {
		return Denied
	}
	if p.core.Publish(uint64(s)) != nil {
		return Denied
	}
	return OK
}
func (r *Registry) Invalidate(fh int64) int64 {
	f := r.fence(fh)
	if f == nil {
		return Invalid
	}
	var next int64
	for {
		old := f.epoch.Load()
		if old == math.MaxInt64 {
			f.exhausted.Store(true)
			next = Exhausted
			break
		}
		if f.epoch.CompareAndSwap(old, old+1) {
			next = old + 1
			break
		}
	}
	if t := f.current.Load(); t != nil {
		if p := t.payload.Load(); p != nil && p.core != nil {
			p.core.Invalidate()
		}
	}
	return next
}
func (r *Registry) InvalidateTransport(fh, th int64) int64 {
	t := r.instance(fh, th)
	if t == nil {
		return Invalid
	}
	p := t.payload.Load()
	if p == nil || p.core == nil {
		return OK
	}
	s, err := p.core.Invalidate()
	if err != nil {
		return Denied
	}
	if s > math.MaxInt64 {
		return Exhausted
	}
	return int64(s)
}

// Retire/Release may block on I/O cancellation and native shutdown. Never call
// under authentication/session/security monitors. Only release closes the TUN.
func (r *Registry) Retire(fh, th int64) int64 {
	f := r.fence(fh)
	t := r.instance(fh, th)
	if f == nil || t == nil {
		return Invalid
	}
	if !f.busy.CompareAndSwap(false, true) {
		return Busy
	}
	defer f.busy.Store(false)
	if f.released.Load() {
		return Invalid
	}
	return r.retirePayload(f, t)
}
func (r *Registry) Release(fh int64, allSensitiveChildrenDead bool) int64 {
	if !allSensitiveChildrenDead {
		return Denied
	}
	f := r.fence(fh)
	if f == nil {
		return Invalid
	}
	if !f.busy.CompareAndSwap(false, true) {
		return Busy
	}
	defer f.busy.Store(false)
	if f.released.Load() {
		return Invalid
	}
	r.Invalidate(fh)
	if entries := r.entries.Load(); entries != nil {
		for _, v := range entries.entries {
			if t, ok := v.(*instance); ok && t.fenceID == fh {
				if r.retirePayload(f, t) != OK {
					return Failed
				}
			}
		}
	}
	if f.releaseFailed {
		return Failed
	}
	core := f.core.Load()
	if core == nil || core.Release() != nil {
		f.releaseFailed = true
		return Failed
	}
	// No new control operation can start. In-flight lease publication either is
	// included by dropFence's CAS retry or finds its reserved slot removed.
	f.released.Store(true)
	f.current.Store(nil)
	f.core.Store(nil)
	r.dropFence(fh)
	return OK
}

// Statistics blocks; no raw UAPI text leaves its bounded numeric accessor.
func (r *Registry) Statistics(fh, th, e int64) (transport.Statistics, int64) {
	_, t, p := r.current(fh, th, e)
	if p == nil {
		return transport.Statistics{}, Denied
	}
	s, err := p.core.Statistics()
	if err != nil {
		return transport.Statistics{}, Failed
	}
	_, again, live := r.current(fh, th, e)
	if again != t || live != p {
		return transport.Statistics{}, Denied
	}
	return s, OK
}

// Negative-result cleanup binds the original generation and immutable epoch;
// delayed cleanup must never resolve-and-retire an unrelated newer current.
func (r *Registry) RetireCurrent(fh, generation, epoch int64) int64 {
	f := r.fence(fh)
	if f == nil || generation <= 0 || epoch <= 0 {
		return Invalid
	}
	if !f.busy.CompareAndSwap(false, true) {
		return Busy
	}
	defer f.busy.Store(false)
	if f.released.Load() {
		return Invalid
	}
	t := f.current.Load()
	if t == nil || t.generation != generation || t.epoch != epoch {
		return Denied
	}
	return r.retirePayload(f, t)
}
