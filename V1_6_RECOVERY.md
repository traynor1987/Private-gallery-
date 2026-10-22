# Private Gallery 1.6 recovery

**Code31 physical Hidden browsing acceptance FAILED. Code32 focused HTTP403 repair implemented; Android/signing gates and physical acceptance pending. Production NO-GO.**

## Code31 physical HTTP403 failure — focused repair active

Recovered authoritative remote `c2d2de7a4af1c790bad548a1378005165e762d9b`, clean fresh clone after workspace pruning. No active implementation/build process found. Code31 source/evidence retained below. Owner physical acceptance FAIL: Tor/VPN Connected but BBC/onion receive synthetic privacy-policy403. Exact message originates in HiddenHttpTransport.blocked(); request() calls it for every3xx, unsupported method and every exception. Readiness probes only example.com200, bypassing browser redirect/render path. Release cleartext default also prevents HTTP onion sites. No Tor/VPN routing failure inferred from this evidence.

CI645 test-first RED reproduced precisely: relative/cross-host redirect expected200 got403; refused transport expected503 got403. Real website403 body test passed. Original code31 otherwise compiled and passed prior host/JVM gates. This is expected failing reproduction, not an acceptance build.

Implemented code32: manual301/302/303/307/308 chain through same native client,10-hop/loop/downgrade guards and per-hop mode/URL checks; main navigation canonical URL callback fenced by intended URL, immutable navigation identity and20-hop cumulative budget. CSS redirects return a canonical @import wrapper so relative assets use correct base; redirected SVG/HTML/XML subresources explicitly501. Safe headers only; native session cookies stay isolated and host-matched. Local451 policy/405 unsupported method/501 unsupported redirect/503 transport distinct from real website403; remote result marker stripped. WebView native networking/scripts remain disabled. Onion v3 names allowed only Tor before DNS; release network-security config permits HTTP only onion suffix. VPN DNS also explicitly rejects onion. No certificate bypass. VPN+Tor unavailable.

Focused deterministic SOCKS/actual-WebView tests cover redirected document+CSS+relative assets, cookies across hosts, remote403, typed failures, blocked private redirect, loop, safe headers, stale main requests. Real packaged Tor test now requests BBC HTTPS and Tor Project-published BBC onion; results must be actual upstream responses, not mocked success. No live WireGuard profile/device or physical packet test is available in this environment.

Local checks passed:53 browser policy,373 storage foundation plus20/100/500MiB streaming,12167 MP4 checks,3 Node video helper tests, no-secret scan. Local Android build unavailable (Gradle distribution/network/SDK); CI is mandatory. Source review caught stylesheet-base and stale-navigation concerns; corrected with focused regressions, follow-up found no remaining critical/important source-confirmed blocker; CSS wrapper limited to successful responses to preserve remote errors. Exact same-URL delayed callbacks lack a WebView navigation ID, so perfect provenance is not claimed. Code32 upgrade baseline is exact code31 source dd877ae8869d556bd25aab62d6707213913f679e. Original production signer/package unchanged. No data migration or owner-media operation.

## Owner acceptance corrections — 22 October 2026

Recovered remote baseline `9ee510eb23de0ac75b05a87e6f4e68bb959348b1`; signed code30 APK source `a4cc3eecd5a2a1cf744ca0c23157d89254165632`. CI635 independently re-read: success, retained artifacts valid. Owner confirms in-place upgrade and real encrypted Primary/Hidden media pass. Owner reports both Hidden browser modes fail; previous emulator results do not supersede this failure.

Latest verified remote code checkpoint: `dd877ae8869d556bd25aab62d6707213913f679e` (all five corrections plus reviewed security fixes, version31). Earlier remote corrections: transfer `21f7d0594f662d68f6235a2f6ee0da9c41c071fa`; Combine `9dd1ee0f6f317f9744307ce7be62abf4132da8e5`; video `09aebea0f989a377301d80ac20ce2427c2d5fae8`; independent PIN `2c6843898e4ea0f5fa45ff9b7b4af4ee21d13914`.

- Transfer: removed 100-selection cap, Copy/Move confirmations, real item progress modal, cooperative cancel/duplicate guard/partial counts; added 120-photo and 250-selection tests.
- Authentication: neutral lock title long-press arms Hidden-only PIN checking for one attempt; Primary PIN remains Primary-only. Independent cold entry/navigation and neutral expired-session reset implemented, with Compose tests.
- Combine: actual two-image Seedream4.5 `image_input` array, exact typed prompt, same-vault reference selection/previews, explicit both-image disclosure, existing generation and encrypted-copy save. No paid live request performed. Review caught consent ordering; now bound to provider/reference and approved capability.
- Video: manifest retained ahead of segments, standalone segment rejection, expected duration comparison, complete-body/finite-stream checks. Hidden responses reject partial206/segment MIME. Hidden progress wired to actual read counts. Sequential sample traversal now checks every extracted audio/video sample (not full frame decoding). Hidden validation uses authenticated encrypted staging before duplicate lookup/publication; no plaintext Hidden staging file.
- Browser: fresh profile/runtime on foreground, permission response independent of retired runtime, stale Primary preparation fenced, app-owned VPN guard preserved, real HTTPS readiness via selected client, numeric native Tor bootstrap, compact controls/settings/shared navigation, explicit empty-tab landing and page/renderer errors. VPN+Tor remains unavailable. Added foreground and real-Tor HTTPS test coverage.
- Hidden VPN can reuse an already approved in-memory WireGuard transport configuration after Primary timeout during Android permission. A cold direct-Hidden entry without a loaded/decryptable profile remains blocked with explicit setup guidance; Primary media stays locked.
- Version31 targets a synthetic in-place code30→31 upgrade from exact signed source `a4cc3eecd5a2a1cf744ca0c23157d89254165632`; original package/certificate requirements unchanged.
- Local checks passed:45 browser policy checks,373 foundation checks plus streaming/storage harness,3 Node video helper tests, diff and tracked-secret scan. Local Android build blocked before compilation because Gradle distribution unavailable/no SDK. CI636–640 failed compile on missing DisposableEffect import; corrected. CI641/642 compilation, JVM tests, lint and APK assembly passed. CI642 preflight failed 1/11: HiddenHostRoutingTest could not find Vault navigation. Root cause was the shared shell still hiding Hidden navigation after duplicate controls were removed. Corrected the shell condition; CI643 then passed the original navigation case but exposed two new acceptance failures: rapid stop/start retained a closed runtime, and the platform media parser accepted a half-length MP4. Foreground now increments a monotonic generation even when Compose state updates coalesce. A strict MP4 box-boundary preflight now precedes platform parsing and rejects oversized/truncated boxes, malformed lengths and standalone fragments. Its host harness passed12167 checks, including every byte truncation of the real fixture. Both failing Android tests remain unchanged for the next CI. Follow-up source review cleared the lifecycle/parser corrections. CI644 passed all gates:333 JVM,258 full Android and36 focused Android tests, zero failures/errors/skips. Focused tests overlap full. Both unchanged failing cases passed. Debug and original-key production code30→31 synthetic upgrades passed before/after restart.
- Independent source review identified Hidden temporary-file cleanup, first/last-only sample validation, consent-ordering and unwired progress. All four corrected and follow-up source review found no remaining critical/important source-confirmed defect. This is not device verification.
- Normal git push lacks credentials. Checkpoints are published using connected GitHub tree/commit/ref APIs without force, each remote reference independently read back. Local task commits have different SHAs but the published trees are their exact source snapshots; GitHub is authoritative.
- Follow-up navigation correction preserves the shared Hidden bottom navigation; existing failing routing test is the regression. Direct Hidden phone moves now use independent Hidden authority without a Primary key; null-key Primary construction remains rejected. Source review found no critical/important issue. New acceptance cases are included in early Android preflight.
- No data format, signing key, owner-data or main changes. CI644 signed code31 APK is recommended solely for controlled owner acceptance. Physical Fold networking/DNS/upgrade acceptance remains required.

## Verified correction acceptance artifact

CI644: https://github.com/traynor1987/Private-gallery-/actions/runs/39854349633 — SUCCESS.
Source: `dd877ae8869d556bd25aab62d6707213913f679e`; later commits contain evidence only.
Recommended APK: `Private-Gallery-1.6-acceptance-code31-dd877ae.apk`,61,127,393 bytes.
SHA256: `1f5b5c42a646adce38a5edaa6f61dda381bbf5a149156bf07aa1828885c69a09`.
Package `uk.co.traynor.privategallery`, code31/name1.6, not debuggable. APK v2 signature verifies with original certificate `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`. Downloaded archive digests and APK hash independently match GitHub evidence. No replacement key.

Compilation, debug/release lint, test/build, native inventory, no-secret gate and permanent signer probe passed. Downloaded XML independently counted333 JVM,258 full Android and36 focused preflight tests, no failures/errors/skips; focused tests overlap full. Separate profile process seed/verify passed. Both debug and production-package synthetic code30→31 upgrades preserved independent PINs, both encrypted vaults/collections/settings/VPN after install and process restart. Real Tor bootstrap/HTTPS emulator check passed; deterministic transport cases and emulator checks do not prove physical Fold routing/DNS.

Evidence: `docs/release16/evidence-ci644/`; checklist: `docs/release16/OWNER_ACCEPTANCE_FIXES.md`. All five implementation areas corrected, with partial limits stated in checklist. No paid live Seedream generation or physical Fold test performed. VPN+Tor unavailable; Hidden segmented download unsupported; cold direct Hidden VPN requires an already approved loaded profile. GO for controlled in-place owner testing only; production NO-GO pending physical acceptance. No main merge.

**Historical candidate evidence follows; it does not verify the new corrections.**

**Restricted signed candidate delivered; NO-GO for the complete three-mode production release.** No merge into main or owner-data operation has occurred.

## Verified GitHub checkpoint

Branch: `release/1.6-hidden-tor`.
Latest independently verified preceding commit: `fcb13dbd6ef4ae8d2aa1749cb5e73251e4e6bd3d`.
The documentation update containing this file has its own commit in Git history, independently verified after publication. A document cannot embed its own commit hash. APK code source is separately fixed below; subsequent changes are evidence/documentation only.

Authoritative V1.5 baseline: `34a107b88072c54f39ac5c5b497f1c1df36bb3f0`.
Previously signed V1.5 APK source: `a46288059fd7dc8d094798dd67d4ad87af5ee6a8`.
Main, V1.5 release, V2 recovery branches and existing PRs preserved. No force push, branch deletion or V2 Task3B work.

## Completed implementation

- Separate authenticated Hidden Browser with supported unique WebView profiles, native HTTP interception and WebView networking always disabled; Primary keeps the existing WireGuard browser.
- Tor Only default: Guardian Tor Android0.4.9.14, private bound process, SOCKS remote names, circuit readiness and shutdown on revocation.
- VPN Only: existing WireGuard infrastructure with exact app-owned Android VPN Network for sockets and DNS; API30+ required.
- Three selectable preferences, persisted mode, explicit readiness/failure/retry states; incompatible requests/views revoked before switching. No fallback.
- Encrypted image/video downloads through HiddenRepository; no public download destination.
- Seven About-version taps followed by fresh owner authentication open existing diagnostics. No ordinary Debug card; Hidden diagnostics retain Hidden authentication.
- Corrected source-review findings: admitted-request/close race, stale VPN callbacks, authority capture, pinned-Network identity and encrypted VPN profile read failures.
- Fixed repeated EOF during encrypted import; supported profile-scoped cleanup plus deferred container removal after process restart.

## Partial features and limits

- **VPN + Tor is unavailable.** Native Tor outbound socket enforcement through WireGuard across failure is not implemented/verified. Selecting it starts no transport and no fallback.
- Restricted WebView: JavaScript, forms, frames, workers, redirects and unsafe schemes blocked. HTTPS only under production cleartext policy; plain HTTP onion sites unsupported. Not official Tor Browser fingerprinting protection.
- Browser tabs/history/cookies are session-only. Leaving Browser closes tabs; authenticated Hidden Vault session follows existing auto-lock settings. No insecure browsing-session restoration.
- Browser profile data is Android-private, not newly encrypted storage. Cleanup is asynchronous/best-effort, not forensic erasure. Native cookies lack complete SameSite/third-party browser semantics.
- Physical Fold layout, network interruption, DNS/packet leakage, lock/background traffic and real-device upgrade acceptance remain unverified.

## Final automated results

CI635: https://github.com/traynor1987/Private-gallery-/actions/runs/39840016827 — **SUCCESS**.
APK/tested source: `a4cc3eecd5a2a1cf744ca0c23157d89254165632`.
Downloaded XML independently counted: **324 JVM tests,248 full Android tests,11 focused Android tests; zero failures/errors/skips**. Focused cases overlap the full suite. Separate manual process seed/verify each passed one test, proving new PID, retired Hidden profile deletion and Primary cookie preservation in the emulator. Real native Tor bootstrap/shutdown passed; deterministic SOCKS fixtures are not real-device leak tests.

Compilation, debug/release lint, APK builds, original-key probe, package/version/certificate and Tor native inventory gates passed. Both debug and original-key production-package synthetic code29→30 upgrades passed, including restart verification of independent PINs and both vaults' encrypted data/settings. Host evidence:41 Hidden browser policy checks,373 Hidden storage checks and3 browser helper tests passed. Earlier CI failures were corrected; historical notes retained in `docs/release16/CHECKPOINT_HISTORY.md`.

Independent source review corrected the findings above; follow-up found no new critical/important defect in EOF/profile cleanup. Reviewer did not perform packet tests. Detailed results: `docs/release16/REVIEW_AND_RESULTS.md`; exact final evidence: `docs/release16/evidence-ci635/`.

## Build and signing

APK: `Private-Gallery-1.6-restricted-candidate-a4cc3ee.apk`,61,094,625 bytes.
SHA256: `40d980568205719e9371459149653ff0939d9a37262d9cd2faf8b7e2905929e6`.
Package: `uk.co.traynor.privategallery`; code30/name1.6 (baseline code29); not debuggable.
Original certificate SHA256: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.
APK Signature Scheme v2 verifies in CI. Downloaded bytes independently hash-match the signed artifact. Original key retained; no signing blocker. No owner-data upgrade claimed.

## Remaining tasks and exact next step

1. Owner performs the controlled Galaxy Z Fold checklist at `docs/release16/OWNER_ACCEPTANCE.md`, records device/WebView versions and packet evidence using synthetic sites, and upgrades in place without uninstalling or clearing data.
2. Record enabled-mode routing/failure/lock tests and all V1.5 media/navigation acceptance. Investigate concrete failures on this branch with verified GitHub checkpoints.
3. Before enabling VPN + Tor, implement enforceable native Tor socket routing through the intended WireGuard Network across revocation, independently review it and prove it with packet captures. Do not substitute a different route.
4. Full three-mode production recommendation remains NO-GO until the blocked mode and physical acceptance are resolved. No main merge without owner approval.
