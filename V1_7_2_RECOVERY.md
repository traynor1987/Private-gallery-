# Private Gallery 1.7.2 recovery

## Verified starting point
- Source branch: release/1.7.1-professional-ui.
- Independently verified full remote SHA: 397c3b480c60f5ea494020804dc0117705099d62.
- New branch: release/1.7.2-camera-ai-refinements, created from that exact commit.
- Clean baseline; existing metallic-blue icon preserved. No earlier branch changed.
- Read V1_7_1_RECOVERY.md current candidate record. 1.7.1 signed code34 and automated regression/synthetic original-key upgrade gates passed; owner physical acceptance remains pending. Preserve that release and its evidence separately.
- Baseline inspection found existing CameraX controller with immutable VaultCaptureAccess, capture journal, input gate, revoke/stop lifecycle and authenticated editor thumbnail loaders.

## Status
Design recorded in docs/superpowers/specs/2026-10-23-camera-ai-refinements-design.md at commit 9d1b30cf35a6fb08fc3b5afd0e245d4839e1e254.
No application implementation yet. No new tests, signing or APK claimed. Design approval is pending under the active brainstorming workflow.

## Next steps
1. Approve/revise the concrete design, then write the implementation plan.
2. Add effective-prompt composition and balanced independent image cards with focused tests.
3. Refine full-screen camera and add Reading presentation over the existing capture pipeline.
4. Verify ownership, immediate authority invalidation, lock handling, encrypted-import-before-cleanup and recovery.
5. Run fresh full regression/security/browser/process tests and production-package original-key code34 to code35 upgrade/restart checks.
6. Verify signing certificate, APK identity/hash and provide one acceptance APK. Physical Fold acceptance remains owner-only.

## Preservation
No owner-device/media operations, uninstall/clear-data, key generation, encryption migration, network routing change, main merge, V2 or face recognition. VPN+Tor remains unavailable. Each coherent implementation must be pushed and independently verified before lengthy CI.

## Owner approval and first implementation work
Owner approved the design and autonomous inline execution, adding compact filename-search toolbar controls. Visual search assessment is recorded in V1_8_SEARCH_REQUIREMENTS.md and deferred to1.8 because shared-space model/runtime and encrypted incremental index integration require substantial independent validation.

Implemented pending Android verification: stateless proportions composition with exact user prompt preserved and conditional guidance; default-On editor switch, effective-prompt preview and result regeneration toggle; thumbnail-led second-image picker grid without oversized numeric labels; Primary/Hidden toolbar search expansion/collapse with query/filter retention and active-filter accessible label. Search expansion/query are memory-only and keyed by vault label.

Host prompt check:12 assertions pass. Local Gradle cannot bootstrap because distribution network access fails; Android/JVM build/UI validation remains pending in fresh GitHub CI. No code35 APK/signing/upgrade success is claimed yet. Owner data untouched.

## Camera implementation and review checkpoint — Android verification pending
Normal camera attempts the live full-screen viewfinder on entry with permissions and a circular shutter. Compact overlay controls share the exact same layout component with Fold fixtures. Reading View is a static original local article, no Preview use case, designated photo tap/explicit video controls, Volume Down and accessible exit over the same origin-bound controller. Recording/finalisation disables mode changes. Capture command type must match bound type; flash/zoom check authority. Save vibration follows successful encrypted import only.

Independent review found two important integration gaps, corrected before CI: long owner prompt plus guidance exceeded the existing4,000-character provider boundary (now actionable UI error, no truncation, toggle-Off retry fixture); entering microphone permission after camera entry could trigger active-camera onPause lock (idle settings now unbinds/rejects inputs before permissions, without weakening active capture lock handling). Fold screenshot fixtures now use the production overlay rather than old split-panel layout. Review remains in progress.

Target code35, original-key code34→35 baseline77b3b9c1b30cb913e5fcbd76d66e40d585b13399. Host camera13/18, Hidden foundation373+stream/tamper/isolation, browser53, MP4 checks12167, browser-video3, release8, emulator-line2 and Python script suite16 pass. Local Gradle bootstrap is network blocked; all fresh Android/JVM/build, original signing and upgrade gates remain pending. No APK is claimed.
