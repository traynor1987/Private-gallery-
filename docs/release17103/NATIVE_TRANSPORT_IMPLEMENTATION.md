# Task2a native transport core report

Implemented only `native/continuousvpn/transport/{go.mod,go.sum,fence.go,bind.go,transport.go,fence_test.go,bind_test.go,transport_test.go}`. No Android, JNI, app, Gradle, upstream, gate, workflow or repository documentation changes; no CI, commit, push or subagents. This is a host-tested native integration layer, **not the complete C1 repair and not API37 containment/security approval**.

## Exact dependency/interface contract

- Go module `uk.co.traynor.privategallery/continuousvpn/transport`, Go1.20.
- Sibling gate import with local `replace .../gate => ../gate`.
- WireGuard pin `v0.0.0-20230223181233-21636207a675`, x/sys `v0.6.0`; indirect dependency versions match existing vendored Android go.mod.
- go.sum is byte-identical to `upstream/tunnel/tools/libwg-go/go.sum`; `go mod verify` passes.
- Compile-time conformance to the actual pin's `tun.Device` single-packet Read/Write/Flush API and `conn.Bind` single-packet Send API; actual pinned `device.NewDevice`, IpcSet, Up, LookupPeer, ExpireCurrentKeypairs, SendHandshakeInitiation, Close and Wait run in host tests.
- Full downloaded pinned module audit: only main.go:199 uses TUN File(), in the unimported CLI restart path. Device event consumption is device/tun.go:19. virtual File() therefore returns nil and never exposes the real owner file.
- Actual outer calls are device/peer.go:126 and direct cookie path device/send.go:186; both use device.net.bind.Send and therefore the supplied protected wrapper. The wrapper makes no packet-type exceptions.

## API and lifetime contract

`NewFence(real tun.Device, queueCapacity int) (*Fence,error)` transfers exclusive ownership of real on success. Caller must not independently read/close it. Capacity is bounded 1..1024. MTU/name are immutable cached configuration. One lifetime reader starts immediately and continuously consumes/drop traffic with no attached target or while CLOSED. Each virtual device owns its bounded copied packets, private event channel, ready signal and cancellation channel. Queue enqueue/dequeue/disposal share synchronization; queues themselves are never closed, avoiding send-on-closed races. Full queues drop/zero. Dequeue rechecks this generation's gate and lease policy; rejected/consumed queue storage is zeroed. Dead generations never use a later gate for reads/writes. OPEN keeps normal valid IP-envelope semantics, including non-PROBE protocols/fragments.

`Fence.Replace(Options)` serializes owner lifecycle outside session monitors. Options carry monotonic generation, exclusively owned bind, synchronous trusted protector, UAPI config, selected peer public key and optional logger. It invalidates/detaches the old target, closes outer sockets to cancel sends/receives, closes and waits for the pinned old WG device (including its virtual TUN reader), drains admitted send/plaintext-write callbacks, then creates a fresh gate, virtual queue, wrapper and WG device. It configures and starts that device with admission CLOSED. All failures retire the partially configured device. No old WG device/queue is reopened.

`Transport.BeginProbe()` is explicit owner activation after protection. It changes CLOSED->PROBE, invokes the selected configured peer's exported `ExpireCurrentKeypairs` to reset any CLOSED keepalive's handshake throttle, then calls `SendHandshakeInitiation(false)`. It requires no ordinary child packet. A successful return is not authentication, a handshake receipt, or readiness proof.

`Transport.AddProbe(epoch, tuple)` / `RemoveProbe(epoch, leaseID)` delegate exact gate capability checks. `RevocationSequence()` captures publication token; `Publish(expectedSequence)` performs only the native CAS. Java must validate owner/authentication/attempt/presentation/original grace and native receipt identity in the same final transaction ordered against revocation. No authentication is implemented here.

`Transport.Invalidate()` delegates the gate's nonblocking, no-owner-lock operation. It is the ONLY callback operation suitable for a HiddenSession monitor. It permanently seals admission but is not a completed-denial acknowledgement.

`Transport.Close()` invalidates, detaches and cancels outer I/O before waiting for the owner lock, so it can cancel outer I/O before serializing full retirement. BeginProbe now holds a per-transport reference while its blocking handshake runs outside that lock; retirement joins that reference before closing the WG device. It then performs pinned WG Close/Wait and gate Drain. It never releases real. `Done()` closes only after completed retirement; `Generation()` exposes immutable identity. Close and replacement can block on an outstanding lifetime TUN Write. They do not falsely acknowledge or activate another generation while it remains outstanding.

`Fence.Errors()` reports the first fatal real read/write failure. Fatal failure permanently seals admission and detaches the target while retaining the real TUN. No automatic reestablishment/recovery is attempted. A fatal write leaves the lifetime reader consuming/drop traffic.

`Fence.Release()` is the sole real-TUN close operation. It refuses an attached target and waits for retired generation barriers. The external owner must prove child retirement before calling it. This pure Go module cannot prove the Java/child descriptor placement or route lifetime. An external deadline should wait on a retirement worker/Done, retain the fence and withhold acknowledgement if it expires.

## Socket protection contract and deliberate fail-closed choices

The supplied bind must implement the pin's `conn.PeekLookAtSocketFd`; the owner must not call that raw bind separately. Every actual initial socket Open obtains IPv4/IPv6 FDs and synchronously protects every present descriptor before returning receive functions or marking outer admission protected. The lifecycle lock spans raw Open, both FD lookups and protection, excluding Close/FD reuse. No lifecycle lock is held across Send, allowing socket close to cancel blocked I/O. Protection failure, lookup failure, empty receive functions, protector panic, and gate invalidation during protection close sockets and permanently seal the generation.

A second Open in the same wrapper invalidates/closes and returns `ErrReopen` WITHOUT opening any new socket. Thus there is no same-generation reopen shortcut needing a second protection receipt; the owner must create a fresh WG generation and all of that generation's actual newly opened sockets are protected again.

Absent-family handling requires positive `EAFNOSUPPORT` evidence with a negative descriptor. The exact Android StdNetBind FD peek returns EINVAL for a nil UDPConn, which is ambiguous; the core deliberately fails closed in that situation rather than treating every FD lookup error as an unsupported family. Android integration may provide an audited adapter proving unsupported-family absence or retain this stricter behavior. The current host fake supplies controlled descriptor results; host tests do not claim a Linux default bind has Android FD peek methods or that Android protection has been exercised.

## RED -> GREEN and final host evidence

Tests for fence behavior were written before fence.go. Initial Go1.20.3 `go test ./...` failed at compile time because `Fence`, `NewFence` and `virtualTun` did not exist. Retained `/tmp/nova-native-transport-red.log`. Bind/transport tests were written before bind.go/transport.go; their absent-symbol RED is `/tmp/nova-native-transport-red-bind.log`. These are explicit absent-implementation build-failure REDs, not claimed behavioral failures against stubs. Additional edge-case coverage was added after initial GREEN.

Toolchain: `/workspace/scratch/nova-go-toolchain/go/bin/go`, verified `go version go1.20.3 linux/amd64`.

Final results:

- Transport `go test -race -timeout=30s ./...`: PASS, all 19 tests and subcases.
- Additional repeated concurrency run `go test -race -count=10 -timeout=45s ./...`: PASS (before only the final defensive post-lock Close invalidation check; final full race suite was rerun after that change).
- Transport `go vet ./...`: PASS, no diagnostics.
- Transport `go test -cover ./...`: PASS, 87.6% statements.
- `go mod verify`: all modules verified.
- Sibling gate `go test -race -timeout=30s ./...` and `go vet ./...`: PASS, no gate edits.
- go.sum exact comparison against vendored pin: PASS.

Tests exercise real behavior using channel-controlled fake TUN/bind/protector and the actual pinned WG device: continuous CLOSED/no-target drain; max one real reader; copied buffer ownership; bounded overflow; queued buffer zero/disposal; revoked queued lease rejection; old-generation rejection; no plaintext Write after drain acknowledgement; normal OPEN protocols and offsets; protection before phase change/Send, protection failures and ambiguous absence; all four encrypted message types through the same Send barrier; protection FD lifetime while an observed Close waits; protector panic cleanup; blocked Send cancellation; configuration failure worker cleanup; monotonic generation rejection; actual startup CLOSED; explicit handshake without child traffic; CLOSED keepalive throttle reset; replacement waiting for old virtual/device shutdown and outstanding plaintext Write; fatal real read/write fail-closed retention; and Close cancelling an explicit BeginProbe handshake before device retirement.

Negative concurrency assertions are paired with observed milestones (callback entry, virtual cancellation, actual pinned Device.Wait, or Close's invalidation during blocked protection). Standalone gate foundation separately provides instrumented deterministic Drain wait tests. No arbitrary sleeps are used; timeouts only bound deadlocked tests.

## Remaining integration obligations

1. JNI synchronized owner-only opaque handle registry, immutable fence identity/generation receipts, exact revocation/publication transaction, Java error/deadline handling, and daemon native build packaging remain unimplemented.
2. The lifetime real-TUN writer is used directly. A blocked real Write cannot be force-cancelled by virtual Close without releasing the invariant-bearing real resource; current retirement safely waits and retains the fence. If Android requires bounded cancellation, add an audited generation-owned write duplicate/cancellation mechanism while retaining the lifetime owner reference, then test its cancellation semantics. Do not turn a timeout into successful denial acknowledgement.
3. Reserve every future probe source socket binding before sensitive child admission. Retain UDP reservations across generations. Pre-bind a bounded TCP slot pool, never reuse a spent slot, burn slots after one attempt, enforce unregister-before-FD-close and fail closed on exhaustion. Pure Go tuples do NOT prove that rule or solve delayed old plaintext with post-hoc registration.
4. Child-held non-forwarding lifetime keeper must be transferred before child traffic; preserve it through Activity cleanup and parent death; prove child death before final release. Real Android protection/service/descriptor lifecycle is not implemented here.
5. Fresh protected handshake receipt and real DNS/HTTPS readiness, owner/auth/grace checks, bind-before-commit denial, parent/service death, queue pressure, ordinary UDP compatibility, and unchanged full PCAP assertions must be exercised on API37 after coherent integration. OS-forced VPN deactivation remains a separate platform limitation, not a host-test claim.


## Important review correction: replacement versus blocked probe

The independent review correctly found a deadlock in the initial implementation: BeginProbe held fence.owner during the synchronous pinned handshake Send, while Replace required owner before it could close the socket. The production regression `TestReplaceCancelsBlockedProbeBeforeWaitingForDevice` was added before changing production code and failed with the exact 3-second timeout. `/tmp/nova-native-transport-replace-red.log` records Replace waiting at transport.go:55 and BeginProbe inside fakeBind.Send waiting for cancellation. The test never releases the send blocker itself.

Correction changes only transport.go and transport_test.go within the existing scope. BeginProbe prepares the peer and registers a per-transport WaitGroup reference while holding owner, then releases owner before calling exported SendHandshakeInitiation. Its defer drops that reference only after the entire call, including timer updates, finishes. Retirement still holds owner, invalidates/detaches/closes outer sockets first, then waits for that reference before Device.Close/Wait and gate Drain. Add and Wait are serialized by owner; no reference may appear after retirement starts. Stalled calls before entering Send remain safe because their gate is already sealed, and their device remains alive until the call exits.

The fix deliberately avoids an out-of-lock snapshot of current or a new replacement-intent/high-water protocol. Current transport selection, monotonic generation checks, old retirement, and new device publication all remain under the same existing owner lock. Because the blocking handshake no longer holds owner, Replace can reach cancellation, while an invalid/stale request still rejects before touching any current generation. This retains the Registry's existing monotonic generation guarantees.

Four added tests cover: cancellation of the observed blocked actual pinned handshake by Replace; stale/equal generations against a newer blocked probe without invalidating/closing its sockets; concurrent Replace(2), Replace(3), and old.Close with generation 3 remaining live; and an older replacement invoked while generation 3's initial descriptor protection is held, with no stale socket open or revocation of the new publication. The existing Close-handshake test was renamed to describe its current behavior accurately.

Final follow-up verification with pinned Go1.20.3:

- Full `go test -race -count=20 -timeout=30s ./...`: PASS, all 23 tests and subcases.
- Full `go vet ./...`: PASS, no diagnostics.
- Full `go test -cover ./...`: PASS, 87.2% statement coverage.

This supersedes the earlier lifecycle-lock description and 19-test result. All earlier Android integration and retained-real-write limitations remain unchanged. No commit, push, CI, Android/JNI changes, or files outside transport source/tests and this temporary report were changed for the correction.

The reviewer's original independent overlay counterexample also now passes unchanged: `go test -vet=off -overlay /tmp/nova-native-review-overlay.json -run '^TestReviewReplaceCancelsBlockedProbe$' -timeout=3s -count=1 ./...` (0.015s). As in the review, vet is disabled only for Go1.20's added-overlay-file limitation; the separate full ordinary vet run above passed.
