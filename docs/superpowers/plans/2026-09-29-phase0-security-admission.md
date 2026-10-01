# Phase 0 Security Admission Implementation Plan

> **For agentic workers:** Use executing-plans for Activity integration and dispatching-parallel-agents for independent file-owned domains. Steps use checkbox syntax. The owner's explicit Phase 0 authorization supplies the execution scope; do not add a second permission gate.

**Goal:** Prove Primary-only security ownership and preserve owner-data compatibility while producing honest Phase 1 admission evidence.

**Architecture:** Keep the legacy Primary root/key/formats. Add revocable immutable operation authority, confirmation-only recovery, complete exclusions, immutable fixtures and deterministic failure tests. A separate nonproduction evidence package contains synthetic Browser/process probes.

**Tech Stack:** Kotlin 2.0.21, Android min26/target36, AndroidX WebKit1.12.1, JUnit4, existing AES-GCM/scrypt/Media3, Python security checks.

**Spec:** `docs/phase0/SECURITY_CONTRACT.md`, authoritative prior architecture audit and owner Phase 0 request.

## Global constraints

- No production Hidden, new owner master key, transfer, discovery, Tor, VPS, aliases, release or tag.
- No Primary VDEK/index/payload crypto format changes or owner ciphertext rewrite.
- Dedicated `phase0/security-admission` branch; main stays unchanged pending review.
- Synthetic test data only; no owner secrets, websites, media or destructive acceptance.
- Runtime-dependent/OEM facts require actual evidence; missing physical acceptance means NO-GO when it is a mandatory gate.
- Incremental tests, logical commits, source/evidence labels and final adversarial review.

## Review focus

- Lock→reunlock ABA at metadata promotion and UI delivery: old epoch denied.
- Pending recovery lost before display/confirmation: authenticated restart without replacing confirmed legacy recovery.
- Corrupt index/partial PIN preferences with existing root: no empty writable/setup overwrite.
- Native Browser/AI callbacks and readers after timeout: fixed operation ownership, no current-key lookup.
- Unsupported profile/provider or profile deletion after process death: explicit closed result, no default fallback or at-rest encryption claim.

## Task 1: Freeze contracts and establish baseline

Files: `docs/phase0/SECURITY_CONTRACT.md`, `docs/phase0/AUDIT_DELTA.md`, this plan; isolated worktree.

- [ ] Record main and audit equality; record SDK/provider/test prerequisites.
- [ ] Reproduce scoped-session, recovery, backup and diagnostic gaps against current source/tests.
- [ ] Run baseline JVM/build/security checks; preserve logs and limitations.
- [ ] Commit contracts/delta independently.

## Task 2: Revocable Primary authority and owned readers

Files: `core/security/PrimarySessionAuthority.kt`, `ProtectedSessionState.kt`, `LockSession.kt`, `core/vault/VaultVideoDataSource.kt`; corresponding tests.

Produces: `PrimarySessionAuthority.open(ByteArray)`, `.operationOrNull(): PrimaryOperation?`, `.revoke()`, `.onBackgrounded(Long)`, `.onForegrounded()`, `.cleanupComplete`; `PrimaryOperation` contract in the spec. Existing `LockSession` policy gains an independent monotonic deadline predicate. No Hidden production identity/key.

- [ ] Write tests for stale commit/publication, ABA, expiry, resource close, copied-key wipe, cancelled jobs and reader close.
- [ ] Run them and retain RED evidence.
- [ ] Implement minimal owner/controller/registry and active reader closure without changing video format.
- [ ] Run targeted tests, then relevant full suite; commit.

## Task 3: Recovery confirmation and backup exclusions

Files: recovery store/policy/tests, manifest/rules, `scripts/verify_backup_exclusions.py` and behavior tests. Root integrates UI in MainActivity after store API is ready.

Produces: pending/confirmed/legacy state API, pending create/restart and `confirm(secret, expectedVdek)`; legacy unwrap/restore unchanged. Exclusion verifier consumes source and optional merged/APK artifacts and fails on eligible domains/missing declarations.

- [ ] Write failing interruption/legacy compatibility tests and exclusion mutation cases.
- [ ] Implement separate pending state and atomic confirmation; never persist plaintext.
- [ ] Implement all-domain rules/checks and verify actual packaged/merged artifacts.
- [ ] Run tests; commit stores/rules/tooling separately from Activity wiring.

## Task 4: Immutable formats, restore rehearsal and fault infrastructure

Files: immutable `app/src/test/resources/phase0/legacy-v1/` corpus/manifest; fixture tests; `core/vault/PrimaryWriteFaults.kt` and narrowly injected index/payload/archive checkpoints; synthetic restore instrumentation.

Produces: independently serialized frozen v1–v6 index bytes, whole/PGVIDEO1 media, PIN/recovery envelopes and archive plus expected hashes/logical data. `WriteCheckpoint`/fault sink defaults no-op. Index final promotion can accept `commit: ((() -> Unit) -> Unit)` from live operation authority; default compatibility remains unchanged.

- [ ] Write tests reading frozen bytes and named corruption cases, not regenerate fixtures each run.
- [ ] Run baseline characterization; add fault tests that fail missing checkpoint/unsafe commit behavior.
- [ ] Implement bounded fault seams and guarded final promotions; no container migration/journal production implementation.
- [ ] Verify restart/retry/low-storage/truncated/corrupt staging never removes sole verified data; full restore metadata/video checks; commit.

## Task 5: Activity and protected path integration

Files: `MainActivity.kt`, `AndroidVaultRepository.kt`, scoped editor/AI integration in `ui/`/`core/editor/`, ancillary encrypted stores' guarded promotions if needed.

Consumes: Primary authority/operation and store confirmation APIs. Produces: fixed operation capture at creation, owned launch helper, epoch-checked publication and repository final commit authorization. Platform-return callbacks retain their original token and can fork only the same epoch.

- [ ] Write integration tests for stale repo mutation/publication, partial setup/corruption, editor/AI/Browser request ownership and source deletion authorization.
- [ ] Run RED behavior evidence.
- [ ] Replace copied-key Activity helpers with captured PrimaryOperation, own jobs/resources and final publication checks; scope restore/auth attempts separately.
- [ ] Wire recovery re-entry UI; retain existing confirmed/legacy secrets; no new discovery.
- [ ] Verify reads/imports/crops/collections/trash/backup/video/Browser/AI functionality and epoch negatives; commit logical slices.

## Task 6: Diagnostics and nonproduction Browser/process evidence

Files: fatal/acceptance diagnostic policies/tests; `src/phase0Evidence/` harness + evidence build type/tests; docs future crypto/credentials/browser/process/checklists.

Produces: fixed-category diagnostics with sensitive-marker tests; separate package `.phase0evidence`; no production harness entry/profile. Frozen future canonical format/KDF/AAD/nonce/parser specification and test vectors are specifications/synthetic evidence only.

- [ ] Write sensitive-marker failures and unsupported-profile tests; retain RED.
- [ ] Harden diagnostic persistence, keeping structural acceptance useful.
- [ ] Add profile probes, synthetic storage markers and supported cold-lifecycle procedure; do not claim provider/OEM tests not run.
- [ ] Record measured results individually and exact unmeasured acceptance steps; commit.

## Task 7: CI, adversarial review and admission report

Files: `.github/workflows/android.yml`, appropriate evidence workflow, docs/phase0/results/physical checklist; report outside repo saved as user artifact.

- [ ] Add explicit regression gates to normal CI and verify complete JVM/lint/debug/instrumentation path on branch.
- [ ] Run security tools, immutable fixture verification and final diff scope check.
- [ ] Inspect unsigned release packaging if useful; permanent signed acceptance remains owner/manual boundary, never fake signer or release.
- [ ] Fresh independent security review, fix in-scope Important findings with RED→GREEN tests.
- [ ] Publish branch/draft PR for review, no merge/release/tag; record ending main and branch SHA.
- [ ] Save all requested Phase 0 deliverables; evaluate A–J without weakening gates; stop.
