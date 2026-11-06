# Encrypted Vault Wallpaper Implementation Plan

> For agentic workers: implement the bounded feature and review it before the existing
> coordinated Android17 build/runtime/signing lane. No independent CI or signing launches.

**Goal:** Add the two requested encrypted Vault wallpaper entrypoints through one secure flow.
**Architecture:** Existing owner repository/reader, captured presentation, shared native editor,
existing encrypted wallpaper storage. No new vault reader or encryption.
**Tech stack:** Existing Kotlin/Compose/Android37, native NovaOS authority and encrypted stores.
**Spec:** docs/superpowers/specs/2026-11-06-vault-wallpaper-design.md

## Global constraints

- NovaOS1.7.x; next1.7.11.2/code58; package uk.co.traynor.privategallery; original certificate.
- Android17/API37 only. Physical acceptance: Samsung Galaxy Z Fold.
- Code57 blockers already shipped independently; preserve all of their gates and fixes.
- Preserve Home appearance, original wallpapers/system picker, Gallery editing and source data.
- No public plaintext, shared URI/export, external editor, new crypto/key, VPN/Tor or new workflow.
- Primary source only Primary wallpaper; Hidden source only Hidden wallpaper.

## Review focus

- Old Hidden authentication must not revive a retired picker in Primary.
- Stale Home snapshots/pending saves must not replace a successful or cancelled wallpaper.
- Failed encrypted write/commit must preserve old wallpaper and leave a recoverable editor.
- Asynchronous read/decode and system-picker returns must reject changed owner/authentication.
- Bitmap/byte ownership must survive every cancellation/exception without persistent plaintext.

### Task1 — Shared owner-scoped workflow and both UI entrypoints

Owner: wallpaper feature agent. Files: new core/home2 wallpaper coordinator/owner and shared
home2 editor/grid; SpacePreferences.kt, PreviewData.kt, WallpaperUi.kt, IntegratedHome.kt,
PreviewUi.kt, MainActivity.kt, FullscreenMediaViewer.kt and HiddenVaultDialog.kt only as needed.

- [ ] Add focused tests before product changes for owner isolation/cancel/apply/retirement.
- [ ] Implement metadata-only WallpaperOwner with sameAuthentication/freshAccess/presentation
  validity, shared WallpaperEditorHost and closable coordinator. Exact private API may follow
  existing EvidenceOwnerSession naming; publish the agreed interface to parent before wiring.
- [ ] List/read through captured EvidenceAccess sources/reader; bounded, oriented, owned
  in-memory bitmap decoding, no cache-owned Gallery bitmap recycling.
- [ ] Reuse native preview/crop/blur/dim controls. Add unique source/grid/action tags and only
  show encrypted-image viewer action with an authorized callback.
- [ ] Atomically commit owner-encrypted wallpaper preference, preserve old data on failure,
  and make ordinary stale Home saves harmless. Deliberate reset remains functional.
- [ ] Wire Home/system and Gallery entrypoints into same host. Return to original destination.
  Reuse existing handoff and secure/Panic window wrappers; original lock screen stays bundled.
- [ ] Verify tests cover real repositories, unchanged originals, ciphertext-only output, both
  owners, Primary-in-Hidden, cancel, stale delivery, timeout/lock/Panic/Space changes and Fold.

### Task2 — Coordinated verification and independent enhancement checkpoint

Owner: parent. Release controls agent owns existing acceptance job/eight controls/fixture/tests;
parent owns version, existing fast workflow, runtime selector/matrix and signing activation.

- [ ] Pin actual original-key57 APK/archive/evidence as the58 upgrade baseline.
- [ ] Run host/JVM and full app/instrumentation compilation before one focused API37 batch.
- [ ] Review feature security/source/test selectors; use actual defaults and scoped nodes.
- [ ] Commit/push the wallpaper enhancement independently after local verification.
- [ ] Require exact-source original complete Android reports with no failures/errors/skips.
- [ ] Independently approve source/controls/evidence, compile fixture against original57,
  sign with original key, verify57→58 in-place upgrade/data/restart on a disposable emulator.
- [ ] Verify published APK package/version/certificate/hash and provide direct GitHub asset.
