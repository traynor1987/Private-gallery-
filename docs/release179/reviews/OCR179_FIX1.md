# OCR179 fix round 1 scoped rereview

Review date: 2026-10-31. Scope: OCR179-1 and OCR179-2, plus breakage introduced by their fixes. This is a source task gate, not release approval.

**Verdict: both findings ADDRESSED at the source/test-design gate. No new fix-induced source defect found.** Android compilation, instrumented execution and native/package verification remain pending.

## Evidence reviewed

Compared the initial package `/workspace/scratch/4181ccd09b2e/review179-ocr.diff` with `/workspace/scratch/4181ccd09b2e/review179-ocr-fix1.diff`, including the full untracked adapter/test contents, and read the actual current files. Reviewed the fix-round report appended to `work/task-3-report.md`, the updated `OCR179.md`, the new host source guard, and the original review. The fix package contains the adapter, Android test and host test; the documentation/report changes were inspected directly in the working tree.

Reused the pinned upstream source inspected during the initial review and checked ResultIterator Java signatures and native paragraph/word iteration against tag4.9.0. No product files were changed; no already-passing tests, Android compile/runtime, CI, signing, commit, push or subagents were run. Only this review report was written.

## Finding dispositions

| Finding | Disposition | Source evidence |
| --- | --- | --- |
| OCR179-1: post-hOCR getters can restart unmonitored recognition | **ADDRESSED** | `EvidenceOcr.kt:42-79` exposes only hOCR recognition plus cached cursor operations. `:224-238` checks authority/deadline and rejects null/blank hOCR after native exit and monitor join. `:250-277` reads cached words/paragraphs and computes confidence without TessBaseAPI text/mean getters. |
| OCR179-2: tests do not establish active native recognition | **ADDRESSED** | `EvidenceOcr179Test.kt:195-226` waits for controlled native entry, requires stop, holds native exit and checks resource lifetime before release. `:238-273` waits for actual JNI hOCR progress before cancellation/revocation, with prebuilt input and bounded completion assertions. |

### OCR179-1

The production `NativeEngine.recognize` delegates only to `getHOCRText`. `cursor()` obtains `api.resultIterator`; its operations delegate to that existing iterator's `begin`, `getUTF8Text(level)`, `confidence(level)`, `getBoundingBox(level)`, `next(level)` and `delete`. The formerly unsafe `api.getUTF8Text()` and `api.meanConfidence()` calls are absent.

In the pinned vendored Tesseract source, `TessBaseAPI::GetIterator()` returns null for absent engine/page result and otherwise constructs a ResultIterator from cached page state; it does not call `Recognize`. ResultIterator text access and navigation operate on that cached state, including null/end-of-page handling. This resolves the initial blank-page fallback, rather than merely renaming or moving it.

Null/blank hOCR is a local failure before cursor access. A successful hOCR result with no cached cursor returns empty text/words and confidence-1. A non-null but empty iterator similarly yields no words/text without another recognition call. Word confidence remains unfiltered; the aggregate is calculated only from available scores and stays-1 when none exist. Unknown pages are excluded from the document mean. The changed aggregation is documented. The text limit and explanatory partial flag are preserved in the paragraph path.

Targeted authored coverage includes null/empty hOCR rejection, empty cached results, low-confidence retention, iterator/bitmap/engine cleanup order, and an actual JNI blank page. The host source guard bans the two unsafe API getters and requires explicit hOCR failure validation. The reported eight host passes are supporting prior evidence, not runtime proof.

### OCR179-2

The controlled seam prepares image bytes before launch, waits for `Engine.recognize` entry, requests cancellation or revocation, and then waits for `stop()`. While native exit remains held, it asserts that the bitmap is not recycled, the engine is not closed, and the operation is incomplete. It then releases native exit and checks no extraction/cursor access, eventual completion, native-exit-before-bitmap/engine cleanup, and a subsequent successful extraction. The successful cached-result case separately checks cursor closure while the bitmap is still live and the subsequent cleanup order.

The real JNI cases use the pinned `TessBaseAPI(ProgressNotifier)` constructor. The first actual native progress callback signals entry and waits on a bounded latch. The test requests cancellation/revocation only after that signal, then releases the callback and requires operation completion within five seconds, no returned result, no additional private evidence files, and successful subsequent recognition. The arbitrary launch-time delays were removed. Both cancellation modes have dedicated real-JNI cases.

These assertions now exercise the intended state transitions by design. Their successful execution and practical native timing are still unobserved. A timeout assertion cannot forcibly terminate an uncooperative JNI call; the adapter correctly continues to require native exit before freeing resources.

## Regression scope and remaining gates

The Engine/Cursor abstraction preserves the public `EvidenceOcr(Context)` entry point and the existing recognize signature. Native cleanup still follows hOCR exit and NonCancellable monitor join; cursor cleanup remains in finally. Existing bitmap bounds, PDF/current checks, model preparation, caller-owned bytes and final authority fencing remain in place. No new networking or evidence output file path was introduced. No actionable source regression was found within this fix scope.

Before closing runtime/release gates, root still needs actual API37 app/androidTest compilation, all14 authored OCR instrumentation methods, resolved dependency/native ABI coverage, ELF16KiB and APK alignment, and packaged model/hash verification. Primary/Hidden integration and final publication fencing remain root integration responsibilities. No compilation, native cancellation latency, JNI blank-page behavior or final package compatibility pass is claimed by this rereview.

Relevant pinned upstream references:

- [TessBaseAPI.java](https://github.com/adaptech-cz/Tesseract4Android/blob/4.9.0/tesseract4android/src/main/java/com/googlecode/tesseract/android/TessBaseAPI.java): progress constructor and cached iterator acquisition.
- [ResultIterator.java](https://github.com/adaptech-cz/Tesseract4Android/blob/4.9.0/tesseract4android/src/main/java/com/googlecode/tesseract/android/ResultIterator.java): level-based text/confidence and delete signatures.
- [Vendored baseapi.cpp](https://github.com/adaptech-cz/Tesseract4Android/blob/4.9.0/tesseract4android/src/main/cpp/tesseract/src/src/api/baseapi.cpp): cached `GetIterator` behavior.
- [Vendored resultiterator.cpp](https://github.com/adaptech-cz/Tesseract4Android/blob/4.9.0/tesseract4android/src/main/cpp/tesseract/src/src/ccmain/resultiterator.cpp): cached text, paragraph navigation and empty/end handling.
