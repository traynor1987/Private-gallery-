# Task2a native transport independent review

Reviewed 2026-11-03. Scope: the eight files in `native/continuousvpn/transport/`, `NATIVE_TRANSPORT_TASK.md`, repository and latest `/tmp/nova-vpn-design-review.md` design review, implementer report, the accepted sibling gate API, and relevant exact pinned WireGuard device/TUN/bind call sites. No repository edits, CI, commits, Android/JNI changes, or subagents.

## Verdict

**Current specification verdict: accepted for the scoped host-testable Task2a core. Current quality verdict: no outstanding Critical or Important findings. Critical: 0. Important: 0.** The initial Important lifecycle cancellation defect below is retained as history and was closed by the follow-up review at the end of this report. This verdict concerns the native core only. It does not claim Android route containment, Java publication authentication, temporal probe-port ownership, or completed C1/API37 proof.

## Resolved Important — Replace could not cancel a blocked BeginProbe handshake

Locations: `native/continuousvpn/transport/transport.go:55`, `:67`, `:115`, `:129`; compare the cancellation-before-owner-lock pattern at `:168`–`:174`.

`BeginProbe` holds `fence.owner` through the actual pinned `peer.SendHandshakeInitiation(false)`, which synchronously reaches the wrapped raw Bind.Send. `Fence.Replace` takes that same owner mutex before it invalidates the old gate, detaches its virtual device, or closes its bind. When the handshake's Send blocks until socket closure, replacement waits for BeginProbe, BeginProbe waits for Send, and Send waits for the socket close that replacement cannot reach. Replacement does not even invalidate the old PROBE gate while waiting. An independent Transport.Close can rescue this, but Replace's own promised cancellation sequence cannot.

This is not the documented retained-fence wait for an uncancellable lifetime TUN Write: the blocked operation here is explicitly cancellable outer I/O, and Replace owns the responsibility to cancel it. The Task2a replacement contract requires invalidation/detachment/socket cancellation before awaiting old workers.

**Counterexample:** a temporary Go overlay adds `TestReviewReplaceCancelsBlockedProbe`, constructs the actual pinned WG device with the existing fake TUN/bind, starts BeginProbe, waits for the observed raw Send entry, then calls Replace for generation 2. No repository source or tests were modified. The focused Go1.20.3 run deterministically times out after 3 seconds. Its stack shows Replace blocked on owner at `transport.go:55`, and BeginProbe at `transport.go:129` inside fakeBind.Send's select waiting for socket close/send release. There are no sleeps; timeout terminates the demonstrated deadlock.

Artifacts: `/tmp/nova-native-review-counterexample_test.go`, `/tmp/nova-native-review-overlay.json`, `/tmp/nova-native-review-counterexample.log`.

Command: `/workspace/scratch/nova-go-toolchain/go/bin/go test -vet=off -overlay /tmp/nova-native-review-overlay.json -run '^TestReviewReplaceCancelsBlockedProbe$' -timeout=3s -count=1 ./...` from the transport directory. `-vet=off` only bypasses Go1.20 overlay vet's inability to open the added virtual filename; the separate supplied full-suite vet result is not contradicted or rerun. The initial attempt with automatic vet failed at that overlay path before executing tests.

**Required correction:** make replacement able to seal/detach/cancel the current transport before waiting behind its blocking owner-held work, with synchronization that cannot target the wrong generation during concurrent replacements. Alternatively remove blocking handshake I/O from the owner critical section while preserving safe device lifetime. Add the blocked-BeginProbe-versus-Replace regression, and verify concurrent Close/replacement and fresh-generation attachment ordering. Merely calling closeLocked after acquiring owner keeps the deadlock.

## Accepted core properties

- One lifetime real-TUN reader continuously drains/drop traffic without a target and while admission is CLOSED. It reads outside dispatch locks.
- Virtual queues own copies, are bounded and nonblocking on capacity, synchronize enqueue/dequeue/disposal, never close their queue channels, and zero queued/rejected/dequeued storage. Dequeue rechecks the original gate/lease policy. Virtual File is nil; events are private and cancellation is idempotent.
- Writes and sends retain immutable old-generation gate identity; fresh generations do not inherit old WG queues. Virtual Close never closes the invariant-bearing real TUN.
- The normal retirement path invalidates, detaches, cancels outer sockets, calls actual pinned Device.Close/Wait, and drains admitted callbacks before creating another generation or closing Done. Pinned Close explicitly waits for its virtual TUN reader and stops peers; any late old-device activity still encounters the sealed old gate.
- Bind.Open holds the FD lifecycle lock through raw open, FD inspection, and synchronous protection; every present socket must succeed. Send does not hold that lifecycle lock. Second Open seals and closes rather than reusing the generation. Cookie, handshake, and data sends all reach the same wrapper in the pinned call sites.
- Invalidate itself delegates only to the nonblocking native gate invalidation; no owner lock, Java callback, I/O, or drain is introduced into the session-handler operation.
- Fatal lifetime reads/writes seal admission and retain the real resource. Explicit release remains separate. Blocking real writes withhold retirement acknowledgement and replacement, as the report correctly states.

## Evidence and limits

I read all eight module files and relevant pinned implementation paths. I did not duplicate the already reported 19-test race/vet suite. The only additional test execution was the targeted counterexample above, which exposes a missing interleaving rather than contradicting the existing passing tests.

The module deliberately does not implement API37 lifetime/child descriptor placement, reserved Java probe sockets/TCP slot burning, native/JNI identity registry, or authenticated final publication. Those are acknowledged integration obligations, not findings against an alleged completed implementation. The stricter unsupported-family handling is likewise explicitly documented, not silent proof that Android FD protection has been exercised.


## Follow-up: Important finding closed

Reviewed the correction in `transport.go` and the four new regression tests in `transport_test.go`, plus the appended implementer report. The correction resolves the demonstrated cancellation cycle without moving current-generation selection outside its owner lock.

`prepareProbe` now checks current identity/state and registers `probes.Add(1)` while holding `fence.owner`; `BeginProbe` executes the blocking pinned Send outside owner and defers Done until the complete exported handshake call returns. `closeLocked` still holds owner while invalidating, detaching and closing the old sockets, then joins the probe reference. No new Add can race the Wait because a later prepareProbe cannot acquire owner until retirement finishes and then rejects the closed/stale transport. A kick stalled before actual Send encounters its already sealed original gate; a kick already inside Send is cancelled by the socket closure. Fresh attachment and Done still occur only after the join, explicit pinned shutdown and gate drain.

The four added tests directly cover replacement cancellation of the blocked handshake, stale/equal replacement against a newer blocked probe, concurrent replacements plus old Close, and stale replacement around newer socket protection. I inspected these tests and did not rerun the supplied 23-test repeated race suite or full vet.

Independent verification: reran the original temporary overlay counterexample unchanged with the same pinned Go1.20.3 command documented above. It now **PASSes in 0.010s**. This directly closes the independently reproduced defect.

Precision note: the pinned RoutineReadFromTUN may itself asynchronously initiate Device.Close after virtual Close makes Read return os.ErrClosed. Therefore the new reference guarantees completion before this wrapper's explicit Device.Close call and, critically, before fresh generation attachment or Done; it does not forbid upstream-initiated shutdown from starting earlier. No new Critical/Important issue was identified from this existing pinned behavior. The peer pointer remains referenced and old admission is sealed throughout retirement.

Final scoped verdict: **Task2a native core accepted; Critical 0 / Important 0 outstanding.** API37, JNI, Java authentication/publication, temporal probe socket ownership and lifetime containment remain separate, unimplemented integration obligations exactly as acknowledged in the report.
