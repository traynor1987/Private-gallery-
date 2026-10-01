# Private Gallery 2.0 — focused Phase 0 admission closeout

1 October 2026. Authority: `PRIVATE_GALLERY_2_0_ARCHITECTURE_AUDIT_2026-09-29.md` §§26–28 and the owner's subsequent focused gate-closure instruction. This report supersedes the old gap assessments in the 30 September Phase 0 report and the historical Phase 1 admission-blocked report. Final owner evidence below closes the focused mandatory admission gates. The owner authorizes Phase 0 integration through PR #56 and verified main CI only. This report authorizes Phase 1 REVIEW, not implementation, Hidden functionality, migration or release.

## Recovered state and implementation scope — SOURCE FACT / IMPLEMENTED

- Starting and unchanged main: `a19218479eb9b9bcdb35ff0035fc78522263c891`.
- Accepted Phase 0 production SHA: `c0ce4013e65118843c4c69a9c868d99dc340cace`.
- Starting branch: `phase0/security-admission` at documentation/evidence commit `fb223993ad69ba3dbaac57b2c5462daffc7f5550`.
- At review start PR [#56](https://github.com/traynor1987/Private-gallery-/pull/56) was open draft and unmerged, head `dafa877cf52158259d7882cb885ec3b376f5547c`, base main `a19218479eb9b9bcdb35ff0035fc78522263c891`. The owner now authorizes its safe merge after final admission review. The resulting integration SHA and terminal main CI will be recorded in that PR's closeout record after verification; they are not claimed in advance.
- Existing Android push/PR runs `36854771568` and `36854776273` on `fb223993` are completed/success. Accepted-source runs `36843855926` and `36843862850` remain completed/success on `c0ce4013` (139 Android cases).
- New production changes: **NONE**. Additions extend synthetic tests and reconcile admission documentation. No crypto-format change, owner-media operation, migration, recovery rotation, production second container/key/root/UI, Phase 1 branch or publication occurred.

Test changes: `app/src/androidTest/java/uk/co/traynor/privategallery/core/vault/Phase0FrozenRestoreRehearsalTest.kt`, new `Phase0SyntheticRestoreFixture.kt` in that same directory, and `app/src/test/java/uk/co/traynor/privategallery/core/vault/VaultBackupArchiveTest.kt`. Documentation changes: this report; `PRIVATE_GALLERY_2_0_PHASE_1_REPORT.md`; `docs/phase0/PHYSICAL_ACCEPTANCE.md`; `docs/phase0/SECURITY_CONTRACT.md`; `docs/phase0/evidence/OWNER_ACCEPTANCE_ADMISSION_2026-10-01.md`; historical `docs/phase0/evidence/FINAL_VERIFICATION.md` and `recovery-backup.md` supersession links; new B02 and clean-state restore records linked below, plus extracted public synthetic `docs/phase0/evidence/clean-state-restore-1cf38b6.json`. Test milestone commit: `1cf38b6e23cfd2964ac82c415e305149b489b59b` (three test files only), pushed to the existing Phase 0 branch. Evidence documentation remains separate. Exact final diff inventory is recorded after verification.

## Previously unresolved gates

| Gate | Current disposition | Evidence and scope |
| --- | --- | --- |
| B02 Android cloud/D2D and API36.1 cross-platform exclusion assurance | PASS for supported app-controlled policy | [Pinned official implementation, configuration and mutation evidence](docs/phase0/evidence/BACKUP_PLATFORM_ASSURANCE_2026-10-01.md). No production/XML fix is needed. |
| Samsung/OEM transfer behavior beyond Android's supported app controls | RESIDUAL PLATFORM LIMITATION — DOCUMENTED / ACCEPTED under the owner's focused instruction | No physical Smart Switch test or universal vendor guarantee is claimed. Optional synthetic vendor measurements remain useful; an observed copy would require reassessment. No destructive migration is requested. |
| Confirmed synthetic recovery → actual encrypted export → separate clean destination/new PIN → authenticated full restore | PASS — AUTOMATED TEST EVIDENCE | [Actual adapters, immutable ciphertext, independent logical expectations and failure cases](docs/phase0/evidence/CLEAN_STATE_RESTORE_2026-10-01.md). No owner restore or Samsung physical device claim. |
| Synthetic interruption / storage / cancellation semantics | PASS — AUTOMATED TEST EVIDENCE | Existing Phase 0 payload/index/recovery faults retained; added backup ENOSPC/cancel, restore read interruption, and six before/after restore-commit actions. |
| Owner-safe independently retained backup and matching recovery possession | PASS — OWNER-REPORTED POSSESSION | On 1 October the owner confirms an independent encrypted Private Gallery backup and its matching recovery material are retained. No secret or backup content requested, exposed, copied or recorded. This is possession evidence, not an owner-data restore claim. |
| Signed exact same-signer in-place upgrade/existing Primary accessibility | PHYSICAL ACCEPTANCE PASSED | Exact #51 owner report below; no individual tests inferred. |
| Ordinary physical PIN/biometric and Primary lifecycle/revocation | PASS — OWNER-REPORTED PHYSICAL ACCEPTANCE | The owner confirms the remaining ordinary Primary PIN/biometric/lifecycle/revocation checks from the focused [owner checklist](docs/phase0/PHYSICAL_ACCEPTANCE.md) are all good. Grouped confirmation is retained as reported; no per-step timings, logs, hardware metadata or destructive test results are inferred. |
| Actual firmware/power-cut filesystem durability / destructive Keystore invalidation | RESIDUAL PLATFORM LIMITATION — DOCUMENTED / ACCEPTED for this milestone | Synthetic exceptions/reconstruction are not real power loss or enrollment invalidation. No stronger durability guarantee is claimed; transaction/migration proof remains required before any future destructive migration. |
| Hidden Browser at-rest/process/provider and Tor | Separately CLOSED / proposed future design | Audit §28 permits Vault-first phases independently. No implementation or feature acceptance is claimed. |

## B02 — SOURCE FACT / AUTOMATED TEST EVIDENCE

The manifest denies cloud backup and legacy full backup. The API31+ XML excludes all nine supported domains at `.` independently in cloud and D2D modes. Existing source/merged/packaged verifier and mutation tests remain enforced by CI.

Official pinned Android16 QPR2 `BackupEligibilityRules` requires `allowBackup` **and** explicit supported iOS platform parameters for cross-platform admission. Both conditions are false for this app. The parser and restore acceptance/matching call chains are documented with decoded-source hashes; upstream tests were inspected, not executed. This resolves the former missing-mode uncertainty without dummy iOS identities or an invented universal transport guarantee. Normal API36 emulator execution does not become API36.1 transport execution.

Samsung documents consumer selection and enterprise Knox restrictions; neither is evidence of an ordinary app-enforceable universal OEM exclusion. Private Gallery has exhausted established supported app controls. OEM/privileged copies outside that contract remain residual platform behavior, distinguished from an implementation defect.

Fresh source and exact signed #51 packaged checks PASS; 14 Python mutation tests PASS with zero skips. The signed APK signature was freshly verified locally (v2, one RSA4096 signer). Current merged/debug packaged checks and complete Android execution are recorded in the final CI section when complete.

## Synthetic independent recovery/restore — IMPLEMENTED / bounded evidence

The source consists of immutable independent Python-authored encrypted v6 index/image/two-chunk PGVIDEO1 ciphertext. The source PIN unlocks through the actual adapter and opens a real Primary operation. Actual recovery is created pending, reconstructed, denied export before confirmation, confirmed by matching secret re-entry and reconstructed before actual repository export. Synthetic Browser/AI/plaintext canaries prove exact archive allowlisting.

Destination is a different randomly named root plus different empty preference namespace. It starts with no Vault or local slots, restores with matching recovery/new PIN, rejects source PIN and reconstructs adapters/session. Assertions compare item count and every fixture-populated filename/type/time/state/nonce/digest, source/provenance/vaultOnly/trash fields, collections/membership/Favourite/crops, ciphertext inventory and recovery-envelope bytes. Complete authenticated image/video plaintext lengths/digests must match independent expected values. The video is a real PGVIDEO1 container of synthetic bytes, not playable MP4 evidence. Non-null pinned collection destinations are not populated here and are explicitly uncovered; the frozen corpus is not regenerated.

Wrong matching-format recovery and video-tag tamper with recomputed public manifest digest must leave destination files/slots untouched. Six before/after real restore action exceptions reconstruct adapters, retain verified ciphertext after root promotion, block empty setup, and allow matching-archive retry. Source ciphertext/preferences and independent archive remain unchanged. New JVM tests inject write ENOSPC, cancellation and interrupted archive reads, require only owned stage cleanup and authenticated retry. These are application-level deterministic faults, not genuine process kill, reboot, power cut or physical disk-full measurements.

## Interruption / safe restart matrix — AUTOMATED TEST EVIDENCE

Fresh debug JVM results below are measured: zero failures/errors/skips in each class. Parameterized boundaries are cases within test methods, not inflated JUnit test counts.

| Boundary/invariant | Actual test and measured coverage |
| --- | --- |
| Encrypted index staging/write/sync/verify/promotion | `PrimaryWriteFaultsTest` 8 methods PASS; all eight INDEX checkpoints throw ENOSPC, fresh store sees authenticated before/after generation, old bytes retained until promotion, stage cleared and retry valid. |
| Whole/chunked payload staging/write/sync/verify/promotion | Same class; all eight PAYLOAD checkpoints for each format (16 cases), cancellation leaves existing verified source unchanged, after-promotion ciphertext retained as safe orphan rather than silently indexed/overwritten. |
| Corrupt/truncated stage, denied/skipped commit, destination race, leftovers | Same class; stages cannot promote, competing verified copy cannot be replaced, reconstructed stores reconcile interrupted write/delete while preserving indexed ciphertext. |
| Pending recovery/confirmation/write failure/rollback/reconstruction | `RecoverySetupLifecycleTest` 10 methods PASS: no export/unlock while pending, matching active VDEK required, failure cannot establish confirmation, corrupt/partial records deny setup, legacy envelope preserved; in-memory false commit is fail-closed. Android preference reconstruction has separate instrumentation coverage. |
| Backup write/storage/cancel | `VaultBackupArchiveTest` 4 methods PASS (two new): ENOSPC after 0/128/4096 bytes and cancellation preserve source; fresh export/reader authenticates matching retry. |
| Restore read interruption | Same class: read interruption at 0/128/half archive removes only owned stage, retains source/independent backup bytes, fresh reader authenticates retry. |
| Restore root/recovery/PIN install | Three before + three after actual adapter actions; instrumentation PASS in PR CI `36858153696` (all four frozen rehearsal methods, 142 total cases/zero failures/errors/skips). Failure must preserve verified promoted root, clear partial slots, deny fresh setup and allow matching retry. |
| Wrong secret/rehash tamper/full independent restore | New actual-adapter Android tests plus unchanged immutable restore; instrumentation PASS in PR CI `36858153696` (all four frozen rehearsal methods, 142 total cases/zero failures/errors/skips). Complete authenticated payload digests, metadata and recovery bytes are required. |
| Compatibility/ABA/expiry/resource denial | `Phase0FrozenLegacyTest` 9 methods, `PrimarySessionAuthorityTest` 11 methods and the complete 382-case JVM suite PASS. Normal Android integration remains required. |

No fault was injected into owner paths. The fixtures/test streams are encrypted; no plaintext archive/staging output is created. Reconstructing stores/adapters after an injected exception does not establish actual process death or directory/power durability. These bounded semantics plus documented residual limitations satisfy the current synthetic-first instruction; they do not authorize future destructive migration.

## Compatibility and immutable identity — AUTOMATED TEST EVIDENCE

Before/after fixture verification must retain all 27 objects and identical instrumentation assets. Existing v1–v6/PIN/recovery/image/video/backup tests remain intact. Installing this gate-closure source requires no owner-data migration because it changes no production or release-build inputs.

| Identity | SHA-256 |
| --- | --- |
| Immutable `SHA256SUMS` manifest | `cd26479fe9b077d81cd52b0c674900f0c475508e7081324cee396512770cc7bc` |
| Frozen independent `backup-v1.pgvault` | `27fd27c5574cd31182effcaba8a8e2389108666df9e320e17d4b46ddcd7251cb` |
| Unchanged/restored encrypted v6 index | `19f1fc8f99fec890c77c335bcac86d66dd61ab251ad5df131b5ca19e721c592d` |
| Unchanged/restored encrypted image payload | `696a27efd39c1958152c61a5b0228c2315cc5c516b10df227e36f46f44dfd9af` |
| Unchanged/restored PGVIDEO1 ciphertext | `281fdad875af45982f79dd8ed15816a338833283a7c0b68812f29b38abeccb79` |
| Restored whole image plaintext, 279 bytes | `a84e734c4490e841122f51990b69e2a0fb055f47b5b08cc7f67d3b5b2d67a1cd` |
| Restored whole PGVIDEO1 plaintext, 1,048,609 bytes | `97a4d1788a0f6720b3840384ce4fdfb003ad1bd85ec2fb049cb5aa75ac426c5e` |

New confirmed-recovery export uses a fresh synthetic random secret/envelope per run, so its archive digest is run-specific and is recorded with ciphertext/media digests in CI additional test output after successful full authentication; no owner secret is recorded. Authenticated restored content and verbatim ciphertext, not merely ZIP readability, are the asserted invariants.

## Signed acceptance — SIGNED BUILD EVIDENCE / PHYSICAL ACCEPTANCE EVIDENCE

[Signed Device Acceptance Build #51 / run 36851657287](https://github.com/traynor1987/Private-gallery-/actions/runs/36851657287) succeeded on exact `c0ce4013e65118843c4c69a9c868d99dc340cace`. APK SHA-256 `306d1f29a7bcaa7c28eccf024c04ecd59ff3da0e5562847182e14b72d217827d`; signer certificate SHA-256 `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`; artifact ZIP SHA-256 `5acbef57c289e72149b49d2ba7e9dab2f0da6333d375a34633a13637253d38db`.

The owner reported that this exact signed acceptance APK was installed **in place**, without uninstalling or clearing data, and that the existing encrypted Primary remained accessible and functional. That reported milestone is physical PASS. In a subsequent final statement on 1 October, the owner confirms that the remaining requested ordinary Primary PIN/biometric/lifecycle/revocation checks from the focused Phase 0 checklist passed, and that an independent encrypted Private Gallery backup and its matching recovery material are retained. This closes the remaining focused owner acceptance and possession gates. The grouped statement is recorded accurately; no per-step logs, timings, configured timeout, Fold-specific measurement, device/Android/One UI metadata, destructive enrollment test, physical power-cut/OEM transfer or owner-data restore is inferred. No recovery secret was requested, exposed, copied or recorded.

`app/src/main` Git tree is `9cd84398f80363ec69730fa7b806b91b064c2387` both at accepted SHA and gate-closure HEAD. Release build scripts/dependencies/wrapper, manifest/resources, release/main source sets and signing workflow are unchanged. Recursive `git ls-tree -r` records for `app/src/main`, app/root Gradle scripts/settings/properties, `gradle`, wrapper scripts and the signed-device workflow match exactly across 159 files; record SHA-256 `6a4fe71d666ceb9b8dcd3fc1cede763824baa45b61a4481df8061189171cf0d7`. The complete acceptance-to-closeout diff contains only tests/docs, so no new release source set is introduced. Tests/docs do not feed the release variant. This establishes relevant release-input identity; it does not falsely claim a newly rebuilt APK is byte-identical. #51 remains evidence for its exact accepted SHA and unchanged production inputs. A new signed artifact is mandatory if any production/release input is subsequently changed.

## Complete verification — actual results only

Local `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` PASS (5m14s, 382 JVM cases/zero failures/errors/skips); the digest-output instrumentation addition separately compiled PASS (31s). Source + fresh merged manifest + debug packaged exclusions PASS. Isolated `testPhase0EvidenceUnitTest lintPhase0Evidence assemblePhase0Evidence` plus gated `assemblePhase0EvidenceAndroidTest` PASS (389 JVM cases, zero failures/errors/skips). Normal PR Android CI [36858153696](https://github.com/traynor1987/Private-gallery-/actions/runs/36858153696), job `110355501042`, completed SUCCESS on exact test milestone `1cf38b6e23cfd2964ac82c415e305149b489b59b`; every required step passed. Downloaded XML independently confirms 382 debug JVM/89 suites, 389 evidence JVM/90 suites, 142 API36 Android cases; all zero failures/errors/skips. Complete scope includes source/mutation exclusions, immutable assets, future vectors, Node, no-secret, WireGuard/notices, both variant builds/lint and instrumentation. Sibling push Android [36858147746](https://github.com/traynor1987/Private-gallery-/actions/runs/36858147746), job `110355481715`, also completed SUCCESS; every required step passed and its log reports 142 Android cases completed. These runs cover exact test milestone sources; later evidence-only documentation does not alter their tested inputs. Local commands required temporary SDK/JDK setup; earlier missing-tool/dependency failures are environment failures, superseded by the measured successful commands above, not hidden product failures. Fresh autonomous non-Gradle results: all 27 immutable hashes, identical instrumented assets, source/exact signed APK exclusions, 14 exclusion tests/zero skips, five future vector tests, three Node tests, no-secret scan, WireGuard production-path and packaged notices policy PASS. Exact `assets/third_party_notices.txt` equality was freshly verified inside both signed #51 and debug APKs; notice SHA-256 `bfc3452aabdf772490f0670bd475180a2b02daa666d61a1b2c87d06205f3bfe6`. CI merged/debug packaged verification passed. Downloaded artifact `11161166357` (62,295,577 bytes), SHA256 `bcfa87759f2f98c57995955e1a8801739db3690b3758d55761510c18a9ef2d5a`, identifies retained logs/XML/APKs/additional output; debug APK SHA256 `7e6a8468f80a3508ab1ef184c2b72ed270162fb9760d6ad5526c6dea245d0163`. [Exact synthetic restore JSON](docs/phase0/evidence/clean-state-restore-1cf38b6.json), SHA256 `fdd510c1bc48460013c3482dde6f670346e573f5c2f9a2309f5f233f91b815c2`, records run-specific confirmed backup SHA256 `6d788b639bbb177a8d46fcd839c71cda0b9177b22102faeda97e8e778cab99b0`, empty/distinct destination, authenticated new PIN/recovery and matching ciphertext/media hashes. API36 device model: Google ATD built for x86_64. This is application-level emulator evidence, not a physical separate Samsung device.

Fresh independent static review found no blocking test-code finding. One evidence overclaim about non-null pinned destinations was narrowed; no immutable fixture changed. It checked synthetic namespace isolation, real adapter/export/commit chains and the pinned cross-platform policy. It executed no Gradle or physical test and supplies no runtime PASS.

## Final owner evidence and fresh exact CI — 1 October 2026

PHYSICAL ACCEPTANCE EVIDENCE — the owner reports: "The remaining requested physical checks passed." The owner confirms the ordinary Primary PIN/biometric/lifecycle/revocation checks from the focused Phase 0 checklist are all good. The owner also confirms an independent encrypted Private Gallery backup and its matching recovery material are retained. This is an aggregate owner confirmation of that focused checklist on the already accepted #51 installation, not independently observed device telemetry or a destructive restore. No recovery secret, PIN, backup filename/content or private media is recorded.

AUTOMATED TEST EVIDENCE — fresh GitHub run metadata verifies both terminal closeout runs on exact `dafa877cf52158259d7882cb885ec3b376f5547c`:

| Event | Run / job | Actual result |
| --- | --- | --- |
| push | [36860505285](https://github.com/traynor1987/Private-gallery-/actions/runs/36860505285), job `110363261383` | COMPLETED / SUCCESS; every required step successful. |
| pull_request | [36860512264](https://github.com/traynor1987/Private-gallery-/actions/runs/36860512264), job `110363282277` | COMPLETED / SUCCESS; every required step successful; base `a19218479eb9b9bcdb35ff0035fc78522263c891`. |

Both ran the normal complete Android workflow: policy/mutations, immutable fixture checks, future vectors, Node, no-secret, WireGuard/notices, debug unit tests/lint/app and instrumentation builds, merged/packaged exclusions, isolated evidence variant builds/tests/lint and normal API36 instrumentation. No new local Gradle execution or newly measured test count is claimed for this review. The final owner-evidence commit changes Markdown only; its CI and merge result are verified separately and recorded in PR #56.

## Final admission review and exact stop boundary

Reviewed audit §§26–28, current security/credential/future-format contracts, immutable legacy/fault/recovery records, supported B02 assurance, authenticated independent synthetic restore, signed #51 release-input identity, current focused owner checklist and exact terminal CI. Mandatory Phase 0 gates are satisfied under the owner's focused supported-controls/synthetic-first instruction. No gate was weakened to produce this result.

The future-format and independent-credential documents are specifications, not implemented Hidden crypto. Hidden Browser/provider/at-rest/process/Tor stay separately CLOSED; OEM/privileged transfer, actual power loss, destructive Keystore invalidation and future cryptographic implementation/benchmark/review retain their documented residual or later-phase gates. None is relabeled as a measured physical PASS. The previous bounded in-place upgrade remains valid and is not expanded into unreported tests.

The owner authorizes removing obsolete blocked/draft state from PR #56, preserving the security/audit commits through the repository's normal safe merge, verifying the resulting exact main SHA and full main CI, then STOPPING. Any intervening production/release input change or genuine failing mandatory check stops integration and requires reassessment. No Hidden container/master key, Jenna Protocol, owner Primary migration, Phase 1 implementation, release or publication is authorized.

PHASE 0 RESULT: GO FOR PHASE 1 REVIEW
