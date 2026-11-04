# Private readiness provenance implementation plan

Goal: continue the reviewed code55 containment design without restoring rejected kernel apertures.
Spec: final parent-private netstack section of docs/release17103/CONTINUOUS_VPN_DESIGN_REVIEW.md; owner recovery/Home2/Store directive.

Constraints: existing release branch and code55; API37; production key, encrypted formats, packet assertions and Tor deferral preserved. No signing or CI before coherent shipping integration. Worktree starts at cb019841. Existing worktree is clean and retained; exclusive local lock records this session; remote publication requires unchanged HEAD.

- [x] Reproduce a REAL packet impersonating a registered readiness tuple through the actual persistent TUN reader.
- [x] Require OPEN for REAL enqueue/dequeue and actual REAL writes; retain revoke/drain barriers. Replace former positive kernel-aperture test with stronger denial across tuple replacement and publication.
- [ ] Independently review and preserve that correction with exact-source scope.
- [ ] Build a bounded generation/attempt-owned private gVisor endpoint adapter using the exact pinned graph. Test producer isolation, stale/cancelled attempts, queue pressure, injection/flow creation sealing and close/join races before transport wiring.
- [ ] Add collision-proof private-flow demultiplexing and lifetime passive port keepers before private->OPEN publication. Integrate DNS codec and dual Android/Go TLS policy; no default resolver/dial/proxy fallback.
- [ ] Complete Android native ABI/service/backend/runtime/child-lifetime integration; run one coherent API37 packet gate and independent security review, then original-key54->55 upgrade/signing.
- [ ] Only after approved55: integrate recovered Home2 foundations and Nova Store into1.7.11, validate catalogue and per-Space installation, upgrade/sign/deliver.

Review focus: matching REAL/private bytes, cancellation during callbacks, delayed replies after OPEN, queued packets across phase changes, endpoint creation racing retirement.

Ruling: owner explicitly requested autonomous recovery and implementation; retain the existing reviewed design and plan rather than restart design approval. Native readiness is a prerequisite and is kept inactive until all shipping gates pass.
