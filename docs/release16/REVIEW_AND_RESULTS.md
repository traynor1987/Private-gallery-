# Private Gallery 1.6 — implementation and acceptance evidence

**NO-GO for production.** VPN + Tor is unavailable, physical routing acceptance is outstanding, and corrected Android tests plus upgrade/signing gates remain pending. No merge into main or operation on owner data has occurred.

## Recovery and source identity

- Authoritative latest V1.5 source: `34a107b88072c54f39ac5c5b497f1c1df36bb3f0`.
- Previously signed V1.5 APK code source: `a46288059fd7dc8d094798dd67d4ad87af5ee6a8`.
- Development branch: `release/1.6-hidden-tor`. Each checkpoint is published without force and checked by independently fetching the remote reference, then matching the full commit/tree locally.
- Current recovery state and exact next step: `V1_6_RECOVERY.md`. Its SHA names the preceding verified commit to avoid a self-referential hash.
- Main, V1.5 release, V2 recovery branches and existing PRs are preserved. V2 Task 3B has not been resumed.

## Implemented scope

Separate authenticated Hidden Browser; supported WebView multi-profile isolation; compact address/navigation/tab/settings controls; three selectable connection preferences; session-only browser state; encrypted image/video downloads through the existing Hidden repository; navigation blocking during connection changes; owner-fenced asynchronous VPN preparation; lock/background revocation; connection/retry presentation. Primary retains its existing WireGuard browser.

Ordinary Settings no longer has a Debug / Acceptance card. About's version requires seven taps followed by fresh owner authentication. Diagnostics access is transient and cannot unlock Hidden or expose its diagnostics without Hidden authority.

This is a restricted browser: JavaScript, forms, embedded frames, workers, HTTP redirects and external schemes are blocked. Production cleartext policy remains in effect: HTTPS sites are supported; plain HTTP onion sites are not. Browser tabs close when leaving Browser; the authorised Hidden Vault session follows existing auto-lock settings. Many modern sites will not work. These restrictions are explicit limitations, not claims of Tor Browser equivalence.

## Tor and routing

Guardian Project `info.guardianproject:tor-android:0.4.9.14`, publisher Maven snapshot `5fdb8dbcfe98d1b7e027e2f99bc086709870b005`, release source `0340ba1609cb73e663e202a316d083803e399db4`. Native component licence texts are retained in source and ordinary in-app notices. Build gates inventory the resolved AAR, packaged ABI libraries and their SHA256 values; this is not a reproducible-native-build claim.

| Mode | Implemented routing | Availability and evidence limit |
| --- | --- | --- |
| Tor Only (default) | Native Tor in a private bound process; native HTTP client uses SOCKS with unresolved hostnames; WebView networking stays disabled | No WireGuard prerequisite. Readiness requires a Tor circuit. Physical DNS/packet verification remains outstanding. |
| VPN + Tor | No transport is started | Unavailable: native Tor outbound routing through the intended WireGuard Network has not been enforced and verified across VPN failure. Never falls back. |
| VPN Only | Existing WireGuard engine plus exact app-owned VPN Network for sockets and DNS | Requires supported Android API and verified Network ownership. No Tor anonymity; physical route verification outstanding. |

Android's shared VPN infrastructure is not represented as two independent simultaneous vault tunnels. Hidden does not stop a shared WireGuard connection merely because its browser closes. Incompatible browser requests and views are revoked before mode/context changes.

## Verified versus unverified

Source review and policy tests support explicit mode gating, no fallback, URL/scheme rejection, transient diagnostics entry and generation-fenced connection state. Instrumentation distinguishes deterministic local SOCKS fixtures from real native Tor bootstrap. Neither proves absence of DNS leaks on the Fold.

Still requires controlled real-device packet observation: Tor exit routing, DNS behaviour, VPN route ownership during failure, background/lock traffic cessation, and rapid network/context transitions. WebView profile separation is Android-private storage isolation, not newly encrypted browser storage or a forensic deletion guarantee. Native cookies are memory-only; this restricted client does not implement full browser SameSite/third-party cookie semantics. Tor fingerprinting defenses are not those of official Tor Browser.

## Automated evidence

- Local Hidden browser/diagnostics policy harness: 41 checks passed.
- Downloaded CI631 XML reports independently confirm **324 JVM tests, zero failures/errors/skips**. Exact resolved Tor AAR/native library hashes are in `tor-distribution-ci631.json`.
- Existing bounded Hidden storage harness: 373 checks passed, including isolation/revocation/corruption/durability and streamed-media fixtures.
- Early diagnostics-only CI617: 237 Android instrumentation tests passed; this predates the Hidden browser and does not certify it.
- CI629 at `f91caccc5c174284de96c29d35395f59e5aa7330`: application, JVM tests and Android test sources compiled; JVM test task passed; debug and test APKs assembled. Lint blocked on one legacy Activity back override. That duplicate override was removed in favour of existing gesture-aware destination handlers.
- CI631 at `c8adfb717375a8864539e17271a9c4149009678f`: compilation, JVM tests, lintDebug, debug/test APK assembly, Tor native ABI/notices inventory and TEST ONLY package/signature checks passed. Focused emulator preflight finished: 11 executed, 8 passed, 3 failed, none skipped. Real native Tor bootstrap/shutdown passed. Two failures exposed Android's prohibition on deleting loaded profile containers; another exposed repeated EOF handling in encrypted downloads. Corrections use supported profile-scoped browsing-data clearing (WebKit1.13), retain container deletion for a later process, and preserve repeatable EOF while still checking authorisation. Corrected tests passed in CI633.
- CI633 at `083aca4f7b18cab396732fd6901897ba70a979e3`: all11 focused Android tests passed, none skipped. The manual process fixture then stopped because AGP had removed the test APK; the script now explicitly reinstalls the same TEST ONLY artifacts before those phases.
- Added a two-process synthetic cookie fixture to verify deferred Hidden container deletion after an actual force-stop while retaining Primary cookies. It asserts distinct process IDs and uses the production cleanup helper. Execution pending.
- CI632 at `bcc0542db328a4803bf6e43e669411c8c842b735`: focused/full Android stage and debug-package synthetic1.5→1.6 upgrade passed; original permanent signer probe passed. Production release build is running.
- Latest-source full regression, process-restart fixture, production signature verification and production-package synthetic upgrade: pending. Final results must replace this pending entry before any claim of successful acceptance gates.

## Independent review

Independent source reviewer identified and the implementation corrected: request admission racing close; stale asynchronous VPN owner callbacks; Hidden authority captured before old composition disposal; readiness checking a generic VPN instead of the pinned Network; and uncaught encrypted VPN profile-load failure. The last fix includes key wiping and corrupt-profile tests. Reviewer found no concrete upgrade integrity defect in the fixture and requested explicit Hidden timeout preservation, now seeded/asserted.

Follow-up review of `bcc0542db328a4803bf6e43e669411c8c842b735` found no new critical/important issue in EOF and profile-cleanup corrections. It confirmed that authority remains checked before repeated EOF, cleanup targets only Hidden, and profile feature requirements precede transport startup. Cleanup failures are caught, so requested cleanup and deferred container deletion are best-effort.

Review was source-only; the reviewer did not run Android or packet tests. Its NO-GO recommendation remains appropriate until runtime and physical evidence is available.

## APK, signing and upgrade

Expected production identity: `uk.co.traynor.privategallery`, versionCode **30** (V1.5:29), versionName **1.6**. Required unchanged signing certificate SHA256: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.

No production-key 1.6 APK is claimed yet. Signing uses the existing CI key only after mandatory automated gates. Separate TEST ONLY artifacts use the `.hiddenprototype` package and do not establish production upgrade compatibility. The upgrade fixture uses synthetic data on a disposable emulator, verifies both independent PINs and encrypted media, then repeats verification after process restart.

## Owner acceptance and remaining blockers

Follow `OWNER_ACCEPTANCE.md` on the Galaxy Z Fold only with an explicitly identified, signature-verified candidate. Record APK/source hashes, certificate, device/Android/WebView versions, network and date. Do not uninstall or clear application data. The checklist covers all requested media/navigation/diagnostics checks and controlled gateway/WireGuard packet captures.

VPN + Tor remains a release-scope blocker. Android CI, signature/upgrade evidence and physical acceptance are additional pending gates. Passing mocked SOCKS tests, a VPN icon or an exit-IP website must never substitute for route verification. Recommendation: **NO-GO for the requested complete three-mode production release**.
