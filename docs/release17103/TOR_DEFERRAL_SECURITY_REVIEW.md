# Code55 Tor deferral: scoped independent security review

Review date: 2026-11-03. Reviewed `/tmp/nova55-tor-deferral-review.diff` and actual sources in `/tmp/novaos55-shipping`. Source-only review; no source edits, CI, broad suites, or fresh Android test execution performed by this reviewer.

## Verdict

Tor resource admission is effectively disabled in the reviewed source. The strict no-fallback UI defect T1 was addressed in the scoped follow-up review below. This is not release security approval. Code55 remains blocked by the established VPN continuity escape C1 regardless of Tor deferral.

`NovaReleaseFeatures.TOR_ENABLED=false` is a main-source constant, independent of build type, preference, intent or saved route. `browserModeAllowed` permits only VPN_ONLY. Home launch checks `NovaNavigation.available`; Tor search results are skipped; settings mode selection excludes Tor; browser runtime rejects stale Tor selection before authority capture, profile creation or connection preparation. Broker construction and mode switch reject Tor. Tor HTTP transport, Tor proxy factory, Evidence Tor route/factory, connection start, isolation establishment and relay bridge creation have early guards. Both manifest services are disabled and unexported, dependency TorService is removed, and lifecycle guards prevent native initialization if a service is nevertheless invoked. `HiddenTorService.initialize` gates before its `super.onCreate()` native startup. No additional actual native Tor startup call site or component re-enabling code was found. Tor implementation remains present.

The internal HiddenVpnProxy primary constructor still permits an explicit Tor mode in principle, but actual application Tor call sites use the guarded factory and broker admission. This is not an observed reachable production bypass; guarding that constructor could reduce future misuse risk.

## T1 — Addressed in scoped source follow-up

Re-read actual `app/src/main/java/uk/co/traynor/privategallery/ui/EvidenceAiDialog.kt` lines 26–37 and 130–158 after the correction. An explicit `initialMode="Tor"` now stays Tor even when disabled. The dialog explains the deferral and requires an explicit route chip selection; it no longer substitutes VPN or System. The permission action is enabled only for VPN, and Prepare exact upload preview is disabled for deferred Tor. The backend Tor admission guard remains in place. This addresses T1 at source level. No new runtime execution or broad suite was performed for this follow-up.

## C1 remains a critical release blocker

The existing `docs/release17103/CONTINUITY_SECURITY_REVIEW.md` records source `24c29fd86e8493b00365357071d9c45e240eb3c4`, run 41688397704, raw capture SHA-256 `5b1ebb4d21de3a6e32ba0722bedd60fbc923dddd3a936c42ecf8c526cc64f982`. Its eight Android cases and receipts passed while the full packet gate failed. It documents off-WireGuard DNS and six direct native/WebRTC-shaped UDP STUN attempts during VPN route transitions, including three exact child-native markers. These are VPN_ONLY continuity failures, not Tor-only execution.

The Tor diff does not change MainActivity.denyHiddenVpnNetwork's disconnect-then-wait mechanism, the retained child policy, or introduce an OS enforcement fence around native sockets during tunnel teardown. It therefore does not remedy or narrow C1. Paused WebViews, app authority fences, proxy guards and remembered Network binding remain insufficient evidence against the recorded native escape. Excluding Tor cannot waive VPN DNS/native UDP containment.

Necessary next security action: retain the rejection; design and demonstrate an OS-enforced no-fallback boundary covering the entire VPN removal/replacement transition while the child survives, preserving the required continuation/authentication/grace behavior. Then obtain fresh exact-source full-capture evidence exercising native UDP/DNS across those transitions and independent security review. Do not rerun the same unchanged broad suite as a claimed fix. Hard retirement before route removal would change the mandatory continuity scope and is not an authorized substitute here.

## Verification and scope boundary

Compile and the two new TorDeferral17103Test Android methods were pending at review time; no new runtime PASS is claimed. The historical eight Android passes belong to the exact source/run above, not this modified source. Confirm the current merged manifest/APK also disables both services and excludes the dependency service, then execute the focused deferral tests on that artifact. A source review cannot replace that artifact verification.

Reviewed changes do not modify Primary/Hidden authentication, cookies, owner scopes, package identity, or app version 1.7.10.3/code55. Their preservation is not a fresh exhaustive audit of those systems. Original-key 54-to-55 upgrade/signing verification remains outside approval, as recorded in the existing rejection.

**Release decision: BLOCKED.** T1 is addressed at source level. Verify the focused deferral artifact and resolve C1 with enforcement plus exact-source packet evidence before any security approval or production delivery claim. No approval marker is created.
