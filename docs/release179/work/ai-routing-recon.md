# Evidence Locker AI and routing reconnaissance

Read-only product reconnaissance, checked 2026-10-31. No live API/key calls, builds, CI, signing or push performed. This report records inspected code and official documentation; proposed behavior below is not implemented or device-verified by this research.

## Existing implementation

Paths relative to repository root:

- `app/build.gradle.kts`: minSdk/targetSdk 37, compileSdk 37.1, Java 17, versionCode 50/versionName 1.7.8. Existing OkHttp 4.12.0, BouncyCastle 1.79; no OpenAI analysis SDK needed.
- `app/src/main/java/uk/co/traynor/privategallery/core/editor/AiHttpTransport.kt`: `AiHttpTransport.execute(AiHttpRequest)` accepts method, URL, headers, streamed body and maximum response bytes. `PrivateAiHttpTransport` uses `HttpsURLConnection` with default Android networking, disabled redirects/cache, bounded response reads, sanitized network failures, cancellation disconnect, no retained provider error bodies. Request/response deliberately avoid data-class secret-bearing `toString`. Its `AiRemoteUrls` permits OpenAI `/v1/images/edits` and `/v1/models/...`, not `/v1/responses`. Default transport does not prove Hidden VPN/Tor routing.
- `app/src/main/java/uk/co/traynor/privategallery/core/editor/AiCredentialStore.kt`: provider-ID-bound AES-256-GCM, AndroidKeyStore key, encrypted atomic files in `noBackupFilesDir`. `read/save/clear/isConfigured` are reusable. Caller must wipe decrypted buffers. Keystore protects at rest, not a compromised unlocked process. Authorization headers currently create immutable Strings, preventing a guarantee of complete memory erasure.
- `app/src/main/java/uk/co/traynor/privategallery/core/editor/AndroidAiConfiguration.kt`: Replicate configuration; not a generic analysis adapter.
- `app/src/main/java/uk/co/traynor/privategallery/core/editor/OpenAiImageProvider.kt`: image-edit adapter, model/options and usage parsing; not incident text/vision analysis. Do not reuse image-generation model IDs for analysis.
- `app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenEditorAccess.kt`: session authority and guarded repository read/save; generation currently invokes `PrivateAiHttpTransport`. This existing call is not evidence of owned Hidden browser routing and must not be copied for new analysis.

## Hidden transport evidence and required adapter behavior

- `app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenVpnBrowserBroker.kt`, especially `verifyTorReady()` and `download()`: active-session Tor HTTP proxy, proxy authentication, DNS implementation that rejects origin DNS; VPN client pins `network.socketFactory` and `OwnedVpnDns.lookup`. Tracks calls and checks owner validity.
- `app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenTorSockets.kt`: private Unix SOCKS endpoint, DOMAIN destination requests, no local origin DNS, revocation-aware sockets.
- `app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenVpnProxy.kt`: existing Tor/VPN proxy mechanics and public-address checks.
- `app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenBrowserRuntime.kt`: establishes authenticated owned routes and explicitly refuses unverified VPN+Tor. Preserve this refusal.
- `app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenHttpTransport.kt`: older/private renderer transport supports GET navigation and downloads. It is not a ready-made POST analysis adapter. Do not infer that its loopback SOCKS constructor is the current authenticated Unix-socket route.

Primary and Hidden analysis are required product scope. Implement purpose-specific transport adapters with the existing owned route machinery: exact OpenAI HTTPS endpoint allowlist, no redirects, no cookies/cache, bounded request/response, no automatic retry of potentially billed POST, admission checks before sending and publication, tracked calls, cancellation on lock/owner retirement/revocation. Hidden Tor uses verified authenticated proxy and rejects local origin DNS. Hidden VPN pins sockets and DNS to the same owned verified Network. No default-network fallback, VPN bypass, global binding changes, browser session reuse, or weakening VPN+Tor policy. Never log API/proxy keys, request bodies, private text/images or raw error bodies.

## Official Responses contract

Local code was inspected first; the missing text/vision API contract was checked against official documentation:

- https://developers.openai.com/api/reference/resources/responses/methods/create
- https://developers.openai.com/api/docs/guides/images-vision
- https://developers.openai.com/api/docs/guides/your-data
- https://help.openai.com/en/articles/5112595-best-practices-for-api-key-safety

Use POST `https://api.openai.com/v1/responses`. Proposed bounded one-shot body:

```json
{
  "model": "gpt-5.6-luna",
  "store": false,
  "background": false,
  "stream": false,
  "reasoning": {"effort": "none"},
  "max_output_tokens": 2048,
  "tools": [],
  "input": [{
    "role": "user",
    "content": [
      {"type": "input_text", "text": "Owner-selected text and task"},
      {"type": "input_image", "image_url": "data:image/jpeg;base64,...", "detail": "high"}
    ]
  }]
}
```

Images are optional and explicitly selected. Sanitize metadata and resize local copies before preview; never upload automatically, never send an entire incident or original by default, and avoid Files API uploads/remote image URLs. Do not include previous-response IDs, background work, tools or unrelated identifiers. JSON fields must be encoded structurally, not interpolated. Parse bounded output message/content `output_text` parts, refusal and completed/incomplete status; incomplete output must be visibly labeled. Capture actual `usage.input_tokens`, cached token detail and `output_tokens` separately from estimates. Maximum output includes reasoning tokens where applicable.

Preview shows exact selected text, image thumbnails/count/bytes, provider, model, route, rates/date and estimated cost/range. Bind explicit confirmation to a digest of immutable serialized payload, selected image hashes, model/options, route and pricing revision. Any changed selection/model/route invalidates confirmation. Setup connectivity checks must be owner initiated and send no incident content. No live API request was made during reconnaissance.

## Model choice and cost assumptions

Official sources checked 2026-10-31:

- https://developers.openai.com/api/docs/pricing
- https://developers.openai.com/api/docs/models
- https://developers.openai.com/api/docs/models/gpt-5.6-luna
- https://developers.openai.com/api/docs/models/gpt-5.6-terra
- https://developers.openai.com/api/docs/models/gpt-6-luna
- https://developers.openai.com/api/docs/models/gpt-6.1-terra
- https://developers.openai.com/api/docs/models/gpt-5-mini
- https://developers.openai.com/api/docs/models/gpt-4.1-mini
- https://developers.openai.com/api/docs/deprecations

Standard USD per million tokens, without regional/priority/batch/flex variations:

| Model | Input | Cached input | Output | Cache write |
| --- | ---: | ---: | ---: | ---: |
| gpt-5.6-luna | 0.20 | 0.02 | 1.20 | 0.25 |
| gpt-5.6-terra | 2.00 | 0.20 | 12.00 | 2.50 |
| gpt-6-luna | 0.10 | 0.01 | 0.50 | 0.125 |
| gpt-6.1-terra | 1.00 | 0.05 | 5.00 | 1.25 |

All four support text/image input and Responses. The inspected vision table explicitly documents 5.6 Luna/Terra sizing and multiplier but omits 6 Luna/6.1 Terra sizing: do not invent exact image-token estimates for omitted models. The 5.6 pair provides fully documented initial options. Current model pages mark 5-mini and 4.1-mini deprecated; the deprecations page schedules 4.1-mini removal January 14, 2027. Avoid making either the new default merely because older local examples mention them.

For 5.6 Luna/Terra, explicit `high` respects a 2048-pixel maximum dimension and 2500-patch resizing budget; `auto` behaves as original and is unsuitable for predictable bounded preparation. Patches are 32 by 32 pixels. After applicable resizing, billable image tokens are `ceil(ceil(width/32) * ceil(height/32) * 1.2)`. With a locally prepared longest edge <=1024, no more than 1024 patches/1230 conservatively rounded image tokens per image. This is an app-selected bound, not an API-wide limit.

Text token estimates need a documented conservative estimator or compatible tokenizer; character/4 is only an approximation and unsafe as a guaranteed spending ceiling. Include instructions and framing in input estimates. Use uncached rates rather than assuming cache hits and allow cache writes at 1.25x input price. Output preview can use the configured maximum. Model pages specify long-context price uplifts beyond 272K input; keep this app's limits far below that. Rates and account terms may change. Label preview as estimate, not guaranteed exact charge/hard account spending cap; actual returned usage is authoritative for the request receipt. Sending no tools avoids additional tool fees.

## Credential and provider privacy limitations

Reuse encrypted local credential storage with a distinct analysis adapter ID, explicit owner setup/removal and cancellation of outstanding jobs on removal. Never embed a developer-owned shared key in the APK. OpenAI recommends server-side key custody rather than mobile client exposure. Owner-supplied local credentials are a conscious existing product pattern with residual unlocked-device/process risk; adding a backend would introduce another recipient and cannot be done silently.

`store:false` disables response application-state storage where supported; it does not mean zero provider retention. Official data controls describe default abuse-monitoring retention up to 30 days with exceptions and approved account-specific controls. API content is not used for training by default absent opt-in. Explain provider transfer/retention in confirmation; avoid promising absolute deletion or anonymity. Model output is assistance, kept separate from originals, raw OCR and manual corrections, with model/input digest/time/usage provenance.

## Offline OCR research outcome

ML Kit bundled Latin 16.0.1 was examined but not selected: input/output processing is local, yet its official terms document metrics and possible server connections. A named app process does not remove shared INTERNET permission, and a speculative isolated worker is not evidence of a working solution.

- https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- https://developers.google.com/ml-kit/terms
- https://developers.google.com/ml-kit/android-data-disclosure

Prefer functional offline Tesseract4Android standard 4.9.0 plus pinned bundled English fast model, with no runtime downloads. Official wrapper has empty manifest, annotations-only runtime dependency, Java17, minSdk21 and flexible16KB support; final API37/native-load tests remain necessary.

- https://github.com/adaptech-cz/Tesseract4Android
- https://github.com/adaptech-cz/Tesseract4Android/releases
- https://raw.githubusercontent.com/adaptech-cz/Tesseract4Android/4.9.0/tesseract4android/build.gradle
- https://raw.githubusercontent.com/adaptech-cz/Tesseract4Android/4.9.0/tesseract4android/src/main/AndroidManifest.xml
- https://github.com/adaptech-cz/Tesseract4Android/blob/master/tesseract4android/src/main/java/com/googlecode/tesseract/android/TessBaseAPI.java
- https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/eng.traineddata
- https://github.com/tesseract-ocr/tessdata_fast/blob/main/README.md
- https://github.com/tesseract-ocr/tessdata_fast/blob/main/LICENSE

Verified model bytes: 4113088, SHA-256 `7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2`. License bytes:11358, SHA-256 `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`. Temporary copies are under `/workspace/scratch/4181ccd09b2e/evidence179-research/`; no model was added to product source by reconnaissance. Root subsequently chose pinned build-time download with SHA/fail-closed verification and APK bundling, not runtime retrieval or a large model Git blob.

Use OEM1 for fast model. Recognition with `getUTF8Text()` alone cannot be interrupted by `stop()`; first call interruptible `getHOCRText(0)`, then read text/ResultIterator. TessBaseAPI is not thread-safe: serialize each instance, stop on cancellation and recycle after recognition returns. Preserve raw words, confidence, boxes, transform/orientation/model provenance; revisions of owner corrections must remain separate. Confidence is not a calibrated factual probability.

## Export facts supporting related design

`core/vault/VaultBackupArchive.kt` produces ZIP of existing vault ciphertext, not password-encrypted incident ZIP. `core/vault/VaultPdfPreview.kt` renders PDFs, not creates them. `MainActivity.kt:1063` already uses SAF `CreateDocument("application/octet-stream")`. `core/crypto/RecoveryEnvelope.kt`/`PinEnvelope.kt` provide existing BouncyCastle scrypt N32768/r8/p1 and AES-256-GCM primitives. Root selected a documented password-encrypted application container holding PDF+JSON+originals, opened in-app; do not call the inner ordinary PDF independently password protected or imply interoperable encrypted ZIP. Do not reuse recovery-key whitespace normalization for owner passphrases. Hashes establish byte consistency, not independent acquisition/authorship or legal chain of custody.
