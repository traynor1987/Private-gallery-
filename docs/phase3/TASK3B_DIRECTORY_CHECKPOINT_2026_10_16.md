# Task3B directory retirement checkpoint — PARTIAL, not accepted

Parent recovery checkpoint: `4d60667108e78b4c9c2f04c1a331a232d5b5bd22`.
The selected-alias fixture fix remains intact. Its controlled reproduction establishes
one concrete fixture-isolation cause; the lost historical broad-run stack is not reconstructed.

## Production batch

`SecondaryDirectoryHandles.PinnedDirectories` now seals new use before cleanup,
tracks one synchronous same-thread borrower (including nested calls), waits for actual
admitted use to return, and attempts all sibling closes after returned failures. The first
Native close failure remains sticky; duplicate close waits for that original outcome.
Borrower self-close and provider close reentry are denied. Interruption during borrower
wait does not abandon descriptors; the closer finishes actual cleanup and restores the flag.

`withPinned` and `withDirectory` hold this scope across their complete synchronous consumer.
All Native methods deny use of a retired scope. Prepared publication reuses the already
admitted outer borrow and does not acquire the lower-ranked lifetime monitor from controller
or authority gates. Existing prepared-move Native-only-move and one-shot assertions remain.
The internal `parent` interface is retained for existing media consumers.

This is a prerequisite, NOT complete physical ownership or cleanup acceptance. It adds no
worker pool, queue, resource quota or generic authentication-admission exemption.

## Verification actually observed

| Source | Completed result |
| --- | --- |
| Unchanged directory production + seven new regressions | 7 tests, 7 genuine assertion failures; exit1; raw XML retained |
| First correction compile | Failed: narrowing parent visibility broke three media callers; no tests ran; retained |
| Corrected directory/store/controller selection | 37 tests, zero failures/errors/skips; exit0 |
| Final directory/prepared/media/Task3A selection | 38 tests (8+1+19+10), zero failures/errors/skips; exit0 |
| Four controlled directory race methods, ten repetitions each | 40/40 passed; includes actual atomicReplace and direct commit under a held lifetime monitor |
| Source-integrity/security Python selection | 25 tests passed on corrected source |
| Corrected directory source Phase0 Android test Kotlin/Java compilation | Completed exit0, 1m7s; PRIVATE_GALLERY_PHASE0_EVIDENCE=true |
| Parent4d6066 complete debug JVM | 1294 tests, zero failures/errors/skips |
| Parent4d6066 complete Phase0 JVM | 1301 tests, zero failures/errors/skips |
| Parent4d6066 lintDebug / lintPhase0Evidence | Both completed: zero errors/fatals; 61/65 warnings, four information items each |
| Parent4d6066 debug Android test Kotlin/Java compilation | Completed; combined Gradle exit0, 16m26s |

The complete baseline JVM/lint/debug instrumentation compilation predates the directory
production change. It is evidence for the exact parent, not a claim of a full-suite pass
on this new source. Final focused verification is on this directory source.
Phase0 Android compilation of this source completed as recorded above. Android
runtime/hardware verification remains unavailable; no physical-device
pass, release build, APK or Task3B acceptance is claimed.

The prior process-inspection command's `fatal library error, lookup self` came from ps in
the execution namespace, NOT Gradle. The broad Gradle command genuinely completed exit0.
No stalled/interrupted execution is counted as passing.

Independent read-only review found no introduced blocking defect in this narrow batch,
subject to the executed checks above. Review-driven production-route coverage was added.
Raw failures, green XML/logs, repeats and review reports are in the checksummed evidence
archives. SOURCE_MANIFEST.json identifies exact production/test Git blobs and SHA256.

## A–D acceptance remains open

| Blocker | Status and exact remaining boundary |
| --- | --- |
| A — prompt lifecycle | NOT ACCEPTED. AndroidXBiometricRetirementSupport.available remains false. AndroidX1.1 graph queues lack public quiescence. API35 CryptoObject operation-handle cross-process alternative is documented by Android but has no accepted complete ownership adapter or runtime proof here. |
| B — complete ownership | PARTIAL. SecondaryController.kt:560 outer Executor queue/input captures; :488 pending publication; :155 recovery display; DomainSnapshot.kt:39–55 PendingSetup/raw copies; SecondaryStore.kt:168 postcreation ownForSession; provider/store/output partial manifests and physical capacity fit remain mandatory. Directory construction and independently blocking channel/stream child reservations are not completed. |
| C — ranked cleanup | PARTIAL. New private lifetime gate contains no Native work and prepared publication acquires no new lower-ranked monitor. SecondaryStore root4-held scope close remains; PrimaryKeySlots.kt:49 still calls preference persistence inside operation.commit. |
| D — final admission | PARTIAL. Existing opaque attempt/epoch/selected generation/identity and single-result handoff checks remain unchanged. Whole-path cleanup cannot be accepted until B/C finish; no allowance for active outer queues or failed cleanup was added. |

Directory close is still synchronous/sequential: a hung first Native close can delay siblings.
Failed handles stay failed in this scope, but this patch does not introduce the mandatory
original physical accounting for existing unreserved scopes. MediaUsageStore has direct raw
stream consumers outside whole wrapper borrows; their existing serialization is not a new
universal lifetime proof. Partial constructor failures and channel ownership remain open.
These authentication-relevant requirements are Task3B obligations, not automatic Task3C deferrals.

The only owner-approved exception remains inaccessible Android-INTERNAL Keystore retirement.
Cancellation, callback completion, Cipher disposal and abort requests do not prove it. All
app-controlled supported cleanup remains mandatory and failed/incomplete ownership charged.
No extension to AndroidX, workers, streams, buffers or provider handles is approved.

Task3A source remains intact and its ten focused ownership tests passed. Encryption formats,
selected-alias protection and original media are unchanged. Main93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10,
protected Phase3 branch79bb1928f3fbe16e935e587a3fe594158cb3f529 and PR59 OPEN/DRAFT/UNMERGED
were rechecked unchanged before this checkpoint. Task3C is NOT safe to begin. Phase3 NO-GO.
