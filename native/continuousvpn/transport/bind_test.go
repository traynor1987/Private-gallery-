// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Private Gallery contributors.

package transport

import (
	"errors"
	"net"
	"runtime"
	"sync"
	"sync/atomic"
	"syscall"
	"testing"
	"time"

	"golang.zx2c4.com/wireguard/conn"
	"uk.co.traynor.privategallery/continuousvpn/gate"
)

type fakeBind struct {
	mu          sync.Mutex
	opened      bool
	done        chan struct{}
	fd4, fd6    int
	err4, err6  error
	openErr     error
	opens       atomic.Int32
	closes      atomic.Int32
	sends       atomic.Int32
	sent        chan []byte
	sendEntered chan struct{}
	sendRelease chan struct{}
}

func newFakeBind() *fakeBind { return &fakeBind{fd4: 41, fd6: 61, sent: make(chan []byte, 64)} }
func (b *fakeBind) Open(port uint16) ([]conn.ReceiveFunc, uint16, error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.opens.Add(1)
	if b.openErr != nil {
		return nil, 0, b.openErr
	}
	b.opened = true
	b.done = make(chan struct{})
	done := b.done
	return []conn.ReceiveFunc{func([]byte) (int, conn.Endpoint, error) { <-done; return 0, nil, net.ErrClosed }}, 51234, nil
}
func (b *fakeBind) Close() error {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.opened {
		close(b.done)
		b.opened = false
		b.closes.Add(1)
	}
	return nil
}
func (b *fakeBind) SetMark(uint32) error { return nil }
func (b *fakeBind) ParseEndpoint(s string) (conn.Endpoint, error) {
	return conn.NewStdNetBind().ParseEndpoint(s)
}
func (b *fakeBind) PeekLookAtSocketFd4() (int, error) { return b.fd4, b.err4 }
func (b *fakeBind) PeekLookAtSocketFd6() (int, error) { return b.fd6, b.err6 }
func (b *fakeBind) Send(p []byte, _ conn.Endpoint) error {
	b.mu.Lock()
	done := b.done
	opened := b.opened
	b.mu.Unlock()
	if !opened {
		return net.ErrClosed
	}
	b.sends.Add(1)
	if b.sendEntered != nil {
		b.sendEntered <- struct{}{}
		select {
		case <-b.sendRelease:
		case <-done:
			return net.ErrClosed
		}
	}
	b.sent <- append([]byte(nil), p...)
	return nil
}
func bindFixture(t *testing.T, raw *fakeBind, protect func(int) bool) (*protectedConnBind, *gate.Gate) {
	t.Helper()
	var r gate.Registry
	g, e := r.NewGate(1)
	if e != nil {
		t.Fatal(e)
	}
	b, e := newProtectedConnBind(raw, protect, g, 1)
	if e != nil {
		t.Fatal(e)
	}
	t.Cleanup(func() { b.Close(); g.Close(1) })
	return b, g
}
func TestBindProtectsAllDescriptorsBeforeProbeOrSend(t *testing.T) {
	raw := newFakeBind()
	entered := make(chan struct{})
	release := make(chan struct{})
	var fds []int
	b, g := bindFixture(t, raw, func(fd int) bool {
		fds = append(fds, fd)
		if fd == 41 {
			close(entered)
			<-release
		}
		return true
	})
	opened := make(chan struct{})
	go func() {
		defer close(opened)
		if _, _, e := b.Open(0); e != nil {
			t.Error(e)
		}
	}()
	await(t, entered)
	if e := g.SetState(1, gate.Probe); e == nil {
		t.Fatal("PROBE before protection")
	}
	b.Send([]byte{1}, nil)
	if raw.sends.Load() != 0 {
		t.Fatal("send during protection")
	}
	close(release)
	await(t, opened)
	if len(fds) != 2 || fds[0] != 41 || fds[1] != 61 {
		t.Fatal(fds)
	}
	b.Send([]byte{2}, nil)
	if raw.sends.Load() != 0 {
		t.Fatal("startup must remain CLOSED")
	}
	if e := g.SetState(1, gate.Probe); e != nil {
		t.Fatal(e)
	}
	for _, messageType := range []byte{1, 2, 3, 4} {
		if e := b.Send([]byte{messageType}, nil); e != nil {
			t.Fatal(e)
		}
	}
	if raw.sends.Load() != 4 {
		t.Fatal("control/data send did not pass wrapper")
	}
}
func TestProtectionFailureAndUnknownDescriptorAbsenceSealGeneration(t *testing.T) {
	for _, which := range []string{"v4", "v6", "unknown absence", "open error"} {
		t.Run(which, func(t *testing.T) {
			raw := newFakeBind()
			if which == "unknown absence" {
				raw.fd6 = -1
				raw.err6 = syscall.EINVAL
			}
			if which == "open error" {
				raw.openErr = errors.New("open")
			}
			b, g := bindFixture(t, raw, func(fd int) bool { return !(which == "v4" && fd == 41 || which == "v6" && fd == 61) })
			if fns, _, e := b.Open(0); e == nil || fns != nil {
				t.Fatal("failed protection exposed receive sockets")
			}
			if e := g.SetState(1, gate.Probe); e == nil {
				t.Fatal("failed generation reusable")
			}
			b.Send([]byte{3}, nil)
			if raw.sends.Load() != 0 {
				t.Fatal("unprotected send")
			}
			raw.mu.Lock()
			open := raw.opened
			raw.mu.Unlock()
			if open {
				t.Fatal("failed sockets retained")
			}
		})
	}
}
func TestUnsupportedFamilyAllowedAndReopenNeverReadmits(t *testing.T) {
	raw := newFakeBind()
	raw.fd6 = -1
	raw.err6 = syscall.EAFNOSUPPORT
	count := 0
	b, g := bindFixture(t, raw, func(int) bool { count++; return true })
	if _, _, e := b.Open(0); e != nil {
		t.Fatal(e)
	}
	if count != 1 {
		t.Fatal(count)
	}
	g.SetState(1, gate.Probe)
	if _, _, e := b.Open(0); !errors.Is(e, ErrReopen) {
		t.Fatal(e)
	}
	if raw.opens.Load() != 1 {
		t.Fatal("replacement sockets opened in old generation")
	}
	if _, e := g.RevocationSequence(1); e == nil {
		t.Fatal("reopen did not invalidate")
	}
}
func TestCloseCancelsBlockedSendWithoutHoldingLifecycleLock(t *testing.T) {
	raw := newFakeBind()
	raw.sendEntered = make(chan struct{})
	raw.sendRelease = make(chan struct{})
	b, g := bindFixture(t, raw, func(int) bool { return true })
	b.Open(0)
	g.SetState(1, gate.Probe)
	sent := make(chan struct{})
	go func() { defer close(sent); b.Send([]byte{3}, nil) }()
	await(t, raw.sendEntered)
	closed := make(chan struct{})
	go func() { b.Close(); g.Drain(1); close(closed) }()
	await(t, closed)
	await(t, sent)
	b.Send([]byte{1}, nil)
	if raw.sends.Load() != 1 {
		t.Fatal("send after close acknowledgement")
	}
}
func TestProtectionHoldsActualFDLifetimeAgainstClose(t *testing.T) {
	raw := newFakeBind()
	entered := make(chan struct{})
	release := make(chan struct{})
	b, _ := bindFixture(t, raw, func(fd int) bool {
		if fd == 41 {
			close(entered)
			<-release
		}
		return true
	})
	opened := make(chan struct{})
	go func() { b.Open(0); close(opened) }()
	await(t, entered)
	closed := make(chan struct{})
	go func() { b.Close(); close(closed) }()
	// Observe Close's invalidation before inspecting FD lifetime. This proves
	// the closer has actually run and is waiting behind protection.
	deadline := time.Now().Add(5 * time.Second)
	for {
		if _, e := b.gate.RevocationSequence(1); e != nil {
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("close never invalidated")
		}
		runtime.Gosched()
	}
	if raw.closes.Load() != 0 {
		t.Fatal("FD closed/reused during protection")
	}
	close(release)
	await(t, opened)
	await(t, closed)
}

func TestProtectPanicClosesSocketsAndCannotLeaveProtectedAdmission(t *testing.T) {
	raw := newFakeBind()
	b, g := bindFixture(t, raw, func(int) bool { panic("protect panic") })
	func() {
		defer func() {
			if recover() == nil {
				t.Error("expected protector panic")
			}
		}()
		b.Open(0)
	}()
	if raw.closes.Load() != 1 {
		t.Fatal("panic retained unprotected sockets")
	}
	if e := g.SetState(1, gate.Probe); e == nil {
		t.Fatal("panic left reusable admission")
	}
}
