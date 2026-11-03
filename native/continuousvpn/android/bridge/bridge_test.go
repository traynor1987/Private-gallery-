package bridge

import (
	"errors"
	"golang.zx2c4.com/wireguard/conn"
	"golang.zx2c4.com/wireguard/tun"
	"math"
	"net"
	"os"
	"runtime"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"
	"uk.co.traynor.privategallery/continuousvpn/transport"
)

// Only kernel-facing TUN and bind are fakes; all fence/gate/WG code is real.
type testTun struct {
	done   chan struct{}
	once   sync.Once
	closes atomic.Int32
}

func (t *testTun) File() *os.File                     { return nil }
func (t *testTun) Name() (string, error)              { return "test", nil }
func (t *testTun) MTU() (int, error)                  { return 1280, nil }
func (t *testTun) Events() <-chan tun.Event           { return nil }
func (t *testTun) Read([]byte, int) (int, error)      { <-t.done; return 0, net.ErrClosed }
func (t *testTun) Write(p []byte, o int) (int, error) { return len(p) - o, nil }
func (t *testTun) Flush() error                       { return nil }
func (t *testTun) Close() error                       { t.once.Do(func() { t.closes.Add(1); close(t.done) }); return nil }

type testBind struct {
	mu   sync.Mutex
	done chan struct{}
}

func (b *testBind) Open(uint16) ([]conn.ReceiveFunc, uint16, error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.done = make(chan struct{})
	d := b.done
	return []conn.ReceiveFunc{func([]byte) (int, conn.Endpoint, error) { <-d; return 0, nil, net.ErrClosed }}, 12345, nil
}
func (b *testBind) Close() error {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.done != nil {
		close(b.done)
		b.done = nil
	}
	return nil
}
func (*testBind) SetMark(uint32) error             { return nil }
func (*testBind) Send([]byte, conn.Endpoint) error { return nil }
func (*testBind) ParseEndpoint(s string) (conn.Endpoint, error) {
	return conn.NewStdNetBind().ParseEndpoint(s)
}
func (*testBind) PeekLookAtSocketFd4() (int, error) { return 41, nil }
func (*testBind) PeekLookAtSocketFd6() (int, error) { return 61, nil }

type testProtector struct {
	fn       func(int) bool
	released atomic.Int32
}

func (p *testProtector) Protect(fd int) bool {
	if p.fn != nil {
		return p.fn(fd)
	}
	return true
}
func (p *testProtector) Release() { p.released.Add(1) }
func cfg() Config {
	return Config{PrivateKey: "0101010101010101010101010101010101010101010101010101010101010101", PeerKey: "0202020202020202020202020202020202020202020202020202020202020202", Endpoint: "127.0.0.1:51820", AllowedIPs: []string{"0.0.0.0/0", "::/0"}}
}
func fixture(t *testing.T) (*Registry, int64, *testTun) {
	t.Helper()
	r := new(Registry)
	d := &testTun{done: make(chan struct{})}
	h := r.create(d)
	if h <= 0 {
		t.Fatal(h)
	}
	t.Cleanup(func() { r.Release(h, true) })
	return r, h, d
}
func replace(t *testing.T, r *Registry, f, g, e int64) int64 {
	t.Helper()
	h := r.Replace(f, g, e, cfg(), &testBind{}, &testProtector{})
	if h <= 0 {
		t.Fatal(h)
	}
	return h
}
func wait(t *testing.T, c <-chan struct{}) {
	t.Helper()
	select {
	case <-c:
	case <-time.After(5 * time.Second):
		t.Fatal("timeout")
	}
}
func TestOwnershipAndLeaseRemoval(t *testing.T) {
	r, f, d := fixture(t)
	other := r.create(&testTun{done: make(chan struct{})})
	defer r.Release(other, true)
	h := replace(t, r, f, 1, 1)
	if r.BeginProbe(other, h, 1) == 0 || r.Publish(other, h, 1, 1) == 0 {
		t.Fatal("foreign fence accepted")
	}
	if r.BeginProbe(f, h, 1) != 0 {
		t.Fatal("probe")
	}
	l := r.Register(f, h, 1, 17, "10.0.0.1:30000", "1.1.1.1:53")
	if l <= 0 {
		t.Fatal(l)
	}
	if r.Remove(other, h, 1, l) == 0 || r.Remove(f, h, 2, l) == 0 || r.Remove(f, h, 1, l+100) == 0 {
		t.Fatal("foreign lease released")
	}
	if r.Remove(f, h, 1, l) != 0 {
		t.Fatal("remove")
	}
	l = r.Register(f, h, 1, 6, "10.0.0.1:30001", "1.1.1.1:443")
	if l <= 0 {
		t.Fatal(l)
	}
	if r.Invalidate(f) <= 1 || r.Publish(f, h, 1, 1) == 0 {
		t.Fatal("revocation")
	}
	if r.Remove(f, h, 1, l) != 0 {
		t.Fatal("known sealed lease")
	}
	if r.Retire(f, h) != 0 || d.closes.Load() != 0 {
		t.Fatal("retirement closed lifetime")
	}
	if r.Release(f, false) == 0 {
		t.Fatal("missing external death proof")
	}
	if r.Release(f, true) != 0 || d.closes.Load() != 1 {
		t.Fatal("release")
	}
	if r.Epoch(f) > 0 || r.Invalidate(f) > 0 {
		t.Fatal("released handle")
	}
}
func TestMalformedAndStaleReplacePreserveCurrent(t *testing.T) {
	r, f, _ := fixture(t)
	h := replace(t, r, f, 3, 1)
	bad := cfg()
	bad.Endpoint = "example.org:443"
	for _, c := range []Config{bad, {}} {
		p := &testProtector{}
		if r.Replace(f, 4, 1, c, &testBind{}, p) > 0 || p.released.Load() != 1 {
			t.Fatal("bad config/owner")
		}
	}
	if r.Replace(f, 2, 1, cfg(), &testBind{}, &testProtector{}) > 0 {
		t.Fatal("old replace")
	}
	if r.BeginProbe(f, h, 1) != 0 {
		t.Fatal("bad request sealed current")
	}
}
func TestInvalidateBeforeAndDuringReplacement(t *testing.T) {
	r, f, d := fixture(t)
	r.Invalidate(f)
	p := &testProtector{}
	if r.Replace(f, 1, 1, cfg(), &testBind{}, p) > 0 || p.released.Load() != 1 {
		t.Fatal("old epoch accepted")
	}
	entered, release, done := make(chan struct{}), make(chan struct{}), make(chan struct{})
	p = &testProtector{fn: func(fd int) bool {
		if fd == 41 {
			close(entered)
			<-release
		}
		return true
	}}
	var result int64
	go func() { result = r.Replace(f, 2, 2, cfg(), &testBind{}, p); close(done) }()
	wait(t, entered)
	if r.Invalidate(f) != 3 {
		t.Fatal("pending revocation")
	}
	if p.released.Load() != 0 {
		t.Fatal("callback prematurely freed")
	}
	if r.Release(f, true) != Busy {
		t.Fatal("release raced protect")
	}
	close(release)
	wait(t, done)
	if result > 0 || d.closes.Load() != 0 {
		t.Fatal("pending transport admitted/released")
	}
	cur := r.fence(f).current.Load()
	if cur == nil || r.BeginProbe(f, cur.id, 2) == 0 {
		t.Fatal("failed ownership unreachable or active")
	}
	if r.Retire(f, cur.id) != 0 || p.released.Load() != 1 {
		t.Fatal("retirement/ref lifetime")
	}
}
func TestReplacementFailureRetainsFenceAndCallbackOwnership(t *testing.T) {
	r, f, d := fixture(t)
	old := replace(t, r, f, 1, 1)
	p := &testProtector{fn: func(int) bool { return false }}
	if r.Replace(f, 2, 1, cfg(), &testBind{}, p) > 0 {
		t.Fatal("protect failure accepted")
	}
	if r.BeginProbe(f, old, 1) == 0 || d.closes.Load() != 0 {
		t.Fatal("failure containment")
	}
	cur := r.fence(f).current.Load()
	if cur == nil || cur.id == old {
		t.Fatal("failed generation unreachable")
	}
	if r.Retire(f, cur.id) != 0 || p.released.Load() != 1 {
		t.Fatal("failure cleanup")
	}
	replace(t, r, f, 3, 1)
}
func TestPublicationInvalidateRace(t *testing.T) {
	r, f, _ := fixture(t)
	for i := int64(1); i < 25; i++ {
		e := r.Epoch(f)
		h := replace(t, r, f, i, e)
		if r.BeginProbe(f, h, e) != 0 {
			t.Fatal("probe")
		}
		s := r.Sequence(f, h, e)
		start := make(chan struct{})
		done := make(chan struct{})
		go func() { <-start; r.Publish(f, h, e, s); close(done) }()
		close(start)
		r.Invalidate(f)
		wait(t, done)
		if r.Sequence(f, h, e) > 0 || r.Publish(f, h, e, s) == 0 {
			t.Fatal("stale publication")
		}
	}
}
func TestCloseVersusLookup(t *testing.T) {
	r, f, _ := fixture(t)
	h := replace(t, r, f, 1, 1)
	done := make(chan struct{})
	go func() {
		for i := 0; i < 1000; i++ {
			r.Sequence(f, h, 1)
			r.Publish(f, h, 1, 1)
			r.InvalidateTransport(f, h)
		}
		close(done)
	}()
	if r.Release(f, true) != 0 {
		t.Fatal("release")
	}
	wait(t, done)
	if r.BeginProbe(f, h, 1) == 0 {
		t.Fatal("use after release")
	}
}
func TestSignedHandleAndEpochExhaustion(t *testing.T) {
	r, f, _ := fixture(t)
	r.next.Store(math.MaxInt64)
	if r.allocate() > 0 || r.allocate() > 0 {
		t.Fatal("handle wrap")
	}
	r.fence(f).epoch.Store(math.MaxInt64)
	if r.Invalidate(f) > 0 || r.Epoch(f) > 0 {
		t.Fatal("epoch wrap")
	}
}
func TestTupleValidation(t *testing.T) {
	r, f, _ := fixture(t)
	h := replace(t, r, f, 1, 1)
	r.BeginProbe(f, h, 1)
	for _, x := range [][2]string{{"host:1", "1.1.1.1:53"}, {"10.0.0.1:0", "1.1.1.1:53"}, {"[::1]:1", "1.1.1.1:53"}, {"10.0.0.1:1", "[::ffff:1.1.1.1]:53"}} {
		if r.Register(f, h, 1, 17, x[0], x[1]) > 0 {
			t.Fatal(x)
		}
	}
	if r.Register(f, h, 1, 257, "10.0.0.1:1", "1.1.1.1:53") > 0 {
		t.Fatal("protocol truncated")
	}
}
func TestInvalidateBetweenPointerPublicationAndFinalEpochCheck(t *testing.T) {
	r, f, _ := fixture(t)
	ff := r.fence(f)
	p := &testProtector{}
	opts, ok := cfg().options(1, &testBind{}, p.Protect)
	if !ok {
		t.Fatal("config")
	}
	core, e := ff.core.Load().Replace(opts)
	if e != nil {
		t.Fatal(e)
	}
	id := r.reserve(f)
	tr := &instance{id: id, fenceID: f, generation: 1, epoch: 1}
	tr.payload.Store(&transportPayload{core: core, protector: p})
	r.commit(id, tr)
	// Exercise the same production steps with revocation in their exact gap.
	r.install(ff, tr)
	r.Invalidate(f)
	if r.finishReplacement(ff, tr) > 0 || r.BeginProbe(f, id, 1) == 0 {
		t.Fatal("pending publication evaded invalidation")
	}
}
func TestOldControlRequestCannotOverwriteNewPointer(t *testing.T) {
	r, f, _ := fixture(t)
	entered, release, done := make(chan struct{}), make(chan struct{}), make(chan struct{})
	p := &testProtector{fn: func(fd int) bool {
		if fd == 41 {
			close(entered)
			<-release
		}
		return true
	}}
	var newest int64
	go func() { newest = r.Replace(f, 3, 1, cfg(), &testBind{}, p); close(done) }()
	wait(t, entered)
	if r.Replace(f, 2, 1, cfg(), &testBind{}, &testProtector{}) != Busy {
		t.Fatal("concurrent control not rejected")
	}
	close(release)
	wait(t, done)
	if newest <= 0 {
		t.Fatal(newest)
	}
	if r.Replace(f, 2, 1, cfg(), &testBind{}, &testProtector{}) > 0 || r.fence(f).current.Load().id != newest || r.BeginProbe(f, newest, 1) != 0 {
		t.Fatal("stale overwrite")
	}
}
func TestListenPortBounds(t *testing.T) {
	c := cfg()
	c.ListenPort = 65536
	if _, ok := c.options(1, &testBind{}, func(int) bool { return true }); ok {
		t.Fatal("oversized listen port")
	}
	c.ListenPort = -1
	if _, ok := c.options(1, &testBind{}, func(int) bool { return true }); ok {
		t.Fatal("negative listen port")
	}
	c.ListenPort = 51820
	o, ok := c.options(1, &testBind{}, func(int) bool { return true })
	if !ok || !strings.Contains(o.IPCConfig, "listen_port=51820\n") {
		t.Fatal("listen port lost")
	}
}

func TestEarlyCoreReplacementErrorCannotDisposeLiveOldCallback(t *testing.T) {
	r, f, d := fixture(t)
	p := &testProtector{}
	h := r.Replace(f, 1, 1, cfg(), &testBind{}, p)
	if h <= 0 {
		t.Fatal(h)
	}
	old := r.instance(f, h).payload.Load()
	d.Close()
	select {
	case <-r.fence(f).core.Load().Errors():
	case <-time.After(5 * time.Second):
		t.Fatal("fatal TUN error")
	}
	if r.Replace(f, 2, 1, cfg(), &testBind{}, &testProtector{}) > 0 {
		t.Fatal("failed fence replaced")
	}
	if p.released.Load() != 0 {
		select {
		case <-old.core.Done():
		default:
			t.Fatal("callback disposed before old core retirement")
		}
	}
}

func TestDeniedReplaceCanRetireByFenceWithoutUnknownHandle(t *testing.T) {
	r, f, d := fixture(t)
	entered, release, done := make(chan struct{}), make(chan struct{}), make(chan struct{})
	p := &testProtector{fn: func(fd int) bool {
		if fd == 41 {
			close(entered)
			<-release
		}
		return true
	}}
	var result int64
	go func() { result = r.Replace(f, 1, 1, cfg(), &testBind{}, p); close(done) }()
	wait(t, entered)
	r.Invalidate(f)
	close(release)
	wait(t, done)
	if result >= 0 {
		t.Fatal("expected generic denial")
	}
	if r.RetireCurrent(f, 1, 1) != 0 || p.released.Load() != 1 || d.closes.Load() != 0 {
		t.Fatal("cannot retire failed replacement via fence")
	}
}

func TestStaleFailureCleanupCannotRetireNewerReplacement(t *testing.T) {
	r, f, _ := fixture(t)
	replace(t, r, f, 1, 1)
	newest := replace(t, r, f, 2, 1)
	if r.RetireCurrent(f, 1, 1) == 0 {
		t.Fatal("stale cleanup acknowledged")
	}
	if r.BeginProbe(f, newest, 1) != 0 {
		t.Fatal("stale cleanup sealed newer transport")
	}
}

// Reclamation tests exercise actual Transport/Fence objects; finalizers merely
// observe reachability after real retirement barriers and are never production hooks.
func watchTransport(r *Registry, f, h int64) <-chan struct{} {
	done := make(chan struct{})
	runtime.SetFinalizer(r.instance(f, h).payload.Load().core, func(*transport.Transport) { close(done) })
	return done
}
func waitCollected(t *testing.T, done <-chan struct{}) {
	t.Helper()
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		runtime.GC()
		select {
		case <-done:
			return
		default:
			runtime.Gosched()
		}
	}
	t.Fatal("retired/released native object remains rooted")
}
func TestRetiredGenerationReclaimedWithKnownLeaseReceipt(t *testing.T) {
	r, f, _ := fixture(t)
	h := replace(t, r, f, 1, 1)
	r.BeginProbe(f, h, 1)
	l := r.Register(f, h, 1, 17, "10.0.0.1:30000", "1.1.1.1:53")
	if l <= 0 {
		t.Fatal(l)
	}
	done := watchTransport(r, f, h)
	if r.Retire(f, h) != OK {
		t.Fatal("retire")
	}
	waitCollected(t, done)
	for i := 0; i < 2; i++ {
		if r.Remove(f, h, 1, l) != OK {
			t.Fatal("known sealed/removed receipt lost")
		}
	}
	if r.Remove(f, h, 2, l) == OK || r.Remove(f, h, 1, l+1000) == OK {
		t.Fatal("foreign removal receipt")
	}
	runtime.KeepAlive(r)
}
func TestRepeatedReplaceAndRemoveReclaimHeavyPayloads(t *testing.T) {
	r, f, _ := fixture(t)
	for g := int64(1); g <= 16; g++ {
		h := replace(t, r, f, g, 1)
		r.BeginProbe(f, h, 1)
		for i := 0; i < 8; i++ {
			l := r.Register(f, h, 1, 17, "10.0.0.1:30000", "1.1.1.1:53")
			if l <= 0 || r.Remove(f, h, 1, l) != OK || r.Remove(f, h, 1, l) != OK {
				t.Fatal("remove receipt")
			}
		}
		done := watchTransport(r, f, h)
		next := replace(t, r, f, g+1, 1)
		waitCollected(t, done)
		if r.Retire(f, next) != OK {
			t.Fatal("retire next")
		}
		g++
	}
	runtime.KeepAlive(r)
}
func TestReleasedLifetimeReclaimedAndMetadataPruned(t *testing.T) {
	r := new(Registry)
	var previous int64
	for i := 0; i < 12; i++ {
		d := &testTun{done: make(chan struct{})}
		f := r.create(d)
		if f <= previous {
			t.Fatal("reused handle")
		}
		previous = f
		h := replace(t, r, f, 1, 1)
		collected := make(chan struct{})
		runtime.SetFinalizer(r.fence(f).core.Load(), func(*transport.Fence) { close(collected) })
		if r.Release(f, true) != OK {
			t.Fatal("release")
		}
		waitCollected(t, collected)
		if r.Epoch(f) > 0 || r.Retire(f, h) == OK || len(r.entries.Load().entries) != 0 {
			t.Fatal("released owner metadata retained")
		}
	}
	runtime.KeepAlive(r)
}
func TestRegistryCapacityRejectedBeforeDestructiveReplace(t *testing.T) {
	r, f, _ := fixture(t)
	h := replace(t, r, f, 1, 1)
	// Populate only metadata to reach the documented practical limit; no native
	// device workload is needed to test capacity reservation's ordering.
	for len(r.entries.Load().entries) < 4096 {
		if r.reserve(f) <= 0 {
			t.Fatal("capacity filled too early")
		}
	}
	p := &testProtector{}
	if r.Replace(f, 2, 1, cfg(), &testBind{}, p) != Exhausted || p.released.Load() != 1 {
		t.Fatal("capacity not rejected")
	}
	if r.BeginProbe(f, h, 1) != OK {
		t.Fatal("capacity rejection destructively changed current")
	}
}

type failingCloseBind struct {
	testBind
	fail atomic.Bool
}

func (b *failingCloseBind) Close() error {
	e := b.testBind.Close()
	if b.fail.Load() {
		return errors.New("injected close failure")
	}
	return e
}
func TestFailedRetirementRetainsHeavyPayloadAndProtector(t *testing.T) {
	r, f, d := fixture(t)
	b := &failingCloseBind{}
	p := &testProtector{}
	h := r.Replace(f, 1, 1, cfg(), b, p)
	if h <= 0 {
		t.Fatal(h)
	}
	b.fail.Store(true)
	if r.Retire(f, h) != Failed || p.released.Load() != 0 || r.instance(f, h).payload.Load().core == nil {
		t.Fatal("failed barrier ownership reclaimed")
	}
	if r.Release(f, true) != Failed || p.released.Load() != 0 || d.closes.Load() != 0 {
		t.Fatal("failed barrier manufactured release")
	}
	// The test ends a fake read without asserting a successful bridge receipt.
	d.Close()
}
func TestConcurrentLookupRemovalAndReclamation(t *testing.T) {
	r, f, _ := fixture(t)
	h := replace(t, r, f, 1, 1)
	r.BeginProbe(f, h, 1)
	l := r.Register(f, h, 1, 17, "10.0.0.1:30000", "1.1.1.1:53")
	start, done := make(chan struct{}), make(chan struct{})
	go func() {
		<-start
		for i := 0; i < 300; i++ {
			r.Sequence(f, h, 1)
			r.Publish(f, h, 1, 1)
			r.Remove(f, h, 1, l)
			r.InvalidateTransport(f, h)
		}
		close(done)
	}()
	close(start)
	if r.Retire(f, h) != OK {
		t.Fatal("retire")
	}
	if r.Release(f, true) != OK {
		t.Fatal("release")
	}
	wait(t, done)
	if r.Remove(f, h, 1, l) == OK || r.Publish(f, h, 1, 1) == OK {
		t.Fatal("reclaimed handle accepted")
	}
}

type blockedCloseBind struct {
	testBind
	block            atomic.Bool
	entered, release chan struct{}
}

func (b *blockedCloseBind) Close() error {
	if b.block.CompareAndSwap(true, false) {
		close(b.entered)
		<-b.release
	}
	return b.testBind.Close()
}
func TestBlockedRetirementRetainsPayloadUntilActualBarrier(t *testing.T) {
	r, f, _ := fixture(t)
	b := &blockedCloseBind{entered: make(chan struct{}), release: make(chan struct{})}
	p := &testProtector{}
	h := r.Replace(f, 1, 1, cfg(), b, p)
	if h <= 0 {
		t.Fatal(h)
	}
	b.block.Store(true)
	done := make(chan struct{})
	var status int64
	go func() { status = r.Retire(f, h); close(done) }()
	wait(t, b.entered)
	if r.instance(f, h).payload.Load() == nil || p.released.Load() != 0 || r.Release(f, true) != Busy {
		t.Fatal("blocked ownership reclaimed")
	}
	close(b.release)
	wait(t, done)
	if status != OK || r.instance(f, h).payload.Load() != nil || p.released.Load() != 1 {
		t.Fatal("successful barrier did not reclaim payload")
	}
}

type failedReleaseTun struct{ testTun }

func (t *failedReleaseTun) Close() error {
	t.testTun.Close()
	return errors.New("injected lifetime close failure")
}
func TestFailedLifetimeReleaseRetainsOwnershipAcrossRetries(t *testing.T) {
	r := new(Registry)
	d := &failedReleaseTun{testTun: testTun{done: make(chan struct{})}}
	f := r.create(d)
	if f <= 0 {
		t.Fatal(f)
	}
	if r.Release(f, true) != Failed || r.fence(f) == nil || r.fence(f).core.Load() == nil {
		t.Fatal("failed lifetime close reclaimed owner")
	}
	if r.Release(f, true) != Failed || r.fence(f) == nil {
		t.Fatal("retry manufactured successful release")
	}
}

func TestReviewFailedConstructionCannotAcknowledgeFailedBarrier(t *testing.T) {
	r, f, d := fixture(t)
	b := &failingCloseBind{}
	p := &testProtector{fn: func(int) bool { b.fail.Store(true); return false }}
	if got := r.Replace(f, 1, 1, cfg(), b, p); got != Failed {
		t.Fatal("expected failed construction", got)
	}
	if got := r.RetireCurrent(f, 1, 1); got != Failed {
		t.Errorf("failed internal barrier acknowledged as %d; callback releases=%d", got, p.released.Load())
	}
	if got := r.Release(f, true); got != Failed {
		t.Errorf("failed internal barrier manufactured lifetime receipt %d; TUN closes=%d", got, d.closes.Load())
	}
	d.Close()
}
