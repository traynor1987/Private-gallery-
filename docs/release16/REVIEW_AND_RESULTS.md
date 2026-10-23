# Code32 focused repair follow-up

CI649 passed; see `HTTP403_REPAIR.md` and `evidence-ci649/` for exact source,333/46/268 test totals, real Tor HTTPS/onion observations, original certificate and code31→32 synthetic upgrades. Source review corrected canonical CSS base, stale navigation and remote CSS error-status handling; follow-up found no critical/important source-confirmed blocker. No live WireGuard or physical Fold routing/DNS acceptance claimed. Production NO-GO pending owner verification.

Historical five-fix review follows.

# Private Gallery 1.6 — implementation and acceptance evidence

**NO-GO for the complete three-mode production release.** An original-key signed restricted candidate has passed all configured automated gates. VPN + Tor remains unavailable and physical Galaxy Z Fold acceptance is outstanding. No merge into main or operation on owner data has occurred.

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
- Added a two-process synthetic cookie fixture to verify deferred Hidden container deletion after an actual force-stop while retaining Primary cookies. It asserts distinct process IDs and uses the production cleanup helper. CI634 reached the restart verification but its seeded profile was absent after immediate force-stop. The fixture now waits for its synthetic profile registry entry to reach disk before killing the process; all cross-process cleanup/Primary-preservation assertions remain. Corrected execution passed in CI635, including distinct-PID verification, retired Hidden container removal and Primary cookie preservation.
- CI632 at `bcc0542db328a4803bf6e43e669411c8c842b735`: focused/full Android stage and debug-package synthetic1.5→1.6 upgrade passed; original permanent signer probe passed. Completed successfully:324 JVM tests,248 full Android tests and11 focused tests; debug and production-package in-place upgrades including restart passed. Release lint/build, APK package/version/certificate verification and native inventory passed. Downloaded APK SHA256 matched `2346ca27875fb47180acb1ad9b1717270d666f91a3e6d00827352136269d6f07`; permanent signing certificate matched the required value. Evidence is preserved under `evidence-ci632/`.
- **CI635 SUCCESS** at `a4cc3eecd5a2a1cf744ca0c23157d89254165632`: 324 JVM, 248 full Android and 11 focused Android tests, all with zero failures/errors/skips, independently counted from downloaded XML. Separate manual process seed/verify phases each passed one test, including distinct-PID verification, deferred Hidden container removal and Primary cookie preservation. Real native Tor bootstrap/shutdown passed; SOCKS routing tests use deterministic local fixtures. Both debug and original-key production-package synthetic V1.5→V1.6 upgrades passed, including verification after restart. Compilation, lintDebug/lintRelease, release build, signer, package/version and native inventory checks passed. Downloaded APK hash matches the CI evidence. Exact records are in `evidence-ci635/`. [Completed run](https://github.com/traynor1987/Private-gallery-/actions/runs/39840016827). Counts overlap between focused and full suites and must not be presented as distinct total test cases.

## Independent review

Independent source reviewer identified and the implementation corrected: request admission racing close; stale asynchronous VPN owner callbacks; Hidden authority captured before old composition disposal; readiness checking a generic VPN instead of the pinned Network; and uncaught encrypted VPN profile-load failure. The last fix includes key wiping and corrupt-profile tests. Reviewer found no concrete upgrade integrity defect in the fixture and requested explicit Hidden timeout preservation, now seeded/asserted.

Follow-up review of `bcc0542db328a4803bf6e43e669411c8c842b735` found no new critical/important issue in EOF and profile-cleanup corrections. It confirmed that authority remains checked before repeated EOF, cleanup targets only Hidden, and profile feature requirements precede transport startup. Cleanup failures are caught, so requested cleanup and deferred container deletion are best-effort.

Review was source-only; the reviewer did not run Android or packet tests. Runtime automated evidence is now available. The NO-GO recommendation remains appropriate because VPN + Tor and physical routing acceptance are unresolved.

## APK, signing and upgrade

Expected production identity: `uk.co.traynor.privategallery`, versionCode **30** (V1.5:29), versionName **1.6**. Required unchanged signing certificate SHA256: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.

Delivered restricted candidate: `Private-Gallery-1.6-restricted-candidate-a4cc3ee.apk` (61,094,625 bytes).

- APK code source: `a4cc3eecd5a2a1cf744ca0c23157d89254165632`.
- APK SHA256: `40d980568205719e9371459149653ff0939d9a37262d9cd2faf8b7e2905929e6`.
- Package/version verified: production package above, code30/name1.6, not debuggable.
- APK Signature Scheme v2 verified; original certificate above matches. No replacement signing key.
- CI635 production artifact ID: `12454068938`; diagnostic artifact ID: `12455715467` (Actions retention is limited; text evidence is committed).
- Downloaded bytes independently match the CI hash. Signature/package evidence comes from Android build tools in CI; this workspace has no Android SDK for a second local apksigner execution.

The production-package fixture starts at the exact V1.5 baseline with code29, installs code30 in place using the original key, verifies independent PINs, Primary and Hidden encrypted media/collections/settings/VPN and timeout settings, and repeats verification after process restart. It uses synthetic data on a disposable emulator; it is not an owner-device upgrade test. Separate TEST ONLY artifacts use the `.hiddenprototype` package and are not the delivered APK.

## Owner acceptance and remaining blockers

Follow `OWNER_ACCEPTANCE.md` on the Galaxy Z Fold only with an explicitly identified, signature-verified candidate. Record APK/source hashes, certificate, device/Android/WebView versions, network and date. Do not uninstall or clear application data. The checklist covers all requested media/navigation/diagnostics checks and controlled gateway/WireGuard packet captures.

VPN + Tor remains a release-scope blocker. Android CI and signature/synthetic-upgrade gates passed; physical acceptance remains pending. Passing mocked SOCKS tests, a VPN icon or an exit-IP website must never substitute for route verification. Recommendation: **NO-GO for the requested complete three-mode production release**.


## Owner correction sprint source review — 22 October 2026

Code snapshot: `c17e0357b230d469be0d684ca46b3ed79f55df1d` (version31).
Independent source reviewer identified four concrete issues in the first integration and confirmed their correction: Hidden plaintext temporary video files, first/last-only video sample inspection, Combine consent ordering, and unwired Hidden progress. Hidden validation now executes over existing encrypted staging before deduplication and publication; all extracted audio/video samples are traversed with cancellation, bounded memory and encrypted-sample rejection. This does not claim every frame is decoded. Old temporary validation paths are cleaned at startup and lock. Consent is bound to provider/reference and approved capability; byte progress comes from actual reads.

Reviewer also checked fresh foreground/browser authority, VPN permission lifecycle, stale Primary profile-load fencing and already-loaded WireGuard configuration reuse after Primary timeout. No new critical or important source-confirmed issue found in the follow-up. Review was source-only; Android CI and physical Fold route/DNS verification remain separate gates. No new candidate is yet approved.


CI643 expanded preflight exposed two further acceptance defects: coalesced stop/start state could reuse a closed browser runtime, and Android accepted the first half of the test MP4 because its complete moov metadata remained while mdat extended past EOF. Foreground now increments a monotonic generation. A bounded ISO BMFF top-level box checker precedes Android parsing. Host harness passed12,167 checks including every truncation of the12,139-byte real fixture. The original two failing Android tests were retained unchanged. Follow-up source review found no critical/important defect. Fragmented MP4/legacy MOV are deliberately unsupported; neither structural nor sample checks claim full frame decoding. Code snapshot:dd877ae8869d556bd25aab62d6707213913f679e. CI644 remains the next execution gate.

## CI644 correction acceptance verification

All mandatory automated gates passed on source `dd877ae8869d556bd25aab62d6707213913f679e`. Independently downloaded XML:333 JVM,258 full Android,36 focused preflight, zero failures/errors/skips; focused overlaps full. Unchanged foreground-runtime and truncated-MP4 regressions passed. Separate process restart isolation and both debug/original-key production-package synthetic code30→31 upgrade checks passed. Host MP4 parser12167 checks passed, including all12139 fixture truncations. Debug/release lint, source-secret scan, package/signature and native inventory passed.

Downloaded APK61,127,393 bytes, SHA256 `1f5b5c42a646adce38a5edaa6f61dda381bbf5a149156bf07aa1828885c69a09`; code31, production package and original certificate confirmed. Evidence in `evidence-ci644/`. Source review found no remaining critical/important source-confirmed issue after corrections, but no physical network or Fold UI verification was performed. No paid live Seedream generation. GO controlled owner acceptance only; production NO-GO pending owner and routing/DNS checks.
