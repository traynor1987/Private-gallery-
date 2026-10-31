# Evidence179 access/controller fix round1 review

Date: 2026-10-31. Scope: A179-1 through A179-4 from ACCESS179_INITIAL.md and new breakage introduced by `review179-access-fix1.diff`, including the refresh availability extension. Read the worker report and brief, inspected the actual frozen Access/Controller/Metadata and both changed instrumentation classes, and followed relevant existing repository, catalogue and session behavior. Main/UI integration is outside this review.

**Source verdict: all four findings ADDRESSED. No new actionable Critical/Important/Moderate product finding identified in this fix-only scope. Compilation and runtime verification remain pending. This is not release approval.**

| Finding | Verdict | Source evidence |
| --- | --- | --- |
| A179-1: live original state after streaming | ADDRESSED | `EvidenceAccess.kt:121-129` compares COMPLETE, ID, MIME, size, SHA-256, vaultOnly and nonce. Primary and Hidden re-inspect after their authenticated repository scopes return (`:156-166`, `:184-193`); the common reader adds a final same-source/currentness check (`:112-116`). Lists/inspection exclude absent payloads. Registration and OCR check the record against a live source inside their catalogue update (`EvidenceController.kt:96-98`, `:126-129`). |
| A179-2: publication versus retirement | ADDRESSED | `EvidenceAccess.kt:74-78` requires Main and serializes currentness plus assignment under the originating retirement monitor. All nonempty controller StateFlow publication now passes through `publish` (`EvidenceController.kt:33`), including busy/result/error, refresh and integrity. Explicit Main close clears immediately; background retirement schedules Main clearing. Blocking cleanup stays outside the publication monitor. |
| A179-3: plaintext ownership on rejected return/cancellation | ADDRESSED | `withVerifiedBytes` owns the result inside the reader callback before any outer postcondition may reject it, and wipes in the enclosing finally (`EvidenceController.kt:159-170`). `withOriginalBytes` returns Unit across the dispatcher boundary (`:114-119`), and OCR uses the same scoped helper. Metadata sample ownership is established before copying; assembled read output is wiped if copying fails. No original ByteArray result is transferred through the public suspend API. |
| A179-4: SAF acquisition metadata lost during registration | ADDRESSED | `importAndRegister` carries the acquired EvidenceOriginal into `registerRecord` (`EvidenceController.kt:68-72`, `:88-90`). `EvidenceMetadata.acquisition` requires the same original identity/policy, preserves bounded source-name and modified-time claims with whitelisted provenance labels, and uses verified base vault dates/policy (`EvidenceMetadata.kt:18-25`). Existing references retain unavailable filesystem times; already-registered sources do not silently rewrite acquisition history. |

The fixed Primary/Hidden payload paths match the existing repository implementations (`AndroidVaultRepository.payloadFile` and `HiddenRecordStore.file`). The postconditions do not rehash or overwrite the reference. They establish source validity when checked; later independent original removal still requires the subsequent operation to reverify, as intended.

The refresh extension (`EvidenceController.kt:49-62`) derives every retained record's status from one same-vault source snapshot and publishes the catalogue, originals and integrity map together. Absent/trash/missing-payload sources are unavailable; changed metadata identity/policy is flagged. Matching entries explicitly say that full original SHA-256 was not reverified. It neither reads full original bytes nor changes catalogue/original state. The subsequent ordinary result publication uses the same gate and the same catalogue.

No original-delete operation, new persistence format, network path or reverse-order repository locking inside the new publication gate was introduced. `EvidenceEgress.kt` and its test remain unchanged from the initial reviewed version; their prior source assessment is not expanded by this fix review.

## Test evidence and remaining gates

The eight new authored instrumentation methods target the original gaps and refresh extension: paused Primary EOF with trash/policy/fingerprint/payload changes; exact retirement-boundary gate rejection; outer-reader buffer rejection; cancelled dispatcher delivery; bounded acquisition claims; actual controller refresh/error/busy/retirement; real provider import/reference removal; and refresh availability warnings. Inspection found their assertions relevant to those paths. **They are not compiled or executed, so this report does not call them passing tests or establish red/green evidence.** The worker's source-shape and whitespace assertions are likewise not behavioral proof.

No tests, Android builds, CI, commits, pushes, signing or product edits were performed by this reviewer. Previously passing host authority/catalogue/store checks were not rerun.

Before accepting runtime completion:

- Compile the integrated Kotlin/Android product and instrumentation, including every removed-`readOriginal` caller and the provider fixture signatures.
- Execute the two changed test classes on API37; verify deterministic latch scheduling, fixture dispatch/cleanup and cancellation delivery.
- Exercise Hidden post-EOF/revocation paths and original removal during registration/OCR, which are not fully covered by the new Primary-focused read test.
- Review and test final Main/UI callers: borrowed bytes must not escape their consumer; derived bitmaps/text require gated Main publication and rejected-resource disposal; lifecycle retirement and original-auth SAF tickets remain mandatory.
- Keep retained-lineage egress, archive, route and complete release gates separate. This scoped pass establishes neither their runtime behavior nor whole-release approval.

## Reviewed hashes

| File | SHA-256 |
| --- | --- |
| EvidenceAccess.kt | 15cfb04664b023d24808f632028f255dc82380bc5df472e2c5e6999d4c144707 |
| EvidenceController.kt | 21c01a67e5faf36f4ed3613c2e000784cdc3f72ac710b3ef06b9c9aa8d7b73a8 |
| EvidenceMetadata.kt | 6652d1fdc9bcbeb1117455f937127a4f9bb5a630bc588daf91bc0698dc41364c |
| EvidenceEgress.kt | 57500350cbe3b66060400c2103f8424d8d967b694ad56b4e4d4b3588117bf15d |
| EvidenceAccess179Test.kt | 5293d075e8d0a76e17e96032f9666d68ed00d5ee23516366379eb5cdab2b93ae |
| EvidenceController179Test.kt | 58a2ab54c1684c12fb259a3258a1b4d061d60041f05db9778429ac96aac68da6 |
| EvidenceEgress179Test.kt | 6adbfe97a494e5f925bfb98938dea892df7c5875bc56a5b7546e3ce1eb1a84e4 |
| review179-access-fix1.diff | 040918768099ffc881375a9b7bd7480b3386dcbb730ec3fb703aaf23dfbd509e |
