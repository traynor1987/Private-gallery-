//go:build android

// SPDX-License-Identifier: MIT
package main

/*
#include <stdint.h>
int nova_protect(uintptr_t protector, int fd);
void nova_release_protector(uintptr_t protector);
*/
import "C"
import (
	"golang.zx2c4.com/wireguard/conn"
	"strings"
	"uk.co.traynor.privategallery/continuousvpn/android/bridge"
	"unsafe"
)

var owner bridge.Registry

// C owns the JavaVM/global reference storage; no Go pointer crosses JNI.
type protector struct{ value C.uintptr_t }

func (p *protector) Protect(fd int) bool { return C.nova_protect(p.value, C.int(fd)) != 0 }
func (p *protector) Release()            { C.nova_release_protector(p.value) }

//export goNovaCreate
func goNovaCreate(fd, mtu C.int, name *C.char) C.longlong {
	return C.longlong(owner.CreateFD(int(fd), int(mtu), C.GoString(name)))
}

//export goNovaReplace
func goNovaReplace(f, g, e C.longlong, private, peer, psk, endpoint, routes *C.char, keepalive, listen C.int, p C.uintptr_t) C.longlong {
	c := bridge.Config{PrivateKey: C.GoString(private), PeerKey: C.GoString(peer), PresharedKey: C.GoString(psk), Endpoint: C.GoString(endpoint), AllowedIPs: strings.Split(C.GoString(routes), ","), Keepalive: int(keepalive), ListenPort: int(listen)}
	return C.longlong(owner.Replace(int64(f), int64(g), int64(e), c, conn.NewStdNetBind(), &protector{p}))
}

//export goNovaEpoch
func goNovaEpoch(f C.longlong) C.longlong { return C.longlong(owner.Epoch(int64(f))) }

//export goNovaBegin
func goNovaBegin(f, t, e C.longlong) C.longlong {
	return C.longlong(owner.BeginProbe(int64(f), int64(t), int64(e)))
}

//export goNovaRegister
func goNovaRegister(f, t, e C.longlong, protocol C.int, source, dest *C.char) C.longlong {
	return C.longlong(owner.Register(int64(f), int64(t), int64(e), int(protocol), C.GoString(source), C.GoString(dest)))
}

//export goNovaRemove
func goNovaRemove(f, t, e, l C.longlong) C.longlong {
	return C.longlong(owner.Remove(int64(f), int64(t), int64(e), int64(l)))
}

//export goNovaSequence
func goNovaSequence(f, t, e C.longlong) C.longlong {
	return C.longlong(owner.Sequence(int64(f), int64(t), int64(e)))
}

//export goNovaPublish
func goNovaPublish(f, t, e, s C.longlong) C.longlong {
	return C.longlong(owner.Publish(int64(f), int64(t), int64(e), int64(s)))
}

//export goNovaInvalidate
func goNovaInvalidate(f C.longlong) C.longlong { return C.longlong(owner.Invalidate(int64(f))) }

//export goNovaInvalidateTransport
func goNovaInvalidateTransport(f, t C.longlong) C.longlong {
	return C.longlong(owner.InvalidateTransport(int64(f), int64(t)))
}

//export goNovaRetire
func goNovaRetire(f, t C.longlong) C.longlong { return C.longlong(owner.Retire(int64(f), int64(t))) }

//export goNovaRetireCurrent
func goNovaRetireCurrent(f, g, e C.longlong) C.longlong {
	return C.longlong(owner.RetireCurrent(int64(f), int64(g), int64(e)))
}

//export goNovaRelease
func goNovaRelease(f C.longlong, proof C.int) C.longlong {
	return C.longlong(owner.Release(int64(f), proof == 1))
}

//export goNovaStatistics
func goNovaStatistics(f, t, e C.longlong, out *C.longlong) C.longlong {
	s, status := owner.Statistics(int64(f), int64(t), int64(e))
	if status != 0 {
		return C.longlong(status)
	}
	values := (*[4]C.longlong)(unsafe.Pointer(out))
	values[0] = C.longlong(s.HandshakeSeconds)
	values[1] = C.longlong(s.HandshakeNanos)
	values[2] = C.longlong(s.RxBytes)
	values[3] = C.longlong(s.TxBytes)
	return 0
}
func main() {}
