# Final consolidated 1.7.9 correction wave — source frozen for scoped review

Date: 2026-10-31. Scope: SEC179-1 and SEC179-2 from SECURITY179_CONSOLIDATED.md, together. Source implementation and regression authoring are complete; Kotlin compilation and actual Android execution are **PENDING**, not passed. No approval, signing, activation or release claim is made. No subagents, Git mutations, push, CI dispatch, credentials or signing were used by this worker.

## SEC179-1: filename omission

`core/evidence/EvidenceAi.kt` no longer puts `Evidence.name()` into the selected JSON or selected-text character accounting. All names are omitted because existing records do not prove owner-authored title provenance. Registration, local names, metadata, display, search and export are unchanged. IDs, description, notes, tags, explicit participants and the current selected transcript remain projected. Existing catalogue/source/route/revision and one-shot consent binding is unchanged.

The AI fixture now reproduces the registration default: record.name equals item.displayName `patient-Jane-London-clinic.png`. Metadata uses a different `DISTINCT_SOURCE_FILENAME`, with separate `PRIVATE_GPS`. Both exact preview and exact transmitted body must omit all three. New coverage also retains selected OCR/notes/IDs, checks zero key reads and POSTs before confirmation, and one key read/POST only after one-shot confirmation. The text quota boundary remains 100,000 projected characters; its fixture now counts annotations rather than an omitted one-character title.

## SEC179-2: authenticated private snapshot before provider acquisition

`EvidenceArchive.preparePlaintext(context, plan, reader)` requires the existing explicit plaintext-confirmation bit and validates the complete selected source lineage. It creates only private ciphertext in the existing owner-specific `evidence-open-*` directory (0700 directory, 0600 creation, 0400 seal). A random 256-bit ephemeral password, represented as 64 hexadecimal chars without constructing a password String, drives the unchanged encrypted writer. Original reads stream in 32 KiB chunks into encrypted ZIP/GCM staging; their EOF tags, actual size/hash, post-reader checks and owner/policy/current guards must finish before staging succeeds.

The completed ciphertext is opened through the existing `EvidenceOpenedArchive` pipeline: bounded ciphertext copy, complete GCM authentication before ZIP parsing, second complete authentication and strict schema/hash/original validation, then one retained sealed ciphertext descriptor. The initial staging file is deleted in finally, leaving the opener's sealed snapshot until close. The random password chars and entropy bytes are wiped on every exit, including a failing archive close. No originals are reread to supply public bytes.

`EvidenceArchive.PlaintextSnapshot.write(output)` rechecks selected live source authority, ownership, policy and fingerprint before and throughout the existing authenticated plaintext-copy API. The copy comes from the sealed snapshot, not a mutable original. Snapshot close releases the descriptor, wipes its key/report and deletes its ciphertext via existing `EvidenceOpenedArchive.close`. Existing startup cleanup recognizes both ciphertext file names without a new cleanup policy. At most two bounded encrypted container copies coexist during preparation (each subject to MAX_CIPHER); memory remains bounded by existing reports/schema/KDF and streaming buffers, never an entire original.

`EvidenceLockerHost` prepares this snapshot **before** `evidenceDestination` opens/truncates any provider destination. Thus source authentication, source hash, post-reader or policy failure cannot cause a public write, flush, close, or even a destination acquisition. Snapshot close is on the IO operation's finally path. Existing controller retirement and `EvidenceDocumentIo` resource cancellation still cancel blocked provider work; original URI identity checks on opened-archive re-export are unchanged. Provider cleanup remains best effort for public-copy failures/cancellation after admission.

`EvidenceArchive.write(plan, reader, passphrase, output, context: Context? = null)` retains its encrypted default and adds an optional final staging context. Confirmed plaintext requires this context and delegates through preparation; a legacy four-argument plaintext call fails closed before touching output. All existing direct writer instrumentation calls now supply context; the production Host uses the explicit preparation API to delay destination acquisition. Encrypted callers and encrypted container/header/KDF/ZIP formats are unchanged. `ReceiptOutput.flush` now checks current authority before and after target flush.

## Exact implementation files

All paths are relative to the repository:

- `app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceAi.kt`
- `app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceArchive.kt`
- `app/src/main/java/uk/co/traynor/privategallery/ui/EvidenceLockerHost.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceAi179Test.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceArchive179Test.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceSaf179Test.kt`

No auth, transport, credentials, model, codec, OCR, incidents, search, archive-opening implementation or workflow/static case manifest was edited. Root owns the latter two and integration.

## Seven added Android cases for root's manifest

Fully qualified class prefix is `uk.co.traynor.privategallery`:

1. `core.evidence.EvidenceAi179Test#defaultFilenameNameIsExcludedFromExactPreviewAndOneShotBodyWithSelectedOcr`
2. `core.evidence.EvidenceArchive179Test#plaintextLateTagHashAndPostReaderFailuresNeverTouchPublicOutput`
3. `core.evidence.EvidenceArchive179Test#plaintextAllOriginalsAuthenticateBeforePublicOutputAndSnapshotNeverRereadsSources`
4. `core.evidence.EvidenceArchive179Test#plaintextSnapshotIsIndependentOfMutableOriginalAndRechecksLivePolicy`
5. `core.evidence.EvidenceArchive179Test#primaryLegacyLateGcmTagFailureNeverPublishesPlaintext`
6. `ui.EvidenceSaf179Test#plaintextLatePrimaryTagFailureNeverAcquiresProviderDestination`
7. `ui.EvidenceSaf179Test#cancelBlockedPlaintextSnapshotProviderWriteRevokesWithoutBlockingMain`

The archive failure probe records attempted writes/flushes/closes before its admission assertion, so an early attempted write cannot be hidden by catching the probe's exception. Its deterministic reader cases require complete bytes actually consumed, then distinguish late-tag/hash/post-reader failure from post-reader policy/retirement. They assert zero public operations and unchanged staging inventory. The real Primary test imports a sufficiently large nonchunked original through AndroidVaultRepository, corrupts only its final GCM tag, demonstrates preliminary bytes from the actual `withHiddenCopySource` reader before failure, then checks the archive publishes nothing and removes staging. The Host version requires zero destination opens/closes on the same real late-tag defect. The blocked-provider version exercises the admitted plaintext snapshot, checks prompt Main cancellation, descriptor/signal cleanup, no receipt notice and retained original.

Existing changed tests:

- AI `prepareMakesZeroPostsAndExactFramingHasOnlySelectedContent`: real default-name fixture and exact-body privacy assertions; names are no longer expected selected content.
- AI `selectedRecordImageAndTextQuotaBoundariesHavePositiveAndNegativeCases`: exact annotation-only 100,000/100,001 boundary.
- Archive existing tests: context argument supplied, retaining success/failure, owner, lineage, integrity and export assertions.
- Archive `outputFailureAndRevocationDuringOriginalStreamingNeverReturnReceipt`: encrypted branch still fails during original streaming. Plaintext branch now fails after 64 KiB of authenticated snapshot copy and explicitly asserts the original reader is not streaming. This is the required change in publication boundary, not removal of output-failure/revocation coverage.

Test-class source inventory: EvidenceAi179Test 21 unique methods; EvidenceArchive179Test 28; EvidenceSaf179Test 31. Seven new methods overall. Root must update exact static case accounting.

## Verification actually attempted

Tests were authored before the production correction; no Android red/green execution could be observed in this environment. The report does not claim executed regression coverage.

1. `command -v gradle`, `command -v kotlinc`, `command -v adb`: no executable reported; final command exit 1.
2. `./gradlew :app:compileDebugAndroidTestKotlin --offline`: exit 1 **before compilation**. Wrapper tried to download uncached `https://services.gradle.org/distributions/gradle-9.5.0-bin.zip`; failed with `java.net.SocketException: Network is unreachable`. No Kotlin compiler/test result was produced.
3. A focused Python source inventory/whitespace audit over the six changed Kotlin files: exit 0; no trailing whitespace or duplicate `@Test` method names. This is only a source inventory check, not a compiler or behavior test. It emitted the hashes below.
4. Manually inspected the changed API call sites, existing sealed-descriptor authentication pipeline, live `EvidenceEgress` checks, actual Primary nonchunked reader, Host destination acquisition/cancellation and protected URI paths. No unrelated host suites were rerun.

SHA-256 at source freeze:

| File basename | SHA-256 |
|---|---|
| EvidenceAi.kt | 75024884ea7efbaaabe2e80e3a21d5178fe146a0af8d5deba91dbb8ba2efec84 |
| EvidenceArchive.kt | 1c30e00a38fe183c68e6f070f9c7ab90da615cbaf35f26e34307c997ca3c40d9 |
| EvidenceLockerHost.kt | 7a1e87c7aa7fccae11726dd7a7c8385fc8791f5336e5d934632b947737bbd03c |
| EvidenceAi179Test.kt | 41db61579454d0d9bb672fdd0733bf04bc1dde4d7a96bb195ff11c580404c998 |
| EvidenceArchive179Test.kt | e45d3b31b3ab6edfce576a6110d140bc946815f7b698736e07338d98309e6398 |
| EvidenceSaf179Test.kt | 350fbb79f91af22f88598c502e6dc07412b71e774bbb10423dd99029237925d0 |

## Mandatory remaining gates and limits

Fresh scoped source re-review of both findings together, Kotlin/main/all-instrumentation compilation, and actual changed-source Android execution are mandatory. The root-controlled run must include the seven new cases and all affected existing AI/archive/SAF cases, with exact unique/unskipped XML accounting. Existing original-source run 41237030300 against ddd0e3c is supporting evidence only after these application changes; it cannot be relabelled as final-source evidence.

Plaintext export now incurs encrypted staging, two KDF derivations and authenticated parsing before output. Storage is bounded, but insufficient private storage fails closed. Existing best-effort ciphertext deletion and provider cleanup limitations remain. After valid publication starts, cancellation cannot revoke bytes a provider already received; the new boundary ensures they came only from a fully authenticated snapshot. No source authentication or policy assertion was intentionally weakened.

Other required release evidence, route/OCR/lifecycle gates, physical Fold owner acceptance, signing/original-certificate/upgrade/distribution gates remain with root. This report is a source handoff for review, not security approval or release authorization.
