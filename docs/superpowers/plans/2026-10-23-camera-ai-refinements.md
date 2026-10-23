# Camera and AI refinements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver code35 with proportions guidance, balanced source selection, professional camera, Reading View and compact vault search.
**Architecture:** Reuse authenticated editor loading and CameraX/VaultCaptureAccess. Reading is a foreground presentation of the same immutable-origin controller. No storage or browser transport changes.
**Tech Stack:** Kotlin/Compose, CameraX, JVM tests, Android instrumentation, GitHub Actions.
**Spec:** docs/superpowers/specs/2026-10-23-camera-ai-refinements-design.md, approved by owner; subsequent search requirements also authorised.

## Global Constraints
- Package uk.co.traynor.privategallery; version1.7.2/code35; original production certificate only.
- Preserve encryption, independent vault credentials, metadata, launcher art and browser routing.
- No owner data operations, clear-data/uninstall, main merge, V2 or face recognition.
- Smart visual search must not delay1.7.2; preserve full privacy/index requirements for1.8.

## Review Focus
- Explicit surreal-scale prompts retain priority over guidance (Task1).
- Replacing one image during delayed loads never changes the other (Task1).
- Collapsing search preserves filters/selection without concealing an active query (Task2).
- Lock during camera rebind or recording immediately rejects inputs (Task3).
- Permission denial/audio changes and narrow landscape keep exit reachable (Task3).

### Task 1: Combine composition and image selection
Files: core/editor/CombinePrompt.java, core/editor/CombinePromptTest.kt; ui/PhotoEditor.kt, ui/AiSourceCards.kt; androidTest/ui/AiEditorProfessionalTest.kt.
Interface: CombinePrompt.compose(String, boolean): String, false preserves exact original; true appends conditional supplied guidance.
- [ ] Add tests for exact off prompt, preserved user prefix, owner priority, Unicode/blank handling and immutable generation snapshots. Run red then implement Java helper and run host/JVM checks.
- [ ] Add editor switch/default On/effective preview and use computed prompt only for Combine; consent accurately describes additions. Keep regeneration settings reachable.
- [ ] Replace oversized candidate numbering with thumbnail-led compact grid; keep independent IDs and authenticated memory-only loads. Test thumbnails/replacement/prompt/Fold.
- [ ] Commit/push verified checkpoint; record Android tests pending until CI.

### Task 2: Compact vault search and future visual search
Files: ui/MediaChrome.kt and VaultScreen call sites; androidTest/ui/MediaChromeTest.kt, relevant layout assertions; V1_8_SEARCH_REQUIREMENTS.md.
Interface: optional MediaHeader.onSearch; VaultBrowseControls expanded/onExpanded with query unchanged on collapse.
- [ ] Test initial collapsed, expand/type/filter/collapse/reopen/clear and scoped state disposal. Implement icon toolbar and close control. Preserve filtered query indicator while collapsed.
- [ ] Assess maintained on-device embedding tooling from official sources; defer substantial model/index integration and save exact requirements/privacy/permission constraints for1.8. No fake AI-search UI.
- [ ] Commit/push checkpoint.

### Task 3: Camera presentation and ownership
Files: ui/VaultCameraScreen.kt, new ui/ReadingCameraView.kt, core/camera/VaultCameraController.kt; androidTest/ui/CameraPresentationTest.kt, VaultCameraUiTest.kt, core/camera/CameraHardwareTest.kt.
Interface: CameraMode.NORMAL/BLACK/READING; modes consume same VaultCameraController; controller alone accepts capture inputs and owns originating access.
- [ ] Add tests for modes, explanation/activation, tap/VolumeDown debounce, idle-only switching, permission denial, recording feedback/exit, Fold layouts.
- [ ] Implement full-screen preview with compact professional overlay controls; Reading static local page without Preview use case; retain audio/quality/recovery controls.
- [ ] Verify immutable destination, immediate revoke and lock/background neutral reopening, safe finalisation and cleanup-after-verified-import. Add focused lifecycle regressions for any actual gap.
- [ ] Run host camera/security checks and commit/push checkpoint.

### Task 4: Fresh release verification and original signing
Files: app/build.gradle.kts, scripts/release_configuration.sh, .github/workflows/android.yml, focused test scripts, V1_7_2_RECOVERY.md.
- [ ] Configure code35 and code34 baseline77b3b9c1b30cb913e5fcbd76d66e40d585b13399; add1.7.2 full fresh CI and preserve prior branches.
- [ ] Run host checks/JVM/build/focused UI/preflight/full Android/browser/process-restart tests. Inspect failures and screenshots; correct causes without weakening assertions.
- [ ] Fresh independent whole-branch security review; resolve blockers.
- [ ] Original signing, package/certificate/APK hash and synthetic production code34→35 both-vault immediate/restart preservation required.
- [ ] Push recovery evidence, independently verify full remote SHA; save one signed APK and physical Fold checklist. No production GO before owner testing.

## Execution ledger
Pre-flight: Tasks1/2 share no state; Task3 owns capture inputs, UI must not bypass controller; Task4 consumes exact tested app and new tests. No interface conflict found.
Ruling: Execute inline without another approval pause — owner explicitly approved design and ordered implementation; implementation plan is internal sequencing, not a new architecture.
