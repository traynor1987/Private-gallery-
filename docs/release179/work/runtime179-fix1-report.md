# Evidence179 first runtime correction handoff

2026-10-31. Started from the assigned authoritative d854b713 source, preserving the separately accepted SEC179-1/SEC179-2 correction. **Source corrections and fixture diagnostics only; no changed-source Android compile or execution is claimed.** No Git, CI, signing, secrets, subagents, workflow or static-manifest edits were performed by this worker.

## Original evidence and limits

Read `work/runtime179-fix1-brief.md`, `work/final179-fix-report.md`, `reviews/SECURITY179_FIX_WAVE.md`, all 18 full exception blocks from their original per-test logcats, the original event/error excerpts, and the stalled Hidden-grace logcat under `/workspace/scratch/4181ccd09b2e/diagnostic179-ddd0`. Source run41237030300/job124642848043 targeted ddd0e3c, not this correction. The verified original artifact identity supplied by Root is SHA256 `55207c02c780913ab39fc7afc5bd9497748e95c5f97117addabe2fb16c13e6fd`. The observed event inventory records103 finished cases with no failure event,18 failed and1 unfinished; this is not an overall pass. All four live owned-route cases were not reached. Current mandatory manifest remains153 entries.

## Root causes, corrections and remaining uncertainty

### SAF seed failure — ten invalid fixtures, not permissive-import behavior

Every one of these ten exceptions originates at `VaultDocumentPolicy.validatingStream` UTF8 decoder line48, through `AndroidVaultRepository.importVerified`, into `EvidenceSaf179Test.Harness` line123, before the relevant Host scenario starts. The old fixture labels 262144 raw `Random(179).nextBytes` bytes as `text/plain`. Strict malformed-input rejection is correct.

The shared seed now uses deterministic ASCII from a 64-character alphabet, retains262144 bytes, checks strict UTF8 decodability and still supplies substantial uncompressible attachment data. A JDK probe reproduces the old exact malformed-input exception, validates the replacement, and verifies its deflated size198564 bytes exceeds65536, retaining blocked-provider pressure. No production document validation, MIME acceptance, EOF, hash or authentication behavior was changed. All31 SAF test declarations, including the two added snapshot regressions, remain present.

### UI traversal — four missing lazy rows, one attempt to scroll a static button

The missing `Review / accept`, `Reviewed time179`, `Review / correct`, and `Chronology179` failures are lookups of lazy content below the currently composed viewport. `performScrollTo` first fetches an existing node, so it cannot materialize such content. The Primary import test additionally applies this same unconditional action to the original-picker confirmation button, which has no scroll ancestor, producing its exact `no parent layout with a Scroll SemanticsAction` exception.

The new androidTest-only `evidenceNode` helper first uses the topmost lazy surface's `performScrollToNode` when a matching node is absent. It then invokes `performScrollTo` only for a matching node with a scrolling ancestor. Static dialog controls are clicked directly. Existing exact matchers, content/owner assertions, landscape/font configuration, manual ordering, selection limits and security checks are retained. Locker, AI and SAF use the shared helper so the same latent traversal failures are not merely moved to a later step. No production layout was changed.

### Bulk registration timeout — original state not recorded; do not infer success

The original bulk case times out at its45-second `waitReady(controller,2)`. Its log contains neither selected-checkbox state nor the resulting controller count/busy/revision/message, so it does not establish whether one selection was missed or registration itself failed. The test now operates each actual named original checkbox and asserts it is on before the single confirmation. The pre-confirmation empty-catalogue assertion and final two-original/mixed-export assertions remain. Readiness failure now reports only expected/actual record counts, busy state, revision and original count. This supplies the missing evidence if a real registration failure persists. No controller error is swallowed or accepted as a pass; this specific observed timeout remains unverified until the corrected-source run.

### AI publication — explicit Main resumption, preserving the Main assertion

The exact AIUI stack is `EvidenceAccess.publishIfCurrent:75` → `EvidenceAiDialog:54`, resumed through Compose test `ApplyingContinuationInterceptor`/`FrameDeferringContinuationInterceptor` on an IO worker after `credentials.isConfigured`. Composition effect inheritance is not a Main-thread guarantee. That result now explicitly hops to `Dispatchers.Main.immediate` before the unchanged current-authority publication fence. The Host's resumed picker/controller-admission effect likewise has an explicit Main envelope, retaining every authentication/resumed/owner guard and return boundary. Existing preview/report/thumbnail/original `LaunchedEffect` post-IO publications already use explicit Main; action scopes explicitly provide Main. The strict `EvidenceAccess` Main assertion is unchanged.

### Network cancellation — cancellation classification plus a stronger real read precondition

The original exception is the generic `EvidenceAiFailure` from `RouteClient.execute:157`; its catch discarded the underlying IO cause. The old fixture only signalled that the server wrote a response prefix, not that the client began reading it. Thus the old stack alone cannot prove the exact low-level exception or distinguish a pre-cancel network failure from the cancellation race.

There is a concrete source defect at this boundary: socket cancellation can throw `IOException`, and the catch previously wrapped it as an ordinary AI failure without rechecking the coroutine's cancellation. The catch now calls `currentCoroutineContext().ensureActive()` before the same generic active-operation error. Already-cancelled work propagates `CancellationException`; an actually active pre-cancel network failure still fails normally. No route fallback, retry, TLS, DNS, proxy or authority policy changed.

The existing response-read case now waits for the real OkHttp client `responseBodyStart` as well as the server prefix. Its server observes TLS peer closure rather than waiting on a manually released response latch. After cancellation the test still requires CancellationException and one request, additionally requires peer closure within5s and asserts no completed response was published. The enclosing ordinary coroutine scope still fails on genuine child failure; no supervisor or broad exception acceptance hides a pre-cancel defect. All8 network test declarations remain.

### Hidden-grace stall — a removed fixture lock-order hazard, original stacks still unavailable

The original log starts this case at20:22:27.294, reaches actual STOPPED20:22:30.332 and resumed20:22:30.645, then has no case completion or app exception before the job timeout. No app Main/instrumentation thread dump or ANR establishes the exact blocked stack. Do not relabel a source hypothesis as a proved original deadlock.

The fixture did contain a concrete forbidden lock-order path: its captured authority guard repeatedly evaluates `compose.activity`, whose ActivityScenario-backed getter may synchronously marshal to Main. That guard is also invoked while Hidden's commit/publication monitor is held, while Main itself checks the same Hidden session. `EvidenceAuthorities.hidden` explicitly requires a nonblocking captured guard. The harness now captures the Activity once during setup and maintains an atomic foreground flag from real ON_RESUME/ON_PAUSE. The guard retains the original authentication epoch, exact Hidden authentication/authority, generation and foreground requirements using only captured nonblocking state. Real ON_STOP `onBackground(60000)` and ON_START `onForeground` reconciliation, grace/expiry/reauthentication, stopped-provider-zero-read, fresh-access/old-access retirement, metadata and original-byte assertions remain.

The import-ready wait no longer executes synchronous encrypted-store IO inside the instrumentation wait predicate: a daemon scheduled observer supplies an atomic snapshot, while the instrumentation's45-second predicate remains bounded; the observer is cancelled/shut down in finally. Timeout diagnostics contain counts and exception class only. A test-only watchdog for this single within-grace case records a fixed phase plus thread names/states/class-method-line stacks after30s from an independent daemon and is cancelled in finally. It neither passes nor skips a test. If a real stall persists, the original stacks will be preserved by Root's outer timeout diagnostics; the exact original hang and successful corrected runtime remain unproved here. No production authentication code was changed.

## Original failure inventory

- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test-permissionReturnAfterReauthenticationCannotReopenAiOrStartTransport`: invalid raw-byte text seed before scenario.
- `uk.co.traynor.privategallery.ui.EvidenceLocker179UiTest-ownerAcceptsAndRejectsActualGroupingHintsAndOrdersUncertainTimeline`: lazy item queried before materialization.
- `uk.co.traynor.privategallery.ui.EvidenceLocker179UiTest-editedExtractedTimestampRemainsUserEnteredWithRawQuotePreserved`: lazy item queried before materialization.
- `uk.co.traynor.privategallery.ui.EvidenceLocker179UiTest-hiddenInnerLockClearsSearchSelectionsAndDialogsWithoutDeletingEncryptedEvidence`: lazy item queried before materialization.
- `uk.co.traynor.privategallery.ui.EvidenceLocker179UiTest-primaryActualImportExtractCorrectIncidentSearchRemoveAndReopenPreservesOriginal`: unconditional scroll on static picker confirmation.
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test-hideDuringBorrowedNativeRenderRejectsAndRecyclesUnpublishedBitmap`: invalid raw-byte text seed before scenario.
- `uk.co.traynor.privategallery.ui.EvidenceLocker179UiTest-bulkOriginalRegistrationAndMixedExportSelectionAreOwnerReviewed`: readiness timeout; original selection/state missing.
- `uk.co.traynor.privategallery.ui.EvidenceLocker179UiTest-firstManualMoveUsesVisibleChronologyAndDisablesBoundaries`: lazy item queried before materialization.
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test-plaintextOutputNeedsSeparateExplicitConfirmation`: invalid raw-byte text seed before scenario.
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test-permissionReturnUsesFreshControllerAndRequiresNewPreviewWithoutAutoConnecting`: invalid raw-byte text seed before scenario.
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test-archiveReexportRejectsExactIncomingUriBeforeTruncateAndCleanup`: invalid raw-byte text seed before scenario.
- `uk.co.traynor.privategallery.core.evidence.EvidenceNetwork179Test-coroutineCancellationDuringResponseReadDisconnectsWithoutPublishing`: generic route failure during cancellation; original low-level cause suppressed.
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test-archiveReexportFreshPasswordAndSeparateConfirmationProducePlaintextOnlyAtDestination`: invalid raw-byte text seed before scenario.
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test-encryptedPreparePickerFreshControllerCopiesExactSealedCiphertext`: invalid raw-byte text seed before scenario.
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test-panicWhileArchiveProviderReadIsActiveCancelsAndPreventsReportPublication`: invalid raw-byte text seed before scenario.
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test-archiveReexportRequiresPasswordAgainAndChangedCiphertextNeverPublishesReport`: invalid raw-byte text seed before scenario.
- `uk.co.traynor.privategallery.ui.EvidenceAi179UiTest-actualPreviewNeedsExplicitSendAndSeparateOwnerApplication`: off-Main post-IO publication.
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test-lockDuringBorrowedNativeRenderRejectsAndRecyclesUnpublishedBitmap`: invalid raw-byte text seed before scenario.

## Exact changed files

- `app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceNetwork.kt`
- `app/src/main/java/uk/co/traynor/privategallery/ui/EvidenceAiDialog.kt`
- `app/src/main/java/uk/co/traynor/privategallery/ui/EvidenceLockerHost.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceNetwork179Test.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceAi179UiTest.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceLocker179UiTest.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceSaf179Test.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/ui/Evidence179UiActions.kt`

The report itself is the ninth written path. No test declarations were added, removed or renamed: affected class counts remain8 network /7 Locker /31 SAF /1 AIUI (47 total). Existing tests were already observed red in original Android evidence; changed-source green is pending. The new helper contains no `@Test` declaration and needs no manifest relaxation.

## Observed verification commands/results

1. `java /tmp/Runtime179TextFixtureProbe.java` — exit0. Output: `OLD fixture strict UTF8 rejection reproduced: java.nio.charset.MalformedInputException: Input length = 1`; `NEW valid UTF8 bytes=262144, compressed=198564, exceeds65536=true`. This probe is narrow fixture evidence, not Android import verification.
2. `command -v kotlinc gradle adb` — exit1, no executables reported. No local Android compiler/emulator was available; the known Gradle distribution bootstrap failure was not pointlessly retried. No Kotlin compile or Android runtime result is claimed.
3. Python read-only integrity audit — exit0: unchanged SHA256 identities for production `EvidenceAi.kt`, `EvidenceArchive.kt`, the AI/archive core regression files and `scripts/evidence179_cases.json`, matching `final179-fix-report.md`/`SECURITY179_FIX_WAVE.md`. Removing only the new Main dispatch envelope from the in-memory Host source reconstructs its accepted SHA256 `7a1e87c7aa7fccae11726dd7a7c8385fc8791f5336e5d934632b947737bbd03c`, demonstrating the authenticated snapshot and protected-output implementation is otherwise byte-identical. All seven final-security test names remain; no static case accounting changed.
4. Python read-only declaration/whitespace audit of the eight changed Kotlin paths — exit0; no trailing whitespace, duplicate case declarations or declaration changes compared with `/tmp/runtime179-fix1-before`. This is source QA only, not behavior or compiler evidence.
5. Original-log extraction read each of the18 complete exception blocks (11–68 exception lines per case) and the15,278-line stalled-case log. No original runtime result was relabelled as corrected-source verification.

## Source freeze identities

| Path | SHA256 |
|---|---|
| `app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceNetwork.kt` | `9b9df0f5ae4a5624ee2f8b3286c15e06e91ed8136c114207a93fb0ac94a33c6f` |
| `app/src/main/java/uk/co/traynor/privategallery/ui/EvidenceAiDialog.kt` | `7dc81bebaf1517a96372fa5d9d72ef8c2623ab2f7c783e1ac280f413f0544579` |
| `app/src/main/java/uk/co/traynor/privategallery/ui/EvidenceLockerHost.kt` | `1122d84b6ce35572f5372977e34a3d8fb7ccdd2bd18e1ce87b96cbb31238fc6b` |
| `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceNetwork179Test.kt` | `5d5d74772e8bb0de9abc145870ad48fe644d36d6fdd19b404bf19c934d884e3d` |
| `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceAi179UiTest.kt` | `18b74e143a00e71d856e5c3b611316c67dd7eb97ba470f8313b135296597ffe5` |
| `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceLocker179UiTest.kt` | `4a9aded4df4dc127e79359f9db71ab91731d807b17208e55242888ab5c3b93fd` |
| `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceSaf179Test.kt` | `701f521a372094f5108beb1b6810d933ab0423f965fbaf4c5c30b50b936b62db` |
| `app/src/androidTest/java/uk/co/traynor/privategallery/ui/Evidence179UiActions.kt` | `aa374e9d375c0d2841bd7563268925ee8b4a612cee0702c2c71d347ba59269d8` |

## Remaining gates

Fresh scoped source review, grouped actual main/test Kotlin compilation and the corrected153-case mandatory suite are required. The bulk timeout's missing state and the Hidden hang's missing original thread stacks remain explicit evidentiary limits; the next run must establish their behavior and retain any new diagnostics. All direct no-name/privacy, fully authenticated plaintext snapshot/late-tag, strict document/owner/hash/lineage, native/PDF/OCR, credential, authority and no-fallback assertions remain mandatory. Real owned VPN/Tor permission/startup/retirement/DNS/packet proof, affected older-feature regressions, final consolidated review, exact APK/test/source accounting, production certificate/upgrade/signing and physical Fold owner acceptance remain separate. Root owns the runner, workflow, manifest and CI. This handoff does not authorize release or imply runtime approval.


## Runtime correction review round 1/5 — R179-1 peer observation

Read `reviews/RUNTIME179_FIX1.md` R179-1 verbatim. The review correctly distinguishes handler termination from an observation that the remote peer closed. The previous unconditional `peerClosed.countDown()` in `finally` could turn a read timeout, an unexpected byte or a local error into a false positive. This section supersedes the initial report's unqualified description of that latch.

Only `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceNetwork179Test.kt` and this report append changed in this round. The saved test preimage is `/tmp/runtime179-peerclose-before.kt`. The report pre-append SHA256 was `42e91b6b28b3c5cc41e789013301ddd7cb9286315e071a92e04af6097076e405`. No production networking/UI/security change, new test declaration, manifest/workflow edit, Git, CI, signing or subagent operation occurred.

The fixture now separates `handlerCompleted` from `peerClosed`. Every request parser read and the held-response read use a private `readPeer` helper that throws a dedicated `PeerEof` only after the underlying read returns EOF. That specific observation or an explicitly classified reset can lower the positive close latch. Reset classification is bounded to eight cause levels and permits only Android `ErrnoException` with `ECONNRESET`, or a `SocketException` whose exact message is `Connection reset`/`Connection reset by peer`. Other TLS/socket errors, local `Socket closed`, timeouts, unexpected held-response input, parser checks and any suppressed cleanup failures are retained as handler failures. Unknown reset forms fail closed rather than count as proof. `finally` now signals completion only.

The response-read cancellation case retains the actual client `responseBodyStart` precondition, `CancellationException`, exactly one request and no-publication assertions. It additionally waits for handler completion and requires its recorded errors to be empty. The existing upload-cancellation case consumes the same peer observation, so it also requires completed error-free handling while preserving the assertion that the source writer has not yet returned. No server-release latch manufactures closure.

### Narrow verification actually executed

- `java /tmp/Runtime179PeerCloseProbe.java app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceNetwork179Test.kt` — exit0. This JDK loopback TLS probe reuses only the existing public disposable test certificate/key in memory and exercises the EOF/reset/error distinction using real socket IO. It reproduces the old unconditional-finally counterexample and tests idle, unexpected-data, local-close and genuine-EOF outcomes. The probe is a JVM observation test of this boundary, not execution of the Kotlin fixture, Android TLS provider or route-cancellation test.

```text
OLD idle peerClose=true failures=[SocketTimeoutException]
NEW idle peerClose=false failures=[SocketTimeoutException]
NEW unexpected peerClose=false failures=[IOException]
NEW local-close peerClose=false failures=[SocketException]
NEW eof peerClose=true failures=[]
```

- A read-only Python comparison against `/tmp/runtime179-peerclose-before.kt` — exit0: eight test declarations/names unchanged; positive signal absent from `finally`; handler-completion signal present; client-read/cancellation/request/no-publication assertions retained; no trailing whitespace. The diff was inspected in full. This is source verification, not compilation.
- No unchanged47-case or broader suite was rerun. No Android compiler/device is available locally; no Android green result is claimed. Exact Android reset/error behavior and actual cancellation remain mandatory in the corrected-source grouped run and fresh narrow review.

Freeze SHA256: `EvidenceNetwork179Test.kt` = `8d8d10fdceeb958304313760114874e86382281ee3ca233aaf3947b23bed5af9`; `/tmp/Runtime179PeerCloseProbe.java` = `3770b18214deda08f8b5cfd6b1aebb3981555db2f04962a5083e151aa5350aac`. All prior bulk/hang evidentiary limits and final-security/release gates remain unchanged.
