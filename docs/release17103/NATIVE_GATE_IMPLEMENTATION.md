# Task1 standalone native gate foundation

Scope: only `native/continuousvpn/gate/{gate.go,gate_test.go,packet.go,packet_test.go,go.mod}`. No Android/Gradle/scripts/workflow changes, vendoring, CI, signing, commit, push or subagents. Existing unrelated worktree changes were preserved. This is not the complete Critical C1 fix or Android runtime proof.

## API contract

Go module: `uk.co.traynor.privategallery/continuousvpn/gate`, `go 1.20`, no dependencies/network syscalls. Own source has MIT SPDX/copyright headers. Tested with `/workspace/scratch/nova-go-toolchain/go/bin/go` (Go1.20.3).

| API | Contract |
| --- | --- |
| `(*Registry).NewGate(generation)` | Zero-value-usable owner-private generation namespace; rejects zero/non-increasing generations. Permanently invalidates and drains previous Gate before returning a fresh CLOSED handle. Registry must outlive every transport handle. Blocks; call outside session monitors. |
| `MarkOuterProtected(generation)` | Owner records checked protection of BOTH outer UDP sockets before activating peer; only accepted in initial CLOSED. Pure gate cannot itself verify real FDs. |
| `SetState(generation, Probe)` | One-way initial CLOSED→PROBE, only after protection. Invalid states rejected. `SetState(..., Closed)` is a synchronous permanent close. OPEN must use Publish. |
| `RevocationSequence(generation)` | Captures current live publication token; initial sequence1. Generation checked. Sealed handles return an error. |
| `Publish(generation, expectedSequence)` | CAS from same live PROBE snapshot to OPEN, checks exact generation/token; stale invalidation cannot reopen handle. Not a substitute for Java authentication/epoch/owner/presentation/grace checks. |
| `Invalidate(generation)` | Lock-free application-level atomic CAS, no application mutex or callback waiting. Permanently seals Gate and advances revocation sequence. Repeated invalidations are idempotent. Safe under a session monitor; existing callbacks can still be running. |
| `Drain(generation)` | Requires invalidation; waits both admitted encrypted sends and plaintext inbound writes; clears lease table. Outside Java/session monitors. No callback can remain or begin after acknowledgement. |
| `Close` / `Retire` | Synchronous Invalidate+Drain. No closed/retired Gate can re-enter PROBE/OPEN. New WG queues/device must use a new generation/Gate. |
| `AddProbe(generation, epoch, Tuple)` | PROBE only, maximum128 leases, nonzero epoch; exact protocol+source address/port+destination address/port. Concrete same-family unzoned unicast addresses and nonzero ports. Duplicate/reverse duplicate rejected. Returns opaque Gate-bound ID. |
| `RemoveProbe(generation, epoch, LeaseID)` | Explicit exact owner/generation/epoch/ID removal. Future admission in both directions is revoked before return. Must precede Java FD release. A removed ID cannot revoke its replacement or a different Gate. |
| `AdmitOutbound(generation, packet)` | Post-TUN-Read plaintext decision. No API wraps or holds a mutex over TUN Read. PROBE requires exact registered outgoing tuple. OPEN permits bounded valid IP envelopes without narrowing normal protocols/fragments/extensions. |
| `WriteInbound(generation, packet, callback)` | Separate actual TUN Write barrier; PROBE requires exact reverse tuple. Counts callback before admission check so Invalidate+Drain cannot miss pre-revocation work. |
| `OuterSend(generation, callback)` | Separate actual encrypted conn.Bind.Send barrier; CLOSED/sealed denied, protected PROBE and OPEN admitted. Every handshake/keepalive/control/data send must pass here. |

I/O callbacks run without gate mutexes; their errors propagate and panic unwinding releases barrier accounting. Nil callbacks are rejected. `(false,nil)` means consumed/drop, not a fatal device error; wrappers must translate to pinned API return semantics.

`RemoveProbe` cannot recall plaintext already queued for encryption, encrypted records, or an executing Write. Invalidate+Drain and transport retirement are required for the stronger boundary acknowledgement. Kernel-completed encrypted UDP writes cannot be recalled. The actual held socket with reuse disabled, not port secrecy, prevents an unprivileged competing socket taking its tuple. Java must implement reservation/register/send/unregister/FD-close order.

## Packet bounds

All parser inputs bounded to65575 bytes. PROBE checks exact IP length, IPv4 base header only, rejects reserved/fragment-offset/MF bits (DF permitted), rejects IPv6 extension/fragment headers and every unsupported transport. UDP length must match complete payload and be at least8; TCP header offset must be at least20 and within payload. Zero/invalid tuples denied. OPEN checks version/header/packet envelope length, preserves ordinary protocols, IPv4 options/fragments and IPv6 extension headers; checksum/transport semantics remain stack responsibility. Non-jumbo IP only. Buffers must remain immutable during a decision/callback, matching pinned single-packet ownership.

## RED → GREEN evidence

Tests were created before `gate.go`/`packet.go`. Initial command under Go1.20.3:

`go test ./...`

failed because production symbols did not exist, including `undefined: Registry`, `undefined: Gate`, `undefined: Probe`, `undefined: Tuple`, followed by `[build failed]`. Full retained output: `/tmp/nova-native-gate-red.log`. This is the explicitly requested absent-implementation/build-failure RED, rather than a behavioral failure against a stub implementation.

After implementation:

- `go test ./...`: PASS.
- `go test -race -timeout=30s ./...`: PASS after final tests.
- `go vet ./...`: PASS, no diagnostics.
- `go test -cover ./...`: PASS, 93.3% statement coverage.
- `go test -run='^$' -fuzz=FuzzProbeParserNeverPanics -fuzztime=3s -parallel=2`: PASS; 98,413 executions,16 new interesting cases,21 total corpus cases.

Tests cover default closure, protection prerequisite, invalid transition/generation, exact four-way IPv4/IPv6 TCP/UDP leases and reverse direction, source/destination address and port mismatch, duplicate tuples, epoch mismatch, unregister-before-socket-release admission, replay/foreign IDs, lease bound/validation, monotonic Registry generations, permanent sealing, stale controls, callback errors/panics, both blocked send/write acknowledgement, nonblocking Invalidate while Send is blocked, old-sequence Publish rejection, Invalidate while control mutex held, and concurrent revoke/admit/replacement. Concurrency uses channels and callbacks, no arbitrary sleeps.

## Required next integration and review

1. Independently review exact source and barrier/lease semantics. No security approval claimed.
2. Integrate with pinned single-packet `tun.Device.Read([]byte,int)`, `Write([]byte,int)`, `Flush()` and `conn.Bind.Send([]byte, Endpoint)` from wireguard-go21636207a675; no current-main batch APIs. The exact supplied snapshots `/tmp/nova-wg-tun-tun.go`, `/tmp/nova-wg-conn-conn.go`, send/receive implementations were inspected.
3. Owner session revocation callback calls only Invalidate while holding its monitor; Drain/Close/device teardown occurs outside monitor. Publication validates existing Java predicates and captured revocation sequence in the same transaction.
4. Retain original Android TUN/service/Network for full child lifetime; stop old reader and retire old WG queues before new duplicate/device enters PROBE. Never attach new Gate to old WG queues. Drain/drop retained packets with bounded fail-closed transition policy.
5. JNI owner-only control/handle registry, protected-before-first-send sockets, exact socket/port lifetime, DNS TCP fallback separate lease, real HTTPS/DNS readiness and fresh peer handshake must still be implemented. No direct native wrappers, device recreation, or Java FD integration is included here.
6. Build vendored pinned native sources/separately named library reproducibly for required ABIs, perform Android API37 lifecycle/revocation/parent-death and unchanged full raw packet evidence. OS forced VPN deactivation and owner-process lifetime remain separate constraints.

## Scoped review follow-up

Addressed all three nonblocking quality observations without API changes. `gate.go` and `go.mod` are byte-identical to the reviewed foundation. `packet.go` now additionally rejects IPv4 limited broadcast on both tuple endpoints. A regression first failed because `255.255.255.255` was accepted, then passed.

The IPv4-options fixture now inserts four NOP bytes before the original transport, updates IHL and total length, and is exercised as PROBE-denied and OPEN-admitted for TCP/UDP.

Close/Drain tests now install a test-only instrumented sync.Locker under sync.Cond to observe actual Cond.Wait entry while callbacks remain held. After Send exits, the Close test explicitly wakes the waiter and requires a second Wait while Write stays blocked. No production hooks or sleeps. A temporary Go overlay deliberately removing the Drain wait made BOTH strengthened tests fail immediately with `barrier returned before reaching Cond.Wait with callback held`, demonstrating the negative assertions catch premature acknowledgement. The real source remained unchanged by that mutation.

Pinned Go1.20.3 post-change race suite and vet pass. A final parser fuzz run also passes. No backend/upstream/repository-documentation changes.
