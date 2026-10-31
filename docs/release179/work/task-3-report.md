# Task3: offline OCR implementation report

Status: implementation and scoped host verification are ready for root review. Android Kotlin compilation, packaged APK/native checks, and real API37 OCR remain **pending**, not passed. No CI, commit, push, signing, or subagents were used. Root-owned controller/metadata/source/UI files were not edited.

## Delivered files

- `app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceOcr.kt`: exact suspend interface from CONTRACTS; immutable Ocr records; strict UTF8/CSV, bounded sampled images, rotation, page-indexed PDF raster OCR via existing VaultPdfPreview; raw text/words/confidence, unverified timestamp hints; no participant auto-assignment; explicit partial flags; cancellation/revocation stop monitor with native-exit-before-cleanup lifecycle; private verified public model installation. No runtime networking or evidence output files.
- `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceOcr179Test.kt`: seven real runtime test methods, written before the adapter. Subsequent assertions strengthen raw correction preservation, large-image sampling, malformed UTF8 after retained prefix, private-directory output checks, and source byte immutability.
- `scripts/prepare_evidence179_model.sh`: fixed official tessdata_fast4.1.0 English model, exact length/SHA, optional verified public cache, existing-output verification, HTTPS-only curl with no retries, unique staging, cleanup, atomic promotion; binary generated under build, never source assets.
- `scripts/tests/test_evidence179_model.py`: seven scoped host tests using actual staged public model and fake curl. Positive fixture location can be overridden by `EVIDENCE179_MODEL_CACHE` for grouped build verification.
- `settings.gradle.kts`: JitPack restricted to `cz.adaptech.tesseract4android` only; existing repositories preserved.
- `app/build.gradle.kts`: only pinned standard dependency plus generated-assets/preBuild model task added by this task. Existing root-applied version1.7.9/code51/API37 metadata preserved.
- `app/src/main/assets/evidence179/`: model source/hash/length manifest; exact staged Apache model license; full upstream wrapper/Tesseract/Leptonica/libjpeg/libpng license texts, their source/hash manifest, and notice. No traineddata source-control binary.
- `docs/release179/OCR179.md`: architecture, bounds/provenance, build procedure, limitations, upstream native evidence, pending package/runtime verification.

## Observed host RED → GREEN

Before creating the preparer, `python3 -m unittest scripts.tests.test_evidence179_model -v` ran five initial cases and failed two positive cases because `scripts/prepare_evidence179_model.sh` did not exist. Three negative cases were not independently useful at that absent-script stage; they now execute the implementation. This was an actual observed host RED. Instrumentation tests were authored first, but no Android RED/GREEN is claimed because Gradle never bootstrapped.

After implementation and final adapter refinements:

`PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.tests.test_evidence179_model -v`

**7 tests PASS**, including exact verified cache/no-network reuse, valid fixed official download via fakecurl, same-size wrong SHA cache, same-size wrong SHA download, partial curl failure cleanup, corrupt generated-output repair, and pinned packaging/no-runtime-fetch architecture check.

`bash -n scripts/prepare_evidence179_model.sh`: PASS.

`git diff --check`: PASS.

Direct staged-public preparation into ignored `app/build/generated/evidence179Assets/tessdata/eng.traineddata`: PASS; SHA256 `7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2`, length4,113,088. Exact staged model Apache license SHA `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`. Six packaged license manifest entries were separately verified against local file length/hash.

A broad Python discovery was run once before root clarified the risk-based scope. It ran229 tests with2 failures and1 error, all outside this OCR change: `test_fast_lane_has_no_emulator_action` (historical fast workflow assertion), `test_current_identity_and_original_code45_baseline` (stale1.7.7/code46 identity assertion), `test_real_document_download_and_exact_multipart_evidence` (fixture `KeyError: 'method'`). Root was informed. These are historical/out-of-scope observations, not1.7.9 OCR gates; no historical tests/workflows were changed and no further broad run was made. Generated Python cache side effects were cleaned/restored.

## Android/runtime tests authored; not executed

`./gradlew :app:compileDebugAndroidTestKotlin --offline` failed in wrapper bootstrap before project configuration: it attempted `https://services.gradle.org/distributions/gradle-9.5.0-bin.zip`, then `java.net.SocketException: Network is unreachable`. No Kotlin compile result, AAR resolution result, APK build, device OCR result, or native cancellation timing result exists here.

The seven test methods are:

1. `renderedMessagePreservesNamesDatesWordsConfidenceAndRawCorrection`
2. `ordinary48MegapixelImageIsSampledBeforeWorkingAllocation`
3. `explicitRotationIsAppliedAndRecorded`
4. `utf8DocumentIsStrictUnknownConfidenceAndBounded`
5. `pdfPagesCarryIndependentWordAndTimeIndicesAndLimitExplanation`
6. `revokedNativeRecognitionPublishesNothingAndCanRunAgainWithoutEvidenceFiles`
7. `cancellationDoesNotPublishAndRejectsOversizedInputOrInvalidRotation`

They require actual packaged model/native JNI and an API37 instrumented target. The PDF test creates33 synthetic pages and accepts the explicit page/time limit partial result, checks processed page indices, and expects multiple completed pages. The48MP test creates an8000×6000 synthetic bitmap then releases it before OCR, and verifies a2000×1500 working raster. Grouped runtime should use its planned512MiB test heap.

## Dependency/native ABI/16KiB evidence and concerns

Upstream tag4.9.0 files were read through the GitHub connector (public read-only): TessBaseAPI, ResultIterator, PageIterator, build.gradle, native CMake files and licenses. Verified API signatures include OEM_LSTM_ONLY, recycle(), stop(), getHOCRText(), resultIterator, iterator.getBoundingBox() as int[], iterator.delete(), meanConfidence(). Upstream explicitly documents getUTF8Text() alone as uninterruptible; the adapter always performs cancellable hOCR recognition first and never gets text after a stopped pass.

Upstream `tesseract4android/build.gradle` pins NDK27.2.12479018, sets `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`, and leaves all default NDK ABIs enabled (comment lists x86/x86_64/armeabi-v7a/arm64-v8a). Native libraries are jpeg, pngx, leptonica, tesseract, with packaged C++ runtime as applicable. This is source evidence, **not actual AAR/APK ELF verification**. Root grouped build must still inspect resolved packaged ABI coverage, 16KiB ELF LOAD alignment and APK zip alignment (`zipalign -c -P 16`), and exact APK model asset bytes/hash. Dependency availability/group mapping at JitPack must be verified during actual resolution; no alternate coordinate or fallback was silently introduced.

Native cancellation calls stop from the independent monitor every25ms until hOCR exits; cleanup joins monitor inside NonCancellable before iterator/native/bitmap lifetime ends. Root still must run real cancellation/revocation tests to establish engine behavior/timing. Synchronous native init, Android bitmap decode and PDF platform render cannot safely be forcibly killed mid-call; authority/deadline checks fence results and PDF proxy reads, but a blocked platform call can delay physical return/cleanup. Do not claim hard wall-clock exit from source inspection.

Mutable bitmaps and byte/char buffers are cleared and recycled/closed. Immutable Java/JNI text strings cannot reliably be wiped; they stay only in memory until collection. No plaintext evidence image/text file is produced. Only public traineddata is written to the private no-backup model directory. `Ocr.width/height` are working raster dimensions matching word geometry; root Metadata preserves source dimensions. Runtime path accepts caller-owned bytes and does not alter them; output save/history/retirement fencing remains controller responsibility.

## Review fix round1 of5 — OCR179-1 and OCR179-2

Status: both source findings have been addressed and the focused test package is prepared for scoped rereview. The source/host changes below supersede the earlier report's assumption that TessBaseAPI text/confidence getters were cached-only and its original delay-based cancellation tests. Android compilation and all instrumented/native results remain **pending**, not passed.

Read `reviews/OCR179_INITIAL.md` in full, reread the task brief/report, and used the receiving-code-review/systematic-debugging guidance. Public read-only upstream inspection confirmed the root cause in the pinned4.9.0 implementation: TessBaseAPI `GetUTF8Text()` and `AllWordConfidences()` can call `Recognize(nullptr)` while `recognition_done_` is false; the empty-page path can return before setting that flag. `GetIterator()` instead checks cached `page_res_` and never calls recognition. Its ResultIterator accessors operate on existing result state. TessBaseAPI's progress-notifier constructor and ResultIterator paragraph methods were also verified against the pinned source before using them.

### OCR179-1: eliminate implicit second recognition

`EvidenceOcr.kt` now owns the native API through a narrow internal Engine/Cursor boundary. The production Engine exposes one recognition entry point, interruptible hOCR. It does not expose any TessBaseAPI UTF8 or mean-confidence convenience getter. Null/blank hOCR fails locally after native exit and monitor join, before opening a cursor. Successful hOCR with an empty cached page returns empty text/words and unknown confidence(-1). A real JNI blank-page case is prepared.

Raw text is accumulated in cached paragraph order using ResultIterator, with the existing100k bound/partial explanation. Word text, geometry and confidence are read from that same cached iterator without confidence filtering. Page confidence is the arithmetic mean of available cached word confidence values, or-1 if none; unknown pages are excluded from the document mean. There is no recognition-triggering fallback for either text or confidence. Cached cursors close in finally; native exit/monitor join still precedes bitmap and engine cleanup. Original bytes, private-model preparation, no-network policy, bounds, provenance, and the final authority checks remain intact.

### OCR179-2: synchronize tests to active recognition and verify lifetime

The two arbitrary20/25ms delay tests were replaced. The internal seam lets tests hold a synchronous native recognition call after an entry latch; cancellation/revocation must cause `stop()`, while the test asserts that the bitmap and engine are still alive and the operation remains incomplete. Only then does it release native exit. It asserts bounded completion, no returned extraction, no cursor read after a stopped pass, native-exit-before-bitmap/engine cleanup, and successful subsequent extraction. A successful cached-result test separately asserts iterator close while the bitmap is still alive, then bitmap cleanup, then engine close. It retains a0.5-confidence word to detect confidence filtering.

Two actual JNI cases remain in the package, one each for coroutine cancellation and captured-authority revocation. Dense2048×2048 synthetic message input and a subsequent ordinary input are fully rendered/compressed before launching the operation. The first actual Tesseract hOCR progress callback uses a bounded latch handshake: the operation is inside JNI when the test requests cancellation/revocation. The test releases the progress callback, requires completion within five seconds, rejects a returned result, compares private file sets, and requires a successful subsequent extraction. It does not infer native entry from launch time or a delay. These are prepared assertions; their practical timing/native behavior has not been observed here.

### Changed files in this fix round

Only these owned files changed: `EvidenceOcr.kt`, `EvidenceOcr179Test.kt`, `scripts/tests/test_evidence179_model.py`, `docs/release179/OCR179.md`, and this appended report. Dependency configuration, public model preparation/assets, controller/Main/Access/source/metadata/egress files were not changed in this round. No broad Python/history/regression suite, CI, commit, push, signing or subagent was run.

### Focused commands and observed output

Before changing production OCR, added the host guard `test_native_results_cannot_trigger_a_second_recognition` and ran:

`PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.tests.test_evidence179_model.ModelPreparation.test_native_results_cannot_trigger_a_second_recognition -v`

Observed **RED**, one failure: the original adapter still contained `api.getUTF8Text(`. This is an architecture/source guard against unsafe getters, not runtime proof of native behavior. New instrumented tests were written before the adapter change, but no Android RED/GREEN is claimed.

After the implementation:

`PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.tests.test_evidence179_model -v`

Observed **8 tests PASS**: all seven existing model preparation/packaging checks plus the new guard banning TessBaseAPI text/mean getters and requiring explicit hOCR failure validation.

`bash -n scripts/prepare_evidence179_model.sh`: PASS.

`git diff --check`: PASS. A direct owned-text-file whitespace check also reports no trailing whitespace.

`./gradlew :app:compileDebugAndroidTestKotlin --offline`: retried once in this round; still fails before project configuration in Gradle9.5.0 wrapper bootstrap with `java.net.SocketException: Network is unreachable`. This is not a Kotlin compiler result. Dependency/AAR/APK/native ABI/16KiB alignment checks remain pending exactly as recorded above.

### Current instrumentation package:14 methods, all pending execution

The five existing message/correction,48MP, rotation, strict UTF8 and PDF methods remain. The replacement/additional focused methods are:

- `failedHocrNeverReadsCachedResultsAndCleansResources`
- `emptyCachedPageDoesNotStartAnotherRecognition`
- `cachedIteratorRetainsLowConfidenceAndClosesBeforeBitmapAndEngine`
- `activeNativeCancellationWaitsForExitBeforeCleanup`
- `activeNativeRevocationWaitsForExitBeforeCleanup`
- `realJniProgressCancellationCompletesWithinBoundAndCanRecognizeAgain`
- `realJniProgressRevocationCompletesWithinBoundAndCreatesNoEvidenceFiles`
- `blankRealJniPageHasEmptyTextAndUnknownConfidence`
- `rejectsOversizedInputAndInvalidRotation`

All instrumented seam assertions and actual JNI progress/blank-page assertions must still run on API37 with the pinned asset/native dependency packaged. Source review and eight host passes do not establish runtime cancellation latency, JNI behavior, compile success or final APK compatibility. Synchronous native initialization/platform decode/render limitations remain as previously documented.
