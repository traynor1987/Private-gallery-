# Phase 3 Design Admission Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve the load-bearing format and ownership contracts before enabling any Phase 3 production media writer.

**Architecture:** One authenticated Hidden selection binds media and transfer evidence. A separate Primary-only transfer catalog owns unchanged rollback ciphertext; paired scoped operations authorize the bridge. Existing source formats and credential domains remain unchanged.

**Tech Stack:** Kotlin/JVM and Android API26–36, canonical bounded binary formats, existing F1 AES-GCM/HKDF, no new network or database dependency.

**Spec:** `docs/superpowers/specs/2026-10-02-phase3-hidden-media-transfer.md`

## Global Constraints

- **PHASE 3 RESULT: NO-GO** until all automated, exact signed-build and owner physical gates pass.
- Preserve app/data; no uninstall, data clear, destructive owner migration or implicit Primary format conversion.
- Source retirement is prohibited until the Hidden copy is durably written, authenticated, reopened and independently verified.
- Move uses the owner's encrypted Recovery hold, not ordinary Trash or immediate permanent removal.
- No Hidden key/material enters Primary; no Primary key/material authorizes Hidden.
- Preserve frozen F1 header/purpose/HKDF/slot bytes and all legacy Primary format fixtures.
- No later Browser, camera, Social Hub, Tor, VPN, VPS, AI or unrelated features.
- No production ciphertext writer from a field-level design whose canonical framing/ownership contracts remain unresolved.

## Review Focus

- A credential/settings mutation must retain the original encrypted index snapshot and usage key needed to authenticate a still-active transfer receipt.
- A current Hidden item may have later metadata changes while a receipt binds its original committed index; the exact allowed cleanup predicate must be explicit.
- A crash can leave a new selected winner even when the caller received an exception; recovery must inspect disk before touching either copy.
- A legacy Primary-only path must not bypass recovery-hold ownership through restore, reconciliation, dedupe, setup admission or deletion.
- Unknown provider length and provider-internal whole-GCM buffering must not force plaintext spooling or uncontrolled memory.
- A valid Primary hold must have a reviewed paired-auth recovery path when ordinary Hidden media admission is blocked by index corruption or exhausted media query budget.

---

### Task 1: Freeze Hidden schema-2 selection and reachability

**Files:**
- Create: `docs/phase3/STORAGE_FORMAT.md`
- Modify: `docs/superpowers/specs/2026-10-02-phase3-hidden-media-transfer.md`
- Create: `docs/phase3/test_storage_format_vectors.py`
- Create: `docs/phase3/storage-format-vectors.json`

**Interfaces:**
- Consumes: existing schema1 bootstrap/descriptor/index/selection in `docs/phase2/STORAGE_FORMAT.md` and frozen F1 contexts.
- Produces: exact byte grammar for schema2 bootstrap/descriptor/media index/references/reservations, canonical order/count limits, and authenticated reachability rules; test-only synthetic vectors, no production writer.

- [ ] **Step 1: Write failing test-only grammar cases**: reject unknown version, duplicate IDs, trailing bytes, over-limit counts, a circular receipt/index hash graph, and a missing retained verification-index dependency after credential rotation.
- [ ] **Step 2: Run** `python3 -m unittest docs/phase3/test_storage_format_vectors.py`; expected missing candidate/vector failure before the grammar exists.
- [ ] **Step 3: Specify exact fields/offsets, context identities and dependency DAG**. Retain an immutable copy of each receipt's originally committed encrypted purpose1 index and its actual-key usage ownership outside credential-generation retirement. A later credential selection may change its own index envelope/hash; it does not rewrite the immutable receipt or silently retire that snapshot.
- [ ] **Step 4: Regenerate synthetic vectors and run the same command**; all grammar/boundary tests pass. These are reference evidence, not production durability evidence.
- [ ] **Step 5: Commit** `docs: freeze Phase3 Hidden selection and receipt reachability candidate`.

### Task 2: Freeze Primary transfer catalog and hold protocol

**Files:**
- Create: `docs/phase3/PRIMARY_TRANSFER_FORMAT.md`
- Create: `docs/phase3/test_primary_transfer_vectors.py`
- Create: `docs/phase3/primary-transfer-vectors.json`
- Modify: `docs/phase3/NEGATIVE_TEST_MATRIX.md`

**Interfaces:**
- Consumes: Task1's exact destination evidence; unchanged Primary VaultItem/index/payload/PGVIDEO1 and archive-v1 contracts.
- Produces: fixed-root namespace/bootstrap/selected-catalog grammar, complete source snapshot revision, purpose-separated Primary-only journal/hold authentication, exact cleanup/restore intents and reachability rules; no source migration.

- [ ] **Step 1: Write failing cases** for digest-equal metadata races, recovery hold after ordinary index removal, mutated/deleted collection covers, backup omission, namespace replacement, and cleanup interrupted after unlink.
- [ ] **Step 2: Run** `python3 -m unittest docs/phase3/test_primary_transfer_vectors.py`; expected failure before candidate/vector generation.
- [ ] **Step 3: Pin canonical encoding/context and ownership order**, including source snapshot ID/gen1 binding to the full fingerprint, unchanged payload path ownership, preserved metadata/covers and reserve-ID rules. Specify restore merge/conflict behavior and why every existing backup/restore/reconciliation/setup/delete path must honor the catalog.
- [ ] **Step 3a: Specify restricted hold-recovery authentication** for corrupt media index or exhausted media query service. It still requires independent Hidden credential proof and Primary authentication, issues no ordinary media/write/delete capability, and never restores from a Primary-only fallback. Pin which surviving selected credential/catalog/descriptor evidence is required and how missing evidence fails safely.
- [ ] **Step 4: Run the same command**; exact vectors/adversarial parsers pass. No claim that test tooling is an Android storage implementation.
- [ ] **Step 5: Commit** `docs: freeze Phase3 Primary recovery hold candidate`.

### Task 3: Freeze video multi-invocation usage and owned streaming

**Files:**
- Create: `docs/phase3/VIDEO_AND_USAGE.md`
- Create: `docs/phase3/test_video_usage_vectors.py`
- Create: `docs/phase3/video-usage-vectors.json`
- Modify: `docs/phase3/NEGATIVE_TEST_MATRIX.md`

**Interfaces:**
- Consumes: frozen F1 purpose10/11 headers and actual-key HKDF domain; Task1 media-ledger ownership.
- Produces: exact durable pre-charge/query ledger grammar, charge overflow/restart rules, pinned channel/reader ownership and non-resumable attempt semantics; test-only vectors.

- [ ] **Step 1: Write failing cases** for shared-key versus per-chunk accounting, interrupted charge promotion, missing ledger, successful queries at cap, exact final partial chunk and unknown-length provider reread mismatch.
- [ ] **Step 2: Run** `python3 -m unittest docs/phase3/test_video_usage_vectors.py`; expected missing specification/vector failure.
- [ ] **Step 3: Define exact offsets, limits and charge-before-GCM order**. Each header/chunk key domain has its own accounting; all chunks sharing a purpose11 key share its aggregate usage. Persist fresh reservation before encryption; no interrupted attempt resumes. Initially unknown-length input requires two bounded passes and exact comparison: product byte quota,20minute total deadline and30second no-progress deadline per pass, safe overflow/zero-read rejection and owned-descriptor cancellation, with no plaintext spool.
- [ ] **Step 3a: Define full-operation query preflight/exhaustion**, including8192 chunk queries for one8GiB verification sweep; include other playback/preview queries. If a required full sweep cannot fit, deny cleanup/retirement and preserve source/hold; never reset ledger or partially verify and claim full evidence.
- [ ] **Step 4: Run the same command**; all arithmetic/canonical vectors pass. Record actual Android provider-memory and directory-sync tests as pending hardware/implementation gates.
- [ ] **Step 5: Commit** `docs: freeze Phase3 video usage admission candidate`.

### Task 4: Resolve integrated design and implementation admission

**Files:**
- Modify: `docs/phase3/DESIGN_REVIEW.md`
- Modify: `docs/phase3/EXIT_GATES.md`
- Modify: `PRIVATE_GALLERY_2_0_PHASE_3_REPORT.md`
- Create only after admission: `docs/superpowers/plans/2026-10-02-phase3-media-implementation.md`

**Interfaces:**
- Consumes: complete Tasks1–3 candidate formats, dependency DAG, Primary/Secondary lock subset order and backup/lifecycle rules.
- Produces: independent design verdict with no unresolved Critical/Important findings, then a bounded production plan with exact Kotlin API ownership and negative-test assignments.

- [ ] **Step 1: Walk every P3-N/P3-F case and exit gate against the frozen contracts**; record uncovered behavior rather than treating planned tests as PASS.
- [ ] **Step 2: Independently review the format DAG, source/hold loss invariants and lock order**, including credential retirement, subsequent Hidden edits/deletion, later Primary writes, missing query ledgers, controller gates, preview-cache→metadata order, producer/reader/player locks and cleanup-complete barriers. No joining/close callback while holding locks a worker requires.
- [ ] **Step 3: Fix all Critical/Important design findings and record concrete rulings/evidence**; do not invent an approval or waive a load-bearing problem because CI is green.
- [ ] **Step 4: If internally consistent, write the exact production task/API/file plan** in the specified path and implement under existing conditional owner authorization. If still inconsistent, stop product implementation and report the actual unresolved findings.
- [ ] **Step 5: Keep all product/signed/owner gates PENDING** until their own exact evidence exists. A passing reference-vector design is not Phase3 GO.

## Production dependency order after admission

1. Versioned Hidden admission/index preservation/retirement reachability, with Phase2 regression.
2. Frozen F1 video codec and media usage accounting; same-object scoped readers.
3. Hidden import/view/preview/video/local-crop/collections/trash and picker resource lifecycle.
4. Primary read-only source/complete revision/namespace/hold and backup/restore enforcement.
5. Paired transfer coordinator, verified receipt selection, exact restart/recovery/restore/cleanup.
6. Whole-branch security review, every negative/fault case, exact push+PR CI, permanent-signer APK verification, focused owner acceptance.

This is a design-admission plan, not a claim those production tasks are already
specified, implemented or passing.
