# Viewer and image editor implementation plan

Goal: implement the owner's final feature milestone on current main.
Architecture: immutable edit state, bounded Android raster renderer, selected-image-only AI boundary, shared verified Vault importer, Compose workspace and viewer chrome.
Spec: ../specs/2026-09-24-viewer-editor.md

## Tasks
- [x] Editor state/history and mask geometry: test crop/rotate/flip, ranges, undo/redo/reset and normalized fit mapping; implement pure model.
- [x] Render/import: test oriented pixels, stripped metadata, limits, distinct encrypted copies and cancellation. Implement original-to-preview/final rendering; no plaintext files; import cancellation at commit.
- [x] AI boundary: test unconfigured, consent, capability gating, sanitize-before-send, sanitize-result, timeout/cancel cleanup with deterministic providers. No real API use.
- [x] UI: viewer toolbar, shared sheet, edit entry, responsive editor, mask selection and provider status/consent Settings. Preserve video and image gestures.
- [x] Review and validation: unit/instrumentation/lint/build in Android CI, fix failures, update main and verify exact SHA green. Document physical acceptance and configuration blocker.

## Review focus
- Backgrounding during load/render/import must not retain plaintext or commit cancelled output.
- Huge and malformed images fail without an unbounded decode.
- Existing crop coordinates remain in oriented source space across rotation/flip.
- Duplicate result still gets distinct identity without changing normal import deduplication.
- No configured provider means zero network requests, including after consent.

## Execution ledger
- Source snapshot reconstructed via GitHub connector; every tracked blob matches remote main. Local snapshot commit is transport bookkeeping only; remote changes will parent actual starting main.
- Owner explicitly authorized continuous implementation, tests, push and CI; no additional plan approval pause.
- Local Android SDK/Gradle cache unavailable; Android CI is required for executable Android verification.

- Independent review (editor_review) found six Important issues: immediate background cleanup, bounded/cancellable decrypt, pager replacement from later page, legacy-crop load race, total mask limit, and per-gesture undo. All addressed with targeted regression coverage; no deferred findings.
- Ruling: no vendor was approved/configured in current repository. Ship explicit unconfigured state with injectable documented adapter interface and Keystore credential store, rather than inventing an API. Live remote acceptance remains externally blocked.
- Ruling: full-resolution render is limited to a 16 MP/device-memory budget, with high-quality sampling above that bound and a UI disclosure. This avoids casual OOM while retaining bounded previews; acceptance must verify real large-photo quality.
- CI #296 established missing editor-model references (test-first baseline). #297 compiled production and passed unit tests, then identified a platform EXIF fixture API mismatch. Fixture corrected without changing production EXIF sanitization.

- Branch validation: Android CI #302 (baba43a279a78cb77d462e11fe385a88600d7de2) passed unit tests, lint, APK builds and all 66 emulator tests. CI #301 also passed. Main verification remains the final gate.
