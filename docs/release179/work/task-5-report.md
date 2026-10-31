# Task5 recovered export completion — fix round1 report

2026-10-31. Recovered implementation retained and extended from frozen source b54bc5ca3e478539a755de0dbe59aa570a250e3d; original Task5 worker supplied no completion report. This report supersedes that absence. E1–E5 source fixes and focused test preparation are complete for independent review. Android/Kotlin compilation, instrumentation, BouncyCastle runtime and PdfDocument/PdfRenderer execution are still **UNCOMPILED/UNRUN** in this worker. No release/runtime approval is implied.

## APIs and behavior

Preserved blocking/off-Main entry points:

- `EvidenceArchive.write(EvidenceExportPlan, EvidenceSourceReader, CharArray, OutputStream): EvidenceExportReceipt` defaults encrypted; only `plaintextConfirmed=true` writes plaintext ZIP. Checks digest, owner, direct+retained source policies before admission, around verified streaming and before completion. Attached originals are counted/streamed independently of provenance-only contributors; actual size/SHA-256 must match. Caller passphrase/output remain caller-owned.
- `EvidencePdf.render(EvidenceExportPlan, EvidenceSourceReader): ByteArray` returns an ordinary bounded20MiB PDF. `text(Selected)` produces deterministic labelled source/OCR/correction/AI/provenance transcript with lexical embedded-metadata ordering. Original source bytes are not rendered/decoded.
- `EvidenceArchive.open(Context, expectedOwner, InputStream, CharArray, current): EvidenceOpenedArchive` stages ciphertext once privately, seals/holds one descriptor, fully authenticates before ZIP parse, then strictly parses/hashes/drains and authenticates again before returning the report.
- `EvidenceOpenedArchive.owner`, `reportBytes`, `originalCount`, `integritySummary`, `ciphertextSha256`, `withReport(consume)`, `writePlaintext(output, confirmed, current)` and idempotent `close()` remain. Plaintext re-export requires explicit confirmation and active authority, freshly authenticates before output, then streams the held ciphertext again. Close retires access immediately and wipes report/key/header plus deletes stage after any synchronized borrow/write completes.
- New package-owned `EvidenceWipingBuffer` bounds incremental growth and wipes every replaced backing allocation, then its final allocation on close. Returned copies have separate owners. PDF and parsed metadata/report buffers use it. Oversized newly allocated UTF8 metadata copies are also wiped before throwing.
- `EvidenceOpenedArchive.openForTest(...)` is an internal fault-injection wrapper over the same private implementation, with only a lowerable ciphertext quota and phase observer. The normal five-argument API and production quota remain unchanged. Hooks exercise staging, first authentication, second parse and final validation without a mutable global bypass.

Coordinated minimal model/codec/AI additions (root approved before edits):

- `EvidenceCatalog.SourceReference(String evidenceId, SourceFingerprint fingerprint)`; `Analysis.sourceReferences()` and `Incident.analysisIds()` trailing record fields, with old11-/12-argument constructors preserved.
- `saveAnalysis` derives new mappings from selected live records, ignores supplied mapping claims and preserves old mappings on application. Constructor/codec validate against lineage and any live matching ID; successor checks prevent fabricated/changed mappings. Removing a record changes live references only; original accepted JSON, mappings, lineage and associations survive.
- `associateAnalysis(String analysisId,String incidentId,long now)` provides durable, explicit owner application in an audited mutation, marks applied and unions all source lineage. Repeat association is a no-op. Upsert preserves old associations with compatible constructors; unlink/removal retain; merge unions; split retains with its derived content/lineage.
- `EvidenceAi.details` retains its strict exact JSON schema and validates raw suggestion/event IDs against saved immutable reference mappings (unsaved old-constructor results use their live validated selection). No raw-ID discovery or fingerprint-overlap association is permitted.
- Export requires every retained contributor's current same-owner original with exact fingerprint and allowed policy. An explicitly associated A+B advisory survives unlink/removal of B. Only A is attached; B's mapping/fingerprint is labelled historical/unattached, with no B original/name/notes/OCR copied. Unrelated A+B advisory is excluded even if the selected incident has the same lineage. Other analyses require all mapped input IDs attached.

## Format and compatibility

Crypto container header remains version1; fixed scrypt/AES-GCM parameters, AAD and existing known-answer vector are unchanged. Hash manifest remains version1. Portable `evidence.json` moves to schema2 with explicit association/reference fields; exact canonical regeneration remains required. Unreleased portable schema1 is rejected as unsupported, rather than fabricating historical reference authority. Root confirmed no1.7.9 Evidence Locker APK/export had been delivered.

Local EvidenceCatalog codec writes2 and reads1 conservatively. Version1 can recover mapping only for still-live selected records. It preserves raw text/lineage but cannot reconstruct removed IDs or incident association from JSON or overlap. Such ambiguous legacy structured references continue to fail interpretation; no silent authorization expansion. App version1.7.9/code51 and1.7.8 original storage formats remain unchanged. Migration remains subject to the existing4MiB catalogue quota.

## Files changed

Product files under `app/src/main/java/uk/co/traynor/privategallery/core/evidence/`:

- `EvidenceArchive.kt`: explicit incident association selection; attached-record analysis projection; strict schema2/reference projection; canonical embedded-key order; combined quota error; failed UTF8 allocation wipe.
- `EvidencePdf.kt`: deterministic metadata ordering, advisory association/reference labels, bounded wiping output.
- `EvidenceOpenedArchive.kt`: bounded wiping parsed-entry buffers, unchanged production API with deterministic internal test hooks, combined metadata quota check.
- `EvidenceWipingBuffer.java`: bounded grow-and-wipe ownership helper.
- `EvidenceCatalog.java`, `EvidenceCodec.java`: validated immutable historical mappings, durable incident associations, compatible constructors, local v2/v1-read, transition guards and merge/split retention.
- `EvidenceAi.kt`: only the saved-details allowed-ID authority changes to validated historical mappings; credentials/transport untouched.

Focused tests/tooling/docs:

- `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceArchive179Test.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidencePdf179Test.kt`
- `tests/evidence179/EvidenceHistory179HostTest.java`
- `tests/evidence179/EvidenceWipingBufferHostTest.java`
- `tests/evidence179/EvidenceArchiveFormatHostTest.java` (existing trailing whitespace cleaned only)
- `scripts/check_evidence179_archive.sh` (now includes both new host suites so grouped CI does not omit them)
- `docs/release179/EXPORT179.md`, `CONTRACTS.md`, `work/task-6-fix1-brief.md`, this report.

No Main/UI/controller/credential/transport/signing/CI-workflow changes. No Git operations, CI runs, subagents or original deletion/import mutation by this worker. Root owns Git and disjoint compile diagnostics.

## Observed local verification

JDK17's `java com.sun.tools.javac.Main` is available; the `javac` launcher is absent (`javac: command not found`, exit127 on the initial attempt). The following affected-only compile and execution completed successfully after the final model/test changes:

```sh
java com.sun.tools.javac.Main -Xlint:all -d /tmp/evidence179-fix1-model \
 app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceCatalog.java \
 app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceCodec.java \
 app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceWipingBuffer.java \
 tests/evidence179/EvidenceHistory179HostTest.java \
 tests/evidence179/EvidenceWipingBufferHostTest.java
java -cp /tmp/evidence179-fix1-model uk.co.traynor.privategallery.core.evidence.EvidenceHistory179HostTest
java -cp /tmp/evidence179-fix1-model uk.co.traynor.privategallery.core.evidence.EvidenceWipingBufferHostTest
```

Observed exit0, no compiler warnings, exact output:

```text
EvidenceHistory179HostTest PASS 40
EvidenceWipingBufferHostTest PASS 19
```

An additional compile-only invocation included `EvidenceArchiveFormat.java` and `EvidenceArchiveFormatHostTest.java` alongside the above files and completed exit0/no warnings. `bash -n scripts/check_evidence179_archive.sh` completed exit0/no output. Existing reported `EvidenceArchiveFormatHostTest PASS 66` was not rerun for a whitespace-only test change; it remains previous evidence, not a new result. No broad historical suites were run.

History40 exercises both owners, save-time mapping capture, explicit application, compatible incident edits, unlink/removal and successor validation, v2 round-trip, merge/split, independent v1 encoding with live-only mapping migration and no association invention, forged mapping rejection and conflicting live identity. Wiping19 forces several buffer reallocations, inspects zeroed old/final allocations, verifies returned-copy lifetime, quota failure, retirement and closed-buffer refusal.

## Prepared Android gate (all pending)

Run the final `uk.co.traynor.privategallery.core.evidence.EvidenceArchive179Test` and `EvidencePdf179Test` on groupedAPI37, including the BouncyCastle known-answer case. New/strengthened method names and covered boundaries:

- `appliedHistoricalReferencesSurviveUnlinkAndRemovalWithoutOriginalOrUnrelatedNotes`: both owners; encrypted/default and confirmed plaintext; real nonempty suggestions/events; A+B apply, B unlink and record removal; unrelated B annotations/advisory exclusion; raw JSON/receipt/fingerprints; encrypted open and re-export; retained live contributor restricted/changed/unavailable before output admission.
- `advisoryAiAndUnattachedRetainedLineageStayLabelledWithoutReattachingOriginals`: now explicit apply and real suggestion/event references with all attached records removed.
- `genuinelyForeignRawAiReferencesNeverAcquireHistoricalAuthority`: raw foreign IDs fail details and both export modes with zero output.
- `reverseMetadataOrderIsCanonicalAcrossCodecWriteOpenAndReexportBothOwners`: deliberately reverse keys; identical projections either side of binary encode/decode; authenticated write/open/plaintext re-export; exact metadata/original bytes.
- `originalCount128Admitted129RejectedBeforeOutput`: actual128-original plaintext writer;129 rejected in both modes.
- `combinedMetadataAndManifestQuotaRejectsAndCleansStage`: individually sub-bound payloads exceed combined4MiB and hit the specific combined bound before schema parsing.
- `writerOriginalActualSizeHashReaderFailuresAndStreamingRetirementNeverSucceed`: actual short/long/wrong-hash bytes, reader I/O failure and retirement inside original reads in both modes.
- `wrongWriterOwnerAndDigestFailBeforeAdmission`: zero output for wrong reader owner or changed digest in both modes.
- `outputFailureDoesNotCloseCallerOrProduceReceipt`; `outputFailureAndRevocationDuringOriginalStreamingNeverReturnReceipt`: admission-output failure plus incompressible192KiB original failure/revocation while reader callback is active.
- `ciphertextLimitAndProviderFailureDeleteOnlyNewStage`: same production copier with a reduced deterministic quota; provider mid-copy failure; exact stage inventory restoration. This is not a2GiB disk stress claim.
- `bothAuthenticatedPassesCancelAndSecondPassTagFaultNeverPublishOrLeavePlaintext`: cancellation in each auth/parse phase and before publication; staged file exactly equals ciphertext; successful first authentication followed by controlled stage-tag change; second pass parses but fails tag and returns no report; stage cleanup.
- `closeWaitsForBorrowThenWipesReportAndDeletesStageAndRetiredReexportRefuses`: borrowed buffer remains owned through the scope, close wipes after it ends, held ciphertext disappears; current-authority loss refuses re-export before output.
- `plaintextReexportOutputFailureReleasesStageOnClose`: failed destination and resource cleanup.
- `multipageWrappingUnicodeAndProvenanceRemainReadableAtEndOfReport`: >4 pages; API37 PdfRenderer text extraction verifies supported accented Latin, end sentinel, provenance and correction text, plus text bounds on every page. Existing PDF header/render test retained.

Existing mandatory cases retained: `encryptedRoundTripBothOwnersAndExplicitPlaintextOriginalsExact`, `wrongPasswordLateTagTruncateHeaderAndForeignOwnerNeverReturnReport`, `deniedAdmissionProducesNoOutputAndRetainedRestrictionBlocksBothModes`, `retirementAfterOriginalReadFailsAndCloseIsIdempotent`, `fixedScryptGcmVectorPreservesWhitespaceUnicodeAndHeaderAad`, `cancelledOpenAndMalformedHeaderDeleteCiphertextStagesAndNeverCloseCallerStreams`, `fullTagAuthenticationPrecedesEvenInvalidZipParsing`, `strictSchemaOwnerDepthUnknownKeysRestrictionHashAndExtraEntriesRejectAfterValidCrypto` (also rejects portable schema1), `perEntryMetadataTranscriptAndPdfBoundsRejectBeforePublishing`, `reportHasPdfHeaderAndReadableProvenanceTextSource`.

## Remaining concerns / handoff

1. All final Android/Kotlin compile/runtime behavior is pending, including PdfRenderer API accessors/font text extraction, close-race scheduling, scrypt/GCM provider behavior, native PDF memory/long-document cancellation and grouped test duration. Local Gradle bootstrap remains unavailable; missing runtime is not approval.
2. Strict ZIP malformed-header/path/duplicate/symlink/compression/CRC/entry-count/expanded2GiB adversarial coverage remains in the preserved host-format suite. The new ciphertext-stage test deliberately lowers the same production bound; it does not write a2GiB stage locally.
3. Immutable JSON/transcript strings, platform PDF allocations and provider cryptographic internals are managed-runtime limitations; no universal heap erasure or secure disk deletion claim. Caller-owned partially written destinations require root SAF best-effort cleanup.
4. UI I5 must use `analysisIds` and `associateAnalysis` as documented in the Task6 fix brief; this worker does not alter UI application/rendering. No arbitrary overlap association or original reattachment is acceptable.
5. Legacy local v1 already-removed IDs cannot be reconstructed safely. New v2 saves retain exact mappings; old raw JSON remains preserved but may be uninterpretable after legacy deletion. Portable metadata schema2 is intentionally explicit and rejects the unreleased schema1.

## Fix round2 — F1 near-limit local-v1 migration correction

2026-10-31. Read independent `reviews/EXPORT179_FIX1.md` in full. Its E1/E2/E3/E5 source findings remain addressed; E4 runtime evidence is still pending. F1's new local-read regression is corrected in source for independent review. The earlier statement that local migration is merely subject to the4MiB quota was insufficient: existing valid annotations must remain readable when added v2 fields exceed that quota.

### Semantics and bounded ownership

A fully parsed/validated local-v1 catalogue now retains an immutable v1 serialization identity. It derives only validated still-live source mappings in memory and re-encodes all original v1 fields losslessly. No raw input byte buffer is retained. A decoder-only `EvidenceCodec.LegacyRead` capability has a private constructor and is retained only inside the codec; it cannot be obtained from a catalogue. The private serialization flag is not an exposed caller choice. Legacy construction additionally rejects associations or non-derivable historical mappings that a v1 encoding would omit.

Public catalogue construction and every actual domain mutation continue to build version2 under the unchanged4,194,304-byte persisted quota. There is no automatic downgrade on encode failure. `validateSuccessor` explicitly rejects any decoded/reconstructed legacy mutation or downgrade. `EvidenceEncryptedStore` product code is unchanged: read/no-op does not rewrite, and an overquota domain mutation throws before encrypted staging or commit. An owner can explicitly shorten a note (or make another permitted size-reducing edit); if the entire v2 result and audit fit, that audited mutation migrates normally. No content is silently trimmed/dropped, no source authority is guessed, and originals are never changed.

`EvidenceArchive.decodeMetadata` explicitly requires an embedded v2 catalogue for portable metadata schema2. This preserves the previous strict export acceptance boundary now that local-v1 re-encoding remains v1. New Android method `portableSchema2RejectsLegacyBinaryCatalog` prepares that boundary regression; it remains uncompiled/unrun with all other Android cases. Existing export/AI/history APIs and approved Task6 association flow are unchanged. Quota refusal UX semantics were appended to the Task6 fix brief.

### Reproduction and final observed checks

New `tests/evidence179/EvidenceMigration179HostTest.java` independently encodes the exact old field order, without a new-codec writer or quota bypass. It creates64 records with65,000-character notes plus one saved analysis selecting all64. Before the correction, the new test compiled and exited1 on its first read:

```text
Near-limit PRIMARY v1 bytes=4188265 maximum=4194304
java.lang.IllegalArgumentException: Invalid evidence catalog
Caused by: java.lang.IllegalArgumentException: Encoded evidence quota exceeded
```

The failure came from eager v2 constructor re-encoding, matching the independent review. Final focused command:

```sh
java com.sun.tools.javac.Main -Xlint:all -d /tmp/evidence179-fix2 \
 app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenRecordStore.java \
 app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceCatalog.java \
 app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceCodec.java \
 app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceEncryptedStore.java \
 tests/evidence179/EvidenceMigration179HostTest.java \
 tests/evidence179/EvidenceHistory179HostTest.java
java -cp /tmp/evidence179-fix2 uk.co.traynor.privategallery.core.evidence.EvidenceMigration179HostTest
```

Final compiler exit0/no warnings and test exit0:

```text
Near-limit PRIMARY v1 bytes=4188265 maximum=4194304
Near-limit HIDDEN v1 bytes=4188264 maximum=4194304
EvidenceMigration179HostTest PASS 486
```

The affected history suite was rerun after the product correction:

```sh
java -cp /tmp/evidence179-fix2 uk.co.traynor.privategallery.core.evidence.EvidenceHistory179HostTest
```

Observed exit0: `EvidenceHistory179HostTest PASS 40`. Unchanged wiping and format suites were not rerun or relabelled. `bash -n scripts/check_evidence179_archive.sh` passes; the existing grouped-host script now includes the migration suite and its real store dependencies.

The486 assertions cover both owners: exact near-limit byte lengths; readable complete notes/accepted JSON/lineage; all64 derived reference mappings; exact canonical v1 encode/read bytes; public v2 construction and a small rename refusing overquota expansion; no mutation of the original snapshot; explicit note shortening producing bounded v2 with retained other63 records/analysis/history; audit-preserving v1 round-trip; rejection of a separately reconstructed v1 successor; real encrypted store read/no-op/refusal with zero commits, byte-identical ciphertext, original decrypted payload and no residual stage; one successful bounded v2 commit after the explicit edit; and downgrade refusal. Tests use only isolated temporary fixture stores and clean them afterwards.

### Exact fix2 delta / remaining gate

Product delta from frozen fix1: `EvidenceCatalog.java`, `EvidenceCodec.java`, and one explicit v2 guard in `EvidenceArchive.kt`. Added `EvidenceMigration179HostTest.java`; added one focused Android portable-binary version test; extended `scripts/check_evidence179_archive.sh`; updated EXPORT179/CONTRACTS/Task6 fix brief and appended this report. No changes to encrypted-store product code, source fingerprint rules, credentials, transport, originals or Android build/CI files. Root's disjoint Git/compile activity is not worker verification.

All Android/Kotlin compilation, groupedAPI37 instrumentation, BouncyCastle known-answer and PDF runtime gates remain pending. F1 host evidence does not satisfy E4 or authorize release. No Git/CI/signing/subagents were used by this worker.
