# Private Gallery 1.7.7 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver encrypted independent recycle bins, remaining owner repairs, and one original-key acceptance APK.
**Architecture:** Extend existing TRASHED states and encrypted index/presentation metadata; retain ciphertext in place. Reuse current authentication and scoped commit guards. Persist no keys for background expiry; scheduled work operates only with live authenticated authority, otherwise unlock reconciliation performs expiry.
**Tech Stack:** Kotlin/Compose, Android17, existing encrypted stores, JobScheduler and GitHub Actions.
**Spec:** Owner's full 1.7.7 directive in this session; recovery baseline in V1_7_7_RECOVERY.md.

## Global Constraints
- Android17/API37 only; package uk.co.traynor.privategallery; original signing certificate only.
- Preserve keys, credentials, media, collections and existing browser isolation.
- Hidden PIN only; no plaintext thumbnail persistence or background unlock.
- Default30 days; independent7/15/30/60/never retention settings.
- No1.8, force push, uninstall, clearing user data or premature acceptance claims.
- Batch changes; reuse existing focused and signing workflows.

## Review Focus
- Lock or navigation during async previews/mutations must reject stale publication and wipe owned memory.
- Corrupt payload restore must leave item in trash and preserve original ciphertext.
- Interrupted physical deletion must reconcile without deleting unrelated encrypted files.
- Retention reduction/clock rollback must have defined safe behavior; locked jobs must not acquire keys.
- Missing/deleted collections and duplicate retries must not invent records or move media across vaults.

### Task1: Recovery and baseline
- [x] Recover source, previous release evidence and code inventory.
- [ ] Record baseline host tests and inspect existing CI before changes.

### Task2: Repository integrity and retention
Files: core/vault/RecentlyDeletedPolicy.kt, VaultPresentationStore.kt, AndroidVaultRepository.kt; core/hidden/HiddenRepository.kt; focused repository tests.
- [ ] Add corruption, restore metadata/idempotence, per-vault retention/expiry and interrupted deletion tests.
- [ ] Implement encrypted retention getters/setters, verified restores and metadata cleanup using existing atomic stores.
- [ ] Add authenticated trashed-only preview path; ordinary read/egress remains COMPLETE-only.
- [ ] Verify focused repository tests; commit coherent storage changes.

### Task3: Gallery and browser repairs
Files: MainActivity.kt; ui/HiddenVaultDialog.kt, HiddenBrowserHome.kt, BrowserSharedChrome.kt; core/browser/hidden/HiddenVpnBrowserActivity.kt.
- [ ] Reproduce missing previews and trace browser chrome ownership/layout.
- [ ] Apply shared Primary presentation guard to previews; preserve epoch/revocation and cache checks.
- [ ] Repair any demonstrated Hidden thumbnail storage/load problem without swallowing corruption.
- [ ] Provide stable connecting browser presentation and visible address bar, preserving isolated child transport.
- [ ] Retain code45 navigation/picker/editor changes with focused runtime verification and owner checklist.

### Task4: Recently Deleted experience and scheduling
Files: new ui/RecentlyDeletedPanel.kt and core/vault/RecentlyDeletedExpiryJob.kt; MainActivity.kt, HiddenVaultDialog.kt, AndroidManifest.xml.
- [ ] Add adaptive authenticated thumbnail grid, deletion date/countdown, selection, restore, permanent delete and confirmed empty actions.
- [ ] Wire encrypted independent retention settings and owned async callbacks.
- [ ] Schedule persisted expiry reconciliation; live authorized sessions only, defer locked vaults until unlock.
- [ ] Test process/reboot/lock, large text, cover/inner screens and destructive confirmations.

### Task5: Grouped verification and security review
- [ ] Update existing focused workflow for1.7.7 branch; exclude automatic fast duplicate run.
- [ ] Select affected storage/auth/transport/picker/thumbnail/editor tests only; keep packet proofs.
- [ ] Run available host tests; push coherent candidate, verify remote SHA, run grouped Android17 checks.
- [ ] Independent final-diff security review; resolve Critical/Important findings with focused proof.

### Task6: Signing and delivery
- [ ] Recheck unused version code; freeze exact verified source.
- [ ] Adapt existing acceptance workflow with strict current-source evidence/review gates and original-key45→46 fixture.
- [ ] Build/sign, verify package/version/cert/hash and in-place upgrade/restart/encrypted data preservation.
- [ ] Deliver APK and physical owner checklist; keep physical acceptance OWNER_REQUIRED.

Ruling: User explicitly authorized autonomous execution and no repeated confirmations; implement within supplied scope without a further design-approval pause.
