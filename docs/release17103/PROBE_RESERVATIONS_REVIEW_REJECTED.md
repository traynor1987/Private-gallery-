# Task2b independent review: changes required

Scope: the three new files in `/tmp/nova-probe-reservations-review.diff`, against BaseHEAD `6241b98`; requirements in `docs/release17103/PROBE_RESERVATIONS_TASK.md`; implementer report in `/tmp/nova-probe-reservations-report.md`. Review of this foundation only, not Android integration, release readiness, or C1 remediation.

**Spec verdict: FAIL. Quality/security verdict: CHANGES REQUIRED.** The class does preallocate real bound sockets and provides a reasonably clear ownership API, but inherited Java socket behavior defeats its central revoke-before-descriptor-release guarantee. Additional inherited proxy and cancellation behavior violates its intended operation boundaries. These are demonstrated on real loopback sockets under OpenJDK 17.0.20, not merely inferred from mock assertions.

## Critical: failed TCP connect releases the source binding before revocation

Evidence: `app/src/main/java/uk/co/traynor/privategallery/core/vpn/continuity/ProbeReservations.java:277` calls inherited `Socket.connect`; revocation only happens after it returns/throws, at lines 278–279 and 319–327. Java 17 `sun.nio.ch.NioSocketImpl.connect` catches its IOException and invokes its own `close()` before returning that failure. This closes the platform descriptor without dispatching through `ReservedTcp.close()`.

Reproduction: `/tmp/FailedConnectRetention.java`, compiled together with the production source into `/tmp/nova-review-classes`. It connects to a deliberately closed loopback port. Inside the permit's revoke callback, a second `ServerSocket` with reuse disabled successfully binds the reserved source port; the callback then returns false. A competing bind also succeeds afterward. Exact output:

```text
BUG: source binding free BEFORE revoke acknowledgement
connect: java.net.ConnectException
BUG: binding free after unknown revoke, isClosed=false
```

This violates both mandatory properties: exact admission must be removed before the actual FD is released, and an unknown revoke must leave the source binding physically parked. There is an observable interval in which the tuple may still be permitted but no longer reserved; false revocation leaves that situation indefinitely. It also disproves the report's assumption that `Socket.isClosed()` accurately reports whether this descriptor remains open: the public Socket state remains false when its underlying implementation has closed itself.

Existing tests at `scripts/tests/vpn/ProbeReservationsChecks.java:129` only check removal after a failed connect and that the port is free afterward; their connected-socket revoke callback check at line 106 does not cover the failed-connect path. Add the failure-path callback collision and false/throwing-revoke cases. Do not approve this foundation until a mechanism that actually preserves exclusive binding across platform connect failures exists. Moving the catch or adding another logical flag cannot undo the platform's earlier release; the limitation may require revisiting the pure-Java ownership boundary rather than another superficial Socket override.

## Important: default Socket construction permits an inherited proxy destination

Evidence: `ProbeReservations.java:245` invokes `super()`; Java 17 default `Socket` uses `SocksSocketImpl` and consults the default `ProxySelector`. Registration at lines 272–273 records the requested final destination, while inherited connect at line 277 may connect to a SOCKS proxy instead, potentially resolving its hostname through platform DNS.

Reproduction: the `proxy()` case in `/tmp/InheritedSocketPaths.java` installs a standard `ProxySelector` returning a numeric loopback SOCKS endpoint and requests a different loopback destination. The unregistered proxy endpoint accepts the actual connection and reads SOCKS greeting byte 5. Exact output:

```text
BUG: unregistered proxy endpoint received SOCKS greeting=5
```

Only the final target tuple was supplied to the registrar. The helper therefore cannot guarantee that every actual connect targets its admitted numeric endpoint, even with the injected no-op binder used by the intended host scope. Future exact native enforcement might deny this wrong tuple, but that would not repair this API's endpoint/no-default-resolver contract. Use an explicitly direct socket construction path (for example `super(Proxy.NO_PROXY)` where supported), and add a hostile-default-ProxySelector test. This fix is independent of the Critical failure-release problem.

## Important: logical cancellation can be followed by a new TCP connection

Evidence: registration and initial `requireOpen()` occur under `lifecycle` at `ProbeReservations.java:264`, but the lock is released at line 276 before the real connect starts at line 277. `close()` can then mark logical closure and get an unknown removal result without physically closing the socket (lines 321–327). Inherited connect only sees the still-open platform state and can start/finish after close has returned; there is no post-connect logical-state check either.

Reproduction: the `race()` case in `/tmp/InheritedSocketPaths.java` uses the real inherited ProxySelector callback only as a scheduling barrier, returning `Proxy.NO_PROXY`. Once connect has left the ownership lock, another thread executes `close()` with a false revocation result and returns. Resuming the pending connect creates a new real loopback connection and returns successfully:

```text
BUG: new connection established AFTER logical close and unknown revoke
connect returned successfully=true
```

The callback is a deterministic reproduction of the scheduling window, not the underlying cause. Removing proxy support does not itself synchronize the gap. On unknown revocation this can initiate traffic after cancellation while the old native permit may still exist. Establish explicit connect-operation ownership and a cancellation protocol that prevents this post-cancel initiation/success while preserving the unknown-revoke binding. Add a deterministic cancellation/connection interleaving test. Do not substitute an unbounded lifecycle lock around blocking connect without addressing bounded cancellation.

## Additional concrete lifecycle defect: inherited input skip bypasses the guard

Evidence: `ProbeReservations.java:295` wraps input in `FilterInputStream` but guards only `read()` and `read(byte[],int,int)`. `FilterInputStream.skip()` delegates directly to the wrapped stream. A retained input stream can consume incoming bytes after `close()` has logically parked the socket.

Reproduction: `skip()` in `/tmp/InheritedSocketPaths.java` obtains the input stream, parks the socket using false revoke, writes one byte from the real peer, then calls `input.skip(1)`:

```text
BUG: parked stream skip consumed bytes=1
```

This contradicts the explicit class/report guarantee of rejected Java I/O after logical close (`ProbeReservations.java:49`). Audit the full inherited stream surface and guard skip/skipNBytes and other relevant delegates consistently. This observation alone is narrower than the central binding failure; it does not demonstrate new descriptor allocation or an Android VPN bypass.

## Other scope findings and verification

- The reviewed diff contains only the requested three new files. It contains no version/code/API, Android, JNI, Gradle, runtime, workflow, or default-DNS integration edits. Existing unrelated worktree changes were not reviewed or changed.
- TCP slots are preallocated, claim-once, and never refilled. Explicit endpoint validation rejects unresolved requested destinations. The proxy inheritance caveat above remains.
- UDP query identity, retained physical binding, unknown-registration parking, and admission-removal-before-quiescence design are coherent in the reviewed paths. A second cancellation while the first is waiting returns false rather than allowing premature reuse; stale query closes cannot release a newer borrower. The reusable flag is published only when operation count is zero and the reservation is not parked. No separate blocking UDP defect was found in this review.
- Retirement is externally guarded and terminal. The class correctly documents that the trusted proof and native exact-lease semantics are integration responsibilities. It makes no actual Android enforcement claim.
- JSSE layering test exercises construction and auto-close only, without a handshake. That is appropriate limited evidence for the report's stated limitation, but it does not establish every TLS cancellation/failure path; inherited failures above remain decisive regardless of that passing test.
- Inspected actual Java17 bytecode with `java com.sun.tools.javap.Main` for Socket, NioSocketImpl and SocksSocketImpl. Saved inspection output at `/tmp/nova-socket-javap.txt`, `/tmp/nova-nio-javap.txt`, and `/tmp/nova-socks-javap.txt`.
- Compiled and executed only the two focused counterexample programs. Did not rerun the reported 113-check suite because these tests cover concrete missing cases. No production/test implementation edits, commits, pushes, CI, or subagents were used.

Foundation approval is withheld until the Critical invariant failure and Important operation-boundary defects are resolved and demonstrated on actual Java17 sockets. This review neither approves Android/C1 enforcement nor changes the release constraints.
