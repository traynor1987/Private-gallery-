# Private Gallery 1.7.2 recovery

## Current verified owner acceptance candidate — 24 October 2026

**GO for owner physical acceptance. Production GO remains pending owner testing.**

- Baseline: release/1.7.1-professional-ui at397c3b480c60f5ea494020804dc0117705099d62. Earlier release/V2 branches remain untouched; no main merge.
- Signed APK source: e0cc1f0d8ddb8482910f01a9de5abf1e9472332b. Fully tested source: c9f7e0712614d7af1d301d74ad6abe9d1a912225. Entire app/build/test/resource tree identical: e33ae81c3ebde2736583c18ef39ff69d69470927. Subsequent final recovery/review changes are documentation only.
- Package uk.co.traynor.privategallery; version1.7.2/code35, label Private Gallery, nondebuggable, minSDK26/targetSDK36. In-place upgrade from installed code34; never uninstall or clear owner app data.
- Original certificate SHA256:94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. CI checks and independent downloaded-APK cryptographic verification pass (APK v2 signature). No replacement signing key.
- One recommended file: Private-Gallery-1.7.2-code35-Acceptance.apk,62,628,076bytes. APK SHA256:f2f0760fb6f942002da0ce763e12b0b6a5a39fa6004a1fb0afa303e1a0a107f1. Saved for owner download.
- Release acceptance#1/run40046703818/job120729980481 SUCCESS. Artifact12542455654 ZIP SHA256 independently verified:73f11d72883dba2f3d8588fe7a0b5408e9552cdee720b833445b086c816c55ef. Diagnostics artifact12542435750, SHA2561a6eff264d8bd07cefe5d7ee73534acd6fc17e84488421dae4e452041a60e3b2 (GitHub metadata).
- Full app tests:336JVM,44focusedUI,73preflight,329unfilteredAndroid, zero failures/errors/skips. Independent browser and camera process-restart fixtures pass. Pinned CI671 artifact12541501894 ZIP SHA256:d08f40fc7434bbc7b4cbe778a38c5642a4d1c264c4d2c4c929e753dc18fe8b9a; source/digest/entire-app-tree/root-path allowlist verified locally, independently reviewed and enforced again in acceptance CI. CI671 overall remains FAILED after passing app tests because its upgrade fixture lacked approved34→35; that exact guard correction is freshly exercised below.
- Fresh launcher rendering and launch pass. Fresh debug34→35 upgrade/restart and original-key production-package34→35 upgrade/restart pass. Both independent PINs/cross-PIN rejection, encrypted photos/videos and metadata, collections, favourites, settings, VPN/AI configuration preserved in synthetic data. Original-key code34 baseline source77b3b9c1b30cb913e5fcbd76d66e40d585b13399; no data clearing after seeding.
- All five packaged owner-art foreground densities match source pixels exactly. Adaptive/round/monochrome resources and display name preserved.

### Implemented scope

1. Combine Photos: default-On Preserve realistic proportions toggle; custom prompt stays verbatim and authoritative, conditional supplied guidance, effective-prompt expansion and result regeneration toggle. Long effective prompts get an actionable limit message and can retry unchanged with guidance Off. Model output remains probabilistic.
2. Two equal-sized authenticated source cards retain actual thumbnails, independent replacement and large previews. Thumbnail-led picker grid removes oversized numeric labels. Memory-only loading/cancellation/wiping retained.
3. Normal camera opens a full-screen viewfinder with compact overlay controls and circular shutter. Photo/video, timer, front/rear, flash/zoom, optional audio, permissions, quality and encrypted recovery retained. Shared production layout is used by Fold screenshot fixtures.
4. Reading View: original static local article, no live preview/network, pre-activation explanation and photo/video selection; designated photo-area taps or VolumeDown; intentional video start/stop or VolumeDown; clear exit. Drags/multi-pointer/consumed gestures do not capture. Lock/expiry/background stopping and immutable origin-vault destination preserved. Idle permission entry releases camera resources/monitor; active capture still locks on pause. No background recording/privacy bypass.
5. Primary/Hidden Vault filename search uses a compact toolbar icon in Media contexts, expands/closes with query/filter retention and a visible/accessible active-filter indicator. No public history/cache. Visual Gallery search is deferred to V1_8_SEARCH_REQUIREMENTS.md; no indexing/model or face recognition implemented.

### Review and limitations

Independent application and CI-gate reviews found no remaining release blocker after corrections for prompt length, idle permission lifecycle, actual production-layout test coverage and Reading swipe discrimination. Required assertions remain intact. One nonblocking future CI-gate hardening item is documented in docs/release172/REVIEW.md; the pinned archive's counters/identities were independently inspected clean.

Physical Fold hardware camera behavior, live owner AI/WireGuard/Tor endpoints and actual owner-media acceptance remain unclaimed. VPN+Tor remains unavailable. No owner device/data operation occurred. The temporary workspace connection interruption caused no committed-work loss; GitHub stayed authoritative.

### Owner Galaxy Z Fold acceptance checklist

1. Install code35 as an update over34. Never uninstall/clear app data. Open both vaults with existing independent PINs; check existing photos/videos, collections, favourites/settings, then close/restart and recheck.
2. On cover/inner screens, portrait/landscape and keyboard open: confirm equal actual Photo1/Photo2 cards, preview/replace either without changing the other, custom prompt persistence, guidance On/Off/effective preview and real Seedream regeneration. Review subject proportions; no perfect-output guarantee.
3. Check normal full-screen camera, Photo/Video switching, shutter, front/rear, supported flash/zoom, timer and AudioOn/Off. Save into each vault and verify correct encrypted destination, timestamps/integrity and no automatic PhoneGallery copy.
4. Check Black-Screen and Reading modes, pre-activation controls, article scrolling without capture, rapid-tap duplicate prevention, VolumeDown photo/video controls, start/stop feedback and reliable exit.
5. While recording, power-lock and test vault auto-lock/expiry; ensure capture stops, next opening is neutral Primary-style unlock and completed/pending encrypted media recovers only into its originating vault. Hidden context must not be disclosed.
6. Expand/type/clear/collapse Vault search on both displays; filters/selection behavior and separate Primary/Hidden results remain correct. Check media grids, collections, settings and bottom navigation.
7. Check Primary WireGuard, Hidden TorOnly/VPNOnly, ordinary/onion browsing and downloads. VPN+Tor stays unavailable. Confirm metallic-blue launcher and themed variant.

Historical status/chronology below is retained for audit and superseded by this current candidate record. Next action is owner physical acceptance; do not silently declare production GO or merge main.

## Verified starting point
- Source branch: release/1.7.1-professional-ui.
- Independently verified full remote SHA: 397c3b480c60f5ea494020804dc0117705099d62.
- New branch: release/1.7.2-camera-ai-refinements, created from that exact commit.
- Clean baseline; existing metallic-blue icon preserved. No earlier branch changed.
- Read V1_7_1_RECOVERY.md current candidate record. 1.7.1 signed code34 and automated regression/synthetic original-key upgrade gates passed; owner physical acceptance remains pending. Preserve that release and its evidence separately.
- Baseline inspection found existing CameraX controller with immutable VaultCaptureAccess, capture journal, input gate, revoke/stop lifecycle and authenticated editor thumbnail loaders.

## Historical initial design status (superseded)
Design recorded in docs/superpowers/specs/2026-10-23-camera-ai-refinements-design.md at commit 9d1b30cf35a6fb08fc3b5afd0e245d4839e1e254.
No application implementation yet. No new tests, signing or APK claimed. Design approval is pending under the active brainstorming workflow.

## Historical initial next steps (superseded)
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

## CI671 results and reviewed release continuation
The Reading swipe fix passes both focused drag controls and real synthetic CameraX tap/swipe/VolumeDown assertions. All four Reading1.3font-scale Fold cases pass and screenshots are archived. Full unfiltered329-case Android suite passes. Upgrade guard correction is restricted to approved34→35; qemu/package/trusted-branch guards and all data/PIN/hash assertions remain.

Independent CI-gate reviewer verified artifact digest, all336/44/73/329 cases with no failure/skip/error, source provenance, exact app tree, fixture-only permitted delta and original-key signing/fresh production-upgrade upload ordering. No Critical/Important finding. Minor future hardening deferred: generic suite counter/duplicate-ID checks (actual pinned archive independently inspected and clean). Recovery current status corrected. Live API availability, signing secrets, fresh upgrade results and physical owner acceptance remain runtime gates, not assumed successes.
