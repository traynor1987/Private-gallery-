# Continuous VPN containment implementation plan

> For agentic workers: execute scoped implementation tasks with independent review. Keep the existing release branch and all mandatory packet gates.

**Goal:** fix Critical C1 while retaining authorised Hidden WebViews, Private App ownership and pending authentication state.

**Architecture:** retain the Android VPN interface for the entire browser lifetime. Close a native packet/send gate before stopping the WireGuard device; recreate that device behind the retained interface and permit only parent-owned DNS/HTTPS verification sockets until authenticated publication. Never remove the protective route while a sensitive child is alive.

**Spec:** owner full-continuity directive; `CONTINUOUS_VPN_DESIGN.md`; existing `COORDINATED_IMPLEMENTATION.md`. The design assessment is not an approval or runtime result.

## Global constraints

- Same branch `release/1.7.10-private-home`, package `uk.co.traynor.privategallery`, version1.7.10.3/code55, API37 only; original signing key and encrypted formats unchanged.
- Preserve ACTIVE/TEMPORARILY_SUSPENDED/RECONNECTING/LOCKED/TERMINATED ownership, original Hidden grace, stale-worker rejection and hard controls.
- No restricted Beta fallback, browser-engine replacement, packet exclusion, timestamp relaxation, silent device-policy change or claimed live Google verification.
- Existing WireGuard Java/native source is pinned to1.0.20230706 and wireguard-go21636207a675. Reproducibly build a separately named native library; retain notices and toolchain hashes. Do not silently upgrade dependencies.
- Primary/Hidden/Tor and saved-app ownership stay separate. Immersive Private Apps remains deferred in `../PRIVATE_APPS_NEXT_UI.md`.

## Task1: native gate and host proof

- [ ] Implement a standalone Go gate package with CLOSED/PROBE/OPEN states, monotonic transport generations and exact IPv4/IPv6 TCP/UDP socket-tuple leases. Reject malformed, fragmented and unsupported probe packets; OPEN preserves normal connected traffic.
- [ ] Tests must first fail for absent implementation, then prove stale-generation denial, exact reverse tuple handling, unregister-before-port-release, default closure, malformed packets, concurrent close acknowledgement and blocked sends/writes. Use a synchronous send barrier; no lock across a blocking TUN read.
- [ ] Integrate the gate with the pinned single-packet tun.Device and conn.Bind interfaces. Stop/recreate old WG device behind the retained original descriptor so old encrypted/staged queues cannot reopen with a new generation.
- [ ] Build/host-test the native control package with the hash-verified pinned Go toolchain before Android CI. Independently review gate/queue/lifetime guarantees.

## Task2: persistent Android boundary and probe ownership

- [ ] Before child admission, require effective NovaOS-only application scope for the persistent fence so an external Google verification app retains its normal network. Already-app-only profiles need no coverage change. A global/other-app profile requires an explicit in-product coverage choice bound to the exact profile/scope; decline preserves existing coverage and cancels launch. Keep the encrypted imported profile unchanged; never silently narrow device-wide coverage or enable OS lockdown. No TUN replacement while a child lives. Restore a prior scope only after child death and only if its original profile/owner remain current.
- [ ] Add a separately named backend/service using existing official configuration and statistics APIs. Retain original TUN ownership; give each WG instance a duplicate; protect both outer UDP sockets successfully before any send. Keep normal Primary VPN behavior compatible.
- [ ] Add parent-private socket leases to OwnedVpnDns and the HTTPS readiness socket factory. Bind concrete local addresses with reuse disabled, register exact tuples before connect/send, unregister before close. Child IPC cannot obtain native handles or register tuples.
- [ ] Implement suspend/resume/close APIs with explicit native-generation receipts. A new peer must initiate handshake without ordinary child traffic. Readiness DNS and HTTPS retain their existing validation and deadlines.
- [ ] On explicit close/lock/timeout/Panic, close admission, revoke broker authority and confirm child death before final TUN release. Test owner/service death and OS revocation separately; do not claim onRevoke is a pre-deactivation firewall.

## Task3: coordinated runtime and navigation integration

- [ ] Replace disconnect-and-dead-Network suspension with persistent-fence/native-generation proof in Main/runtime/broker/Activity/monitor/heartbeat. Same Android Network is intentional; require new WG transport generation and old-proxy/epoch revocation instead of Network inequality alone.
- [ ] Keep ordinary admission closed through verification and bound-before-commit; open only in the current authenticated publication transaction. Cancellation, replacement and expiry close native admission before acknowledging revocation.
- [ ] Repair explicit Browser/Tor/Private App destinations without recreating WebViews to select them. Preserve ordinary and saved-app tab owners; encrypted ordinary metadata must not restore a saved-app URL as Browser. Coordinate Home retention with the same authorised suspension boundary.
- [ ] Independently review implementation and run host/JVM checks. Keep all existing adversarial native probes and full captures; adapt only proofs made obsolete by the persistent Network architecture, replacing them with stronger explicit fence/generation assertions.

## Task4: focused Android evidence and release

- [ ] Production/native/build changes invalidate old protected-tree reuse. Select only affected boundary/lifecycle/route cases in the existing workflow; preserve historical eight passes as historical evidence, not a green verdict for changed transport.
- [ ] One grouped API37 run after coherent integration: authorised background, DNS/native UDP throughout shutdown, verification and commit races, valid/foreign/replayed callbacks, same WebView/app, lock/expiry/Panic, service/process loss and navigation selection. Full packet capture must independently pass unchanged no-fallback rules.
- [ ] Independent exact-source security approval only after complete runtime and raw packet verification. Existing Beta disk-profile limitation remains disclosed.
- [ ] Freeze source; run existing original-key54→55 install-r/restart gates and production signing workflow; verify actual APK package/version/certificate/hash; deliver one signed acceptance APK.

## Review focus

Protect against queued encrypted packets after gate close; a socket tuple reused after its parent lease retires; initial handshake before protect succeeds; deadlock or competing readers on a retained TUN; and native admission racing authentication revocation. Each owning task must cover these conditions before release activation. No CI or signing is authorised by this plan alone.
