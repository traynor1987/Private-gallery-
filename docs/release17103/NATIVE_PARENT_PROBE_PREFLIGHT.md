# Task2d contract preflight

**Verdict: YES — ready to dispatch the bounded implementation after Task2c. No remaining contract blocker identified.** This is design-contract feasibility only, not implementation acceptance, Android route proof, security approval or signing authorization.

Reviewed `/tmp/novaos-code55/docs/release17103/NATIVE_PARENT_PROBE_TASK.md` as present during this review; observed repository HEAD `ef4e0fd0`. The material-revision addendum in `CONTINUOUS_VPN_DESIGN_REVIEW.md` remains the binding detailed contract.

The three preflight corrections are now explicit:

- Generic registration preserves handle/lease semantics but can never admit REAL packets during PROBE, including byte-identical registered tuples.
- Private-flow tombstones persist for the fence lifetime across WG-generation replacement, preventing late replies from being reclassified as ordinary child traffic.
- IPv4-only readiness HTTPS is identified as a deliberate bounded choice; existing ordinary IPv6 routing is preserved.

The remaining requirements align with the reviewed architecture: one existing pinned WG device; owner-private provenance-tagged readiness; passive keepers that never connect/send; bounded gVisor lifecycle barriers; existing bounded DNS validation; normal Go TLS chain/hostname validation plus mandatory Android trust-policy verification; same-device prepared receipt; and later authenticated child publication. No private-stack queue drain is misrepresented as a WG-internal queue flush. Cancellation, stale identity, callback lifetime and fail-closed exhaustion obligations are included.

Implementation review must assess the actual behavior tests named in the task, including generic registration followed by attempted REAL admission and late replies across a generation replacement. The host/JVM/four-ABI checks are appropriate for this scoped task; Android exclusivity, lifecycle and unchanged full-capture assertions remain mandatory later evidence. Tor is deferred and outside this task; no Tor work is proposed.

Read-only review only. No source edits, implementation, tests, CI, subagents, commit/push or approval changes performed.
