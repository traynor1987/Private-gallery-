# Private Gallery 1.6 recovery

IN DEVELOPMENT — NO-GO for production. Owner authorised implementation, continuous backups, CI and original-key candidate signing. No merge or owner-data operations.

## Independently verified remote checkpoint

Branch `release/1.6-hidden-tor`, SHA `f91caccc5c174284de96c29d35395f59e5aa7330`.
This is the preceding verified commit. The update's own commit is identified by Git history and independently checked after publication, avoiding self-reference.

## Verified baseline and preserved work

V1.5 latest source `34a107b88072c54f39ac5c5b497f1c1df36bb3f0`; V1.5 signed APK code source `a46288059fd7dc8d094798dd67d4ad87af5ee6a8`. Initial 1.6 baseline branch was independently verified before development. Separate checkout preserves the clean recovered1.5 checkout. Main, V1.5, V2 recovery branches and PRs untouched. No force push.

Package `uk.co.traynor.privategallery`; baseline code29, candidate code30/name1.6. Original certificate SHA256 `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`. Prior evidence in `docs/release15/HIDDEN_RESTORE_CANDIDATE.md`.

## Implemented source, pending Android validation

- Separate authenticated Hidden Browser using unique supported WebView profiles, native HTTP interception and permanently blocked WebView networking. JS/forms/frames/workers/HTTP redirects disabled. Eight session-only tabs; no session restoration.
- Tor Only default: Guardian Project Tor0.4.9.14 in private bound process; explicit SOCKS, no local origin DNS, circuit readiness gating and private-process shutdown.
- VPN Only: existing WireGuard engine plus exact app-owned Android VPN Network for sockets/DNS. API30+ required.
- VPN+Tor deliberately unavailable: native Tor socket pinning through WG across revocation has not been proved. No fallback.
- Mode switching/route exit/background closes requests, tabs and Tor process; Hidden Vault auth retains its existing timeout. Encrypted image/video downloads stream directly into HiddenRepository.
- Seven About-version taps plus fresh owner authentication open existing diagnostics. No ordinary Debug card. Hidden playback diagnostics require Hidden context/auth.
- Independent review's request-admission race, asynchronous VPN-owner race, stale composition authority and pinned-network identity findings corrected in source; runtime tests pending.
- Publisher Maven repository pinned to5fdb8dbcfe98d1b7e027e2f99bc086709870b005; Tor source release tag points to0340ba1609cb73e663e202a316d083803e399db4.
- Added protocol-fixture SOCKS tests, admission-close race, profile-cookie/render isolation, encrypted download/revocation, actual Hidden/Browser transition and diagnostics UI tests.
- Upgrade fixture now starts at exact V1.5 baseline and verifies code29->30, both PINs, both vaults' encrypted bytes/media/favourites, Primary settings/VPN/AI credentials, and process restart. Disposable emulator only.

## Tests and build status

Passed host:41 browser/diagnostics policy checks;373 Hidden foundation checks plus isolation/revocation/record-corruption and20/100/500MiB encrypted stream tests;3 browser helper tests; no-secret and supported-dependency scripts.

CI617 (earlier diagnostics checkpoint f337d06f2fa89ca33d64fd14e22a7a870d96d706) PASSED compilation, lint, JVM and237 Android instrumentation tests, zero skipped/failed. This does not verify the later Hidden browser implementation. Browser CI618 failed AAR metadata because Tor requires newer compile SDK. CI620 failed wrong SDK package name. CI621 installed37.0 successfully then needed AGP9 resValues explicitly enabled. CI622 revealed the dependency requires37.1 (old AGP reported only37). Corrected to compile37.1/AGP9.3.3/Gradle9.5.0. Target36/min26 unchanged; no dependency metadata bypass or Tor downgrade. Latest Android compilation and all new instrumentation remain UNVERIFIED.

Local Gradle cannot download dependencies: network unreachable and no SDK/cache. Android testing uses authorised GitHub Actions. No1.6 APK is claimed built or signed. Original signer checks and candidate upload are gated after mandatory tests and synthetic upgrade; no replacement key.

## Remaining blockers and limits

- Compile/lint/instrumentation and synthetic signed upgrade must pass.
- VPN+Tor unavailable; native socket enforcement not implemented.
- Real Tor bootstrap, VPN routing, DNS/packet captures, teardown/background races and Fold UI require physical acceptance.
- Restricted browser is not Tor Browser; no anti-fingerprinting claim, no JavaScript/forms/redirect compatibility. Cookies do not implement full SameSite/third-party-context policy.
- Browser profile files are Android-private, not a new encrypted DB; stale profiles are deleted on next authenticated entry, never reused. No forensic erasure claim.
- Complete native licensing/composition and binary provenance review pending.

## Exact next step

Publish this coherent WIP checkpoint and verify full remote SHA. Inspect the next Android CI; resolve compilation/runtime failures. Execute upgrade/signature gates only after tests. Record results, update recovery and push before ending. Owner checklist: `docs/release16/OWNER_ACCEPTANCE.md`; architecture: `docs/release16/TOR_AND_ROUTING.md`.

## Native notices and fixture isolation checkpoint

Added exact upstream Tor/OpenSSL/libevent/zlib/zstd/jtorctl licence texts to ordinary in-app notices and source documentation. Added build-time AAR/native ABI/hash inventory (syntax checked, artifact execution pending). Local HTTP fixture exception is restricted to reserved .invalid domains in the TEST ONLY manifest; production cleartext policy remains unchanged (HTTPS origins required; plain HTTP onion sites unsupported). CI623 currently compiling37.1 source; no Android pass claimed.

## First full source compilation findings

CI623 passed SDK37.1 installation, dependency metadata, manifest and native packaging, then reported3 source type mismatches: Profile uses framework ServiceWorkerClient, and OkHttp4 Dns is not a Kotlin SAM. Corrected to explicit framework client and Dns implementations. No behavior weakening. Android compile and new tests still pending.

Added page-console suppression and explicit Tor SafeLogging. CI617 final237/237 pass applies only to early diagnostics source; CI625 current browser source still running. Physical logging/packet behaviour remains an acceptance gate.

## Final focused independent source review

Reviewer confirmed prior four source corrections and found one remaining important issue: encrypted VPN profile read exceptions escaped a coroutine. Added a bounded loader consuming/wiping the copied key, generic failed-connection handling fenced to current owner, and corrupt/successful profile tests. No transport starts on failure. Reviewer found no concrete upgrade integrity flaw; added explicit Hidden timeout preference seed/assert to close a noted evidence gap. Review is source-only, not runtime assurance. Next run current Android tests/signing gates.

Added a focused new-browser/diagnostics instrumentation preflight before the full existing regression suite, preserving its results separately. This shortens failure feedback without removing any full-suite/signing gates. Current browser compile/lint still pending CI; no1.6 APK claimed.

Added real Tor bootstrap/private-process shutdown instrumentation gate (90s readiness deadline, no fallback). This distinguishes actual native-library/bootstrap execution from local mocked SOCKS tests; it is not DNS/packet routing proof. The gate is pending execution and will block candidate generation if Tor cannot start in CI.

CI625 compiled the application source successfully, then failed existing JVM test compilation under the newer toolchain: removed createTempDir API and JSONObject.keySet unavailable on Android compile classpath. Replaced temporary-directory creation with Files.createTempDirectory and equivalent keys iterator set assertions; no expectations removed. Compile both test source sets before expensive lint. Android tests/signing remain pending.

Current CI has passed compilation of both JVM and Android test source sets and moved to JVM tests/lint/APK assembly. Added explicit Reconnecting presentation for retries and a Hidden-specific ordinary Browser settings summary, plus JVM/lint report retention. No tested security property inferred from compilation.
