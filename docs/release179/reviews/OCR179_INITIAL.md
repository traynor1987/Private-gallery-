# OCR179 initial task review

Review date: 2026-10-31. Scope: offline OCR task only, not release approval.

**Spec verdict: changes requested. Quality/security verdict: changes requested.** Two source-verifiable findings below need correction. Android compilation, dependency resolution, APK/native packaging and API37 execution remain pending gates, not observed failures.

## Scope and evidence

Read `work/task-3-brief.md`, the OCR/model/authority/timestamp portions of `CONTRACTS.md`, `work/task-3-report.md`, `OCR179.md`, and `/workspace/scratch/4181ccd09b2e/review179-ocr.diff`. Inspected the current adapter, instrumentation, model preparer/host tests, Gradle changes, manifests/notices, `VaultPdfPreview`, `EvidenceTextHints`, and the Java record constructors. The version1.7.9/code51 diff is understood to be root-owned, as recorded in the brief/report.

Also inspected the pinned upstream Tesseract4Android4.9.0 Java/JNI and vendored Tesseract source through read-only GitHub access to verify the getter behavior used in finding OCR179-1. No product files were edited, no host tests were rerun, no Android compilation/runtime was attempted, and no CI, signing, commit, push or subagents were used. This review writes only this report.

## Findings

### OCR179-1 — P2: post-hOCR getters can restart unmonitored recognition

**Location:** `app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceOcr.kt:179-190`, also `:212`.

`nativePage` discards the hOCR return, terminates/joins the stop monitor, and assumes that `getUTF8Text()` and `meanConfidence()` only read cached results. That assumption is false for the exact pinned upstream version. In vendored `baseapi.cpp`, `GetUTF8Text()` calls `Recognize(nullptr)` when `recognition_done_` is false; `MeanTextConf()` uses `AllWordConfidences()`, which has the same fallback. `Recognize()` returns successfully for an empty page before it sets `recognition_done_ = true`. Thus an ordinary blank image/page can take this path even when hOCR succeeds. Failed hOCR is also not checked before these getters.

The second pass has no cancellation monitor. The documented `stop()` mechanism does not interrupt that fallback recognition, so revocation/deadline can be observed only after it returns. Final authority checks still prevent publication, and the adapter does not recycle while that synchronous call is running; this is a cancellation/deadline contract defect, not evidence of a use-after-free or cross-vault publication.

**Required change:** positively handle hOCR failure and empty results, and obtain text/confidence without a getter path that can implicitly restart recognition after the monitored pass. A cached ResultIterator-based path or an explicitly established completed/nonempty-result condition with a separate empty-page result can satisfy this. Preserve existing native-exit-before-cleanup ordering. Add focused blank-page and failed-hOCR coverage, including an assertion that no unmonitored recognition fallback is used.

**Upstream evidence inspected:**

- [TessBaseAPI.java, tag4.9.0](https://github.com/adaptech-cz/Tesseract4Android/blob/4.9.0/tesseract4android/src/main/java/com/googlecode/tesseract/android/TessBaseAPI.java): `getUTF8Text` documents its uninterruptible behavior; `getHOCRText` is interruptible.
- [JNI tessbaseapi.cpp, tag4.9.0](https://github.com/adaptech-cz/Tesseract4Android/blob/4.9.0/tesseract4android/src/main/cpp/tesseract/tessbaseapi.cpp): hOCR passes an ETEXT_DESC monitor; UTF8 getter does not; mean confidence delegates to `MeanTextConf`.
- [Vendored baseapi.cpp, tag4.9.0](https://github.com/adaptech-cz/Tesseract4Android/blob/4.9.0/tesseract4android/src/main/cpp/tesseract/src/src/api/baseapi.cpp): `Recognize` empty-page early return (approximately lines758-772), `GetUTF8Text` (approximately1303), and `AllWordConfidences` (approximately1733).
- [Vendored hocrrenderer.cpp, tag4.9.0](https://github.com/adaptech-cz/Tesseract4Android/blob/4.9.0/tesseract4android/src/main/cpp/tesseract/src/src/api/hocrrenderer.cpp): `GetHOCRText(ETEXT_DESC*,int)` can return null when recognition fails.

### OCR179-2 — P2: cancellation tests do not establish that native recognition is active

**Location:** `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceOcr179Test.kt:103-125`.

Both cancellation/revocation tests use an arbitrary20/25ms delay from launching the coroutine. The cancellation case creates/compresses its bitmap inside that coroutine before calling `recognize`; the revocation case can still be hashing the public model, initializing Tesseract or decoding when access is revoked. Both therefore can pass without ever entering hOCR or exercising `api.stop()` and native exit/cleanup. Conversely, sufficiently fast completion before the delay makes the revocation test fail for a reason unrelated to security. There is also no bounded completion assertion for native stop or a deterministic assertion that resources remain alive until recognition exits.

This is a verifiable test-design gap, separate from the fact that instrumentation has not yet run. Running the current suite successfully would not establish the cancellation-lifetime claim in the brief.

**Required change:** synchronize revocation/cancellation to observed native recognition entry/progress, use sufficiently nontrivial synthetic input, and assert bounded eventual completion, no returned result and successful subsequent recognition. For the lifetime ordering invariant, a narrow controllable engine seam can deterministically assert stop, native exit, iterator/engine cleanup and bitmap cleanup ordering; keep at least one real API37 JNI cancellation case. Prepare input before launching the operation under test. This should be targeted coverage, not a broad test expansion.

## Requirements satisfied by source inspection

| Area | Assessment |
| --- | --- |
| Interface and provenance | Exact suspend interface and current Java record shape. Text/CSV uses strict UTF8 and confidence-1. Raw text/words are not confidence-filtered; timestamps preserve helper provenance and confidence-1. No participant auto-assignment. Working dimensions and explicit rotation are recorded. |
| Original preservation | Input bytes are caller-owned and only read. Decoder wraps a read-only byte buffer; image/PDF APIs consume in-memory bytes. Adapter creates new extraction records and does not revise originals or corrections. Correction/raw preservation assertion exists in instrumentation. |
| Bounds |32MiB input cap; dimensions inspected before decode; source pathological-dimension limits; power-of-two sampling before image allocation; long edge2048 and16MP working checks; PDF renderer bounded to1400 edge;32-page/100k-text/100k-word/4096-time caps and partial explanations. |
| Authority and owner isolation | Authority checked before work, during decode loops/PDF proxy reads/native iteration, after recognition and at return. Each native instance is per call, globally serialized and recycled; shared disk data is only public model. Root/controller still owns captured owner/session identity, persistence and late-result publication fencing. |
| Native lifetime | Monitor is independent of parent cancellation, joined in NonCancellable after synchronous hOCR exit; iterator is deleted and engine recycled in finally; bitmap cleanup follows nativePage. No direct free-while-hOCR-runs race was found. OCR179-1 limits the cancellation claim. |
| No evidence plaintext files or networking | Adapter has no networking/provider transport. Its only FileOutputStream writes the bundled public model to private no-backup storage. PDF uses memory-only proxy descriptors. No OCR evidence image/text output file path was found. |
| Model packaging | Exact dependency/model versions; restricted JitPack group; build-only fixed HTTPS download; exact model length/SHA verification; unique staging and atomic promotion; existing generated asset is verified every preBuild; runtime repeats checksum before model reuse/install. Model binary is generated, not stored in source assets. |
| Licenses and documented limits | Model/native notices and source/hash manifests exist. Report clearly distinguishes upstream16KiB flags from actual packaged ELF/APK verification. Immutable text cannot be reliably wiped; mutable buffers/working bitmaps have cleanup. |

## Pending gates, not source defects

1. Resolve the exact4.9.0 dependency and compile app plus androidTest Kotlin on the required API37 toolchain. Local wrapper bootstrap failed before compilation per the implementation report; this review does not reclassify that as a compile failure.
2. Inspect actual AAR/APK native ABI coverage, each relevant ELF LOAD alignment and `zipalign -c -P 16`; inspect packaged model size/SHA and notices. Upstream NDK/page-size build settings are not final package evidence.
3. Run the focused API37 OCR suite after the findings are addressed: names/date/time/confidence/raw correction, rotations,48MP downsampling, strict/truncated UTF8, indexed/limited PDF pages, deterministic active-JNI cancellation/revocation and no evidence output files.
4. Root integration must still verify Primary/Hidden captured-authority behavior and fail-closed persistence after lock/Hide/Panic/session replacement. This adapter-only review cannot certify controller/UI or final release behavior.

The seven host model-preparation passes, shell syntax pass and diff check are reported prior evidence; they were deliberately not rerun. No full release or signing approval is given.
