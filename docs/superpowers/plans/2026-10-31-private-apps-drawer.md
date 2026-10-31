# Private Apps Drawer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver 1.7.8/code50 with an accessible native-style private app drawer, preserving code49 repairs and data.
**Architecture:** Reuse PrivateAppsLibrary and original controllers/profiles. Atomic MOVE_TO14 updates catalogue schema2; UI state remains memory-only and tied to its original authority. Parallel independent release/test preparation uses disjoint files, followed by one consolidated security review.
**Tech Stack:** Kotlin/Compose Material3, Java catalogue/protocol, existing Android37/GitHub release gates.
**Spec:** docs/superpowers/specs/2026-10-31-private-apps-drawer-design.md

## Global Constraints

- Version1.7.8 unchanged, nextcode50; Android17/API37only; package uk.co.traynor.privategallery.
- Preserve signed49 and all older evidence; original certificate only; no uninstall/data clear.
- No browser/encryption/authentication rewrite, direct fallback or Hidden metadata leakage.

## Review Focus

- Search versus persisted indices: reorder uses full UUID order and clears search.
- Pending PIN after disposal: stale presentation cannot navigate/close replacement.
- Large fonts/narrow Fold: fewer columns, complete accessible app names and48dp menu hit targets.
- Concurrent/revoked mutation: atomic one-publication MOVE_TO preserves all11fields and rejects stale authority.
- Upgrade/reopen: original49 encrypted catalogues/media/PIN/cookies/settings preserved before explicit candidate mutations.

### Task1: Atomic ordering and presentation-owned launch
**Files:** core/privateapps/PrivateAppCatalog.java, PrivateAppCommands.java, PrivateAppsController.kt; existing host/JVM tests.
**Interfaces:** MOVE_TO14 payloadexact4bytebigendian finalindex; controller.moveTo(id,index); launchFromDrawer(id,presentationCurrent,onOpened).
- [x] Add failing tests for destination bounds/current order/all-fields/single publish and stale presentation.
- [x] Observe host RED, implement single store.update, run scripts/check_private_apps178.sh (all8 PASS); JVM tests execute in grouped CI.

### Task2: Drawer and owner integration
**Files:** ui/PrivateAppsLibrary.kt, BrowserV2Home.kt, core/browser/hidden/HiddenVpnBrowserActivity.kt; PrivateAppsDrawer50Test and migrated UI/native tests.
- [x] Write tests for tap/PIN, longpress/menu/search/settings/remove/reorder, four-column360dp, large text, separate owner/lock/reopen.
- [x] Replace management cards with grid/menu/reorder mode; use existing editor and PrivateAppSettings; gate callbacks to original presentation.
- [ ] Run host checks, compile/lint and one grouped Android suite; diagnose failures before retry. Preserve all previous43 mandatory cases.

### Task3: Release verification and delivery
**Files:** established178 release controls, UpgradeFixtureTest, version metadata, recovery/tracker/evidence reports.
- [x] Pin exact actual signed49 APK/source/certificate, code50; preserve prior releases.
- [ ] Review exact implementation/runtime independently, resolve Critical/Important findings; freeze exact passing SHA.
- [ ] Activate original-key49→50 seed/install-r/verify/restart and production identity/distribution gates.
- [ ] Independently verify actual APK signature/digest/identity and upgrade logs; save one APK + filtered evidence, commit/push/independently verify remote and hand off with Fold checklist.

Ruling: Owner explicitly authorised autonomous execution and simple reorder fallback; no routine design/continuation approval pauses. Existing isolated recovered-gallery checkout has no competing writer; parallel work owns disjoint files.
