# Post-Audit Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct every source-proven audit defect, add production-path regression coverage, and produce an acceptance-only controlled Browser focus/diagnostics build without changing production Browser security or releasing.

**Architecture:** Keep the existing Compose/MainActivity architecture. Replace route-derived favourite state with callback-updated observable state; give Gallery paging an observer-backed invalidation signal; make displayed-image capture explicit about bounds availability; preserve all encrypted storage formats while adding fixtures; keep compatibility toggles behind the existing acceptance build flag.

**Tech Stack:** Kotlin, Jetpack Compose, Android MediaStore/Paging, Android WebView, WireGuard Android tunnel, encrypted private files, GitHub Actions.

**Spec:** `docs/audits/2026-09-22-private-gallery-forensic-audit.md`

## Global Constraints

- No production release or production Browser compatibility toggle.
- Do not weaken TLS, mixed-content, file/content, popup, third-party-cookie, JavaScript-bridge, or VPN fail-closed policy.
- No public plaintext Browser/Vault media intermediate.
- WireGuard remains the only production VPN engine; OpenVPN remains out of the APK.
- Acceptance-only diagnostics and focus controls require `BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS`.

## Review Focus

- Favourite deletion must immediately remove the live navigation label while preserving the no-Jenna fresh-install rule.
- A Gallery observer must invalidate safely after its lifecycle ends and never show a source item as deleted before platform verification.
- A visual-image fallback must never label a whole-viewport screenshot as element capture.
- v2/v3 fixtures must preserve all fields available in their respective schema before v4 re-save.
- A release build must compile with verbose Browser controls disabled.

### Task 1: Licensing, notice packaging, and unused build surface

**Files:** build scripts, root `LICENSE`, `NOTICE`, `app/src/main/assets/`, Settings licence UI, workflow.

- [ ] Add a failing source-policy test for packaged notices and acceptance/release flags; run it.
- [ ] Add proprietary/all-rights-reserved owner notice; package only distributed dependency notices; remove unused Room/KSP; remove dead planned destination.
- [ ] Extend signed acceptance workflow to run source audits before signing.
- [ ] Run focused and full CI verification; commit.

### Task 2: Favourite live state and Gallery observer

**Files:** `MainActivity.kt`, `DeviceGalleryRepository.kt`, tests.

- [ ] Add failing tests for favourite update propagation and observer invalidation semantics; run them.
- [ ] Implement minimal callback/state and lifecycle-safe invalidator.
- [ ] Run focused tests and commit.

### Task 3: Displayed-image capture contract

**Files:** Browser policy/UI and tests.

- [ ] Add failing tests distinguishing bounded capture from explicit screenshot fallback.
- [ ] Implement native-hit-test bounded path only when reliable; otherwise show Screenshot to Vault action.
- [ ] Run focused tests and commit.

### Task 4: Migration and Browser-source Vault fixtures

**Files:** encrypted index tests, Vault tests and test fixtures.

- [ ] Add failing v2/v3 fixture tests and Browser-source item tests.
- [ ] Implement only fixture/test helpers necessary to read/re-save current supported formats.
- [ ] Run focused tests and commit.

### Task 5: Buffer hygiene and diagnostics sanitisation

**Files:** encrypted bookmark/VPN profile stores, acceptance diagnostics, tests.

- [ ] Add failing sanitiser/buffer-path tests.
- [ ] Wipe mutable plaintext buffers in `finally`; extend sanitiser for socket URLs, IP literals, credentials, tokens, and encoded URL forms.
- [ ] Run focused tests and commit.

### Task 6: Acceptance-only controlled compatibility console

**Files:** Browser console/UI/callbacks and tests.

- [ ] Add failing tests for acceptance-only focus mode/verbose switches and release-disabled behavior.
- [ ] Implement a one-shot explicit focus request only after attach/window focus, plus verbose diagnostics off control; include state in trace.
- [ ] Run focused tests and commit.

### Task 7: Whole-branch verification and signed acceptance handoff

**Files:** no production functional changes expected.

- [ ] Run no-secret and WireGuard audits locally.
- [ ] Push branch; inspect Actions, fix failures using TDD, repeat until green.
- [ ] Dispatch no release; provide signed acceptance workflow instructions and physical checklist.
