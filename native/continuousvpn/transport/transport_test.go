// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Private Gallery contributors.

package transport

import (
	"encoding/hex"
	"errors"
	"net/netip"
	"runtime"
	"sync/atomic"
	"testing"
	"time"

	"golang.zx2c4.com/wireguard/device"
	"uk.co.traynor.privategallery/continuousvpn/gate"
)

func options(raw *fakeBind, generation uint64) Options {
	var peer device.NoisePublicKey
	for i := range peer {
		peer[i] = 2
	}
	return Options{Generation: generation, Bind: raw, Protect: func(int) bool { return true }, PeerPublicKey: peer, IPCConfig: "private_key=0101010101010101010101010101010101010101010101010101010101010101\npublic_key=" + hex.EncodeToString(peer[:]) + "\nendpoint=127.0.0.1:51820\nallowed_ip=0.0.0.0/0\n"}
}
func TestPinnedDeviceStartsClosedAndExplicitProbeKicksHandshake(t *testing.T) {
	f, r := fixture(t, 2)
	raw := newFakeBind()
	tr, e := f.Replace(options(raw, 1))
	if e != nil {
		t.Fatal(e)
	}
	defer tr.Close()
	if raw.sends.Load() != 0 {
		t.Fatal("startup emitted before BeginProbe")
	}
	feed(t, r, packet(1))
	seq, e := tr.RevocationSequence()
	if e != nil {
		t.Fatal(e)
	}
	if e = tr.BeginProbe(); e != nil {
		t.Fatal(e)
	}
	select {
	case p := <-raw.sent:
		if len(p) != device.MessageInitiationSize || p[0] != 1 {
			t.Fatal("explicit probe did not send handshake")
		}
	case <-time.After(5 * time.Second):
		t.Fatal("handshake depended on child traffic")
	}
	if e = tr.Publish(seq); e != nil {
		t.Fatal(e)
	}
	tr.Invalidate()
	if e = tr.Publish(seq); e == nil {
		t.Fatal("stale publication reopened transport")
	}
	if e = tr.Close(); e != nil {
		t.Fatal(e)
	}
	if r.closed.Load() != 0 {
		t.Fatal("transport closed real fence")
	}
}
func TestReplacementWaitsForOldPlaintextWriteAndUsesFreshDevice(t *testing.T) {
	f, r := fixture(t, 2)
	first, e := f.Replace(options(newFakeBind(), 1))
	if e != nil {
		t.Fatal(e)
	}
	first.BeginProbe()
	seq, _ := first.RevocationSequence()
	first.Publish(seq)
	r.writeEntered = make(chan struct{})
	r.writeRelease = make(chan struct{})
	written := make(chan struct{})
	go func() { first.tun.Write(packet(1), 0); close(written) }()
	await(t, r.writeEntered)
	secondRaw := newFakeBind()
	replaced := make(chan struct{})
	var second *Transport
	var replacementErr error
	go func() { second, replacementErr = f.Replace(options(secondRaw, 2)); close(replaced) }()
	// Detachment is an observed retirement milestone, not an arbitrary sleep.
	await(t, first.tun.done)
	await(t, first.device.Wait())
	if secondRaw.opens.Load() != 0 {
		t.Fatal("new sockets opened before old write drained")
	}
	select {
	case <-replaced:
		t.Fatal("replacement acknowledged active old write")
	default:
	}
	close(r.writeRelease)
	await(t, written)
	await(t, replaced)
	if replacementErr != nil {
		t.Fatal(replacementErr)
	}
	defer second.Close()
	if first.device == second.device || first.gate == second.gate {
		t.Fatal("old WG queues/gate reused")
	}
	first.tun.Write(packet(2), 0)
	if len(r.writes) != 1 {
		t.Fatal("retired generation wrote after replacement")
	}
	if e = first.BeginProbe(); e == nil {
		t.Fatal("old generation restarted")
	}
	if e = f.Release(); e != ErrBusy {
		t.Fatal("released attached generation", e)
	}
}

func TestFailedConfigurationRetiresWorkersAndNextGenerationCanStart(t *testing.T) {
	f, r := fixture(t, 1)
	bad := options(newFakeBind(), 1)
	bad.IPCConfig = "not_a_setting=oops\n"
	if _, e := f.Replace(bad); e == nil {
		t.Fatal("invalid config accepted")
	}
	if f.current != nil {
		t.Fatal("failed configured device retained after successful retirement")
	}
	if r.closed.Load() != 0 {
		t.Fatal("config error released fence")
	}
	tr, e := f.Replace(options(newFakeBind(), 2))
	if e != nil {
		t.Fatal(e)
	}
	defer tr.Close()
	if tr.Generation() != 2 {
		t.Fatal("wrong generation")
	}
	if _, e = f.Replace(options(newFakeBind(), 2)); e != gate.ErrGeneration {
		t.Fatal("non-increasing generation accepted", e)
	}
	if e = tr.BeginProbe(); e != nil {
		t.Fatal(e)
	}
	tuple := gate.Tuple{Protocol: 17, Source: netip.MustParseAddrPort("10.0.0.1:4660"), Destination: netip.MustParseAddrPort("10.0.0.2:53")}
	id, e := tr.AddProbe(1, tuple)
	if e != nil {
		t.Fatal(e)
	}
	if e = tr.RemoveProbe(1, id); e != nil {
		t.Fatal(e)
	}
}

func TestCloseCancelsExplicitHandshakeBeforeDeviceRetirement(t *testing.T) {
	f, _ := fixture(t, 1)
	raw := newFakeBind()
	tr, e := f.Replace(options(raw, 1))
	if e != nil {
		t.Fatal(e)
	}
	raw.sendEntered = make(chan struct{})
	raw.sendRelease = make(chan struct{})
	probeDone := make(chan struct{})
	go func() { tr.BeginProbe(); close(probeDone) }()
	await(t, raw.sendEntered)
	seq, e := tr.Invalidate()
	if e != nil || seq != 2 {
		t.Fatal(seq, e)
	}
	closed := make(chan struct{})
	go func() { tr.Close(); close(closed) }()
	await(t, closed)
	await(t, probeDone)
	await(t, tr.Done())
}

func TestExplicitProbeRestartsHandshakeThrottleFromClosedKeepalive(t *testing.T) {
	f, _ := fixture(t, 1)
	raw := newFakeBind()
	opts := options(raw, 1)
	opts.IPCConfig += "persistent_keepalive_interval=25\n"
	tr, e := f.Replace(opts)
	if e != nil {
		t.Fatal(e)
	}
	defer tr.Close()
	// Explicitly exercise the exported pin path while CLOSED. It records a
	// recent attempt even though our actual Bind.Send consumes/drops it.
	if e = tr.device.LookupPeer(opts.PeerPublicKey).SendHandshakeInitiation(false); e != nil {
		t.Fatal(e)
	}
	if raw.sends.Load() != 0 {
		t.Fatal("CLOSED keepalive escaped")
	}
	if e = tr.BeginProbe(); e != nil {
		t.Fatal(e)
	}
	select {
	case <-raw.sent:
	case <-time.After(5 * time.Second):
		t.Fatal("recent CLOSED attempt suppressed probe kick")
	}
}

func TestReplaceCancelsBlockedProbeBeforeWaitingForDevice(t *testing.T) {
	f, r := fixture(t, 1)
	raw := newFakeBind()
	old, err := f.Replace(options(raw, 1))
	if err != nil {
		t.Fatal(err)
	}
	raw.sendEntered = make(chan struct{})
	raw.sendRelease = make(chan struct{})
	probeDone := make(chan struct{})
	go func() { old.BeginProbe(); close(probeDone) }()
	await(t, raw.sendEntered)
	// The actual pinned handshake is blocked in raw Send. Only cancellation
	// by Replace can release it; the test never releases sendRelease.
	next, err := f.Replace(options(newFakeBind(), 2))
	if err != nil {
		t.Fatal(err)
	}
	defer next.Close()
	await(t, probeDone)
	await(t, old.Done())
	if r.closed.Load() != 0 {
		t.Fatal("replacement released the lifetime fence")
	}
}

func TestStaleReplaceCannotCancelNewerBlockedProbe(t *testing.T) {
	f, _ := fixture(t, 1)
	raw := newFakeBind()
	current, err := f.Replace(options(raw, 2))
	if err != nil {
		t.Fatal(err)
	}
	raw.sendEntered = make(chan struct{})
	raw.sendRelease = make(chan struct{})
	probeDone := make(chan struct{})
	go func() { current.BeginProbe(); close(probeDone) }()
	await(t, raw.sendEntered)
	for _, generation := range []uint64{1, 2} {
		staleRaw := newFakeBind()
		if _, err = f.Replace(options(staleRaw, generation)); err != gate.ErrGeneration {
			t.Fatalf("stale generation %d: %v", generation, err)
		}
		if staleRaw.opens.Load() != 0 || raw.closes.Load() != 0 {
			t.Fatal("stale replacement touched current or replacement sockets")
		}
		if _, err = current.RevocationSequence(); err != nil {
			t.Fatal("stale request invalidated current generation", err)
		}
	}
	next, err := f.Replace(options(newFakeBind(), 3))
	if err != nil {
		t.Fatal(err)
	}
	defer next.Close()
	await(t, probeDone)
}

func TestConcurrentReplacementsAndOldCloseKeepNewestGenerationLive(t *testing.T) {
	f, _ := fixture(t, 1)
	raw := newFakeBind()
	old, err := f.Replace(options(raw, 1))
	if err != nil {
		t.Fatal(err)
	}
	raw.sendEntered = make(chan struct{})
	raw.sendRelease = make(chan struct{})
	probeDone := make(chan struct{})
	go func() { old.BeginProbe(); close(probeDone) }()
	await(t, raw.sendEntered)
	type result struct {
		transport *Transport
		err       error
	}
	start := make(chan struct{})
	second := make(chan result, 1)
	third := make(chan result, 1)
	closed := make(chan struct{})
	go func() { <-start; tr, err := f.Replace(options(newFakeBind(), 2)); second <- result{tr, err} }()
	go func() { <-start; tr, err := f.Replace(options(newFakeBind(), 3)); third <- result{tr, err} }()
	go func() { <-start; old.Close(); close(closed) }()
	close(start)
	two, three := <-second, <-third
	if two.err != nil && two.err != gate.ErrGeneration {
		t.Fatal(two.err)
	}
	if two.transport != nil {
		defer two.transport.Close()
	}
	if three.err != nil {
		t.Fatal(three.err)
	}
	defer three.transport.Close()
	await(t, closed)
	await(t, probeDone)
	await(t, old.Done())
	if two.transport != nil {
		await(t, two.transport.Done())
	}
	if _, err = three.transport.RevocationSequence(); err != nil {
		t.Fatal("older request or Close cancelled newest generation", err)
	}
	if err = three.transport.BeginProbe(); err != nil {
		t.Fatal("newest generation cannot probe", err)
	}
}

func TestOlderReplaceDuringNewDeviceProtectionCannotRevokePublication(t *testing.T) {
	f, _ := fixture(t, 1)
	old, err := f.Replace(options(newFakeBind(), 1))
	if err != nil {
		t.Fatal(err)
	}
	defer old.Close()
	newRaw := newFakeBind()
	protectEntered, protectRelease := make(chan struct{}), make(chan struct{})
	opts := options(newRaw, 3)
	opts.Protect = func(fd int) bool {
		if fd == newRaw.fd4 {
			close(protectEntered)
			<-protectRelease
		}
		return true
	}
	type result struct {
		transport *Transport
		err       error
	}
	newestResult := make(chan result, 1)
	go func() { tr, err := f.Replace(opts); newestResult <- result{tr, err} }()
	await(t, protectEntered)
	// Generation 3 is being configured under owner. Invoke an older request
	// during this window; it must check the serialized current generation,
	// rather than cancelling a previously captured transport out of lock.
	staleRaw := newFakeBind()
	staleStarted, staleResult := make(chan struct{}), make(chan error, 1)
	go func() { close(staleStarted); _, err := f.Replace(options(staleRaw, 2)); staleResult <- err }()
	await(t, staleStarted)
	close(protectRelease)
	newest := <-newestResult
	if newest.err != nil {
		t.Fatal(newest.err)
	}
	defer newest.transport.Close()
	if err = <-staleResult; err != gate.ErrGeneration {
		t.Fatal("older concurrent request was accepted", err)
	}
	if staleRaw.opens.Load() != 0 || newRaw.closes.Load() != 0 {
		t.Fatal("older request modified sockets during new publication")
	}
	if err = newest.transport.BeginProbe(); err != nil {
		t.Fatal("new publication was revoked by older request", err)
	}
}

func TestSuccessfulRetirementForgetsOnlyCurrentAfterBarrierAndKeepsHighWater(t *testing.T) {
	f, _ := fixture(t, 1)
	old, e := f.Replace(options(newFakeBind(), 3))
	if e != nil {
		t.Fatal(e)
	}
	if old.Close() != nil {
		t.Fatal("close")
	}
	if f.current != nil {
		t.Fatal("closed current retains retired device")
	}
	for _, g := range []uint64{1, 2, 3} {
		if _, e := f.Replace(options(newFakeBind(), g)); e != gate.ErrGeneration {
			t.Fatal("lost generation high water", g, e)
		}
	}
	newest, e := f.Replace(options(newFakeBind(), 4))
	if e != nil {
		t.Fatal(e)
	}
	defer newest.Close()
	old.Close()
	if f.current != newest {
		t.Fatal("older close forgot newer transport")
	}
}

func TestRetiredWireGuardDeviceCollectibleWhileLifetimeFenceRemains(t *testing.T) {
	f, r := fixture(t, 1)
	collected := make(chan struct{})
	func() {
		tr, e := f.Replace(options(newFakeBind(), 1))
		if e != nil {
			t.Fatal(e)
		}
		runtime.SetFinalizer(tr.device, func(*device.Device) { close(collected) })
		if e = tr.Close(); e != nil {
			t.Fatal(e)
		}
	}()
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		runtime.GC()
		select {
		case <-collected:
			if r.closed.Load() != 0 {
				t.Fatal("collection closed lifetime TUN")
			}
			runtime.KeepAlive(f)
			return
		default:
			runtime.Gosched()
		}
	}
	t.Fatal("retired WG device remains rooted by live lifetime fence")
}

type partialTeardownFailureBind struct {
	*fakeBind
	fail       atomic.Bool
	closeError error
}

func (b *partialTeardownFailureBind) Close() error {
	err := b.fakeBind.Close()
	if b.fail.Load() {
		return b.closeError
	}
	return err
}
func TestFailedConstructionReturnsExactPartialTransportAndReleasePropagatesBarrier(t *testing.T) {
	real := newFakeTun()
	f, err := NewFence(real, 1)
	if err != nil {
		t.Fatal(err)
	}
	defer func() { real.Close(); <-f.readerDone }() // End fake reader without claiming a bridge release receipt.
	sentinel := errors.New("partial teardown failure")
	b := &partialTeardownFailureBind{fakeBind: newFakeBind(), closeError: sentinel}
	opts := options(b.fakeBind, 1)
	opts.Bind = b
	opts.Protect = func(int) bool { b.fail.Store(true); return false }
	tr, err := f.Replace(opts)
	if tr == nil || tr != f.current || !errors.Is(err, sentinel) {
		t.Error("failed construction discarded partial transport/barrier error")
	}
	retained := f.current
	if err = f.Release(); !errors.Is(err, sentinel) {
		t.Errorf("final release discarded failed barrier: %v", err)
	}
	if real.closed.Load() != 0 || f.released || f.current != retained {
		t.Error("failed barrier released lifetime ownership")
	}
	if tr != nil && !errors.Is(tr.Close(), sentinel) {
		t.Error("failed partial handle manufactured retirement")
	}
}
