// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Private Gallery contributors.

package transport

import (
	"bytes"
	"errors"
	"net/netip"
	"os"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"golang.zx2c4.com/wireguard/tun"
	"uk.co.traynor.privategallery/continuousvpn/gate"
)

type readResult struct {
	packet []byte
	err    error
}
type fakeTun struct {
	packets      chan readResult
	reading      chan struct{}
	done         chan struct{}
	events       chan tun.Event
	once         sync.Once
	active       atomic.Int32
	maxActive    atomic.Int32
	closed       atomic.Int32
	mu           sync.Mutex
	writes       [][]byte
	writeEntered chan struct{}
	writeRelease chan struct{}
	writeErr     error
}

func newFakeTun() *fakeTun {
	return &fakeTun{packets: make(chan readResult), reading: make(chan struct{}, 32), done: make(chan struct{}), events: make(chan tun.Event)}
}
func (f *fakeTun) File() *os.File           { return nil }
func (f *fakeTun) Name() (string, error)    { return "persistent-test", nil }
func (f *fakeTun) MTU() (int, error)        { return 1420, nil }
func (f *fakeTun) Flush() error             { return nil }
func (f *fakeTun) Events() <-chan tun.Event { return f.events }
func (f *fakeTun) Close() error {
	f.once.Do(func() { f.closed.Add(1); close(f.done); close(f.events) })
	return nil
}
func (f *fakeTun) Read(b []byte, off int) (int, error) {
	n := f.active.Add(1)
	defer f.active.Add(-1)
	for old := f.maxActive.Load(); n > old; old = f.maxActive.Load() {
		if f.maxActive.CompareAndSwap(old, n) {
			break
		}
	}
	f.reading <- struct{}{}
	select {
	case p := <-f.packets:
		return copy(b[off:], p.packet), p.err
	case <-f.done:
		return 0, os.ErrClosed
	}
}
func (f *fakeTun) Write(b []byte, off int) (int, error) {
	if f.writeEntered != nil {
		f.writeEntered <- struct{}{}
		<-f.writeRelease
	}
	f.mu.Lock()
	defer f.mu.Unlock()
	f.writes = append(f.writes, append([]byte(nil), b[off:]...))
	return len(b) - off, f.writeErr
}
func await(t *testing.T, ch <-chan struct{}) {
	t.Helper()
	select {
	case <-ch:
	case <-time.After(5 * time.Second):
		t.Fatal("timed out waiting for worker")
	}
}
func fixture(t *testing.T, capacity int) (*Fence, *fakeTun) {
	t.Helper()
	r := newFakeTun()
	f, e := NewFence(r, capacity)
	if e != nil {
		t.Fatal(e)
	}
	await(t, r.reading)
	t.Cleanup(func() {
		f.mu.Lock()
		v := f.target
		f.mu.Unlock()
		if v != nil {
			v.Close()
		}
		if e := f.Release(); e != nil {
			t.Error(e)
		}
	})
	return f, r
}
func packet(value byte) []byte {
	p := make([]byte, 28)
	p[0] = 0x45
	p[3] = 28
	p[9] = 17
	p[12] = 10
	p[15] = 1
	p[16] = 10
	p[19] = 2
	p[20] = 0x12
	p[21] = 0x34
	p[23] = 53
	p[25] = 8
	p[1] = value
	return p
}
func virtual(t *testing.T, f *Fence, generation uint64) *virtualTun {
	t.Helper()
	g, e := f.registry.NewGate(generation)
	if e != nil {
		t.Fatal(e)
	}
	v, e := f.attach(g, generation)
	if e != nil {
		t.Fatal(e)
	}
	return v
}
func openVirtual(t *testing.T, v *virtualTun) {
	t.Helper()
	if e := v.gate.MarkOuterProtected(v.generation); e != nil {
		t.Fatal(e)
	}
	if e := v.gate.SetState(v.generation, gate.Probe); e != nil {
		t.Fatal(e)
	}
	seq, e := v.gate.RevocationSequence(v.generation)
	if e != nil {
		t.Fatal(e)
	}
	if e = v.gate.Publish(v.generation, seq); e != nil {
		t.Fatal(e)
	}
}
func feed(t *testing.T, r *fakeTun, p []byte) {
	t.Helper()
	r.packets <- readResult{packet: p}
	await(t, r.reading)
}

func TestFenceContinuouslyDrainsClosedAndHasOneRealReader(t *testing.T) {
	f, r := fixture(t, 2)
	for i := 0; i < 8; i++ {
		feed(t, r, packet(byte(i)))
	}
	v := virtual(t, f, 1)
	for i := 0; i < 8; i++ {
		feed(t, r, packet(byte(i)))
	}
	if len(v.queue) != 0 {
		t.Fatal("CLOSED plaintext queued")
	}
	v.Close()
	for i := 0; i < 8; i++ {
		feed(t, r, packet(byte(i)))
	}
	if r.maxActive.Load() != 1 || r.closed.Load() != 0 {
		t.Fatal("real reader was competed with or closed")
	}
}
func TestQueueCopiesBoundsAndZeroesRetirement(t *testing.T) {
	f, r := fixture(t, 2)
	v := virtual(t, f, 1)
	openVirtual(t, v)
	p := packet(1)
	feed(t, r, p)
	p[1] = 99
	feed(t, r, packet(2))
	feed(t, r, packet(3))
	if len(v.queue) != 2 {
		t.Fatal("queue not bounded")
	}
	b := make([]byte, 64)
	n, e := v.Read(b, 4)
	if e != nil || n != 28 || b[5] != 1 {
		t.Fatalf("copy/offset: %d %v %v", n, e, b)
	}
	queued := <-v.queue
	v.queue <- queued
	v.Close()
	if !bytes.Equal(queued, make([]byte, len(queued))) || len(v.queue) != 0 {
		t.Fatal("retired packet storage retained")
	}
	if v.File() != nil {
		t.Fatal("lifetime FD exposed")
	}
	if _, ok := <-v.Events(); ok {
		t.Fatal("events not closed")
	}
	if _, e = v.Read(b, 0); !errors.Is(e, os.ErrClosed) {
		t.Fatal(e)
	}
}
func TestQueuedPacketRechecksRevocationAndOldGenerationCannotWrite(t *testing.T) {
	f, r := fixture(t, 2)
	old := virtual(t, f, 1)
	openVirtual(t, old)
	feed(t, r, packet(1))
	old.gate.Invalidate(1)
	b := make([]byte, 64)
	done := make(chan struct{})
	go func() { defer close(done); _, _ = old.Read(b, 0) }()
	old.Close()
	await(t, done)
	next := virtual(t, f, 2)
	openVirtual(t, next)
	if n, e := old.Write(packet(3), 0); n != 28 || e != nil {
		t.Fatal(n, e)
	}
	if len(r.writes) != 0 {
		t.Fatal("old writer used new admission")
	}
	feed(t, r, packet(2))
	n, e := next.Read(b, 0)
	if e != nil || n != 28 || b[1] != 2 {
		t.Fatal("new queue inherited old plaintext")
	}
}
func TestVirtualWriteBarrierRetainsFenceUntilCallbackFinishes(t *testing.T) {
	f, r := fixture(t, 1)
	v := virtual(t, f, 1)
	openVirtual(t, v)
	r.writeEntered = make(chan struct{})
	r.writeRelease = make(chan struct{})
	written := make(chan struct{})
	go func() { defer close(written); v.Write(packet(1), 0) }()
	await(t, r.writeEntered)
	v.gate.Invalidate(1)
	v.Close()
	drained := make(chan struct{})
	go func() { v.gate.Drain(1); close(drained) }()
	if r.closed.Load() != 0 {
		t.Fatal("virtual close released lifetime fence")
	}
	select {
	case <-drained:
		t.Fatal("acknowledged blocked write")
	default:
	}
	close(r.writeRelease)
	await(t, written)
	await(t, drained)
	v.Write(packet(2), 0)
	if len(r.writes) != 1 {
		t.Fatal("write after drain acknowledgement")
	}
}
func TestFatalRealReadSealsButRetainsFence(t *testing.T) {
	f, r := fixture(t, 1)
	v := virtual(t, f, 1)
	openVirtual(t, v)
	r.packets <- readResult{err: errors.New("fatal read")}
	select {
	case <-f.Errors():
	case <-time.After(5 * time.Second):
		t.Fatal("failure not reported")
	}
	if _, e := v.gate.RevocationSequence(1); e == nil {
		t.Fatal("fatal read left gate live")
	}
	if r.closed.Load() != 0 {
		t.Fatal("fatal read released fence")
	}
	if _, e := f.attach(v.gate, 2); e == nil {
		t.Fatal("fatal fence reactivated")
	}
}

func TestRealProbeDeniedAcrossLeaseReplacement(t *testing.T) {
	f, r := fixture(t, 2)
	v := virtual(t, f, 1)
	v.gate.MarkOuterProtected(1)
	v.gate.SetState(1, gate.Probe)
	tuple := gate.Tuple{Protocol: 17, Source: netip.MustParseAddrPort("10.0.0.1:4660"), Destination: netip.MustParseAddrPort("10.0.0.2:53")}
	id, err := v.gate.AddProbe(1, 1, tuple)
	if err != nil {
		t.Fatal(err)
	}
	feed(t, r, packet(1))
	if len(v.queue) != 0 {
		t.Fatal("REAL packet entered private readiness queue")
	}
	if err = v.gate.RemoveProbe(1, 1, id); err != nil {
		t.Fatal(err)
	}
	tuple.Source = netip.MustParseAddrPort("10.0.0.1:4661")
	if _, err = v.gate.AddProbe(1, 1, tuple); err != nil {
		t.Fatal(err)
	}
	next := packet(2)
	next[21]++
	feed(t, r, next)
	if len(v.queue) != 0 {
		t.Fatal("new tuple granted REAL readiness aperture")
	}
	seq, err := v.gate.RevocationSequence(1)
	if err != nil {
		t.Fatal(err)
	}
	if err = v.gate.Publish(1, seq); err != nil {
		t.Fatal(err)
	}
	feed(t, r, packet(3))
	buf := make([]byte, 64)
	n, err := v.Read(buf, 0)
	if err != nil || n != 28 || buf[1] != 3 {
		t.Fatal("pre-publication REAL packet survived until OPEN", n, err, buf)
	}
}

func TestOpenPreservesNonProbeProtocolsAndWriteOffsets(t *testing.T) {
	f, r := fixture(t, 2)
	v := virtual(t, f, 1)
	openVirtual(t, v)
	p := packet(7)
	p[9] = 1
	p[6] = 0x20 // ICMP fragment: valid OPEN envelope, forbidden in PROBE.
	feed(t, r, p)
	b := make([]byte, 64)
	n, e := v.Read(b, 12)
	if e != nil || n != len(p) || !bytes.Equal(b[12:12+n], p) {
		t.Fatal("OPEN packet restricted", n, e)
	}
	if n, e = v.Write(b[:12+n], 12); e != nil || n != len(p) {
		t.Fatal(n, e)
	}
	if !bytes.Equal(r.writes[0], p) {
		t.Fatal("Write offset semantics changed")
	}
	if name, e := v.Name(); e != nil || name != "persistent-test" {
		t.Fatal(name, e)
	}
	if e := v.Flush(); e != nil {
		t.Fatal(e)
	}
}

func TestFatalRealWriteSealsAllDirectionsAndRetainsReader(t *testing.T) {
	f, r := fixture(t, 1)
	v := virtual(t, f, 1)
	openVirtual(t, v)
	boom := errors.New("fatal write")
	r.writeErr = boom
	if _, e := v.Write(packet(1), 0); !errors.Is(e, boom) {
		t.Fatal(e)
	}
	select {
	case <-f.Errors():
	default:
		t.Fatal("fatal Write not reported")
	}
	if _, e := v.gate.RevocationSequence(1); e == nil {
		t.Fatal("fatal write did not seal gate")
	}
	feed(t, r, packet(2))
	if r.closed.Load() != 0 {
		t.Fatal("fatal write released lifetime TUN")
	}
	if _, e := f.Replace(options(newFakeBind(), 2)); e != ErrClosed {
		t.Fatal("fatal fence recovered silently", e)
	}
}
