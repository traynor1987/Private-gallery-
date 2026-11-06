# Black Screen and Android Pinning Implementation Plan

> Parent owns integration, builds, Git, CI and release. Isolated components and reviewers work on
> disjoint files; execute the owner's concrete specification without redesign or approval loops.

**Goal:** Foreground black overlay preserving retained web audio, plus legitimate Android pinning.
**Architecture:** Native Activity cover and existing App Mode action; separate public-API pinning
controller/settings section; retain existing session/Space and privacy authority.
**Tech:** Kotlin/Compose/native Android17/API37, existing Gradle/CI/signing.
**Spec:** docs/superpowers/specs/2026-11-06-black-screen-pinning-design.md.

## Global constraints and review focus

- Original package/signer, NovaOS1.7.x/Home2.0, all existing encrypted data/security/BrowserV1 unchanged.
- A stale menu/permission/focus callback cannot activate after app/tab/Space/authentication changes.
- Cover activation/wake never invokes WebView pause/reload/navigation, and touches cannot leak.
- Calls/focus loss/lock/Panic must restore window ownership without exposing revoked contents.
- NONE pinning state is not disabled-switch detection; managed/unknown policy fails closed.
- Rotation/recreation/accessibility/system unpin controls must remain usable.

## Task A Black Screen

Files: new ui/NovaBlackScreenController.kt and core/ui/NovaBlackScreenPolicy.kt; modify
ui/PrivateAppControls.kt, ui/BrowserV2Home.kt and MainActivity.kt only for presentation hooks.
Tests: JVM NovaBlackScreenPolicyTest; Android NovaBlackScreen61Test plus real retained audio case.
Interfaces: controller.request(current:()->Boolean), dismiss(), close(), active:Boolean;
parent provides exact existing app/tab/auth guard and synchronous privacy/lock revocation.
- [x] Write policy/lifecycle/ownership tests; observe host/JVM RED before implementing rules.
- [x] Implement the smallest state/window owner, scoped brightness/bars/keepScreenOn and wake actions.
- [x] Add contextual menu callback, bound same owner, Main privacy/lock/Panic hooks.
- [ ] Test touch blocking, doubletap/accessibility/Back wake, exact restore, unchanged WebView+audio,
  focus interruption/actual background, revocation, layout/recreation and stale callback rejection.
- [x] Build/host/JVM verify, independent source review and separate feature checkpoint.

## Task B Pinning

Files: new core/security/NovaScreenPinningPolicy.kt, ui/NovaScreenPinningController.kt,
ui/NovaScreenPinningSettings.kt; parent wires native Settings and existing auth authority.
Tests: JVM policy and Android NovaScreenPinning61Test (real API boundary and UI).
Interfaces: composable NovaScreenPinningSettings(current:()->Boolean); no Main engine mutations.
- [x] Write guard/state tests and observe JVM RED; implement public-only state/request/settings controller.
- [x] Integrate neutral native Privacy & Security section with captured existing Space/auth lifetime.
- [ ] Test cancellation, foreground/revoked/keyguard/managed/unknown states, Settings return,
  no success before actual PINNED, no automatic request/recreation persistence, supported OS exit.
- [x] Verify and commit independently from Black Screen.

## Release

- [x] Determine next unused code above60 and pin actual original signed60 baseline and fixture.
- [x] Run existing host/JVM checks and compile app/instrumentation before one combined API37 batch.
- [ ] Inspect original complete XML, actual black captures/window state and retained-audio evidence.
- [ ] Independently review exact source, controls and original runtime evidence before original signer.
- [ ] Original-key60-to-next upgrade/preservation/restart; verify actual APK/cert/hash and publish asset.
- [ ] Record final SHAs/results/limitations and physical Fold acceptance checklist, clean pushed tree.
