// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Private Gallery contributors.

package transport

import (
	"errors"
	"sync"

	"golang.zx2c4.com/wireguard/conn"
	"golang.zx2c4.com/wireguard/device"
	"uk.co.traynor.privategallery/continuousvpn/gate"
)

// Options is trusted owner configuration, not an untrusted child API. Bind must
// implement the pin's conn.PeekLookAtSocketFd and be exclusively transferred to
// this generation. Protect must synchronously protect each actual fd and may
// not reenter the transport. Supply a fresh Bind on every Replace.
type Options struct {
	Generation    uint64
	Bind          conn.Bind
	Protect       func(fd int) bool
	IPCConfig     string
	PeerPublicKey device.NoisePublicKey
	Logger        *device.Logger
}

// Transport is an opaque owner handle to one fresh gate, virtual TUN, outer bind
// and WG device. Device/bind are deliberately not exported: arbitrary UAPI or
// BindUpdate must never reactivate old queues. Never copy a Transport.
type Transport struct {
	fence      *Fence
	generation uint64
	gate       *gate.Gate
	tun        *virtualTun
	bind       *protectedConnBind
	device     *device.Device
	peerKey    device.NoisePublicKey
	done       chan struct{}
	probes     sync.WaitGroup // Add under fence.owner; Wait under owner after cancellation
	closed     bool           // fence.owner
	closeErr   error
}

// Replace invalidates/detaches the old generation, cancels outer I/O, waits for
// pinned Device.Close/Wait and the plaintext/send barriers, then allocates fresh
// queues. It may block on old writes; call outside session monitors. A blocked
// teardown never admits a new generation and never releases the lifetime TUN.
// The resulting device is configured and Up, protected, and still CLOSED.
// If construction teardown fails, return that exact partial Transport together
// with the error. Its owner must retain it and its protector; nil is not returned
// while a newly constructed generation has an unacknowledged retirement error.
func (f *Fence) Replace(options Options) (*Transport, error) {
	if options.Generation == 0 || options.Protect == nil || options.Bind == nil {
		return nil, ErrConfig
	}
	if _, ok := options.Bind.(conn.PeekLookAtSocketFd); !ok {
		return nil, ErrConfig
	}
	f.owner.Lock()
	defer f.owner.Unlock()
	f.mu.Lock()
	unusable := f.released || f.failure != nil
	f.mu.Unlock()
	if unusable {
		return nil, ErrClosed
	}
	if f.current != nil {
		if options.Generation <= f.current.generation {
			return nil, gate.ErrGeneration
		}
		if err := f.current.closeLocked(); err != nil {
			return nil, err
		}
	}
	g, err := f.registry.NewGate(options.Generation)
	if err != nil {
		return nil, err
	}
	v, err := f.attach(g, options.Generation)
	if err != nil {
		g.Close(options.Generation)
		return nil, err
	}
	b, err := newProtectedConnBind(options.Bind, options.Protect, g, options.Generation)
	if err != nil {
		v.Close()
		g.Drain(options.Generation)
		return nil, err
	}
	logger := options.Logger
	if logger == nil {
		logger = device.NewLogger(device.LogLevelSilent, "")
	}
	t := &Transport{fence: f, generation: options.Generation, gate: g, tun: v, bind: b, peerKey: options.PeerPublicKey, done: make(chan struct{})}
	f.current = t
	t.device = device.NewDevice(v, b, logger) // Constructor starts its virtual reader CLOSED.
	if err = t.device.IpcSet(options.IPCConfig); err == nil {
		if t.device.LookupPeer(t.peerKey) == nil {
			err = ErrConfig
		} else {
			err = t.device.Up()
		}
	}
	if err != nil {
		if closeErr := t.closeLocked(); closeErr != nil {
			// The caller must retain this exact partial generation/protector:
			// construction failure is not a successful retirement receipt.
			return t, errors.Join(err, closeErr)
		}
		return nil, err
	}
	return t, nil
}

func (t *Transport) Generation() uint64    { return t.generation }
func (t *Transport) Done() <-chan struct{} { return t.done }

// BeginProbe is explicit owner activation after Open has protected all sockets.
// ExpireCurrentKeypairs resets the pin's handshake throttle: a CLOSED keepalive
// may already have attempted (and dropped) a handshake. The exported kick does
// not depend on admitting any ordinary child packet. It does not prove readiness.
func (t *Transport) BeginProbe() error {
	peer, err := t.prepareProbe()
	if err != nil {
		return err
	}
	defer t.probes.Done()
	// The synchronous kick can block in outer Send. The registered reference
	// keeps the peer/device alive without holding owner, so Replace can close
	// the sockets before waiting for this call to finish.
	if err := peer.SendHandshakeInitiation(false); err != nil {
		t.Invalidate()
		return err
	}
	return nil
}

func (t *Transport) prepareProbe() (*device.Peer, error) {
	t.fence.owner.Lock()
	defer t.fence.owner.Unlock()
	if t.closed || t.fence.current != t {
		return nil, ErrClosed
	}
	if err := t.gate.SetState(t.generation, gate.Probe); err != nil {
		return nil, err
	}
	peer := t.device.LookupPeer(t.peerKey)
	if peer == nil {
		t.Invalidate()
		return nil, ErrConfig
	}
	peer.ExpireCurrentKeypairs()
	t.probes.Add(1)
	return peer, nil
}

// AddProbe delegates tuple validation. Java must reserve all future probe ports
// before sensitive child admission, retain UDP reservations across generations,
// and burn each prebound TCP slot after one attempt. A post-hoc exclusive bind
// cannot authenticate the origin of delayed old plaintext. This module does not
// create/reserve parent sockets or prove Android's exclusivity semantics.
func (t *Transport) AddProbe(epoch uint64, tuple gate.Tuple) (gate.LeaseID, error) {
	return t.gate.AddProbe(t.generation, epoch, tuple)
}
func (t *Transport) RemoveProbe(epoch uint64, id gate.LeaseID) error {
	return t.gate.RemoveProbe(t.generation, epoch, id)
}
func (t *Transport) RevocationSequence() (uint64, error) {
	return t.gate.RevocationSequence(t.generation)
}

// Publish performs the native CAS only. Java must validate owner, authentication,
// attempt, presentation and original grace inside the SAME revocation-ordered
// final transaction, and pass its captured expected sequence. No auth is here.
func (t *Transport) Publish(expectedSequence uint64) error {
	return t.gate.Publish(t.generation, expectedSequence)
}

// Invalidate is the only revocation operation suitable for a session callback:
// nonblocking and no owner mutex, I/O, drain, or Java callback. It does not itself
// acknowledge completed denial. Close must follow outside session monitors.
func (t *Transport) Invalidate() (uint64, error) { return t.gate.Invalidate(t.generation) }

// Close retires the transport and acknowledges completion only after all
// admitted writes/sends and pinned device shutdown complete. It never closes
// the lifetime TUN. A blocked lifetime Write can prevent acknowledgement; a
// caller requiring a deadline must wait for this on a worker and retain the
// fence if its deadline expires. Done closes only on completed retirement.
func (t *Transport) Close() error {
	t.Invalidate()
	t.tun.Close()
	t.bind.Close() // Cancel outer I/O before serializing full retirement.
	t.fence.owner.Lock()
	defer t.fence.owner.Unlock()
	return t.closeLocked()
}

func (t *Transport) closeLocked() error {
	if t.closed {
		return t.closeErr
	}
	t.Invalidate()
	t.tun.Close()
	bindErr := t.bind.Close()
	// No new Add can run while owner is held. Cancellation above releases any
	// blocked kick; wait for the entire exported call (including timer updates)
	// before destroying its peer/device or publishing a new generation.
	t.probes.Wait()
	if t.device != nil {
		t.device.Close()
		<-t.device.Wait()
	}
	t.closeErr = errors.Join(bindErr, t.gate.Drain(t.generation))
	t.fence.mu.Lock()
	delete(t.fence.children, t.tun)
	t.fence.mu.Unlock()
	t.closed = true
	close(t.done)
	// Successful retirement must not keep the last closed WG device/private
	// identity rooted by the lifetime fence. The registry's high-water mark
	// independently preserves strict monotonic generations. Never forget a
	// failed barrier, nor a different current transport installed later.
	if t.closeErr == nil && t.fence.current == t {
		t.fence.current = nil
	}
	return t.closeErr
}
