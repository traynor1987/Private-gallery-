# Phase 3 Verified Hidden Media Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Checkpoints are not Phase 3 acceptance.

**Goal:** Implement direct Hidden import and independently verified Primary Copy/Move with encrypted recovery hold, safe restart and explicit paired restoration/cleanup.

**Architecture:** Keep the existing single Hidden selector and independent authority. Canonical codecs implement S2/P1/R2/V2/A2; fixed storage adapters own keys and paths. The coordinator consumes concrete original operations and opaque verification results, while existing Primary writers honor authenticated reserved ownership.

**Tech Stack:** Kotlin/JVM 17, Android API26–36, existing F1 AES-GCM/HKDF, JUnit4, existing Android instrumentation and Gradle8.10.2.

**Spec:** `docs/superpowers/specs/2026-10-02-phase3-hidden-media-transfer.md`; six base normative contracts plus `INITIAL_REGISTRATION_AND_RECOVERY.md` in `docs/phase3`, with the independent PF-01/IC-02 readiness reviews. The current owner instruction authorizes production implementation after clean design readiness; complete product/signed/device/owner acceptance remains pending. Recovery wording/provenance is recorded in `DOCS_RECOVERY_ALIGNMENT_REVIEW.md`.

## Global Constraints

- Copy is default; external provider originals are never deleted.
- Move retains unchanged encrypted Primary payload; no retirement before durable/authenticated/reopened/independently verified Hidden selection.
- No owner bulk migration, Primary encrypted format changes, key bridge, plaintext spool, later-phase work, merge or public Release.
- Preserve Phase0/1/2 checks, immutable vectors/fixtures, independent credentials, backup exclusions and permanent signer.
- Bounds: 512 Hidden items,128 collections,64 retained transfers,16 abandoned attempts,48MiB image,8GiB video,64MiB legacy whole source,16MiB index,64KiB descriptor,4096-byte UTF8,8192 entries/depth6 with exact path grammar.
- A2's exact restricted credential-projection capacity exception never grants ordinary full-tree admission; no restricted canonical creation/repair/stage discard. Fresh complete inventory is mandatory before ordinary service resumes.
- All promotion/destructive boundaries use original current operations and pinned identity. Every exception after rename inspects the selected winner.
- Keep **PHASE 3 RESULT: NO-GO** until complete signed candidate and owner acceptance.
- Before each remote checkpoint: targeted/matrix tests, full JVM,lint,debug/instrumentation builds, immutable checks, backup/secret/packaging checks and independent review; then exact push/PR CI.

## Review Focus

- Valid metadata-only edits after receipt must preserve exact original payload eligibility without requiring an obsolete whole index.
- Terminal backup export must not enable destructive replacement before paired evidence pruning.
- A malformed previously abandoned attempt retains bytes and denies ordinary writes; only IC-02's explicitly bounded CURRENT paired hold restoration may bypass that blockade. Quarantine never becomes disposable or authentic by filename.
- Revocation from an outer storage/controller lock must enqueue cleanup without running a resource callback there.
- A provider's final authentication error after emitted bytes must reach the coordinator as failure, never EOF success.

---

### Task 1: Canonical bounded storage and transaction codecs

**Files:** Create `core/domain/Phase3Encoding.kt`, `HiddenMediaIndex.kt`, `PrimaryTransferEncoding.kt`, `TransferEvidence.kt` and corresponding `core/domain/*Test.kt` (under existing main/test Java package roots).

**Interfaces:** Produce internal immutable validated `MediaContext` (purpose,objectID,generation), `MediaReference` (context,ciphertextLength,hash), `HiddenMediaIndex`, `PrimaryTransferCatalog`, `SourceProjection`, `TransferJournal`, `DestinationReceipt`, `MediaUsage`, `CredentialProof`. Each exposes `encode(): ByteArray` and a bounded `parse(bytes: ByteArray)` factory; IDs/hashes are immutable defensive copies and equality is by bytes. None is authority.

- [ ] Write literal-vector tests for C26/R66/U82/bootstrap120/bootstrap102/emptyI2/M1/journal182/receipt228; malformed count/enum/UTF8/optional/trailing/reordered/zero-ID/crop tests. Mutating framing acceptance must fail these tests.
- [ ] Run targeted codec tests RED before codecs exist; retain missing-symbol compile evidence separately from behavioral RED.
- [ ] Implement exact six-contract bodies and semantic cross-reference checks with bounded allocation, strict UTF8/raw-byte sorting, no public key/root selectors.
- [ ] Run targeted tests GREEN, reference vectors and full JVM; independently review codec behavior. No storage or UI integration in this task.

### Task 2: Frozen F1 video and actual-key accounting

**Files:** Create `core/domain/F1Video.kt`, `MediaUsageStore.kt`, tests.

**Interfaces:** Consume Task1 contexts/U82. Produce `F1Video.write(master,context,input,length,expectedSha,output,usageStore,attemptID,checkValid)` and owned read/full-verification APIs with mandatory accounting; separate header/chunk actual-key ledgers and nontransferable in-process precharge lease. Store helpers remain fixed-root internal APIs. No new media crypto entry point may make accounting optional or issue GCM before its required durable charge. Include charged whole-record sealing/verification for schema2 media, indexes, owners and A2; retain the existing58-byte credential ledger format.

Writer output must be the store's concrete private pinned owned output for the admitted context/attempt. A generic OutputStream or produced ByteArray cannot establish physical completion. Complete hash pinning follows exact original output sync/reopen/length/EOF/hash; selected promotion and full charged authentication remain separate.

- [ ] RED tests: every header/chunk tamper, bad unread chunk,empty/max/partial/overflow framing, budget8192+1 at8GiB, partial two-ledger charge failure and restart with unused charges, changing input/extraEOF.
- [ ] Implement and execute ledger-relevant portions of P3-LS01..06 against exact S2/P1/V2/A2 counter-staging closure: stage shape/size/quota, every update fault boundary, canonical-only restart, own-stage-only discard and proof-ledger isolation. Full restricted-proof integration remains Task6. Never invent restart stage ownership.
- [ ] Implement ledger portions of P3-LC01..06: exact credential projection census/reservation and restricted canonical-only/no-discard behavior; no opaque media traversal or false whole-tree pass. Full proof and ordinary re-admission integration remain Tasks4/6.
- [ ] Implement P3-PF01..06 fresh P1 pending58/private one-shot registration and exact fixed original output sync/reopen before immutable hash completion; no restart finalization or query/copy/select authority from pending state. Preserve S1 behavior and frozen bytes.
- [ ] Implement Task2 IC-02 short-initial canonical portions:0/half-write faults, required-key denial, OTHER intact selected keys, creator permission loss and no adopt/repair/refund/discard. Full hold/ordinary-admission cases remain Tasks4/5/6.
- [ ] Implement frozen156-byte purpose10/11 records;1MiB chunk buffer, exact EOF/hash, durable charge before GCM, no resume/refund/reconstructed lease.
- [ ] GREEN targeted tests, Phase0 F1 vectors/full JVM and independent review; capture realistic large-video arithmetic without allocating8GiB in unit tests.

### Task 3: Deferred resource cleanup and lock enforcement

**Files:** Modify `PrimarySessionAuthority.kt`, `SecondarySessionAuthority.kt`, `SecondaryController.kt`; create `core/security/SessionCleanup.kt`; existing authority/controller tests plus latch regressions.

**Interfaces:** Existing cryptographic operation/presentation signatures preserved; resource/job
ownership APIs must narrow to pre-creation reservation-backed factories under
`docs/phase3/CLEANUP_CAPACITY.md` (CB1). Migrate every production caller; no generic
already-created object fallback. Produce enqueue-only revocation and acknowledged asynchronous cleanup; `cleanupComplete` denies fresh authentication while any resource/job/native release failed or unfinished. Add explicit transfer/hold scopes without granting foreign domain authority.

- [ ] RED latch tests: controller/root revoke while reader close needs authority; blocked producer cancellation; unfinished/native failed acknowledgement; fresh auth blocked until complete; original closed/ABA operations denied.
- [ ] Implement enqueue/schedule after gates, outside-lock cancellation/transport close/join and authoritative pending counts. Audit cache eviction below metadata/storage and marshal main-thread release outside application locks.
- [ ] GREEN all existing Phase1/2 authority/controller tests/full JVM; independent concurrency review.

### Task 4: Single-selector schema2 Hidden storage

**Files:** Modify `SecondaryStore.kt`, `DomainSnapshot.kt`, `DomainInventory.kt`, `SecondaryRetirementJournal.kt`; create fixed `HiddenMediaStore.kt`; tests/instrumentation.

**Interfaces:** Consume Tasks1/2/3. Produce concrete Hidden media read/import/metadata/trash/collection operations from `SecondaryOperation`, opaque selected verification result and exact retained closure. Credential mutation carries media forward, original historical envelopes/ledgers survive retirement.

- [ ] RED real-disk tests: schema1 authenticated upgrade; every credential mutation after import; selected corruption/missing ledger; attempt reservation/owner failures; terminal and shared evidence retention; before/during/after selector sync/rename/reopen.
- [ ] Implement exact S2/A2 grammar and owner manifests, root-depth6 limits, immutable writes/selection and full readback. Direct import has no receipt/source-deletion authority. Unknown ownership preserves ciphertext and denies writes.
- [ ] GREEN targeted/matrix subsets,full JVM/lint/APKs, regression/vector/backup/secret/packaging checks; independent review and coherent checkpoint only after all pass.

### Task 5: Authenticated Primary source and recovery ownership

**Files:** Create `core/vault/PrimaryTransferStore.kt`, `PrimaryTransferSource.kt`; modify `AndroidVaultRepository.kt`, `EncryptedPayloadStore.kt`, `EncryptedIndexStore.kt`, `PrimaryVaultSetupGuard.kt` as needed; tests/instrumentation.

**Interfaces:** Consume Task1 P1/M1 and original `PrimaryOperation`; produce pinned verified read-only source, authenticated catalog state transitions/restore merge/backup admission and final gated hold-intent/index ownership mutation. Never call video migration/export/viewing shortcuts.

- [ ] RED tests: bad final legacyGCM/PGVIDEO1 tag,digest/fingerprint changes,pinned root/file replacement,ID reuse,collection conflicts,later unrelated writes; all Primary mutation/reconcile/delete/import/backup/restore paths honoring reserved ownership.
- [ ] Implement unchanged payload hold and exact M1 merge into current legacy index; intent sync/auth/reopen before source-index removal; source file+directory sync/reopen before ACTIVE_HOLD; no unlink in move/restore.
- [ ] Terminal export allowed; destructive replacement remains blocked until authenticated empty/pruned catalog and resolved inventory, while existing fresh-only restore stays enforced.
- [ ] GREEN targeted/matrix/full JVM and immutable compatibility; independent review and coherent checkpoint checks.

### Task 6: Paired coordinator, receipts, restart and restricted restoration

**Files:** Create `core/domain/VerifiedMediaTransfer.kt`, `HoldRestoreProof.kt`; extend fixed Primary/Hidden stores/controllers; tests/instrumentation.

**Interfaces:** Consume concrete original operations, source/store APIs and internal verification result. Produce direct import, Copy default, Move hold, exact-ID retry,restore,explicit cleanup and five-step terminal release. No receipt/public DTO mints deletion authority.

- [ ] RED matrix tests for all95 negatives+50 closure negatives and24 before/during/after fault boundaries using real encrypted synthetic disk state; differentiate model, JVM and Android evidence.
- [ ] Complete P3-LS01..06 integration, including actual restricted proof with admitted stages and opaque corrupt media. Ledger-only Task2 checks do not satisfy whole proof/transfer cases.
- [ ] Complete P3-LC01..06 with actual restricted proof, interruption and fresh ordinary full-inventory refusal; projection accounting is not full-tree acceptance.
- [ ] Implement frozen transfer states and12 local hold states, same-ID rejection, durable evidence ordering, full fresh verification before each irreversible boundary, paired cleanup/release and current-state winner recovery.
- [ ] Execute all IC-02 negatives with real CURRENT selected state4/5/6, unrelated failed P1 attempts/generations/selector stages, private revision/one-shot advances, M1 winner inspection and all capacity/identity/revocation faults.
- [ ] Implement A2 selected independent PIN/confirmed recovery proof without ordinary media admission; opaque one-shot tuple/deadline/revision, restricted restoration only, fresh paired auth after restart, no Hidden key persists in proof.
- [ ] GREEN matrix/restart/lock tests and full checkpoint checks; independent full integration security review with no unresolved Critical/Important finding.

### Task 7: Concealed import/transfer and scoped media presentation

**Files:** Modify concealed private-space UI/controller and `MainActivity.kt`; create fixed owned Hidden preview/video adapters; tests/instrumentation.

**Interfaces:** Consume coordinator/current operations. Only concealed authenticated route offers source selection/Copy/Move/hold restore/explicit cleanup and Hidden-local collections/trash/crop. Ordinary Primary remains neutral.

- [ ] RED picker revoke-before-launch,nonce-limited16 URI/10minute ticket,new unlock/explicit confirmation,late result/death; bounded decode/preview/player revoke/secure-window/Primary route privacy tests.
- [ ] Implement professional private-space media flow with Copy default; Hidden-only encrypted thumbnails,scoped memory cache,owned F1 DataSource and non-destructive crop. No plaintext files,persisted URI grants or global cache.
- [ ] GREEN JVM and Android lifecycle/provider tests; document supported/OEM memory/durability limits and independent UI/privacy review.

### Task 8: Candidate evidence and owner acceptance boundary

**Files:** Extend `.github/workflows/android.yml` push allowlist for exact Phase3 branch; update report/matrix/exit gates/evidence; permanent-signer acceptance workflow unchanged unless exact justified requirement.

**Interfaces:** Exact source SHA and full check results; candidate artifact/run/APK hash/package/version/signer; owner physical acceptance is external evidence and cannot be fabricated.

- [ ] Add workflow Phase3 targeted matrix/reference checks and push trigger; preserve all Phase0/1/2 CI checks.
- [ ] Run complete required checks before coherent push; independently review; verify remote head/push and PR event CI,check no conflicting newer head.
- [ ] Build one permanent-signer artifact-only acceptance candidate after implementation gates pass; download and verify actual APK identity and signer continuity against54.
- [ ] Provide focused synthetic physical acceptance including restart/background/low-storage/paired recovery; await owner observations. No owner sole-copy fault tests/uninstall/data clear.
- [ ] Keep PR59 draft/unmerged and Phase3 NO-GO until every mandatory gate and owner acceptance passes. Do not begin Phase4.
