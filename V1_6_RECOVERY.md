# Private Gallery 1.6 recovery

IN DEVELOPMENT — NO-GO for production. Owner authorised implementation, continuous backups, CI and original-key candidate signing. No merge or owner-data operations.

## Independently verified remote checkpoint

Branch `release/1.6-hidden-tor`, SHA `49b17007faba7db334f83d0502623a0b224aa0f5`.
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

CI629 compiled app/JVM/Android test sources, passed JVM tests and assembled debug/test APKs. Lint failed one legacy MainActivity.onBackPressed override (GestureBackNavigation); removed this duplicate legacy path because the production BrowserV2 destination already owns AndroidX Compose BackHandlers for history, fullscreen and gallery fallback. Existing BrowserPolishTest dispatcher regression retained. Full Android runtime suite and release signing remain pending; next inspect the new CI gates.

CI631 at c8adfb717375a8864539e17271a9c4149009678f passed compilation, JVM tests, lintDebug, debug/test APK assembly, Tor native ABI/notices inventory and TEST ONLY package/signature checks. Android emulator instrumentation is running; no runtime pass or production-key APK is claimed. Added consolidated REVIEW_AND_RESULTS.md. Next inspect focused browser instrumentation, then full regression and synthetic upgrade/signing gates.

CI631 focused Android preflight: 11 executed, 8 passed, 3 failed, 0 skipped. Real Tor bootstrap/private-process shutdown passed, along with SOCKS/revocation/diagnostics/Hidden routing/profile-loader checks. Failures: loaded-profile deletion is forbidden by Android even after WebView.destroy (two tests), and repeated EOF reads after closing an HTTP response broke encrypted download import. Fixed stream EOF semantics while retaining authority checks; use WebKit1.13 supported profile-scoped deleteBrowsingData with callback, require its feature before browsing, and delete retired containers only in a later process. Updated instrumentation to assert Hidden cookies cleared and Primary cookies retained. Next run new Android preflight/full regression; release signing remains blocked until gates pass.

Follow-up independent read-only review of bcc0542 found no new critical/important defect in EOF/profile-cleanup changes. Cleanup is best-effort and asynchronous, not guaranteed erasure. Downloaded CI631 reports independently confirm324 JVM tests, zero failures/errors/skips, and real Tor bootstrap PASS among8/11 preflight passes. Preserved exact resolved Tor AAR/native hashes in docs/release16/tor-distribution-ci631.json. CI632 corrected source and both test sets compiled; JVM/lint/assembly are running. Next inspect corrected Android preflight before advancing signing gates.

Added the requested real process-restart isolation fixture: seed synthetic persistent Primary/Hidden cookies, force-stop only the TEST ONLY emulator app, verify a new PID, remove retired Hidden profile containers using the production cleanup helper before loading them, assert fresh Hidden cookies absent and Primary cookie retained. Existing ordinary isolation tests remain unchanged when no fixture phase is supplied. Test is pending the next CI; no process-death pass is claimed. CI632 has passed build gates and is still in Android tests/upgrade; no signed1.6 artifact yet. Next inspect both runs and complete remaining gates.

CI633: all11 focused Android browser tests PASS, zero failures/skips, including corrected encrypted downloads/profile cleanup and real Tor bootstrap. Two-process fixture did not execute: AGP removed instrumentation APK after connected tests. Updated script to reinstall the exact built TEST ONLY app/test APKs and verify registered instrumentation before manual phases. Application behavior unchanged. CI632 still running full regression/upgrade. Next run corrected manual process fixture and finish all gates; production signing remains pending.

CI632 at bcc0542db328a4803bf6e43e669411c8c842b735 completed the focused/full Android regression stage and debug-package synthetic V1.5→V1.6 upgrade successfully. Original permanent signing identity probe PASS. Production release build/verification is running; production-package signed upgrade is not yet verified. CI634 at cc67716f96819a7227a93e7c8584aa4992a36c33 is running the new process fixture plus full gates. Next inspect its fixture result and both release/signing outcomes; deliver only an exact-source, hash/certificate-verified artifact.

CI634 again passed11/11 focused Android tests. Manual restart fixture seed passed but verify failed because the seeded profile container was absent after immediate force-stop; cookie flush does not guarantee the separate Chromium profile registry has persisted. Added test-only bounded read-only observation of the synthetic registry name on disk before forcing death; retained every cross-process assertion. Production cleanup still uses supported APIs only. CI632 release lint/signature/package/native inventory passed and its production-package synthetic upgrade is running. Next inspect corrected fixture and production upgrade; no physical acceptance claimed.

CI632 COMPLETE SUCCESS:324 JVM tests,248 full Android tests,11 focused tests; debug and permanent-key production-package synthetic1.5→1.6 upgrades PASS including restart. Release APK source bcc0542db328a4803bf6e43e669411c8c842b735, SHA2562346ca27875fb47180acb1ad9b1717270d666f91a3e6d00827352136269d6f07, package uk.co.traynor.privategallery, code30/name1.6, original certificate94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. Downloaded artifact hash matches CI; evidence preserved under docs/release16/evidence-ci632. This earlier source predates the extracted cleanup helper/process fixture. Latest-source CI635 remains running; inspect process fixture and finish its gates before final handoff. Physical routing/VPN+Tor remain NO-GO.

Downloaded CI632 XML reports independently confirm324 JVM,248 full Android and11 focused Android tests, all zero failures/errors/skips. Exact counts preserved in evidence-ci632/test-counts.json. CI635 remains in its Android/regression/upgrade stage; await the latest-source result and then complete final APK handoff. No source changes are pending.
