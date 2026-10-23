# Private Gallery 1.8 — on-device visual search requirements

## Scope decision for 1.7.2
Compact filename search is in1.7.2. Semantic indexing is deferred to1.8 so model/runtime evaluation, secure indexing and device profiling do not delay camera acceptance. No semantic search, index, model download or new photo-analysis permission is implemented in1.7.2. Do not label filename matching as AI search.

## Required experience
Normal Gallery gets an icon opening natural-language visual search, ranked by actual content relevance without manual image naming. Examples: women, girls (no reliable age inference claim), bra, beach, cars, red dress. People grouping is future1.8 scope; face recognition is not implemented during1.7.2. Reuse compatible media identity/index scheduling infrastructure where justified, with separate authority for any future face data.

## Model assessment
Primary-source candidates assessed23Oct2026:
- Google AI Edge Universal Embedder documents shared on-device text/image embedding space, Android guidance and EmbeddingGemma V2 Text-Vision440M; requires license/artifact provenance, memory/thermal/battery evaluation and supported-device testing. https://developers.google.com/edge/mediapipe/solutions/retrieval/universal_embedder/index
- ONNX Runtime Mobile offers Android on-device execution but requires selecting and validating an appropriate model, tokeniser, compatible text/image embedding space, licensing and performance. https://onnxruntime.ai/docs/tutorials/mobile/
- Image-only feature extraction alone does not establish natural-language cross-modal retrieval. Do not compare unrelated text/image vector spaces or fabricate results.
No dependency is selected or bundled yet. Substantial integration and secure index lifecycle are required; this is not a safe last-minute1.7.2 addition.

## Privacy and indexing contract
- Analyse only currently authorised Photo Picker/media-permission grants; remove inaccessible entries on revocation/deletion and never enumerate beyond grants.
- All inference stays on device; no cloud uploads, private-term/content logging, public thumbnails or embedding caches.
- Gallery/Primary/Hidden namespaces must be distinct; vault indexes and associated metadata encrypted using existing supported vault authority, never a shared plaintext database.
- Lock revokes workers/queries and wipes sensitive in-memory vectors, results, terms and thumbnails. Resume only after reauthentication; no Hidden result/notification/history disclosure through Primary or recents.
- Efficient bounded background batches with pause/resume/incremental checkpoints, model-version invalidation, cancellation, battery/thermal constraints and no UI-thread inference.
- Clear progress and indexed/unindexed counts; distinguish incomplete coverage from no matches. Unsupported/missing models fail gracefully with honest availability, no invented rankings.
- Atomic encrypted index updates and corruption recovery; bounded private temporary storage with verified cleanup. Define embedding integrity/model identity and secure deletion strategy before implementation.

## Acceptance before shipping1.8 search
Actual-image fixtures with misleading/random filenames establish ranking, clothing/colour/scenes and people terms without asserted age certainty. Test grant revocation, incremental edits/deletes, pause/restart, cancellation/lock races, cross-vault query attacks, encrypted-at-rest files, no network inference, no public caches/logs, unsupported model behavior and Fold performance/thermal/battery. Profile realistic large libraries on hardware and preserve independent vault PIN/media upgrades.
