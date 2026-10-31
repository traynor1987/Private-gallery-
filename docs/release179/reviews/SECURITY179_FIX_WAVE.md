# Evidence179 final correction wave — scoped source review

Date: 2026-10-31. **Specification verdict: both requested source corrections are addressed; compilation and runtime verification remain pending. Implementation-quality verdict: no concrete newly introduced source defect established in this correction wave; runtime quality is unverified.** This is not consolidated security approval, release approval, signing permission or activation authorization.

This review covers only SEC179-1, SEC179-2 and regressions introduced by their complete consolidated correction. Inputs were `work/final179-fix-brief.md`, `reviews/SECURITY179_CONSOLIDATED.md`, `work/final179-fix-report.md` and `work/final179-fix-review.diff`, all under `docs/release179`. The six changed Kotlin files and static manifest were inspected against that diff. Direct unchanged archive-opening, crypto/format, original-reader, egress, document-IO and staging-cleanup interfaces were inspected only to validate these fixes. This is not a second whole-branch review.

## Finding dispositions

| Finding | Disposition | Source evidence |
|---|---|---|
| SEC179-1 — default filename in AI text | **ADDRESSED in source** | `EvidenceAi.kt:190` omits `Evidence.name()` from both JSON projection and selected-text accounting. No name provenance is invented. Local record names and exported metadata are unchanged. The existing exact-body, source/route/revision and one-shot consent machinery is unchanged. The updated fixture makes the evidence name equal the sensitive source display name; the new test checks distinct filename, sourceName and GPS sentinels against both preview and transmitted body, retains selected ID/OCR/notes, and checks zero credential reads/POSTs before confirmation and exactly one afterward. |
| SEC179-2 — public plaintext before authenticated EOF | **ADDRESSED in source** | `EvidenceArchive.kt:100` prepares ciphertext through the encrypted writer, whose complete original read, actual size/hash and post-reader/egress checks must return before preparation can continue. The completed container then passes the unchanged opener's full authentication-before-parser and subsequent strict schema/original/hash validation. Only its retained sealed private descriptor supplies public bytes; no second original read supplies output. `EvidenceLockerHost.kt:96` completes this preparation before invoking `evidenceDestination`, so an original late-tag/hash/post-reader failure cannot acquire, write, flush or close the public output. |

## Security and change-impact checks

- The preparation path streams originals in bounded buffers into private ciphertext. Each staging copy is limited by the existing container bound; no new plaintext file or whole-original memory buffer is introduced. The owner directory is 0700, stage creation is 0600 and completed stages are 0400. The original staging descriptor is closed before reopening; the validated snapshot retains the opener's single descriptor. The implementation does not expose a mutable-original reread as a snapshot.
- The random 32-byte entropy and 64-character ephemeral password are wiped in the outer `finally`, including failure of cleanup on an opened archive. Existing derivation wipes its owned password/encoding, writer cleanup wipes its key and bounded documents, and snapshot close wipes the retained key/header/report and releases/deletes its ciphertext. Both temporary files use the existing startup-cleanup naming policy. File deletion remains best effort, as before.
- Snapshot writes repeat live owner, current-authority, VaultOnly, source existence and fingerprint/lineage checks through `allowed`. The coroutine-cancelled reader remains captured by the snapshot. Existing `EvidenceDocumentIo` retirement closes admission immediately and schedules provider cancellation/descriptor cleanup off Main; the Host closes the snapshot in its IO `finally`. The original-URI protection for opened-archive re-export is untouched.
- Explicit plaintext confirmation remains required. The old four-argument plaintext writer fails before output without a private staging context, and production plaintext export now uses explicit preparation. The encrypted default still uses the same header, scrypt, AAD/GCM, ZIP entries and metadata format. Its existing four-argument production caller remains valid. The added flush guards check current authority before and after flushing.
- No concrete newly introduced Critical, Important or minor source defect was established. Plaintext preparation now requires bounded private storage, extra complete ciphertext passes and two KDF derivations; latency, cancellation responsiveness and storage-failure behavior require the pending actual Android evidence. No performance pass is inferred from source inspection.

## Regression authoring and static accounting

Seven actual new `@Test` declarations are present, each exactly once in the manifest:

1. `EvidenceAi179Test#defaultFilenameNameIsExcludedFromExactPreviewAndOneShotBodyWithSelectedOcr`
2. `EvidenceArchive179Test#plaintextLateTagHashAndPostReaderFailuresNeverTouchPublicOutput`
3. `EvidenceArchive179Test#plaintextAllOriginalsAuthenticateBeforePublicOutputAndSnapshotNeverRereadsSources`
4. `EvidenceArchive179Test#plaintextSnapshotIsIndependentOfMutableOriginalAndRechecksLivePolicy`
5. `EvidenceArchive179Test#primaryLegacyLateGcmTagFailureNeverPublishesPlaintext`
6. `EvidenceSaf179Test#plaintextLatePrimaryTagFailureNeverAcquiresProviderDestination`
7. `EvidenceSaf179Test#cancelBlockedPlaintextSnapshotProviderWriteRevokesWithoutBlockingMain`

The failure probe increments its public operation counters before checking admission; swallowed probe exceptions therefore cannot masquerade as no attempted output. Its deterministic cases distinguish EOF tag, complete-size hash, post-consumer failure, policy change and retirement. The multi-original case requires both readers to return before output and rejects rereads. The mutable-original case verifies snapshot independence, sealed-file permissions and later live-policy/fingerprint/retirement denial. The Primary late-tag test uses the actual nonchunked repository and demonstrates preliminary bytes before authentication failure. The Host test checks zero provider opens/closes; the blocked-provider case checks cancellation, descriptor cleanup, staging cleanup and absence of success.

Existing assertions were not weakened outside the required behavior changes. The AI quota fixture now counts the still-projected annotations. Existing encrypted output-failure/revocation still interrupts original streaming; the plaintext variant now interrupts authenticated snapshot output after 64 KiB and asserts the original reader is no longer streaming. Existing owner, integrity, lineage, retained restriction, wrong digest and destination ownership assertions remain.

A read-only Python inventory confirmed **153 manifest entries / 153 unique pairs**, all methods in the three affected classes included, and class counts of **21 AI / 28 archive / 31 SAF**. A line-anchored diff declaration count confirmed exactly seven new tests. These are source-accounting checks, not executed behavioral tests. No unchanged passing suite was rerun.

## Reviewed identity and evidence limits

SHA-256 values independently calculated in this review:

| Input | SHA-256 |
|---|---|
| `work/final179-fix-review.diff` | `49bebc363c8e98c2618f172f7665c6b36979c6e4f182510ca5d9f0da131a3e35` |
| `EvidenceAi.kt` | `75024884ea7efbaaabe2e80e3a21d5178fe146a0af8d5deba91dbb8ba2efec84` |
| `EvidenceArchive.kt` | `1c30e00a38fe183c68e6f070f9c7ab90da615cbaf35f26e34307c997ca3c40d9` |
| `EvidenceLockerHost.kt` | `7a1e87c7aa7fccae11726dd7a7c8385fc8791f5336e5d934632b947737bbd03c` |
| `EvidenceAi179Test.kt` | `41db61579454d0d9bb672fdd0733bf04bc1dde4d7a96bb195ff11c580404c998` |
| `EvidenceArchive179Test.kt` | `e45d3b31b3ab6edfce576a6110d140bc946815f7b698736e07338d98309e6398` |
| `EvidenceSaf179Test.kt` | `350fbb79f91af22f88598c502e6dc07412b71e774bbb10423dd99029237925d0` |
| `scripts/evidence179_cases.json` | `f2a622f5fa919e29d5465d0e8bac1dacdd1a4bbb504371b939658f3a671ee2f6` |

No actual Android compile or execution of this correction was available. The implementation report's local wrapper attempt failed before the compiler; it is not compilation evidence. At the supplied checkpoint, old 146-case run `41237030300` remained in progress and targeted earlier application source. It is supporting evidence only and cannot establish these corrections or the final source. No independent CI status query was made by this reviewer.

Main/all-instrumentation compilation, actual changed-source Android execution of the seven additions and affected existing AI/archive/SAF cases, and exact unique/unskipped final XML accounting remain mandatory. All broader outstanding gates in the consolidated review remain open. The reviewer used no subagents, Git operations, CI actions, credentials or signing; this report is its only repository write.
