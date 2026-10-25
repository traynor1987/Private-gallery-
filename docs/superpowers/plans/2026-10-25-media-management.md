# Private Gallery 1.7.6 Implementation Plan

**Goal:** Deliver the owner's coordinated media-management release on the verified 1.7.5.2 baseline.
**Architecture:** Extend existing shared Compose vault views and authenticated repositories. Keep Primary and Hidden authority independent; use additive encrypted records for transfers and external editing, never plaintext journals or automatic recovery without authentication.
**Tech Stack:** Kotlin, Java 17 pure policies, Android API36+, Compose, existing encrypted stores and GitHub gates.
**Spec:** Owner execution request in this conversation, sections 1–20; no 1.8 work.

## Global constraints
- Baseline 451725c402230a7373458cddb5af324de904b670/tree ed69a470f11588ce315990d82b5d9e4c4dde6f09; package uk.co.traynor.privategallery.
- One writer, coherent backup commits, no force push/main merge. Do not touch owner data or signing keys.
- Original certificate 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17.
- Do not sign or increment release admission until focused and complete gates pass.

## Review focus
- Date rules across DST and missing/edited dates; sizes near Long limits and unavailable sizes.
- Context changes and lock cannot retain selection or external result authority.
- Crash between destination commit, verification and source deletion must not duplicate/delete originals.
- Samsung may ignore intents or return no URI; require explicit picker fallback, never scan arbitrary public media.
- Empty/partial Android results and CI continue-on-error must not count as successful release verification.

### Task 1: Shared selection and filtering
- [ ] Add Java MediaSelection predicates with host tests: inclusive local date boundaries, adjusted date precedence at Kotlin adapter, strict size rules, unknown dates/sizes excluded, stable drag range and ID intersection.
- [ ] Add shared Compose rule dialog and compact quick filters. Scope Select All explicitly, Deselect All, preserve sorting selection and clear on context change. Extend collection selection controls and loaded-only phone selection.
- [ ] Add synthetic JVM/instrumentation checks; compile and focused UI run on API36/37 once coherent.

### Task 2: Durable transfers
- [ ] Extend existing encrypted repository/journal contracts with stable operation and destination IDs. Persist before publishing, verify destination under its keys before source mutation. Reconcile uncertain commits after reauthentication.
- [ ] Integrate cancellation, real progress, failures, resume/retry and authenticated domain-specific history. Test crash points and large synthetic batches.

### Task 3: External edit
- [ ] Implement explicit confirmation, app-private bounded temporary content URI grants, supported intent resolution, explicit edited-result picker fallback and preview.
- [ ] Enforce originating authority on return; encrypted import before Replace Original/cleanup. Public deletion requires Android consent; make no cache/cloud erasure claim.
- [ ] Test unsupported editor, lock expiry, interrupted result/import and cleanup.

### Task 4: Collection refinements
- [ ] Extend existing encrypted cover/favourite fields without destructive migration. Add validated cover choice, unpin and bulk membership UI; verify counts and multiple membership.

### Task 5: Release admission
- [ ] Independent security review, host/JVM, API36/37 full regression, browser/camera/video/privacy and exact-source upgrade gates.
- [ ] Freeze tested source, choose available code above40, original-key sign/verify, synthetic40 upgrade on both SDKs, APK digest and one acceptance artifact.
- [ ] Record exact remote SHA, results, limitations and physical checklist. Physical acceptance remains owner's.
