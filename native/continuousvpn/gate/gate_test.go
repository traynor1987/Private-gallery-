// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Private Gallery contributors.

package gate

import (
	"errors"
	"net/netip"
	"sync"
	"testing"
)

func newGate(t *testing.T, generation uint64) (*Registry, *Gate) {
	t.Helper()
	r := new(Registry)
	g, err := r.NewGate(generation)
	if err != nil {
		t.Fatal(err)
	}
	return r, g
}
func probeGate(t *testing.T, g *Gate, generation uint64) {
	t.Helper()
	if err := g.MarkOuterProtected(generation); err != nil {
		t.Fatal(err)
	}
	if err := g.SetState(generation, Probe); err != nil {
		t.Fatal(err)
	}
}
func openGate(t *testing.T, g *Gate, generation uint64) {
	t.Helper()
	probeGate(t, g, generation)
	if err := g.Publish(generation, 1); err != nil {
		t.Fatal(err)
	}
}
func tuple4(proto uint8) Tuple {
	return Tuple{Protocol: proto, Source: netip.MustParseAddrPort("10.0.0.2:32001"), Destination: netip.MustParseAddrPort("1.1.1.1:443")}
}
func requireDeniedCallbacks(t *testing.T, g *Gate, generation uint64, packet []byte) {
	t.Helper()
	if g.AdmitOutbound(generation, packet) {
		t.Fatal("outbound admitted")
	}
	if admitted, err := g.WriteInbound(generation, packet, func() error { t.Fatal("write called"); return nil }); admitted || err != nil {
		t.Fatal(admitted, err)
	}
	if admitted, err := g.OuterSend(generation, func() error { t.Fatal("send called"); return nil }); admitted || err != nil {
		t.Fatal(admitted, err)
	}
}
func TestDefaultClosedAndProtectionRequired(t *testing.T) {
	_, g := newGate(t, 1)
	p := packet4(17)
	requireDeniedCallbacks(t, g, 1, p)
	var zero Gate
	requireDeniedCallbacks(t, &zero, 0, p)
	if err := g.SetState(1, Probe); err == nil {
		t.Fatal("probe before protection")
	}
	if err := g.SetState(1, Open); err == nil {
		t.Fatal("open without probe")
	}
	if err := g.SetState(1, State(99)); err == nil {
		t.Fatal("invalid state")
	}
	if err := g.MarkOuterProtected(0); err == nil {
		t.Fatal("zero generation")
	}
	probeGate(t, g, 1)
	if admitted, err := g.OuterSend(1, func() error { return nil }); !admitted || err != nil {
		t.Fatal(admitted, err)
	}
	requireDeniedCallbacks(t, g, 0, p)
}
func TestExactProbeTupleAndReverseOnly(t *testing.T) {
	for _, proto := range []uint8{6, 17} {
		for _, ipv6 := range []bool{false, true} {
			_, g := newGate(t, 4)
			probeGate(t, g, 4)
			p := packet4(proto)
			tuple := tuple4(proto)
			if ipv6 {
				p = packet6(proto)
				tuple.Source = netip.MustParseAddrPort("[fd00::2]:32001")
				tuple.Destination = netip.MustParseAddrPort("[2606:4700:4700::1111]:443")
			}
			if _, err := g.AddProbe(4, 71, tuple); err != nil {
				t.Fatal(err)
			}
			if !g.AdmitOutbound(4, p) {
				t.Fatal("exact outgoing denied")
			}
			if g.AdmitOutbound(4, reversePacket(p)) {
				t.Fatal("reverse packet admitted outbound")
			}
			if ok, err := g.WriteInbound(4, reversePacket(p), func() error { return nil }); !ok || err != nil {
				t.Fatal("exact reverse denied", err)
			}
			if ok, _ := g.WriteInbound(4, p, func() error { t.Fatal("forward admitted inbound"); return nil }); ok {
				t.Fatal("wrong direction")
			}
			for _, off := range tupleOffsets(ipv6) {
				other := append([]byte(nil), p...)
				other[off] ^= 1
				if g.AdmitOutbound(4, other) {
					t.Fatalf("inexact tuple admitted: byte %d", off)
				}
			}
			other := append([]byte(nil), p...)
			if ipv6 {
				other[6] = 1
			} else {
				other[9] = 1
			}
			if g.AdmitOutbound(4, other) {
				t.Fatal("wrong protocol admitted")
			}
		}
	}
}
func TestLeaseRemovalBeforePortReleaseAndEpoch(t *testing.T) {
	_, g := newGate(t, 1)
	probeGate(t, g, 1)
	id, err := g.AddProbe(1, 9, tuple4(17))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := g.AddProbe(1, 9, tuple4(17)); err == nil {
		t.Fatal("duplicate tuple")
	}
	if err := g.RemoveProbe(1, 8, id); err == nil {
		t.Fatal("wrong epoch removed lease")
	}
	if !g.AdmitOutbound(1, packet4(17)) {
		t.Fatal("wrong epoch changed lease")
	}
	if err := g.RemoveProbe(1, 9, id); err != nil {
		t.Fatal(err)
	}
	if g.AdmitOutbound(1, packet4(17)) {
		t.Fatal("removed tuple still admitted before socket release")
	}
	if ok, _ := g.WriteInbound(1, reversePacket(packet4(17)), func() error { t.Fatal("revoked reverse written"); return nil }); ok {
		t.Fatal("revoked reverse admitted")
	}
	if err := g.RemoveProbe(1, 9, id); err == nil {
		t.Fatal("replayed removal")
	}
	fresh, err := g.AddProbe(1, 10, tuple4(17))
	if err != nil {
		t.Fatal(err)
	}
	if fresh == id {
		t.Fatal("lease ID reused")
	}
	if err := g.RemoveProbe(1, 10, id); err == nil {
		t.Fatal("stale ID revoked fresh tuple")
	}
}
func TestLeaseBoundsAndValidation(t *testing.T) {
	_, g := newGate(t, 1)
	if _, err := g.AddProbe(1, 1, tuple4(17)); err == nil {
		t.Fatal("closed registration")
	}
	probeGate(t, g, 1)
	for _, tuple := range []Tuple{{}, {Protocol: 1, Source: tuple4(17).Source, Destination: tuple4(17).Destination}, {Protocol: 17, Source: netip.MustParseAddrPort("0.0.0.0:32001"), Destination: tuple4(17).Destination}, {Protocol: 17, Source: tuple4(17).Source, Destination: netip.MustParseAddrPort("[::1]:443")}, {Protocol: 17, Source: netip.MustParseAddrPort("10.0.0.2:0"), Destination: tuple4(17).Destination}} {
		if _, err := g.AddProbe(1, 1, tuple); err == nil {
			t.Fatalf("invalid tuple accepted: %v", tuple)
		}
	}
	if _, err := g.AddProbe(1, 0, tuple4(17)); err == nil {
		t.Fatal("zero epoch")
	}
	for i := 0; i < MaxProbeLeases; i++ {
		tuple := tuple4(17)
		tuple.Source = netip.AddrPortFrom(tuple.Source.Addr(), uint16(1000+i))
		if _, err := g.AddProbe(1, 1, tuple); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := g.AddProbe(1, 1, tuple4(17)); err == nil {
		t.Fatal("unbounded leases")
	}
}
func TestMonotonicGenerationsAndPermanentClosure(t *testing.T) {
	r, g := newGate(t, 3)
	openGate(t, g, 3)
	for _, gen := range []uint64{0, 2, 3} {
		if _, err := r.NewGate(gen); err == nil {
			t.Fatal("accepted stale generation", gen)
		}
	}
	if !g.AdmitOutbound(3, packet4(17)) {
		t.Fatal("invalid new generation mutated live handle")
	}
	next, err := r.NewGate(4)
	if err != nil {
		t.Fatal(err)
	}
	requireDeniedCallbacks(t, g, 3, packet4(17))
	for _, state := range []State{Probe, Open} {
		if err := g.SetState(3, state); err == nil {
			t.Fatal("retired gate reopened")
		}
	}
	if err := g.MarkOuterProtected(3); err == nil {
		t.Fatal("retired protection accepted")
	}
	openGate(t, next, 4)
	if err := next.Close(3); err == nil {
		t.Fatal("stale close accepted")
	}
	if !next.AdmitOutbound(4, packet4(17)) {
		t.Fatal("stale control changed new gate")
	}
	if err := next.Close(4); err != nil {
		t.Fatal(err)
	}
	if err := next.Close(4); err != nil {
		t.Fatal("close not idempotent", err)
	}
	requireDeniedCallbacks(t, next, 4, packet4(17))
	if err := next.SetState(4, Probe); err == nil {
		t.Fatal("closed handle reused: encrypted old queues could reopen")
	}
}
func TestCallbacksPropagateErrors(t *testing.T) {
	_, g := newGate(t, 1)
	openGate(t, g, 1)
	want := errors.New("underlying I/O")
	if ok, err := g.OuterSend(1, func() error { return want }); !ok || err != want {
		t.Fatal(ok, err)
	}
	if ok, err := g.WriteInbound(1, packet4(17), func() error { return want }); !ok || err != want {
		t.Fatal(ok, err)
	}
	if ok, err := g.OuterSend(1, nil); ok || err == nil {
		t.Fatal("nil send accepted")
	}
	if ok, err := g.WriteInbound(1, packet4(17), nil); ok || err == nil {
		t.Fatal("nil write accepted")
	}
}

// observedWaitLocker reports Cond.Wait's actual Unlock, after the condition
// has registered its waiter. It is installed before concurrent operations and
// does not add production hooks or rely on the worker being scheduled promptly.
type observedWaitLocker struct {
	sync.Locker
	waiting chan struct{}
}

func (l *observedWaitLocker) Unlock() {
	l.Locker.Unlock()
	l.waiting <- struct{}{}
}
func observeDrainWait(g *Gate) <-chan struct{} {
	waiting := make(chan struct{}, 1)
	g.idle = sync.NewCond(&observedWaitLocker{Locker: &g.mu, waiting: waiting})
	return waiting
}
func awaitDrainWait(t *testing.T, waiting <-chan struct{}, returned <-chan error) {
	t.Helper()
	select {
	case <-waiting:
	case err := <-returned:
		t.Fatalf("barrier returned before reaching Cond.Wait with callback held: %v", err)
	}
}
func TestCloseWaitsForSendAndWriteAndDeniesAfterAcknowledgement(t *testing.T) {
	_, g := newGate(t, 1)
	openGate(t, g, 1)
	waiting := observeDrainWait(g)
	enteredSend, enteredWrite := make(chan struct{}), make(chan struct{})
	releaseSend, releaseWrite := make(chan struct{}), make(chan struct{})
	sendDone := make(chan struct{})
	var workers sync.WaitGroup
	workers.Add(2)
	go func() {
		defer workers.Done()
		defer close(sendDone)
		g.OuterSend(1, func() error { close(enteredSend); <-releaseSend; return nil })
	}()
	go func() {
		defer workers.Done()
		g.WriteInbound(1, packet4(17), func() error { close(enteredWrite); <-releaseWrite; return nil })
	}()
	<-enteredSend
	<-enteredWrite
	if _, err := g.Invalidate(1); err != nil {
		t.Fatal(err)
	}
	closed := make(chan error, 1)
	go func() { closed <- g.Close(1) }()
	awaitDrainWait(t, waiting, closed)
	close(releaseSend)
	<-sendDone
	// Wake the real waiter after Send has completely exited. It must re-enter
	// Wait because Write remains blocked; this catches acknowledging either
	// barrier alone, independent of goroutine scheduling.
	g.mu.Lock()
	g.idle.Broadcast()
	g.mu.Unlock()
	awaitDrainWait(t, waiting, closed)
	close(releaseWrite)
	workers.Wait()
	if err := <-closed; err != nil {
		t.Fatal(err)
	}
	requireDeniedCallbacks(t, g, 1, packet4(17))
}
func TestConcurrentRevocationAndAdmission(t *testing.T) {
	r, g := newGate(t, 1)
	probeGate(t, g, 1)
	id, err := g.AddProbe(1, 1, tuple4(17))
	if err != nil {
		t.Fatal(err)
	}
	start := make(chan struct{})
	var wg sync.WaitGroup
	for i := 0; i < 8; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			for j := 0; j < 200; j++ {
				g.AdmitOutbound(1, packet4(17))
				g.WriteInbound(1, reversePacket(packet4(17)), func() error { return nil })
				g.OuterSend(1, func() error { return nil })
			}
		}()
	}
	close(start)
	if err := g.RemoveProbe(1, 1, id); err != nil {
		t.Fatal(err)
	}
	if _, err := r.NewGate(2); err != nil {
		t.Fatal(err)
	}
	wg.Wait()
	requireDeniedCallbacks(t, g, 1, packet4(17))
}

func TestInvalidateIsNonblockingAndPublicationCannotReuseSequence(t *testing.T) {
	_, g := newGate(t, 1)
	probeGate(t, g, 1)
	waiting := observeDrainWait(g)
	entered, release, done := make(chan struct{}), make(chan struct{}), make(chan struct{})
	go func() { defer close(done); g.OuterSend(1, func() error { close(entered); <-release; return nil }) }()
	<-entered
	seq, err := g.Invalidate(1)
	if err != nil || seq != 2 {
		t.Fatal(seq, err)
	}
	if err := g.Publish(1, 1); err == nil {
		t.Fatal("stale publication accepted")
	}
	if err := g.Publish(1, seq); err == nil {
		t.Fatal("sealed handle reopened")
	}
	drained := make(chan error, 1)
	go func() { drained <- g.Drain(1) }()
	awaitDrainWait(t, waiting, drained)
	close(release)
	<-done
	if err := <-drained; err != nil {
		t.Fatal(err)
	}
	requireDeniedCallbacks(t, g, 1, packet4(17))
}
func TestForeignLeaseIDCannotRevokeNewGeneration(t *testing.T) {
	r, g := newGate(t, 1)
	probeGate(t, g, 1)
	old, err := g.AddProbe(1, 7, tuple4(17))
	if err != nil {
		t.Fatal(err)
	}
	next, err := r.NewGate(2)
	if err != nil {
		t.Fatal(err)
	}
	probeGate(t, next, 2)
	if _, err := next.AddProbe(2, 7, tuple4(17)); err != nil {
		t.Fatal(err)
	}
	if err := next.RemoveProbe(2, 7, old); err == nil {
		t.Fatal("foreign ID removed new lease")
	}
	if !next.AdmitOutbound(2, packet4(17)) {
		t.Fatal("new lease revoked by old ID")
	}
}

func TestInvalidateDoesNotWaitForControlMutex(t *testing.T) {
	_, g := newGate(t, 11)
	probeGate(t, g, 11)
	sequence, err := g.RevocationSequence(11)
	if err != nil || sequence != 1 {
		t.Fatal(sequence, err)
	}
	g.mu.Lock()
	revoked, err := g.Invalidate(11)
	g.mu.Unlock()
	if err != nil || revoked != 2 {
		t.Fatal(revoked, err)
	}
	if _, err := g.RevocationSequence(11); err == nil {
		t.Fatal("sealed sequence offered for publication")
	}
	if _, err := g.RevocationSequence(12); err == nil {
		t.Fatal("foreign generation offered publication token")
	}
	if err := g.Drain(11); err != nil {
		t.Fatal(err)
	}
}
func TestDrainRejectsLiveTransportAndPanicReleasesBarrier(t *testing.T) {
	_, g := newGate(t, 1)
	openGate(t, g, 1)
	if err := g.Drain(1); err == nil {
		t.Fatal("live drain accepted")
	}
	for _, inbound := range []bool{false, true} {
		func() {
			defer func() {
				if recover() == nil {
					t.Fatal("callback panic swallowed")
				}
			}()
			if inbound {
				g.WriteInbound(1, packet4(17), func() error { panic("write") })
			} else {
				g.OuterSend(1, func() error { panic("send") })
			}
		}()
	}
	if err := g.Retire(1); err != nil {
		t.Fatal(err)
	}
	requireDeniedCallbacks(t, g, 1, packet4(17))
}
func TestPublishMustUseCapturedSequence(t *testing.T) {
	_, g := newGate(t, 1)
	probeGate(t, g, 1)
	for _, sequence := range []uint64{0, 2} {
		if err := g.Publish(1, sequence); err == nil {
			t.Fatal("wrong sequence published")
		}
	}
	sequence, err := g.RevocationSequence(1)
	if err != nil {
		t.Fatal(err)
	}
	if err := g.Publish(1, sequence); err != nil {
		t.Fatal(err)
	}
	if err := g.Publish(1, sequence); err == nil {
		t.Fatal("publication replay accepted")
	}
	if !g.AdmitOutbound(1, packet4(17)) {
		t.Fatal("captured sequence did not publish")
	}
}

func TestLimitedBroadcastTuplesRejected(t *testing.T) {
	_, g := newGate(t, 1)
	probeGate(t, g, 1)
	broadcast := netip.MustParseAddrPort("255.255.255.255:443")
	for _, source := range []bool{false, true} {
		tuple := tuple4(17)
		p := packet4(17)
		if source {
			tuple.Source = broadcast
			copy(p[12:16], []byte{255, 255, 255, 255})
		} else {
			tuple.Destination = broadcast
			copy(p[16:20], []byte{255, 255, 255, 255})
		}
		if _, err := g.AddProbe(1, 1, tuple); err == nil {
			t.Fatalf("limited broadcast lease accepted (source=%v)", source)
		}
		if _, ok := parseProbePacket(p); ok {
			t.Fatalf("limited broadcast probe parsed (source=%v)", source)
		}
	}
}
