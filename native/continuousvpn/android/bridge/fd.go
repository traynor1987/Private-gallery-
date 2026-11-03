//go:build linux || android

// SPDX-License-Identifier: MIT
package bridge

import (
	"golang.org/x/sys/unix"
	"golang.zx2c4.com/wireguard/tun"
	"os"
	"strings"
	"sync"
)

// ownedTun deliberately does not invoke upstream CreateUnmonitoredTUNFromFD:
// os.File is the sole closer after adoption, including every error path. We
// require an already nonblocking owned dup so Close cancels the sole Read, and
// never change shared open-file flags inherited by the Java protective PFD.
type ownedTun struct {
	file     *os.File
	name     string
	mtu      int
	events   chan tun.Event
	once     sync.Once
	closeErr error
}

// CreateFD consumes ownedFD at entry on EVERY result. Never pass the original
// protective Java PFD. JNI transfers its owned duplicate only; Java retains the
// original until the later independently reviewed lifetime teardown protocol.
func (r *Registry) CreateFD(ownedFD, mtu int, name string) int64 {
	if ownedFD < 0 {
		return Invalid
	}
	flags, err := unix.FcntlInt(uintptr(ownedFD), unix.F_GETFL, 0)
	if err != nil || flags&unix.O_NONBLOCK == 0 {
		unix.Close(ownedFD)
		return Invalid
	}
	f := os.NewFile(uintptr(ownedFD), "nova-owned-tun")
	if f == nil {
		unix.Close(ownedFD)
		return Failed
	}
	// After adoption never raw-close; os.File.Close also suppresses its finalizer.
	if mtu < 576 || mtu > 65575 || len(name) < 1 || len(name) > 15 || strings.IndexByte(name, 0) >= 0 {
		f.Close()
		return Invalid
	}
	return r.create(&ownedTun{file: f, mtu: mtu, name: name, events: make(chan tun.Event)})
}
func (t *ownedTun) File() *os.File           { return nil }
func (t *ownedTun) Name() (string, error)    { return t.name, nil }
func (t *ownedTun) MTU() (int, error)        { return t.mtu, nil }
func (t *ownedTun) Events() <-chan tun.Event { return t.events }
func (t *ownedTun) Read(p []byte, offset int) (int, error) {
	if offset < 0 || offset > len(p) {
		return 0, unix.EINVAL
	}
	return t.file.Read(p[offset:])
}
func (t *ownedTun) Write(p []byte, offset int) (int, error) {
	if offset < 0 || offset > len(p) {
		return 0, unix.EINVAL
	}
	return t.file.Write(p[offset:])
}
func (t *ownedTun) Flush() error { return nil }
func (t *ownedTun) Close() error {
	t.once.Do(func() { t.closeErr = t.file.Close(); close(t.events) })
	return t.closeErr
}
