//go:build linux

package bridge

import (
	"errors"
	"golang.org/x/sys/unix"
	"runtime"
	"syscall"
	"testing"
)

func ownedPipe(t *testing.T) (int, int) {
	t.Helper()
	fds := []int{0, 0}
	if err := unix.Pipe2(fds, unix.O_NONBLOCK|unix.O_CLOEXEC); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { unix.Close(fds[1]) })
	return fds[0], fds[1]
}
func TestOwnedFDConsumedOnEveryErrorAndNeverDoubleCloses(t *testing.T) {
	for _, name := range []string{"", "bad\x00name"} {
		fd, _ := ownedPipe(t)
		r := new(Registry)
		if r.CreateFD(fd, 1280, name) > 0 {
			t.Fatal("invalid name")
		}
		if _, e := unix.FcntlInt(uintptr(fd), unix.F_GETFD, 0); !errors.Is(e, syscall.EBADF) {
			t.Fatal("duplicate not consumed", e)
		}
		fd2, _ := ownedPipe(t)
		if fd2 != fd {
			t.Fatal("expected lowest fd reuse", fd, fd2)
		}
		runtime.GC()
		if _, e := unix.FcntlInt(uintptr(fd2), unix.F_GETFD, 0); e != nil {
			t.Fatal("finalizer closed reused descriptor", e)
		}
		unix.Close(fd2)
	}
}
func TestLifetimeFDReaderCancelledOnlyOnFinalRelease(t *testing.T) {
	fd, _ := ownedPipe(t)
	r := new(Registry)
	f := r.CreateFD(fd, 1280, "owned-test")
	if f <= 0 {
		t.Fatal(f)
	}
	if r.Release(f, false) == 0 {
		t.Fatal("no death proof")
	}
	if _, e := unix.FcntlInt(uintptr(fd), unix.F_GETFD, 0); e != nil {
		t.Fatal("early close")
	}
	if r.Release(f, true) != 0 {
		t.Fatal("release")
	}
	if _, e := unix.FcntlInt(uintptr(fd), unix.F_GETFD, 0); !errors.Is(e, syscall.EBADF) {
		t.Fatal(e)
	}
}
func TestBlockingDuplicateRejectedWithoutChangingSharedFlags(t *testing.T) {
	fds := []int{0, 0}
	if e := unix.Pipe(fds); e != nil {
		t.Fatal(e)
	}
	defer unix.Close(fds[1])
	duplicate, e := unix.Dup(fds[0])
	if e != nil {
		t.Fatal(e)
	}
	defer unix.Close(fds[0])
	r := new(Registry)
	if r.CreateFD(duplicate, 1280, "blocking") > 0 {
		t.Fatal("blocking fd adopted")
	}
	flags, e := unix.FcntlInt(uintptr(fds[0]), unix.F_GETFL, 0)
	if e != nil || flags&unix.O_NONBLOCK != 0 {
		t.Fatal("shared flags mutated")
	}
}
