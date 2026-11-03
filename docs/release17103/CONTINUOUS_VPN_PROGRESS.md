# Code55 continuous VPN repair progress

Owner selected the full continuity repair on2026-11-03. Same branch, API37, version1.7.10.3/code55. Security approval/signing remain blocked by historical Critical C1 at source24c29fd; no runtime claim is made for this development work.

- [x] Recovered authoritative e772b50a, exact failed run41688397704 and verified raw artifact13257432448. Real child DNS/UDP escaped during app-controlled VPN removal; no fixture exception is valid.
- [x] Selected persistent TUN + native CLOSED/PROBE/OPEN gate + fresh WG devices. Independent architecture review conditionally supports implementation, not release. Protect every Bind.Open, retain a child-held non-forwarding fence reference, and serialize final publication with revocation.
- [x] Pinned official upstream snapshots to WireGuard Android58789d6372e8948b87f5c47da7e5f2ad570eb531/tag1.0.20230706; source hashes and notices recorded. Hash-verified Go1.20.3 used for host tests; toolchain binaries are not repository artifacts.
- [x] Standalone native gate host foundation: RED→GREEN, race/vet, parser fuzz and independent scoped review. Review follow-ups fixed broadcast validation and deterministic barrier-test proof. It is not yet wired into Android or a WG device, so C1 is not fixed.
- [x] Host-testable persistent reader/virtual-TUN/protected-bind core against exact WG Go21636207a675. Independent review found a Replace/blocked-handshake cancellation deadlock; fixed with tracked handshake lifetime outside the owner lock. The original counterexample now passes;23 race-tested cases and vet pass; scoped review has no outstanding Critical/Important finding. This remains a native core, not Android proof.
- [ ] Reproducible separately named Android native library/JNI/backend and child-held TUN reference.
- [ ] Parent pre-reserved DNS/HTTPS probe sockets with exact generation leases, fail-closed reservation exhaustion and unregister-before-release.
- [ ] Main/runtime/broker/Activity/monitor/heartbeat integration; same Android Network with fresh WG generation; nonblocking invalidation and drain outside session monitors; final commit/revocation transaction.
- [ ] Hidden Home retention and explicit Browser/Tor/Private App destination selection without destroying authorised contexts.
- [ ] Android verification of navigation draft and new transport, independent raw packet validation, exact-source security approval, original-key54→55 upgrade/restart, signing and one APK.

Navigation work is separately scoped: Primary explicit ordinary/app owners and ordinary-only encrypted metadata; stale focus and asynchronous restore guarded independently of filtered disk metadata. Hidden local tab selection is partitioned, but full Hidden Home retention depends on the containment integration and is not complete. Local app and Android test compilation now pass with the verified SDK/NDK/Gradle/JDK and normal trusted runtime proxy. The targeted encrypted browser-session JVM suite passes3/3. New Android instrumentation cases have not executed. Navigation source review closed the asynchronous restore and ownerless legacy metadata findings. No live Google transaction is claimed.

Immersive Private Apps is explicitly deferred as the first planned1.7.11 UI task in `../PRIVATE_APPS_NEXT_UI.md`; no new branch/version or presentation overhaul starts here. Preserve all existing UI and encryption.

No new CI is justified until coherent transport integration and host/source review are complete. Production/native/build changes invalidate earlier protected-tree reuse; passing old eight cases remain historical and cannot approve changed transport. All native probes and complete packet assertions stay mandatory. A development checkpoint marked `[skip ci]` is preservation, not a green release.
