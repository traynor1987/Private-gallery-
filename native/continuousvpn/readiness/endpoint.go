// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Private Gallery contributors.

// Package readiness owns a bounded private network stack for fixed owner control
// flows. It opens no host network sockets and exports no stack or raw producer.
// It is not Android containment, DNS/TLS validation, or a readiness receipt.
package readiness

import (
	"context"
	"encoding/binary"
	"errors"
	"net"
	"net/netip"
	"sync"
	"time"

	"gvisor.dev/gvisor/pkg/bufferv2"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/adapters/gonet"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/link/channel"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv4"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv6"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/tcp"
	"gvisor.dev/gvisor/pkg/tcpip/transport/udp"
	"gvisor.dev/gvisor/pkg/waiter"
)

var (
	ErrClosed = errors.New("private readiness attempt closed or foreign")
	ErrConfig = errors.New("invalid private readiness configuration")
	ErrPacket = errors.New("not a configured private control packet")
	ErrBusy   = errors.New("private flow already owned")
)

type Identity struct{ Fence, Generation, Epoch, Attempt uint64 }
type Flow struct {
	Protocol      uint8
	Local, Remote netip.AddrPort
}
type Config struct {
	Identity      Identity
	Addresses     []netip.Addr
	Flows         []Flow
	MTU, Capacity int
}

// Packet cannot be constructed by a renderer, reconstructed from bytes or moved
// between attempts. CopyPacket rechecks its exact owner after dequeue. Only the
// endpoint pump produces one. Integration must preserve that provenance through
// transport enqueue/dequeue rather than exposing an arbitrary tagged JNI send.
type Packet struct {
	owner *Endpoint
	bytes []byte
}

type Endpoint struct {
	config   Config
	link     *channel.Endpoint
	stack    *stack.Stack
	ctx      context.Context
	cancel   context.CancelFunc
	out      chan []byte
	pumpDone chan struct{}
	mu       sync.Mutex
	closed   bool
	used     map[int]bool
	conns    map[*ownedConn]struct{}
	ops      sync.WaitGroup
	once     sync.Once
}

func addressOK(a netip.Addr) bool {
	return a.IsValid() && !a.IsUnspecified() && !a.IsMulticast() && !a.Is4In6() && a.Zone() == "" && a != netip.MustParseAddr("255.255.255.255")
}
func New(c Config) (*Endpoint, error) {
	if c.Identity.Fence == 0 || c.Identity.Generation == 0 || c.Identity.Epoch == 0 || c.Identity.Attempt == 0 || c.MTU < 1280 || c.MTU > 65535 || c.Capacity < 1 || c.Capacity > 1024 || len(c.Addresses) < 1 || len(c.Addresses) > 16 || len(c.Flows) < 1 || len(c.Flows) > 128 {
		return nil, ErrConfig
	}
	addresses := make(map[netip.Addr]bool)
	for _, a := range c.Addresses {
		if !addressOK(a) || addresses[a] {
			return nil, ErrConfig
		}
		addresses[a] = true
	}
	slots := make(map[struct {
		protocol uint8
		local    netip.AddrPort
	}]bool)
	for _, f := range c.Flows {
		key := struct {
			protocol uint8
			local    netip.AddrPort
		}{f.Protocol, f.Local}
		if (f.Protocol != 6 && f.Protocol != 17) || !addresses[f.Local.Addr()] || !addressOK(f.Remote.Addr()) || f.Local.Port() == 0 || f.Remote.Port() == 0 || f.Local.Addr().Is4() != f.Remote.Addr().Is4() || slots[key] {
			return nil, ErrConfig
		}
		slots[key] = true
	}
	c.Addresses = append([]netip.Addr(nil), c.Addresses...)
	c.Flows = append([]Flow(nil), c.Flows...)
	ctx, cancel := context.WithCancel(context.Background())
	p := &Endpoint{config: c, ctx: ctx, cancel: cancel, link: channel.New(c.Capacity, uint32(c.MTU), ""), out: make(chan []byte, c.Capacity), pumpDone: make(chan struct{}), used: make(map[int]bool), conns: make(map[*ownedConn]struct{})}
	p.stack = stack.New(stack.Options{NetworkProtocols: []stack.NetworkProtocolFactory{ipv4.NewProtocol, ipv6.NewProtocol}, TransportProtocols: []stack.TransportProtocolFactory{tcp.NewProtocol, udp.NewProtocol}})
	cleanup := func() { cancel(); p.link.Close(); p.stack.Close(); p.stack.Wait() }
	if e := p.stack.CreateNIC(1, p.link); e != nil {
		cleanup()
		return nil, ErrConfig
	}
	for _, a := range c.Addresses {
		proto := protocol(a)
		if e := p.stack.AddProtocolAddress(1, tcpip.ProtocolAddress{Protocol: proto, AddressWithPrefix: tcpip.Address(a.AsSlice()).WithPrefix()}, stack.AddressProperties{}); e != nil {
			cleanup()
			return nil, ErrConfig
		}
	}
	for _, v4 := range []bool{true, false} {
		for _, a := range c.Addresses {
			if a.Is4() == v4 {
				subnet := header.IPv6EmptySubnet
				if v4 {
					subnet = header.IPv4EmptySubnet
				}
				p.stack.AddRoute(tcpip.Route{Destination: subnet, NIC: 1})
				break
			}
		}
	}
	go p.pump()
	return p, nil
}
func protocol(a netip.Addr) tcpip.NetworkProtocolNumber {
	if a.Is4() {
		return ipv4.ProtocolNumber
	}
	return ipv6.ProtocolNumber
}
func full(a netip.AddrPort) tcpip.FullAddress {
	return tcpip.FullAddress{NIC: 1, Addr: tcpip.Address(a.Addr().AsSlice()), Port: a.Port()}
}
func wipe(b []byte) {
	for i := range b {
		b[i] = 0
	}
}

func (p *Endpoint) begin() error {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.closed {
		return ErrClosed
	}
	p.ops.Add(1)
	return nil
}
func (p *Endpoint) reserve(slot int, proto uint8) (Flow, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.closed {
		return Flow{}, ErrClosed
	}
	if slot < 0 || slot >= len(p.config.Flows) || p.config.Flows[slot].Protocol != proto {
		return Flow{}, ErrConfig
	}
	if p.used[slot] {
		return Flow{}, ErrBusy
	}
	p.used[slot] = true
	p.ops.Add(1)
	return p.config.Flows[slot], nil
}
func (p *Endpoint) unreserve(slot int) { p.mu.Lock(); delete(p.used, slot); p.mu.Unlock() }

type ownedConn struct {
	conn  net.Conn
	owner *Endpoint
	slot  int
	once  sync.Once
	err   error
}

// Every adapter method joins the same retirement barrier. Caller-owned
// processing after a method returns still needs an attempt check/worker join.
func (c *ownedConn) Read(b []byte) (int, error) {
	if err := c.owner.begin(); err != nil {
		return 0, err
	}
	defer c.owner.ops.Done()
	return c.conn.Read(b)
}
func (c *ownedConn) Write(b []byte) (int, error) {
	if err := c.owner.begin(); err != nil {
		return 0, err
	}
	defer c.owner.ops.Done()
	return c.conn.Write(b)
}
func (c *ownedConn) SetDeadline(t time.Time) error {
	if err := c.owner.begin(); err != nil {
		return err
	}
	defer c.owner.ops.Done()
	return c.conn.SetDeadline(t)
}
func (c *ownedConn) SetReadDeadline(t time.Time) error {
	if err := c.owner.begin(); err != nil {
		return err
	}
	defer c.owner.ops.Done()
	return c.conn.SetReadDeadline(t)
}
func (c *ownedConn) SetWriteDeadline(t time.Time) error {
	if err := c.owner.begin(); err != nil {
		return err
	}
	defer c.owner.ops.Done()
	return c.conn.SetWriteDeadline(t)
}
func (c *ownedConn) LocalAddr() net.Addr {
	if c.owner.begin() != nil {
		return nil
	}
	defer c.owner.ops.Done()
	return c.conn.LocalAddr()
}
func (c *ownedConn) RemoteAddr() net.Addr {
	if c.owner.begin() != nil {
		return nil
	}
	defer c.owner.ops.Done()
	return c.conn.RemoteAddr()
}
func (c *ownedConn) Close() error {
	c.once.Do(func() {
		c.err = c.conn.Close()
		c.owner.mu.Lock()
		delete(c.owner.conns, c)
		delete(c.owner.used, c.slot)
		c.owner.mu.Unlock()
	})
	return c.err
}
func (p *Endpoint) own(c net.Conn, slot int) (net.Conn, error) {
	p.mu.Lock()
	if p.closed {
		p.mu.Unlock()
		c.Close()
		return nil, ErrClosed
	}
	owned := &ownedConn{conn: c, owner: p, slot: slot}
	p.conns[owned] = struct{}{}
	p.mu.Unlock()
	return owned, nil
}
func (p *Endpoint) OpenUDP(slot int) (net.Conn, error) {
	f, err := p.reserve(slot, 17)
	if err != nil {
		return nil, err
	}
	defer p.ops.Done()
	ok := false
	defer func() {
		if !ok {
			p.unreserve(slot)
		}
	}()
	local, remote := full(f.Local), full(f.Remote)
	c, err := gonet.DialUDP(p.stack, &local, &remote, protocol(f.Local.Addr()))
	if err != nil {
		return nil, err
	}
	result, err := p.own(c, slot)
	ok = err == nil
	return result, err
}

// DialTCP owns its endpoint from allocation, including bind/cancellation error
// exits omitted by the pinned convenience DialTCPWithBind helper. Neither a Go
// default dialer nor a system resolver/proxy is used.
func (p *Endpoint) DialTCP(parent context.Context, slot int) (net.Conn, error) {
	if parent == nil {
		return nil, ErrConfig
	}
	f, err := p.reserve(slot, 6)
	if err != nil {
		return nil, err
	}
	defer p.ops.Done()
	ok := false
	defer func() {
		if !ok {
			p.unreserve(slot)
		}
	}()
	ctx, cancel := context.WithCancel(parent)
	joined := make(chan struct{})
	go func() {
		defer close(joined)
		select {
		case <-p.ctx.Done():
			cancel()
		case <-ctx.Done():
		}
	}()
	defer func() { cancel(); <-joined }()
	if err = ctx.Err(); err != nil {
		return nil, err
	}
	var wq waiter.Queue
	ep, e := p.stack.NewEndpoint(tcp.ProtocolNumber, protocol(f.Local.Addr()), &wq)
	if e != nil {
		return nil, ErrConfig
	}
	transferred := false
	defer func() {
		if !transferred {
			ep.Close()
		}
	}()
	entry, notify := waiter.NewChannelEntry(waiter.WritableEvents)
	wq.EventRegister(&entry)
	defer wq.EventUnregister(&entry)
	if e = ep.Bind(full(f.Local)); e != nil {
		return nil, errors.New(e.String())
	}
	e = ep.Connect(full(f.Remote))
	if _, started := e.(*tcpip.ErrConnectStarted); started {
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		case <-notify:
		}
		e = ep.LastError()
	}
	if e != nil {
		return nil, errors.New(e.String())
	}
	if err = ctx.Err(); err != nil {
		return nil, err
	}
	c := gonet.NewTCPConn(&wq, ep)
	transferred = true
	result, err := p.own(c, slot)
	ok = err == nil
	return result, err
}

func (p *Endpoint) pump() {
	defer close(p.pumpDone)
	for {
		pkt := p.link.ReadContext(p.ctx)
		if pkt.IsNil() {
			return
		}
		view := pkt.ToView()
		pkt.DecRef()
		b := append([]byte(nil), view.AsSlice()...)
		view.Release()
		if p.ctx.Err() != nil || !p.matches(b, false) {
			wipe(b)
			continue
		}
		select {
		case p.out <- b:
		default:
			wipe(b)
		}
	}
}
func (p *Endpoint) Next(ctx context.Context) (Packet, error) {
	if ctx == nil {
		return Packet{}, ErrConfig
	}
	if err := p.begin(); err != nil {
		return Packet{}, err
	}
	defer p.ops.Done()
	select {
	case <-ctx.Done():
		return Packet{}, ctx.Err()
	case <-p.ctx.Done():
		return Packet{}, ErrClosed
	case b := <-p.out:
		p.mu.Lock()
		closed := p.closed
		p.mu.Unlock()
		if closed || ctx.Err() != nil {
			wipe(b)
			return Packet{}, ErrClosed
		}
		return Packet{owner: p, bytes: b}, nil
	}
}
func (p *Endpoint) CopyPacket(pkt Packet, dst []byte) (int, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.closed || pkt.owner != p || len(pkt.bytes) == 0 {
		return 0, ErrClosed
	}
	if len(dst) < len(pkt.bytes) {
		return 0, ErrPacket
	}
	return copy(dst, pkt.bytes), nil
}
func (p *Endpoint) Inject(b []byte) error {
	if err := p.begin(); err != nil {
		return err
	}
	defer p.ops.Done()
	if !p.matches(b, true) {
		return ErrPacket
	}
	owned := append([]byte(nil), b...)
	pkt := stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: bufferv2.MakeWithData(owned)})
	defer pkt.DecRef()
	proto := header.IPv6ProtocolNumber
	if b[0]>>4 == 4 {
		proto = header.IPv4ProtocolNumber
	}
	p.link.InjectInbound(proto, pkt)
	return nil
}

// Close seals creation/injection before joining workers and closing the pinned
// stack. It acknowledges only a complete barrier, with no callback under mu.
// External passive kernel keepers must outlive this stack and all sensitive
// children. This close is not permission to release the lifetime Android TUN.
func (p *Endpoint) Close() {
	p.once.Do(func() {
		p.mu.Lock()
		p.closed = true
		p.cancel()
		conns := make([]*ownedConn, 0, len(p.conns))
		for c := range p.conns {
			conns = append(conns, c)
		}
		p.mu.Unlock()
		for _, c := range conns {
			c.Close()
		}
		p.ops.Wait()
		<-p.pumpDone
		p.link.Close()
		p.stack.Close()
		p.stack.Wait()
		for {
			select {
			case b := <-p.out:
				wipe(b)
			default:
				return
			}
		}
	})
}

// Only unfragmented, extension-free exact configured control tuples are private
// traffic. This is flow filtering, never proof of origin for Android TUN bytes.
func (p *Endpoint) matches(b []byte, inbound bool) bool {
	if len(b) < 20 || len(b) > p.config.MTU {
		return false
	}
	var src, dst netip.Addr
	off := 20
	var proto uint8
	switch b[0] >> 4 {
	case 4:
		if b[0] != 0x45 || int(binary.BigEndian.Uint16(b[2:4])) != len(b) || binary.BigEndian.Uint16(b[6:8])&0xbfff != 0 {
			return false
		}
		src = netip.AddrFrom4([4]byte{b[12], b[13], b[14], b[15]})
		dst = netip.AddrFrom4([4]byte{b[16], b[17], b[18], b[19]})
		proto = b[9]
	case 6:
		if len(b) < 40 || int(binary.BigEndian.Uint16(b[4:6]))+40 != len(b) {
			return false
		}
		off = 40
		proto = b[6]
		var a, c [16]byte
		copy(a[:], b[8:24])
		copy(c[:], b[24:40])
		src = netip.AddrFrom16(a)
		dst = netip.AddrFrom16(c)
	default:
		return false
	}
	payload := b[off:]
	if proto == 17 {
		if len(payload) < 8 || int(binary.BigEndian.Uint16(payload[4:6])) != len(payload) {
			return false
		}
	} else if proto == 6 {
		if len(payload) < 20 || int(payload[12]>>4)*4 < 20 || int(payload[12]>>4)*4 > len(payload) {
			return false
		}
	} else {
		return false
	}
	source := netip.AddrPortFrom(src, binary.BigEndian.Uint16(payload[:2]))
	dest := netip.AddrPortFrom(dst, binary.BigEndian.Uint16(payload[2:4]))
	if inbound {
		source, dest = dest, source
	}
	for _, f := range p.config.Flows {
		if f.Protocol == proto && f.Local == source && f.Remote == dest {
			return true
		}
	}
	return false
}
