# Hidden Browser continuous containment design assessment

Read-only analysis, 2026-11-03. Repository `/workspace/scratch/novaos`, branch `release/1.7.10-private-home`, inspected HEAD `e772b50a8927df0a2976428487c4d4f9826cb233`. No repository edits, builds, CI, signing or approval changes performed. This is a feasible implementation design, not Android runtime proof or security approval.

## Decision

The most bounded candidate preserving the current browser feature set is **one persistent Android TUN/Network for the whole Hidden child lifetime, plus a pinned native WireGuard gate and replaceable WireGuard device instances**. Retaining a duplicate TUN descriptor is a necessary lifetime mechanism, not sufficient enforcement. A native gate must reject ordinary packets throughout suspension and verification; a parent-owned readiness channel must pass through the new WireGuard instance while the gate is closed to the child. Recreate the WireGuard device behind that persistent boundary to eliminate old queues and handshake identity. This adds a maintained native backend, but avoids replacing Chromium or normal connected-state native UDP behavior.

A permanent deny-TUN plus WireGuard userspace netstack/proxy is also technically feasible without root, and its control plane is conceptually simpler. It adds gVisor and new stream/DNS integration, and a proxy-only form permanently drops native WebRTC/UDP even when connected. It therefore is not a transparent feature-preserving substitute unless all current required native capabilities are explicitly carried through a packet transport. Do not silently select it as a scope reduction.

Neither design can guarantee the same fence after Android forcibly deactivates the VPN itself. Public `onRevoke()` is delivered after deactivation [S1]. This is a precise remaining platform limitation, not a reason to abandon the authorized app-controlled continuity repair.

## Local evidence and integration points

The reviewed source and `RUNTIME_EVIDENCE.json` establish eight passing Android cases and twelve recorded race identities, but the full packet capture contains default-route DNS and native child UDP during removal of the old VPN. The real marked probe occurs before `denied_confirmed`; moving that timestamp cannot fix it.

`MainActivity.denyHiddenVpnNetwork()` currently calls `wireGuardEngine.disconnect()` then waits for ownership release and disappearance of the old Network. `OfficialWireGuardBackend.connect()` also takes the previous backend DOWN. Both deliberately remove the only enforcement route. `HiddenBrowserRuntime.resumeContinuation()` currently requires a different Network from the old one, then runs HTTPS/DNS verification. That Network-inequality assertion must become a transport-generation/verification assertion: persistent containment intentionally preserves the same Android Network. Updating that assertion is an architectural consequence, not permission to weaken route verification.

`HiddenTorIsolationService` already demonstrates the useful ownership pattern: full IPv4/IPv6 routes, numeric sink DNS, allowed application, duplicate reader, forwarding withheld, and deferred route teardown until child death. Its current `onDestroy()` closes its descriptors immediately; that path must not be copied as a proof of survivable parent/service failure. Tor stays outside the continuity feature scope.

`OwnedVpnDns` uses explicit numeric-server UDP and truncated-response-only TCP over the owned Network. `HiddenHttpTransport.verifyReady()` makes a certificate-verified HTTPS request to example.com, requires 200 and nonempty body. These existing probes can be retained with a capability-aware socket factory/registry; they do not need Android's fallback resolver.

## Exact dependency facts

The repository pins `com.wireguard.android:tunnel:1.0.20230706`. I retrieved the tag's official mirror sources into `/tmp/nova-GoBackend-20230706.java`, `/tmp/nova-api-android.go`, `/tmp/nova-jni.c`, `/tmp/nova-go.mod`, and `/tmp/nova-Makefile` [S2-S5].

The tag pins `golang.zx2c4.com/wireguard v0.0.0-20230223181233-21636207a675`, Go 1.20.3, and an Android runtime boottime patch. Java GoBackend exposes only UP/DOWN/TOGGLE. UP establishes and detaches a TUN; DOWN calls `wgTurnOff()` and `stopSelf()`. `wgTurnOff()` closes the entire device. Native startup calls `device.Up()` before Java subsequently protects the outer sockets. This is not an exposed pause/rebind API.

The exact Go revision uses **single-packet**, not modern batch, interfaces: `tun.Device.Read([]byte,int)`, `Write([]byte,int)`, `Flush()`, and `conn.Bind.Send([]byte,Endpoint)`. Do not implement a current-main batch wrapper against this pin. Exact source snapshots are `/tmp/nova-wg-*` [S6].

## Candidate A: retained TUN with native packet and send gates

### Ownership and API shape

Establish one non-bypass full-route VpnService before admitting any child traffic. Keep its original `ParcelFileDescriptor` owned by a dedicated lifetime controller. Give each native WG instance a duplicate. Public `ParcelFileDescriptor.dup()` and normal shared-file-description semantics support this lifetime; native `NativeTun.Close()` closes its copy, not a separately held copy [S6,S7]. Do not call `Builder.establish()` again on routine suspend/resume, and do not stop the service. Android allows only one active VPN; a second deny service is a replacement, not an independent parallel firewall [S8].

Suggested JNI surface, design names only:

- `createTransport(retainedTunDup, config, generation)` starts CLOSED, with outer sends disabled until both socket protection results are checked.
- `closeAdmission(generation)` synchronously closes Read/Write admission and the outer-send gate; waits for currently executing sends/writes to return. Returns an acknowledged generation.
- `retireTransport(generation)` closes the WG device and its duplicate while preserving the original TUN and service.
- `registerProbe(generation, exactTuple, lease)` / `revokeProbe(...)` permit only the parent-controlled readiness sockets.
- `beginProbeOnly(generation)` allows the protected new instance's handshake/probe packets while ordinary TUN traffic remains discarded.
- `publishVerified(generation, authorityEpoch, presentationId)` opens ordinary traffic only after existing ownership/auth/grace predicates and the actual child commit authorize it.

Native code should not accept arbitrary Binder callers or renderer-provided tuples. Keep native handles and registry in the trusted owner process; existing authenticated broker IPC carries only bounded state transitions. Engine availability, persistent fence ownership and ordinary-data admission are distinct states. `hasTunnel=false` cannot describe the denied-but-still-owning-fence state.

### Why both gates matter

A wrapper around `tun.Device.Read` drains/drops ordinary outgoing IP packets while denied or probe-only. `Write` drops ordinary incoming plaintext, so old inbound records cannot reanimate the retained page. Dropping must consume buffers and return semantics expected by the pinned API; do not return a fatal read error to signal routine denial, because the WG reader treats errors as device failure.

Read gating alone is insufficient. Packets may already occupy staged/encryption/sequential-send queues. The exact pin's sequential sender even documents tolerated stop stragglers; `Peer.Stop()` waits for sender/receiver exit and then zeroes/flushes key state [S9]. Add a synchronized `conn.Bind.Send` gate around the actual UDP write. Close acknowledgement must wait for in-progress sends, not merely set an atomic Boolean. A datagram already handed to the kernel cannot be recalled; it remains on the previously verified encrypted route. No route is removed during this boundary.

Then close/recreate the WG device while retaining the Android TUN. Recreating avoids having to prove old encrypted payload cannot be released when the send gate later allows probes. Stop old reader before new reader; duplicate descriptors refer to the same packet queue and must not have competing consumers. Retained packets should be drained/discarded, with a bounded fail-closed policy if a flood prevents a clean transition. New wrapper state defaults CLOSED even on constructor/error paths. Do not hold a gate mutex across a blocking TUN read: close/suspend would deadlock waiting for the read it needs to stop.

### Probe isolation and publication

The native TUN provides IP packets without trustworthy process identity. Neither UID checks nor destination-only allowlists identify the parent: child and parent share app UID, and the child can contact the same DNS/HTTPS destinations.

Use exact reserved socket tuples: IP family, transport, source address/port, destination address/port. Parent creates an unconnected socket, binds it to the owned Network and a concrete local address/ephemeral port with reuse disabled, keeps the descriptor alive, registers the exact tuple before any connect/send, then performs the existing DNS or HTTPS operation. Reverse tuple admission is equally narrow. Remove permit before closing/releasing its socket. DNS TCP fallback needs its own lease. Do not open all port 53/443 traffic, trust only source port, or allow arbitrary fragments/extensions whose tuple cannot be parsed. Reject unrecognized/malformed/fragmented probe packets; add deliberate support only where required and tested.

The held real socket is what prevents another ordinary unprivileged socket from binding its tuple, not secrecy of the port. Parent-private FD/registration is essential. This does not claim isolation against arbitrary native code already executing with full parent-process authority; that is not supplied by a same-UID app architecture.

Avoid a readiness deadlock: an idle newly created WG peer may have no handshake until traffic triggers one, while current Main waits for CONNECTED before creating the HTTPS/DNS probe. The native owner must explicitly initiate the configured peer handshake (`LookupPeer` plus the pinned exported `Peer.SendHandshakeInitiation(false)`), or run a parent-only DNS probe during PROBE_ONLY before publishing CONNECTED. Do not rely on browser traffic or assume every provider config has persistent keepalive. Obtain fresh handshake statistics from the newly created device, complete configured-DNS and HTTPS verification using only those leases, then revoke readiness leases and commit ordinary admission under the existing epoch/owner/presentation/grace transaction. Lock, Panic, replacement and expiry must invalidate native publication as well as broker publication. Failed/stale work leaves the fence CLOSED. Process death remains required before final TUN release.

### Build impact

A Kotlin-only adapter around the pinned AAR cannot implement this cleanly: JNI methods and descriptor creation are private, and native Close destroys its TUN handle. Vendor a minimal audited fork/module at the exact upstream tag/revision, or a separately named native library plus custom VpnService/backend, retaining public config parsing if useful. Avoid duplicated `libwg-go.so` packaging and JNI symbol/class conflicts. Build pinned C/Go sources through NDK for the existing release ABIs (at least runtime-test x86_64 and device arm64-v8a), preserve notices, pin module checksums/toolchain, and validate API37 loading/page-size constraints. Do not silently upgrade WireGuard while implementing the gate. The existing Gradle app has no declared native build configuration; adding this is a real build-system change, not a one-line dependency tweak.

## Candidate B: permanent sink and userspace WG proxy

WireGuard's exact Go pin already includes `tun/netstack`. `CreateNetTUN(localAddresses,dnsServers,mtu)` returns a userspace TUN and `*Net`; `DialContext`, TCP/UDP dials and DNS lookup operate in that stack. The official example constructs `device.NewDevice(netTun,bind,logger)`, applies WireGuard config, raises the device, and supplies `tnet.DialContext` to HTTP [S10]. No privileged OS TUN is needed for this *transport*; Android's one real TUN remains the permanent deny sink.

A feasible bridge is the existing authenticated local HTTP/CONNECT proxy backed by an owner-private Unix/FD stream API whose Go side calls `tnet.DialContext`. Preserve target resolution inside netstack; never fall back to Go's ordinary resolver or Java default sockets. Only WG's encrypted outer UDP sockets receive `VpnService.protect()`, and the result must be checked before first send. Provider endpoint bootstrap must preserve existing validated profile policy and avoid leaking page DNS. Parent probes have a separate capability; ordinary proxy stream creation and existing streams are revoked on suspension. Recreate netstack/WG behind the sink before re-verification; the child's actual WebViews, profile, cookies and auth remain untouched.

Build impact is larger: import pinned gVisor `703fd9b7fbc0` plus its dependencies, create bounded cancellable stream/FD bridging, DNS integration and byte-copy handling, and exercise TLS/CONNECT/WebSocket/download behavior. This is implementable with public APIs but is not available through the current AAR. Proxy-only native UDP remains permanently denied. A hybrid that forwards native packets into WG when admitted converges toward Candidate A and inherits its queue/gate obligations.

## Unsolved constraints and required proof

1. **OS revocation:** Android says onRevoke arrives after interface deactivation [S1]. A retained descriptor is not authority to restore a revoked Network. Hard-retire on revocation; do not describe that asynchronous response as a mathematically gap-free fence. Independent OS lockdown would be a separate user/device policy, not something this app may silently configure. This limitation affects both candidates.
2. **Owner-process death:** keeping all duplicates in one process cannot retain the TUN after that process dies. A separately surviving guardian or child-held non-forwarding lifetime reference can preserve the kernel descriptor during owner death, but service/Network behavior and safe final release require exact Android37 proof. The current Tor `onDestroy()` is not that proof.
3. **Verification aperture:** tuple ownership, atomic unregister-before-close, parser bounds, IPv6 extensions/fragments, and stale handles require direct tests. No runtime native DNS/WebRTC packet may use the probe aperture.
4. **Publication race:** an ordinary data gate opened before final child publication reintroduces precommit native egress. Gate opening must participate in the final authenticated transaction, with cancellation returning CLOSED, while preserving current same-view continuation assertions.
5. **No evidence reuse shortcut:** any production/native/build change invalidates the old 18-case reuse compatibility. Preserve full packet validation, all child probes and whole-capture timing. After implementation review, test suspension, verification, bound-before-commit, failure, queue pressure, native IPv4/IPv6 DNS/UDP, transport replacement, service death and revocation. This report schedules no CI.

## Primary sources

[S1] Android VpnService API, `onRevoke`, `protect`: https://developer.android.com/reference/android/net/VpnService . Public revocation timing was verified with web source `turn35search5`.

[S2] Official WireGuard mirror, exact Java tag: https://github.com/WireGuard/wireguard-android/blob/1.0.20230706/tunnel/src/main/java/com/wireguard/android/backend/GoBackend.java . Downloaded from raw.githubusercontent.com into the local snapshot above.

[S3] Exact native entrypoints: https://github.com/WireGuard/wireguard-android/blob/1.0.20230706/tunnel/tools/libwg-go/api-android.go . Downloaded and inspected locally.

[S4] Exact dependency pin: https://github.com/WireGuard/wireguard-android/blob/1.0.20230706/tunnel/tools/libwg-go/go.mod . Downloaded and inspected locally.

[S5] Exact Android native build: https://github.com/WireGuard/wireguard-android/blob/1.0.20230706/tunnel/tools/libwg-go/Makefile and sibling `jni.c`. Downloaded and inspected locally.

[S6] WireGuard exact Go TUN/device sources: https://github.com/WireGuard/wireguard-go/blob/21636207a675/tun/tun.go ; https://github.com/WireGuard/wireguard-go/blob/21636207a675/tun/tun_linux.go ; https://github.com/WireGuard/wireguard-go/blob/21636207a675/device/device.go . Downloaded and inspected locally; current upstream comparison is https://git.zx2c4.com/wireguard-go/tree/device/device.go (`turn32view2`).

[S7] Linux kernel TUN lifetime: https://docs.kernel.org/networking/tuntap.html (`turn35search0`); Linux man-pages shared descriptor semantics: https://man7.org/linux/man-pages/man2/dup.2.html and https://man7.org/linux/man-pages/man2/close.2.html (`turn35search3`,`turn35search4`).

[S8] Android VpnService.Builder API (full-route/no-bypass/interface handover behavior): https://developer.android.com/reference/android/net/VpnService.Builder (`turn31view3`,`turn34view1`).

[S9] Exact pinned queues and send behavior: https://github.com/WireGuard/wireguard-go/blob/21636207a675/device/send.go ; https://github.com/WireGuard/wireguard-go/blob/21636207a675/device/peer.go ; https://github.com/WireGuard/wireguard-go/blob/21636207a675/conn/bind_std.go . Downloaded and inspected locally.

[S10] Exact netstack implementation: https://github.com/WireGuard/wireguard-go/blob/21636207a675/tun/netstack/tun.go and exact root go.mod, downloaded and inspected locally. Official usage example: https://git.zx2c4.com/wireguard-go/tree/tun/netstack/examples/http_client.go (`turn31view2`); current API comparison https://git.zx2c4.com/wireguard-go/tree/tun/netstack/tun.go (`turn32view3`).
