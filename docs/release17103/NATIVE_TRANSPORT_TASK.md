# Native transport integration task contract

This is Task2a of CONTINUOUS_VPN_PLAN.md, not a completed C1 repair. Implement a host-testable Go transport core against the exact existing WireGuard pin before Android/JNI integration.

## Files and scope

Create `native/continuousvpn/transport/` Go module with `go.mod`, `go.sum`, focused source and tests. Import the sibling `uk.co.traynor.privategallery/continuousvpn/gate` module via local replace. Pin WireGuard and x/sys to the exact versions and sums in the vendored upstream files. No app, Gradle, workflow, JNI, signing or version changes in this task.

## Persistent fence

One `Fence` owns a real pinned `tun.Device` for its lifetime. Exactly one reader continuously drains it. Per-generation `virtualTun` devices feed bounded, nonblocking copied-packet queues to WG. When no admitted generation exists, consume/drop traffic; never stop consuming merely because WG is absent. A virtual device's Close detaches its target, cancels Read, drains/zeroes queued buffers and closes its own Events, but never closes the real TUN. Return nil from virtual File unless an audited pinned call requires it. A retired generation cannot enqueue or write through the new generation. Recheck gate/lease policy when returning queued plaintext to WG, not only when enqueuing.

Per-device replacement: invalidate old gate without waiting; detach it; close its WG device/outer sockets to cancel blocked IO; await old virtual reader and WG shutdown; drain the gate; then create a fresh gate/device. No new transport may reuse old WG queues. The real fence is released only by an explicit external owner call after child retirement; routine transport Close never releases it. JNI/Java will later prove child death and retain a child-held non-forwarding descriptor across parent death.

## Protected outer bind

Wrap exact pinned `conn.Bind`. Every Open must open sockets, obtain the actual present IPv4/IPv6 descriptors, and synchronously call a supplied trusted `protect(fd) bool` before returning receive functions or allowing any Send. Protect failure closes sockets and permanently invalidates the generation. Do not race Close/FD reuse with protection. Every encrypted Send, including handshake/cookie paths, passes the gate's callback barrier. Do not lock across a potentially blocked Send in a way that prevents Close from cancelling it. Mark protection only after every present socket succeeds. Unexpected reopen invalidates the current verified generation and requires a fresh owner-controlled device, not silent admission.

Configure/start the device with admission CLOSED. Add an explicit owner BeginProbe operation after protection. Kick the configured peer's handshake without ordinary child packets, using the pin's exported APIs. Probe tuple registration delegates to the gate; final publication takes the same expected revocation sequence. Do not place authentication in this pure native module: Java must validate owner/auth/grace inside the final transaction before Publish.

## Important integration boundaries

Probe tuples identify actual exclusively bound parent sockets. Android integration must reserve their ports before admitting sensitive child traffic, retain UDP sockets across generations, and use each pre-reserved TCP socket slot only once. A later exclusive bind alone cannot distinguish an old queued packet from a previous owner. Exhaustion is fail-closed. Do not imply this pure Go task proves those Java FD rules.

Invalidate must remain nonblocking for HiddenSession callbacks. Blocking drain/retirement occurs outside session monitors; failure to drain means the persistent fence stays closed and retained, not a successful denial acknowledgement. Service/process/OS revocation behavior is future API37 proof, not a host-test claim.

## Verification

Write failing tests first, then implement. Use fake real TUN, fake bind/protector and channels for deterministic ownership/interleavings; also compile against the actual pinned device/bind interfaces. Prove continuous CLOSED drain, no competing real readers, copied buffer ownership, bounded overflow, zero/retire behavior, old generation rejection, no write after close acknowledgement, every Open protection ordering/failure, cookie/handshake Send gating, and replacement waiting for old workers. Preserve normal OPEN packet semantics. Run pinned Go1.20.3 tests with race and vet. No CI or commit/push.
