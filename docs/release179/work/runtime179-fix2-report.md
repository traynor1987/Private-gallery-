# Evidence179 runtime correction batch 2

2026-10-31. Source handoff from authoritative docs backup202d30890b6c7432648a2795c2efa604255e7e2d/application sourceed836f92c88c24bdb7ddb9b9cdf0a17a40fdb3c6. Read `work/runtime179-fix2-brief.md` first, all14 original aggregate-XML failure stacks in `/workspace/scratch/4181ccd09b2e/diagnostic179-ed836/failures.json`, relevant full per-case logcats, and the current source/working references. **Corrected-source Android compilation and runtime are UNRUN locally. No release or runtime approval is claimed.**

Original run41242395860/job124658786946 completed149 nonroute cases normally:135PASS/14FAIL/0skip, no duplicate identities, four owned-route cases not reached. Artifact13058815723 identity verified by Root:2950138-byte ZIP SHA256 `3fc53b09bcdf5c54eea0094343320b5b25c6efdea5cad7f324b7aa5c68c04f00`. Those are original-source results, not results of this correction. The prior Hidden within-grace hang is gone; all8 network,8 credential,14 OCR and21 core-AI cases passed on that source and were not rerun here.

## Corrections and evidence

### 1. Core archive late-tag regression: invalid text fixture before crypto

`primaryLegacyLateGcmTagFailureNeverPublishesPlaintext` throws `MalformedInputException` through the unchanged `VaultDocumentPolicy.validatingStream:48` into `importVerified` and fixture line385. Its raw `Random(181)` bytes were labelled `text/plain`, so the test never reached its intended tag corruption.

The fixture now contains262144 deterministic ASCII/UTF8 bytes from a64-character alphabet. A strict decoder check retains the valid-document precondition. The JDK probe reproduced the old rejection and measured198569 deflated bytes for the replacement, well above65536. Original size, nonchunked real repository path, final genuine GCM-tag corruption, observed preliminary plaintext greater than64KiB, rejected authenticated export, zero public writes/flushes/closes and empty ciphertext staging assertions are unchanged. No production document validation or archive/crypto/snapshot code changed.

### 2. AI Confirm: title/button ambiguity

The exact XML contains two `Confirm` nodes: a text-only title and an actionable Role=Button. The failing selector required exactly one and never injected its touch. The AIUI case now selects exact text plus `hasClickAction()` and still uses real `performClick()`. Explicit send, separate apply, one request, advisory association/retention, original preservation and egress assertions remain. There is no semantic-click bypass.

### 3. Hidden filename after selection: direct query of lazy content

The record remains in the filtered catalogue; selection does not remove/filter it in the production model/UI. The failing line directly queried `alpha179.txt` after a checkbox scroll/recomposition rather than using the existing lazy traversal helper. It now materializes and scrolls the exact actionable name with `evidenceNode` before the same injected touch. The test still opens the real record/correction form, locks Hidden, requires transient state to clear and verifies stored evidence/original preservation. No search/model change was made.

### 4. Original-selection touch: a concrete nested viewport/input defect

Both original-registration failures observed an actual injected tap followed by checkbox state Off at the same clipped24px semantic bounds `(36,631)–(60,655)` in the320×840/font2 fixture. No retry or semantic activation is used to conceal that result.

Source inspection established that both Evidence selection dialogs put an up-to400dp LazyColumn inside `GalleryAlertDialog`'s default outer `verticalScroll`, contrary to that shell's documented `scrollBody=false` contract for bodies that own a viewport. The original chooser also gives its clickable Row and child Checkbox separate interactive owners. The XML does not identify the exact ancestor that consumed the old pointer; that device-level detail is not invented here.

The original and export selection dialogs now opt out of the shell scroll and use one bounded LazyColumn containing intro, choices and count/limit information. Each choice has one full-width `toggleable` Row, minimum48dp, Role.Checkbox and the existing content description. The child checkbox is a passive visual indicator, not a second interactive target. The128-original bound, distinct IDs, owner confirmation, no automatic registration, source preservation and export validation are unchanged. Selection does not truncate: unselected originals become disabled at the bound and selected ones remain removable.

The existing registration tests retain actual `performClick()`/`assertIsOn()`, expected one/two catalogue records, pre-confirmation empty catalogue, multiple incidents plus standalone selection and original preservation. They additionally require the target to be displayed and at least48dp high. Thus a future pass still requires the real product touch target to work. Actual corrected layout/taps remain a mandatory Android gate, not a host-proved claim.

### 5. Eight provider/native gate-entry failures: frame-dependent waits, with producer-lifetime proof

Five write cases fail before `writeEntered` and three native cases before their renderer-return hook. Original logcats contain the gate assertion but no recorded pre-gate operation error/class. There is no evidence here of an unsupported PDF descriptor or a reason to change the renderer. Three `activeReport` cases passed with the exact same real `VaultPdfPreview` proxy-FD/PdfRenderer. Encrypted byte-exact output and explicit plaintext output passed with the same provider paths. These successful cases use `compose.waitUntil` to progress composition. In contrast, the failures block the instrumentation thread on raw latch `await` immediately after UI/picker actions, before the next Host effect or opened-report recomposition can be driven by the Compose test frame clock. The existing blocked read case can begin directly from the button coroutine and passed; it is left unchanged.

`awaitEvidenceGate` now uses condition-based `ComposeTestRule.waitUntil` at the same15s/30s/45s deadlines, allowing the frame clock to advance while requiring the actual entry latch. It never releases a callback/provider hold. A pre-gate captured failure exits the wait only to fail the test; it is not accepted as entry. Timeout/assertion diagnostics contain only fixed phase, counts and exception class, plus fixed generic error-UI flags. Provider worker exceptions are retained instead of discarded; the native hook records whether it was not called, inside the real native render, or holding its borrowed return, and captures the actual thrown class. No report bytes, file names, credentials or evidence text are logged.

Gate entry alone is not accepted as proof that work remains active. Provider `writeHolding` wraps the real release-latch await after the first output byte. Before retirement the tests also require the Saving dialog still present and zero prior capability-cancellation/closure signals. The actual CancellationSignal records `cancelledDuringWriteHold` before releasing; both encrypted and explicitly confirmed plaintext tests require that positive capture after retirement. Thus a worker that timed out/completed before cancellation cannot pass. The native wrapper tracks both `nativeHolding` around the actual borrowed-return await and `renderCompleted` in its callback finally. The tests require holding/not-completed immediately before Lock/Hide/Panic AND after retirement before manually releasing, with no render error. Their prompt-Main, pending-bitmap recycling, no late report, ciphertext cleanup and original-preservation assertions remain.

This corrects the frame-blocking test protocol and adds the missing phase/error/lifetime evidence. Because the old run did not capture those operation states, successful corrected entry and the exact old scheduling timeline remain unverified until the next Android execution. No production Host, renderer, descriptor or DocumentIO change was justified or made. There is no private/public plaintext-file fallback.

### 6. Hidden reauthentication: deliver the stale result before expecting callback rejection

The original15s timeout is before delivering any picker result: the fixture first waits for owner dismissal after foreground reconciliation. The Host may retain an opaque pending picker until a result arrives; its effect keys can also coalesce stopped/resumed state while composition is paused. Its actual contract rejects a stale authentication on result admission, not a promise of dismissal before delivery. The ordinary expired-result and within-grace cases both passed in the same original run.

The test now performs real Activity resume/ON_START foreground reconciliation, explicitly asserts that the captured Hidden authentication is expired, the key is locked, the old operation remains retired and no second access exists. It then authenticates a genuinely new identity while resumed, asserts that the old authentication is still invalid, delivers the original picker result, and only then waits for rejection. It retains zero provider reads, exactly one access, owner retirement and no old-controller revival. It does not proactively close the owner to manufacture rejection and makes no production authority/lifecycle change.

## Exact original14 failures

- `uk.co.traynor.privategallery.core.evidence.EvidenceArchive179Test#primaryLegacyLateGcmTagFailureNeverPublishesPlaintext`
- `uk.co.traynor.privategallery.ui.EvidenceAi179UiTest#actualPreviewNeedsExplicitSendAndSeparateOwnerApplication`
- `uk.co.traynor.privategallery.ui.EvidenceLocker179UiTest#hiddenInnerLockClearsSearchSelectionsAndDialogsWithoutDeletingEncryptedEvidence`
- `uk.co.traynor.privategallery.ui.EvidenceLocker179UiTest#bulkOriginalRegistrationAndMixedExportSelectionAreOwnerReviewed`
- `uk.co.traynor.privategallery.ui.EvidenceLocker179UiTest#primaryActualImportExtractCorrectIncidentSearchRemoveAndReopenPreservesOriginal`
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test#lockDuringBorrowedNativeRenderRejectsAndRecyclesUnpublishedBitmap`
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test#hideDuringBorrowedNativeRenderRejectsAndRecyclesUnpublishedBitmap`
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test#cancelBlockedPlaintextSnapshotProviderWriteRevokesWithoutBlockingMain`
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test#cancelBlockedProviderWriteClosesAdmissionWithoutBlockingMain`
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test#hideDuringBlockedProviderWriteRevokesAndRequestsPartialCleanup`
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test#panicDuringBorrowedNativeRenderRejectsAndRecyclesUnpublishedBitmap`
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test#lockDuringBlockedProviderWriteRevokesAndRequestsPartialCleanup`
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test#hiddenReauthenticatedPickerCannotReviveOriginalAuthentication`
- `uk.co.traynor.privategallery.ui.EvidenceSaf179Test#panicDuringBlockedProviderWriteRevokesAndRequestsPartialCleanup`

## Scope and verification actually performed

Six Kotlin paths changed, plus this report:

- `app/src/main/java/uk/co/traynor/privategallery/ui/EvidenceLocker.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceArchive179Test.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceAi179UiTest.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceLocker179UiTest.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceSaf179Test.kt`
- `app/src/androidTest/java/uk/co/traynor/privategallery/ui/Evidence179UiActions.kt`

Preimages are preserved under `/tmp/runtime179-fix2-before/`. No test method was added, removed or renamed: archive28, AIUI1, Locker7, SAF31 (67 methods) remain represented by the unchanged153-case manifest. No Git, CI, signing, secrets, subagents, workflow/runner/manifest edits or unchanged passing-suite reruns were performed.

1. `java /tmp/Runtime179TagFixtureProbe.java` — exit0. Exact output:

```text
OLD fixture strict UTF8 rejection reproduced: java.nio.charset.MalformedInputException: Input length = 1
NEW valid UTF8 bytes=262144, compressed=198569, exceeds65536=true
```

This is a JDK strict-decoder/compressibility check of the fixture, not execution of Android import, BouncyCastle prefix behavior or tag verification.

2. Read-only Python comparison of the six changed paths against their preimages — exit0: unchanged `@Test` names/order/counts and no trailing whitespace. Actual touch methods remain; no `performSemanticsAction` activation was introduced. Existing real late-tag/prefix/zero-output assertions were checked present.
3. Read-only SHA256 preservation audit — exit0: `EvidenceAi.kt`, `EvidenceArchive.kt`, `EvidenceLockerHost.kt`, `EvidenceNetwork.kt`, `EvidenceNetwork179Test.kt`, core `EvidenceAi179Test.kt` and `scripts/evidence179_cases.json` exactly match accepted pre-batch identities. SEC179-1 no-name projection, SEC179-2 authenticated snapshot-before-output, R179-1 peer-close proof, transport and credentials are untouched. Core archive test changes are only the valid data precondition; all seven final-security regression declarations remain.
4. Read-only original XML comparison — exit0: byte-exact encrypted output, explicit plaintext output, three active native report views, blocked archive read, Hidden expiry and Hidden within-grace reference cases have no original failure/error/skip. This is inspection of retained original-source evidence, not a rerun or final-source pass.

No local Android compiler/emulator is available. The known uncached Gradle bootstrap was not retried. No Kotlin/main/test compile, on-device pointer, provider, renderer, lifecycle or corrected153-case green result is claimed.

## Source freeze identities

| Path | SHA256 |
|---|---|
| `app/src/main/java/uk/co/traynor/privategallery/ui/EvidenceLocker.kt` | `ea3d8542b25ba2e4bd6c9bdde95658ce7da35666542b47d07334c0467e330ec7` |
| `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceArchive179Test.kt` | `adb56af447c5265343e63d92161ae9b9af27a7b86e8d243e3b614938a4e45c6f` |
| `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceAi179UiTest.kt` | `f25e53a90c489a29028ad6e2e43c34ff6b24e51e7322bb4447b1158d16e1499b` |
| `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceLocker179UiTest.kt` | `62c4c7ff863c968e09149c25cd390fa67bd2a05a1af5aa1766e1e8ee241a9068` |
| `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceSaf179Test.kt` | `b9bdc1508edeecb887aeff229d79e513cbb0c6d437aa34e4aeaf729f1a40b7fd` |
| `app/src/androidTest/java/uk/co/traynor/privategallery/ui/Evidence179UiActions.kt` | `22adb7f9ddae1c2a43a917876d6ca8975ca9ef070f8f3e9d8afa21f03b11d818` |

## Remaining gates and precise limits

Fresh scoped review and actual grouped main/test compilation plus all153 required unskipped cases remain mandatory. The new selection layout requires real font2 small-viewport touch proof. The gate tests must actually enter the held encrypted/plaintext/native boundaries, prove producers remain active at retirement, then prove signal/resource cleanup and no late publication. Their new fixed diagnostics must be retained if any pre-gate operation fails. Exact old pointer-consumer and pre-gate exception/scheduling details were not captured by the original14 failures; this report does not fabricate them.

All current authority/Main, source integrity/EOF, owner/lineage/VaultOnly policy, OCR/PDF/PNG, credentials, native borrowing, no fallback/no billed retry and fully authenticated snapshot assertions remain strict. Four live owned VPN/Tor cases, exact source/APK/test accounting, affected existing-feature regression evidence, consolidated review, original production certificate/code50→51 upgrade/signing/distribution and physical Fold acceptance remain separate Root-owned gates. This is a correction handoff, not release approval.


## Review round 1/5 — R179-F2-1 non-vacuous stale-result ordering

Read `reviews/RUNTIME179_FIX2.md` R179-F2-1 verbatim. The earlier two-call resume/reauthentication sequence permitted Host dismissal between calls. Registry dispatch could then succeed against a callback whose `pending` was already cleared, making zero-read/final-dismissal assertions vacuous. This append supersedes that earlier sequencing claim; no production lifecycle or authority requirement was relaxed.

Only `EvidenceSaf179Test.kt` and this report append changed in this round. The saved test preimage is `/tmp/runtime179-fix2-reauth-before.kt`; the report pre-append SHA256 was `2f51f8998a5e15016c7b00cda606be95a7b82a6906405a2b122093dad426734d`. No unchanged67-case suite, Android build, CI, Git, signing or subagent work was performed.

The harness's existing Activity lifecycle observer is installed during construction before the Host's composition observer. It now counts completed real ON_START/`HiddenSession.onForeground` reconciliations and supports a cleared-before-use, one-shot ON_RESUME test hook. The reauthentication case installs that hook while the Activity is actually stopped. Inside the actual resume event, on Main and in Lifecycle.State.RESUMED, it requires a new completed foreground reconciliation, verifies the old authentication expired and key locked, keeps the old controller retired and one access, then completes a genuinely new Hidden authentication. In that same uninterrupted Main turn, it checks the original OpenDocument request identity/one launch, zero provider reads, one access, a read-only reflection of the original owner's still-false `closed` atomic flag, and **zero prior dismissals immediately before dispatching the old result**. The closed-flag observation also rejects an earlier owner disposal even if it did not increment the dismissal callback. The Host's later resume/result admission must then dismiss the stale original owner. No `owner.close`/`owner.sameAuthentication` is invoked to manufacture the pre-dispatch condition.

Any hook assertion failure is captured solely to propagate it back from the lifecycle callback as an AssertionError after the actual Activity transition; it cannot be ignored. A separate positive flag requires that this one-shot hook actually dispatched the result. Afterward the case still requires subsequent dismissal, no provider read, exactly one access, retired old controller and invalid original owner, and additionally requires the genuinely new session to remain unlocked and distinct from the original captured authentication. All31 SAF test declarations/names remain unchanged. Other cases leave the one-shot hook unset.

### Observed narrow checks

1. `java com.sun.tools.javac.Main -d /tmp/runtime179-reauth-probe-classes app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenSession.java /tmp/Runtime179ReauthOrderProbe.java` — exit0.
2. `java -cp /tmp/runtime179-reauth-probe-classes Runtime179ReauthOrderProbe` — exit0:

```text
OLD split Main turns: finalAssertions=true deliveredToPending=0 (vacuous counterexample)
NEW ordered resume turn: preDispatchDismissals=0 deliveredToPending=1 subsequentRejection=true
NEW precondition on predismissed owner: rejected=true callbackNotDispatched=true
```

The probe executes the real `HiddenSession` expiry/new-authentication behavior and models serialized Main/Host pending-result ordering to demonstrate the counterexample and its new precondition. It does not execute Android LifecycleRegistry, Compose, ActivityResultRegistry or Host and is not Android runtime proof.

3. Read-only Python comparison against the saved preimage — exit0:31 unchanged test declarations, no trailing whitespace; inspected the complete narrow diff. The only modifications are one-shot lifecycle sequencing/counts and stronger pre/post-dispatch assertions in the existing reauthentication flow. No new `@Test` or static-manifest entry was added.

Updated freeze identity for `EvidenceSaf179Test.kt`: `768415be97f88806d0f3317cc18641d24cfdb7d8d36ea26f1f2266df00cf861a` (supersedes only its row in the prior batch freeze table). Host probe `/tmp/Runtime179ReauthOrderProbe.java`: `4863179bf0337ade559d71f24d21d3f2e3d2a58e5b4cdbb2b66aadbc0f59ad4d`. Other source identities and all pending Android/153-case/security/release gates remain unchanged. This exact stale-result case still requires actual Android execution after fresh narrow source review; no green result is claimed.
