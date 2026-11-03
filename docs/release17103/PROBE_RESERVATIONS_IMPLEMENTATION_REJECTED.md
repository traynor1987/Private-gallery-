# Task2b: pre-child probe reservation foundation

Completed in `/workspace/scratch/novaos`. Files are frozen for review. No existing app, Android/JNI/runtime, Gradle, workflow, signing or CI files were edited. No commit/push was performed.

## Files

- `app/src/main/java/uk/co/traynor/privategallery/core/vpn/continuity/ProbeReservations.java`
- `scripts/tests/vpn/ProbeReservationsChecks.java`
- `scripts/tests/vpn/check_probe_reservations.sh`

## Behavior and integration API

`ProbeReservations(List<InetAddress> localAddresses, InetSocketAddress numericDnsTarget, NetworkBinder binder, int tcpSlotsPerAddress, int receiveTimeoutMillis, int connectTimeoutMillis)` creates every socket immediately. TCP slot count is PER concrete local address (1..256), with at most 8 local addresses. One UDP reservation is made per local address matching the single fixed DNS target's family. No descriptors are created after construction, including after sealing. Reuse is disabled before the network callback, and the callback runs before concrete local bind. Wildcards, multicast, unresolved targets, mapped/scoped/link-local IPv6 ambiguities and duplicate local addresses are rejected. Ordinary canonical unscoped IPv6 is supported by the API, but host tests use IPv4.

`sealForChildAdmission()` permits borrowing; caller must construct/seal before any sensitive child traffic exists. There is deliberately no refill/reset API. A new child lifetime requires trusted retirement and a fresh fence/pool.

`tcpFactory(concreteLocalAddress, generationRegistrar)` returns a real `javax.net.SocketFactory`. Every returned socket is an actual already-bound `Socket` subclass, not a delegation facade. Claiming permanently burns a slot even if no connect occurs. Connect registers the exact family/protocol/local-address+port/remote-address+port tuple before `super.connect`, caps connect timeouts, and fails closed on invalid endpoints/family or exhaustion. All factory overloads preserve ownership: String overloads accept strict dotted IPv4 only (no resolver); InetAddress overloads support numeric IPv4/IPv6; local-address overloads require the reserved concrete address and port zero. TLS may layer with `autoClose=true`; stream close routes through owner close. Reuse cannot subsequently be enabled.

For HTTPS readiness choose a concrete IPv4 factory and IPv4 resolved results, per the mandatory IPv4 profile. DNS TCP fallback must claim its OWN new slot from the correct family factory. Never reuse this factory for general browser traffic and never fall back to default sockets/resolution.

`Registrar.register(Tuple)` supplies a generation-scoped owner-private permit. The future JNI adapter must capture immutable fence identity, generation and a unique lease identity in each `Permit`, register exactly the supplied tuple, and remove only that lease. `Tuple.family()` returns 4 or 6; `protocol()` is TCP/UDP and address getters expose numeric InetAddress values for conversion to JNI address bytes. `Permit.revoke()` returns true only on positively acknowledged exact removal. False/exception/null permit/registration exception are unknown outcomes; the FD is kept parked and unavailable. Registration exceptions are conservatively considered possibly admitted.

Native exact lease removal is NOT a receipt for a full transport drain or completion of previously admitted callbacks. Any subsequent publication/transport-level drain remains the integration's responsibility.

TCP `close()` revokes before actual FD release. Failed connect/cancellation/TLS/stream disposal burn the slot. After unknown revocation, future Java I/O, urgent data and output-shutdown transmission are rejected, while the actual FD stays open. Consequently `Socket.isClosed()` remains false for parked sockets; clients must not use it as a native-removal receipt. No tuple, URL, payload or token is logged; socket/tuple `toString()` are redacted.

`udpQuery(concreteLocalAddress, generationRegistrar)` returns an exclusive identity-scoped `Query` for the retained DNS UDP descriptor. `send(byte[])` registers exact admission before sending to the fixed numeric DNS target; `receive(maxBytes)` uses the configured bounded socket timeout and checks response address/port. A query permits at most one in-flight operation. `cancel()` marks cancellation, removes admission, then waits at most the receive interval plus 250 ms for operations to leave. Reuse requires positive removal and quiescence; timeout, interruption or unknown removal park the descriptor. `close()` is logical cancellation only, never UDP FD destruction. Old query closes are idempotent and cannot revoke or interrupt a newer borrower. `publishIfActive(response, boundedNonblockingPublisher)` suppresses publication after cancellation under the ownership lock. Registrar and publisher callbacks must be bounded/nonblocking and must not reenter pool/query APIs or invert caller session locks.

The future optional OwnedVpnDns ProbeContext must close/cancel and finish queries before publishing. It must also validate DNS transaction IDs/questions/payload bounds and use new transaction identity: the persistent socket can retain delayed responses from earlier parent queries. This class does not parse DNS or claim such responses belong to the current query.

`disposeAfterRetirement(BooleanSupplier trustedReleasePredicate)` is the only final destruction API. It marks the pool terminal, cancels active queries and closes all descriptors including parked/unused ones only after an external trusted proof that sensitive producers AND native fence/registry have retired. A false/throwing predicate leaves the pool unchanged. Java cannot establish this Android/process-lifetime proof. There is no unguarded pool `close()`.

## RED/GREEN evidence

Initial tests and focused runner were written before the production source. Initial RED was the expected missing implementation file compiler failure. A later behavior RED demonstrated that an unknown-revoke parked socket could still use inherited urgent-send/output-shutdown methods; their guards were then implemented. A host test also exposed that overriding Socket.isClosed to mean logical closure prevented JDK super.close from releasing the actual FD; that override was removed and actual FD semantics are explicitly documented.

Final command:

```sh
cd /workspace/scratch/novaos
scripts/tests/vpn/check_probe_reservations.sh
```

Runner compiles the helper and tests with `java com.sun.tools.javac.Main --release 17 -Xlint:all -Werror` into a disposable temporary directory, then runs with assertions enabled. Runtime: OpenJDK 17.0.20. Final output:

```text
ProbeReservationsChecks: 113 checks passed
```

`git diff --check -- app/src/main/java/uk/co/traynor/privategallery/core/vpn/continuity scripts/tests/vpn` also passed.

Covered using real loopback sockets and injected no-op network binding:

- Every descriptor exists before seal; concrete reserved TCP/UDP ports reject competing binds.
- Network callback happens before local bind; address reuse is disabled.
- Seal-before-use, never-used TCP abandonment, failed connect, unresolved destination, burned slots, exhaustion and no refill/allocation.
- Exact TCP registrar callback observes `!socket.isConnected()` before SYN/connect; UDP registrar observes no received datagram before send.
- Actual bidirectional TCP streams, all factory overloads, JSSE socket layering and JSSE/stream-driven close.
- Removal callback sees descriptor still bound; successful failed-connect cleanup actually frees its never-connected source port.
- Register exception, false revoke and thrown revoke conservatively retain descriptors; no false acknowledgment or Java I/O after logical close.
- Single UDP owner, real blocking receive verified through its thread stack, concurrent-operation rejection, cancellation/removal before the receive exits, wait-for-quiescence before the next borrower, bounded cancellation and no old receiver stealing a later response.
- Old query close cannot revoke a new generation's independent permit; cancelled publication is suppressed, live publication succeeds.
- UDP physical reservation persists across logical close/reborrow and unknown removal.
- Guarded final disposal, false/throwing proof retains bindings, terminal pool cannot be reused, and construction failure closes unpublished sockets.

## Limits / next integration work

This is only the Java FD-ownership foundation. No Android enforcement/C1 repair or full transport drain is claimed. Android API37 must still verify concrete/wildcard/IPv4-mapped/v6-only/auto-bound exclusivity on the real VPN network, pre-child reservation order, Binder/Network binding behavior, JNI exact-lease semantics, lifecycle/process retirement and existing full-capture no-fallback assertions. JSSE layering was verified on Java17; no live TLS endpoint or credential was used. No Android app wiring, JNI adapter, DNS parsing, readiness client, full app/Gradle build or Android test was run by this task.
