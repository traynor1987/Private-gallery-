# Task2 access/controller fix round1 report

Date: 2026-10-31. Scope: A179-1 through A179-4 in `reviews/ACCESS179_INITIAL.md`; binding `CONTRACTS.md` and `work/task-2-fix1-brief.md`. Source changes and narrow instrumentation coverage are ready for scoped review. **Android/Kotlin compilation and API37 execution remain pending; this is not release approval or a runtime pass claim.**

## Owned files

Changed:

- `app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceAccess.kt`
- `app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceController.kt`
- `app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceMetadata.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceAccess179Test.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceController179Test.kt`
- This report.

`EvidenceEgress.kt` and `EvidenceEgress179Test.kt` are unchanged from the reviewed snapshot. No Main/UI/model/OCR/Gradle/network/export files were edited. No commits, pushes, CI, signing or historical regression suites were run. No source originals or previous178 data were changed by these edits.

## Exact changes

### A179-1: live original postconditions

Both source adapters retain the original captured during streaming. After the repository has returned from authenticated EOF and closed its read scope, they require a fresh same-vault COMPLETE item with identical ID, MIME, size, SHA-256, vaultOnly and payload nonce. Primary reloads the same originating repository instance. Hidden reinspects its same originating repository. Both also require the fixed encrypted payload file still to exist, catching Unix open-but-unlinked reads. The common EvidenceSourceReader wrapper rechecks live identity/currentness before returning as well.

`inspect` now rejects a missing encrypted payload. Hidden lists explicitly require COMPLETE. Registration uses the common reader and rechecks the verified record against a live original inside its catalogue update; OCR does the same immediately before saving extraction. Coroutine cancellation is included in these readers. There is no silent fingerprint overwrite or source deletion.

The payload existence checks use the existing fixed repository layouts (`files/vault/payloads/UUID.vault` and `noBackup/hidden-media-v1/media-UUID.enc`); no new files or alternate repository fallback are introduced. As with any live read, later independent source retirement after the postcondition remains possible and subsequent operations must reverify.

### A179-2: atomic authority/publication boundary

`EvidenceAccess.publishIfCurrent(action: () -> Unit): Boolean` is available on the internal access capability. It requires Main, runs the final current-authority check and the action together under the existing originating retirement boundary (Primary authentication commit gate or the exact HiddenSession monitor), and returns true only when the action ran. It introduces no additional lock and performs no cleanup inside that boundary.

Every nonempty controller StateFlow assignment now uses that primitive: busy start, refresh originals, operation catalogue/result message, integrity result and error/busy reset. Retirement still cancels the scope and clears the originating controller on Main; explicit close on Main clears immediately. Hidden's revocation callback remains only an atomic flag plus preallocated cleanup enqueue. Resource/lease closing stays on the retirement executor, outside the publication/authority monitor.

Actions supplied by UI must only perform short state/resource assignment. They must not perform repository IO, blocking cleanup or acquire reverse-order owner locks. A decoded bitmap rejected by this gate remains the caller's cleanup responsibility.

### A179-3: explicit plaintext ownership

The unsafe ByteArray-returning `readOriginal` API is removed. Its replacement is:

```kotlin
suspend fun withOriginalBytes(
    evidenceId: String,
    limit: Int = 32 * 1024 * 1024,
    consumer: suspend (ByteArray) -> Unit
): Unit
```

This member is available through the internal EvidenceController. It runs on IO. The consumer is invoked only after the complete reader, including all outer source/authority postconditions, succeeds. The consumer borrows the array until it returns and must not retain it; the method returns Unit. UI must parse/decode within the consumer and use `access.publishIfCurrent` on Main for any resulting text/bitmap state, with disposal on rejected publication.

The shared internal `withVerifiedBytes` helper captures ownership **inside** the original reader callback, before any repository/access postcondition can throw. Its enclosing finally wipes the array on every completion/failure/cancellation, including consumer failure. OCR uses this same helper. Wiping therefore completes before a cancellable IO-to-caller result handoff; no original ByteArray ownership travels as that result. Metadata sampling is likewise assigned to its enclosing owner's variable before chunk assembly. `readBounded` wipes its assembled output if copying throws and always wipes chunks.

No GC or secure-deletion claim is made. This does not make immutable strings wipeable; UI publication/lifetime remains governed by the originating gate and retirement clear.

### A179-4: acquisition claims reach verified registration

`fun importAndRegister(uri: Uri, extract: Boolean = true)` is exposed through the internal controller for future authorised SAF integration. The operation imports into its originating encrypted repository and passes the acquired EvidenceOriginal directly into registration. A fresh authenticated read is still required. `EvidenceMetadata.acquisition` first binds the acquired identity to that verified original using the full identity/policy check, then retains only the bounded provider source name, nonnegative provider modified timestamp, and the two bounded explicit provenance labels. Vault dates, policy and dimensions come from the verified original/base metadata. No provider URI is retained.

Existing-vault registration continues to use unavailable filesystem dates and the existing-vault acquisition-name label. An already-registered source is checked live and returned without silently rewriting its prior acquisition history. Acquisition claims remain claims, never independently verified timestamps.

## Tests authored

The first regression tests for all four findings were authored before the corresponding product edits. Additional integration assertions followed. Because no Android toolchain/runtime was available, **no red/green Android execution was observed**; these are authored instrumentation tests, not passing runtime evidence.

New tests in `EvidenceAccess179Test`:

- `primary_post_eof_rechecks_trashed_changed_policy_fingerprint_and_missing_payload`: latches pause an already-open Primary read; a second repository trashes the item, or the encrypted index changes policy/fingerprint, or the payload is unlinked. Authenticated EOF must not return success; no catalogue registration is created by the read.
- `publication_gate_rejects_every_result_after_exact_retirement_boundary`: a worker retires under the real Primary gate after Main's precheck and before attempted publication, using controlled latches. Refresh/operation/integrity/error/busy/UI publication attempts are rejected without executing their actions.

New tests in `EvidenceController179Test`:

- `scoped_bytes_are_wiped_when_outer_reader_rejects_after_consumer_returns`: retains the actual reader callback result, rejects it in the outer reader, asserts zeroed bytes and no consumer publication.
- `cancellation_during_io_return_cannot_abandon_owned_bytes`: controls caller dispatch with a queue, waits until IO return delivery is pending, cancels before delivery, and asserts the retained original array is already zeroed and the caller did not receive completion.
- `acquisition_claims_are_bounded_bound_to_original_and_unavailable_for_existing_reference`: provider claims and provenance survive, overlong names/labels are bounded, URI-shaped extra fields are excluded, changed policy is rejected and existing references keep unavailable modified time.
- `controller_state_paths_clear_at_retirement_and_reject_late_operation`: uses the actual controller and store through refresh, unavailable-original integrity publication, controlled operation error, busy state and a latched operation retired before its result. Main clears state and no nonempty post-retirement state is accepted.
- `import_register_retains_provider_claims_and_reference_removal_preserves_original`: uses an attached in-process ContentProvider with the platform ContentResolver wrapper, actual encrypted import and controller registration; asserts provider name/timestamp/claim labels, scoped byte identity, no retained URI, and reference removal leaving the original readable unchanged. Provider plaintext fixture exists only inside the test cache and is deleted by the test.

The existing same-vault/read-only, revocation, bounds and annotation fingerprint tests remain. Runtime Hidden post-EOF mutation/revocation and repeated owner/retirement scheduling still require API37 coverage.

## Observed local checks

- Ran 12 task-specific Python source assertions against only Access/Controller/Metadata. All 12 passed: Main gate/boundary, full COMPLETE identity fields, both payload existence checks, captured source postchecks, exclusive gated nonempty state assignments, all five publication paths, Unit scoped API, callback-owned/finally-wiped arrays, registration/OCR live checks, bound acquisition flow, bounded/whitelisted claims, and no original trash/delete calls. These assertions inspect source shape; they are **not behavioral, Kotlin compilation, Android or security proof**.
- Compared all seven owned source/test files against `/workspace/scratch/4181ccd09b2e/access179-before-fix1`: exactly the five listed code/test files changed; both Egress files matched byte-for-byte.
- Explicit whitespace scan covered all seven owned source/test files and passed. `git diff --check` also returned clean, but these newly authored files are currently untracked, so the explicit scan is the meaningful whitespace evidence.
- Local PATH checks located no `adb`, `gradle` or `kotlinc`; the brief records the blocked Gradle9.5 bootstrap. No further Gradle bootstrap/build was attempted and no Android execution was claimed. The public Android reference was checked for `ContentResolver.wrap(ContentProvider)` (API29+, suitable for the required API37 fixture); no optional deprecated android.test.mock dependency was added.

## Pending gates / handback

Root must adapt the two original-view callers to the borrowed-byte API and use the Main publication gate for text/decoded resources, disposing rejected resources. Root owns Main/UI lifecycle retirement and authorised picker tickets; no SAF host UI was added here.

Compile the integrated product and instrumentation, then run the two changed test classes on API37. Confirm the provider fixture dispatch/descriptor cleanup, precise Primary and Hidden retirement, outer-check byte wiping, queued cancellation delivery, live COMPLETE/policy/fingerprint/missing-payload failures, source deletion during registration/OCR, and actual UI state/bitmap clearing. Add device-specific assertions where needed; source-only checks cannot establish these outcomes. Egress retained-lineage, archive, route and resumed-auth SAF gates remain separately required by the existing release contracts.

## A179-1 integration extension: refresh availability warnings

Root requested this additional narrow integration before rereview. `refresh` now computes a live metadata status for **every** retained evidence record from a single same-vault source snapshot, then publishes catalogue, originals and the complete integrity map together through the Main authority gate. Both source lists exclude absent encrypted payload files as well as non-COMPLETE items. No full byte read, full SHA calculation or catalogue mutation is introduced by refresh.

Missing/trash/payload-missing references are flagged unavailable. Changed ID/MIME/size/catalogue SHA-256/vaultOnly is flagged unavailable or changed through the existing record/source comparator. A matching live catalogue entry explicitly says: “Live metadata fingerprint and policy match; full original SHA-256 has not been reverified by this refresh.” This is a cheap snapshot warning, not a content-integrity verdict. Refresh replaces previous integrity entries with this appropriately limited snapshot result; manual Verify still performs the full authenticated-byte read. Record/reference and original bytes remain unchanged.

Added instrumentation test **before** this extension's product edits: `refresh_flags_live_missing_changed_policy_and_fingerprint_without_full_read`. It imports five distinct encrypted originals, retains their records, trashes one, changes another's live vaultOnly policy, changes a third live fingerprint, removes a fourth encrypted payload, and leaves one matching. The actual controller refresh must expose all five statuses without manual Verify, exclude unavailable selectable originals, label the match as not fully reverified, and retain all catalogue references. This test remains uncompiled/unexecuted pending API37.

After the extension, 8 additional narrow source assertions passed: Primary/Hidden existence-filtered snapshots, one snapshot mapped across every record, changed identity/policy comparison, explicit full-byte disclaimer, atomic catalogue/originals/integrity publication, no full read/catalogue write during refresh, and presence of the actual-controller instrumentation. Explicit whitespace scans of all three extension-edited files passed. These are source-only checks. No Gradle retry, broad suite, CI or Android run occurred. The earlier 12 source checks record the pre-extension state; this final check covers the new combined publication shape replacing the earlier originals-only publication shape.
