// SPDX-License-Identifier: MIT
package readiness

import (
	"bytes"
	"context"
	"encoding/binary"
	"errors"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"net"
	"net/netip"
	"sync"
	"testing"
	"time"
)

func config() Config {
	return Config{Identity: Identity{Fence: 1, Generation: 2, Epoch: 3, Attempt: 4}, Addresses: []netip.Addr{netip.MustParseAddr("10.0.0.2")}, MTU: 1420, Capacity: 2,
		Flows: []Flow{{Protocol: 17, Local: netip.MustParseAddrPort("10.0.0.2:31001"), Remote: netip.MustParseAddrPort("1.1.1.1:53")},
			{Protocol: 6, Local: netip.MustParseAddrPort("10.0.0.2:31002"), Remote: netip.MustParseAddrPort("93.184.216.34:443")}}}
}
func deadline(t *testing.T) context.Context {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	t.Cleanup(cancel)
	return ctx
}
func endpoint(t *testing.T) *Endpoint {
	t.Helper()
	p, err := New(config())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { p.Close() })
	return p
}
func TestUDPOutputIsOwnedAndCloseInvalidatesReturnedPackets(t *testing.T) {
	p := endpoint(t)
	c, err := p.OpenUDP(0)
	if err != nil {
		t.Fatal(err)
	}
	if _, err = c.Write([]byte("fixed-control-query")); err != nil {
		t.Fatal(err)
	}
	pkt, err := p.Next(deadline(t))
	if err != nil {
		t.Fatal(err)
	}
	buf := make([]byte, 65575)
	n, err := p.CopyPacket(pkt, buf)
	if err != nil || n < 28 || string(buf[n-19:n]) != "fixed-control-query" {
		t.Fatal(n, err)
	}
	other := endpoint(t)
	if _, err = other.CopyPacket(pkt, buf); !errors.Is(err, ErrClosed) {
		t.Fatal("foreign packet accepted", err)
	}
	p.Close()
	if _, err = p.CopyPacket(pkt, buf); !errors.Is(err, ErrClosed) {
		t.Fatal("retired packet accepted", err)
	}
	if _, err = p.OpenUDP(0); !errors.Is(err, ErrClosed) {
		t.Fatal("flow created after close", err)
	}
}
func TestCloseCancelsBlockedOutputAndTCPDial(t *testing.T) {
	p := endpoint(t)
	done := make(chan error, 1)
	go func() { _, err := p.DialTCP(context.Background(), 1); done <- err }()
	if _, err := p.Next(deadline(t)); err != nil {
		t.Fatal(err)
	}
	read := make(chan error, 1)
	go func() { _, err := p.Next(context.Background()); read <- err }()
	p.Close()
	select {
	case err := <-done:
		if err == nil {
			t.Fatal("TCP succeeded without reply")
		}
	case <-deadline(t).Done():
		t.Fatal("dial survived close")
	}
	select {
	case err := <-read:
		if err == nil {
			t.Fatal("read succeeded after close")
		}
	case <-deadline(t).Done():
		t.Fatal("read survived close")
	}
}
func TestQueuePressureNeverBlocksClose(t *testing.T) {
	p := endpoint(t)
	c, err := p.OpenUDP(0)
	if err != nil {
		t.Fatal(err)
	}
	for i := 0; i < 500; i++ {
		c.Write([]byte("query"))
	}
	done := make(chan struct{})
	go func() { p.Close(); close(done) }()
	select {
	case <-done:
	case <-deadline(t).Done():
		t.Fatal("pressure blocked retirement")
	}
}
func TestMalformedAndUnrelatedInboundIsRejected(t *testing.T) {
	p := endpoint(t)
	for _, b := range [][]byte{nil, {}, make([]byte, 28), {0x45}} {
		if err := p.Inject(b); err == nil {
			t.Fatal("invalid private response accepted")
		}
	}
	p.Close()
	if err := p.Inject(make([]byte, 28)); !errors.Is(err, ErrClosed) {
		t.Fatal("closed injection", err)
	}
}
func TestInvalidConfigurationFailsBeforeStackCreation(t *testing.T) {
	for _, mutate := range []func(*Config){func(c *Config) { c.Identity.Attempt = 0 }, func(c *Config) { c.Capacity = 0 }, func(c *Config) { c.MTU = 0 }, func(c *Config) { c.Flows[0].Local = netip.MustParseAddrPort("10.0.0.9:31001") }, func(c *Config) { c.Flows = append(c.Flows, c.Flows[0]) }} {
		c := config()
		mutate(&c)
		if p, err := New(c); err == nil {
			p.Close()
			t.Fatal("invalid config accepted")
		}
	}
}

func TestPrivateUDPReplyIsDeliveredAndForeignTuplesDenied(t *testing.T) {
	p := endpoint(t)
	c, err := p.OpenUDP(0)
	if err != nil {
		t.Fatal(err)
	}
	payload := []byte("dns-codec-bytes")
	if _, err = c.Write(payload); err != nil {
		t.Fatal(err)
	}
	pkt, err := p.Next(deadline(t))
	if err != nil {
		t.Fatal(err)
	}
	buf := make([]byte, 65575)
	n, err := p.CopyPacket(pkt, buf)
	if err != nil {
		t.Fatal(err)
	}
	reply := append([]byte(nil), buf[:n]...)
	copy(reply[12:16], buf[16:20])
	copy(reply[16:20], buf[12:16])
	copy(reply[20:22], buf[22:24])
	copy(reply[22:24], buf[20:22])
	// IPv4 permits a zero UDP checksum. Recompute the real IP checksum after reversal.
	reply[26] = 0
	reply[27] = 0
	ip := header.IPv4(reply)
	ip.SetChecksum(0)
	ip.SetChecksum(^ip.CalculateChecksum())
	foreign := append([]byte(nil), reply...)
	foreign[23]++
	if err = p.Inject(foreign); !errors.Is(err, ErrPacket) {
		t.Fatal("foreign reply accepted", err)
	}
	if err = p.Inject(reply); err != nil {
		t.Fatal(err)
	}
	c.SetReadDeadline(time.Now().Add(time.Second))
	got := make([]byte, 1024)
	n, err = c.Read(got)
	if err != nil || !bytes.Equal(got[:n], payload) {
		t.Fatal("private reply not delivered", n, err)
	}
	// Private filtering remains strict on malformed envelopes, fragments and directions.
	for _, offset := range []int{0, 2, 6, 9, 12, 16, 20, 22, 24} {
		malformed := append([]byte(nil), reply...)
		malformed[offset] ^= 1
		if err = p.Inject(malformed); !errors.Is(err, ErrPacket) {
			t.Fatalf("mutation at %d accepted: %v", offset, err)
		}
	}
	if _, err = p.OpenUDP(0); !errors.Is(err, ErrBusy) {
		t.Fatal("duplicate flow admitted", err)
	}
	if err = c.Close(); err != nil {
		t.Fatal(err)
	}
	if _, err = p.OpenUDP(0); err != nil {
		t.Fatal("closed UDP slot not reclaimed", err)
	}
}
func TestIPv6PrivateOutputUsesConfiguredSourceAndRejectsExtensions(t *testing.T) {
	cfg := config()
	cfg.Addresses = []netip.Addr{netip.MustParseAddr("fd00::2")}
	cfg.Flows = []Flow{{Protocol: 17, Local: netip.MustParseAddrPort("[fd00::2]:31001"), Remote: netip.MustParseAddrPort("[2606:4700:4700::1111]:53")}}
	p, err := New(cfg)
	if err != nil {
		t.Fatal(err)
	}
	defer p.Close()
	// Mutating caller configuration must not change the owned tuple.
	cfg.Flows[0].Local = netip.MustParseAddrPort("[fd00::3]:32000")
	c, err := p.OpenUDP(0)
	if err != nil {
		t.Fatal(err)
	}
	c.Write([]byte("query"))
	pkt, err := p.Next(deadline(t))
	if err != nil {
		t.Fatal(err)
	}
	b := make([]byte, 65575)
	n, err := p.CopyPacket(pkt, b)
	if err != nil || n < 48 || b[0]>>4 != 6 || binary.BigEndian.Uint16(b[40:42]) != 31001 {
		t.Fatal("wrong IPv6 producer", n, err)
	}
	copy(b[8:24], netip.MustParseAddr("2606:4700:4700::1111").AsSlice())
	copy(b[24:40], netip.MustParseAddr("fd00::2").AsSlice())
	b[40], b[41], b[42], b[43] = 0, 53, 0x79, 0x19
	b[6] = 44
	if err = p.Inject(b[:n]); !errors.Is(err, ErrPacket) {
		t.Fatal("IPv6 fragment/extension accepted", err)
	}
}
func TestConcurrentRetirementAndFlowCreation(t *testing.T) {
	p := endpoint(t)
	var wg sync.WaitGroup
	for i := 0; i < 16; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := 0; j < 10; j++ {
				if c, err := p.OpenUDP(0); err == nil {
					c.Close()
				}
			}
			p.Close()
		}()
	}
	wg.Wait()
	if _, err := p.Next(context.Background()); !errors.Is(err, ErrClosed) {
		t.Fatal("closed output readable", err)
	}
}

// A controllably blocked I/O boundary makes retirement acknowledgement
// observable without relying on how quickly the gVisor scheduler returns Read.
type blockedConn struct {
	net.Conn
	entered, release, closed chan struct{}
}

func (c *blockedConn) Read(b []byte) (int, error) { close(c.entered); <-c.release; return 0, ErrClosed }
func (c *blockedConn) Close() error               { close(c.closed); return nil }
func TestCloseWaitsForAdmittedConnectionMethod(t *testing.T) {
	p := endpoint(t)
	raw := &blockedConn{entered: make(chan struct{}), release: make(chan struct{}), closed: make(chan struct{})}
	c, err := p.own(raw, 0)
	if err != nil {
		t.Fatal(err)
	}
	readDone := make(chan struct{})
	go func() { c.Read(make([]byte, 1)); close(readDone) }()
	<-raw.entered
	done := make(chan struct{})
	go func() { p.Close(); close(done) }()
	<-raw.closed
	premature := false
	select {
	case <-done:
		premature = true
	case <-time.After(100 * time.Millisecond):
	}
	close(raw.release)
	<-readDone
	<-done
	if premature {
		t.Fatal("retirement acknowledged while an admitted connection Read still accessed endpoint")
	}
}
