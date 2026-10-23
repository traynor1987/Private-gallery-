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

## CI668 — compile failure diagnosed
Run40040019709/job120708652117, source86e513acf792d568b7bf3d9062def0beefaa5e79: host checks pass; Kotlin compilation failed at VaultCameraScreen.kt231–232 because nested Box scope hides BoxWithConstraints implicit maxWidth/maxHeight receiver. No Android tests/signing/upgrades ran. Minimal correction captures landscape Boolean in the outer constraint scope, alongside already-captured height. Fresh CI must prove compilation and all release gates; no assertion weakened.

## Idle permission lifecycle follow-up
Code inspection found that idle settings must also cancel the controller's100ms authority monitor: its lifecycle-RESUMED predicate would otherwise revoke on a permission dialog even after entered=false. pauseForSettings now cancels that monitor, unbinds, invalidates pending binds and stops accepting inputs. Re-entry starts one monitor; active-camera onPause/screen-off/session-expiry locking is unchanged. Added Activity STARTED/RESUMED regression: idle permission-style pause retains session, re-entered active pause locks and removes camera. Android execution pending. This is a narrow verified lifecycle integration correction, not a background-recording exception.

## CI669 — application compiled, test import correction
Run40040307856/job120709572838, source3e1231be6740693e29ba7eff0a857f04e17e53d2: application compilation passed, instrumentation compilation failed solely at explicit import androidx.compose.ui.test.assertDoesNotExist. That assertion is a member of SemanticsNodeInteraction, so remove the invalid import while retaining every assertDoesNotExist invocation. No runtime tests/signing/upgrades ran. Next fresh CI includes checkpoint205b3c0 idle-monitor correction, Activity pause regression, explicit screen-off Hidden/Primary revocation fixture and source-review findings. Both prior failures are tooling/compile integration failures, not evidence of passed runtime acceptance.

## CI670 — real Reading swipe capture defect
Run40040729324/job120710933828, source3a1861f4c9cab509797f5566e837579f2d449010:336 JVM tests pass (zero failures/skips), lint/build pass;39 focused UI tests pass (zero failures/skips). Preflight73 ran:72pass, Reading camera navigation-gesture test failed because a horizontal article swipe took a photo (expected0 captures, actual1). Full regression/signing/upgrades did not run.

Root cause: general Compose clickable handling on article text does not reject every horizontal drag when no horizontal scroller consumes it. Reading photo area now uses explicit touch-slop, multi-pointer and consumed-gesture discrimination as the verified Black-Screen camera, plus a separate accessible semantic click. Existing failing real-CameraX swipe/rapid-tap/VolumeDown assertions remain unchanged. Added a fast presentation swipe/scroll negative test and four1.3font-scale Fold Reading viewport/screenshot tests.

Artifact12539293873 downloaded and ZIP SHA256 independently matched574374b90b66a0e26dc39171e81e352a47505eeac6860379ac84931970aeaa18. Cover/landscape production-overlay screenshots inspected with controls readable and unclipped. Follow-up also restricts the filename-search toolbar icon to Media contexts where the existing search interface is available (no inert icon in Collections/Trash/empty vault). No browser transport change or weakened assertion. New fresh CI required.
