# Owner-private readiness endpoint — inactive substrate

This package implements the isolated endpoint/connection lifecycle from the final parent-private netstack design in `docs/release17103/CONTINUOUS_VPN_DESIGN_REVIEW.md`. It is not wired into WireGuard, JNI, an Android service or the shipping browser. It creates no host network sockets; all UDP/TCP endpoints are allocated inside the exact pinned gVisor stack already selected by WireGuard21636207a675. gVisor is Apache-2.0; its module contains the upstream LICENSE and notices. Module hashes are pinned in go.sum. No upstream source was copied or changed.

## Implemented and verified

- Nonzero fence/generation/epoch/attempt identity; copied immutable numeric address/flow configuration; bounded NIC and output queues; exact unfragmented TCP/UDP control-flow filtering in both directions, IPv4/IPv6.
- One cancellation-aware endpoint output pump, owned packet copies, no blocking notifications. Only this pump creates Packet values; CopyPacket requires their exact endpoint owner and checks retirement. There is no arbitrary tagged-packet setter or JNI send interface.
- Explicit bound UDP/TCP flows; one active owner per configured slot. TCP owns its endpoint before checking cancellation/bind/connect and closes every unsuccessful allocation. No system resolver, proxy, default dialer or host network connection is used.
- Creation, injection, output reads and all returned connection methods join a common retirement barrier. Close seals creation, cancels pending dials/reads, closes connections, waits for admitted adapter operations/pump, then closes/drains the endpoint and closes/waits the stack. A stalled operation stalls acknowledgement. Caller processing after an API returns still needs owner-worker/attempt control at integration.
- Nine host behavioral tests pass with Go1.20.3 -race; go vet passes. Positive UDP reply delivery, malformed/wrong tuple/direction/fragment rejection, IPv6, configuration ownership, foreign/retired Packet rejection, queue pressure, concurrent flow creation/close, blocked TCP cancellation and a controlled connection Read barrier are covered.
- Initial tests failed because the package was absent. A subsequent deterministic blocked-Read counterexample reproduced early retirement acknowledgement before explicit connection-method tracking; it passes after the fix. Final independent source review and independently rerun readonly Android-root race suite (all nine endpoint tests) found no Critical/Important issue, including the explicit connection-method barrier. No shipping security approval is implied.
- Android native root dependency graph compiles/tests bridge plus this package using pinned WG21636207a675, gVisor703fd9b7fbc0, x/sys0.6.0 and x/net0.8.0. No four-ABI rebuild or Android execution is claimed.

## Required next work

Wire a concrete endpoint owner into a structurally private WG queue. REAL bytes remain denied in PROBE; copying raw bytes or matching identity fields/tuples alone is not provenance. Recheck exact owner, attempt and native revocation at private enqueue/dequeue. No child-facing arbitrary packet injection API is permitted.

Implement passive lifetime kernel port keepers and bounded reverse-flow tombstones before any private->OPEN transition: late private replies must never enter the real Android TUN. Endpoint.Close is not a keeper/child retirement receipt and does not flush all encrypted WG queues.

Retain the existing strict DNS codec via bounded private datagrams/TCP fallback; implement dual normal Go plus Android app-policy TLS validation and fixed HTTP readiness. Then integrate persistent VPN backend/service, child-held lifetime fence, broker/runtime/session publication, reconnection and cleanup. Actual API37 packet verification, independent exact-source security approval and original-key54->55 upgrade/signing remain mandatory. Critical C1 is unresolved.
