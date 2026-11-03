# Standalone native gate independent review

Reviewed 2026-11-03. Scope: the five new files in `native/continuousvpn/gate`, Task1 in `docs/release17103/CONTINUOUS_VPN_PLAN.md`, repository design review, authoritative `/tmp/nova-vpn-design-review.md`, and implementer `/tmp/nova-native-gate-report.md`. No repository edits, CI, Android execution, signing, or additional test-suite runs. The implementer's race/vet/fuzz results are supplied evidence, not independently rerun results.

## Verdict

**Accept the standalone foundation for further integration, with nonblocking test-quality follow-ups. No concrete blocking concurrency defect, packet bounds defect, stale reopening path, or tuple matching bypass found in this scope.** This does not complete Task1 integration or fix/approve Critical C1. Actual parent socket ownership, descriptor lifetime, all-send wrapping, WG queue retirement, Android transaction integration and runtime packet proof remain mandatory.

## Source reasoning

- New gates start CLOSED; zero-value gates deny. Registry serializes a strictly increasing nonzero generation namespace and permanently closes/drains its old gate before returning the new handle. Sealed handles cannot transition back to PROBE or OPEN. The registry must remain the lifetime owner as documented.
- Publish compares the immutable admission pointer and captured sequence. An invalidation before its CAS defeats publication; publication that linearizes first is then permanently sealed by invalidation. There is no sequence wrap path because only the single terminal invalidation increments this gate's sequence.
- Invalidate uses atomic operations without the application mutex or callback waits. It is suitable for the specified nonblocking session-listener split; Drain and control/lease methods are not.
- Send and Write increment the atomic active count before reading admission. In Go's sequentially consistent atomic order, an operation which read live admission before sealing is already counted before a subsequent Drain check; an entrant after Drain observes zero cannot subsequently read the earlier live admission. Thus Drain cannot miss an admitted callback, even though denied late entrants can transiently change the counter. The mutex/condition-variable decrement notification has no lost-wakeup gap because Wait releases the mutex atomically. Callback completion/panic releases accounting. Sustained denied calls may delay Drain, but cannot cause an early successful acknowledgement.
- A plaintext outbound admission decision may finish after invalidation or removal if its decision linearized first; it is deliberately not a send receipt. Permanent OuterSend sealing and old device/queue retirement are the essential later boundary. This distinction is correctly documented.
- Probe lease admission/removal shares the mutex. Exact protocol, family, source/destination addresses and ports are compared, with reversal only inbound. Gate-bound never-reused lease IDs and exact epochs prevent stale removal of replacements. Removing a lease does not claim to cancel an already admitted callback or queued packet.
- Parser reads and slices are guarded by IP envelope and minimum transport lengths. IPv4 options and all fragments/reserved fragment bits are rejected in PROBE; IPv6 admits only immediate TCP/UDP, so extensions/fragments are rejected. UDP declared length must equal payload; TCP data offset is bounded. OPEN intentionally checks only the IP envelope and does not apply PROBE's protocol restrictions. Checksums and transport semantics are explicitly delegated to the stack.
- There is no blocking TUN Read wrapper or callback under the gate mutex. The provided API can support the required integration, but cannot itself demonstrate placement at actual encrypted Send or prevent attaching a fresh Gate to old WG queues.

## Nonblocking quality follow-ups

1. **Strengthen the negative barrier tests.** `gate_test.go:266-280` signals `started` before calling Close and then uses immediate nonblocking selects; `gate_test.go:336-342` starts Drain and immediately polls its result. Scheduling can allow both checks to pass even if a faulty Drain returns prematurely, because the worker need not run before callbacks are released. The production barrier is sound by source review, but these tests do not deterministically prove the advertised blocking property. Add a test synchronization observation that Drain actually reached its waiting state, or a bounded negative observation window with controlled blocked callbacks, then verify that releasing only one callback cannot acknowledge closure. Ensure the test would fail if Drain skipped waiting.

2. **Make the IPv4-options fixture structurally valid apart from the prohibited option.** `packet_test.go` constructs the options case by appending four bytes and setting IHL=6 while retaining the old total-length field; it fails the envelope check before reaching the options rejection. Insert the option bytes before the transport header, fix the total length, then assert PROBE denial and OPEN admission. OPEN currently has no explicit IPv4-options fixture. This is test coverage, not a production parser flaw.

3. **Keep the unicast claim precise.** `Tuple.valid` rejects unspecified, multicast, mapped-v4 and zoned addresses, but does not reject the IPv4 limited-broadcast address `255.255.255.255`. The implementer report describes accepted addresses as unicast. Either explicitly reject limited broadcast or narrow that description. This is not an observed owner bypass: the trusted integration must still derive the source from its real exclusive concrete socket and validate destination policy.

## Integration obligations retained

Protect every present bind socket before first actual send and after every rebind; do not treat MarkOuterProtected as FD proof. Stop old device/readers and retire encrypted/staged queues before using the fresh gate. Retain the lifetime TUN across transport replacement and child lifetime. Register/unregister leases around real exclusive parent socket lifetimes, including timeout/cancellation paths, and complete verification-lease cleanup before publication. Serialize Java authentication/epoch/owner/presentation/grace checks with native publication and nonblocking invalidation, then await Drain outside session locks for hard acknowledgement. The JNI owner-handle registry, wrapper return semantics, bounded cancellation, and Android/full-packet verification are not present in this module.

## Reviewed file SHA-256

- gate.go: a3269f4b9982faadc9299de3da5ae11b225f16d26dca168ff7994198d92840f8
- gate_test.go: 97c6a75bbb0f83ffa455ed97b698e51bd84db9958efd60960ea78ae3ff1fc9c9
- packet.go: 137ebfb06721f5e8ab825b6bcb0201be0c0dc95c997b3bc280c82b3841351b97
- packet_test.go: 328c6dd12ccd2d81b180f3e2a9666eeef53b7c1b7d96c1062bb1ff2ce6dfe4bd
- go.mod: d01c5be4647eebe28e8606837d2ea85f90a217d8836fe837fe61dd02cfebc49d
