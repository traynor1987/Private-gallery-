# Private Gallery1.7.9 — AI Evidence Locker design

Owner supplied complete specification and explicitly requested autonomous implementation, features before cosmetic polish. Routine choices below are implementation decisions, not additional owner promises. No design approval pause is required under that directive.

## Baseline and constraints

Create release/1.7.9-evidence-locker from verified2e420b9db485f5d08529d45fd306374510827101. Latest signed1.7.8 code50 frozenAPP4820e176/controlD06c54c1, originalcertificate94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. Nextcode51 audited; recheck beforemetadata assignment. Physical Fold acceptance of50 remains OWNER_REQUIRED; actual phone installedcode unobservable. Verify actual archived original-key50→51; no uninstall/data clear. Android17/API37only. Preserve all1.7.8 functions/data. No1.7.10/1.8.

## Approaches and decisions

Reuse same-vault original references + independent encrypted evidence catalogues rather than copying every image or altering the existing vault index. Existing encryption/session commit primitives remain authoritative. Search over decrypted authorised metadata in memory avoids a plaintext secondary index. A bounded immutable binary catalogue makes whole updates atomic; quota failures preserve prior state.

Choose bundled offline Tesseract4Android4.9.0/English LSTM model4.1.0 instead of a network-capable OCR SDK. Official MLKit documents metrics connections; avoid introducing that egress architecture into Hidden. Bundled modelSHA7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2,4113088bytes. OCR raw output/confidence/geometry/rotation remains separate from owner correction; no extracted text treated as fact.

Choose existing scrypt/AES-GCM encrypted container around PDF/JSON/ZIP originals, with app password opening and authenticated extraction, rather than claiming platform PDF encryption or weak ad-hoc ZIP encryption. Plaintext export is separately confirmed and restricted. Existing VaultEgressPolicy blocks vaultOnly originals AND derived text from AI/export; local organisation/OCR remains available. Hashes establish bytes only, not authorship/truth/legal certification.

## Features and flows

Independent Primary/Hidden home, library, incidentlist/detail, timeline, OCRreview, search/filters, AI confirmation and export screens reuse theme/shared secure dialogs. Entry from both vault menus. Bulk register existing images/screenshots/documents as references, or SAF-import supported original image/PDF/text/CSV into encrypted originating vault; preserve bytes. Metadata distinguishes filesystem times, vault added/capture times, embedded claims, visibleOCR times and owner/inferred dates. Integrity checks compare originalSHA/size/currentmetadata, flag unavailable/changed originals. Rename/description/notes/tags/explicitparticipants are annotations.

Incidents support manual assignment/remove/move, merge/split/archive/restore and reference-only deletion. Timeline stores sourced/extracted/user/inferred/AI events, source links, labelled unknown times and explicit manual order. Suggestions combine time/text/participant/content/hash signals; accept/reject/edit choices append owner audit, never auto-merge. Search includes OCR/raw correctedtext/names/notes/tags/incidenttitles/explicitparticipants and date ranges. Lock/revocation clears all transient names/text/query/thumbnail/selection and cancels operations.

AI is optional and owner-initiated, defaulttext-only; explicit image selection, exact payload preview/model/route/cost+provider retention/confirm. Payload/revision/source/route digest and one-shot ticket prohibit changed/late/reused consent. No whole-vault uploads, filesAPI, background/provider tools, cookies, redirects or automaticPOST retry. Existing own-key protection extended owner-specifically. Hidden sends only through verifiedVPN/Tor lease with captured session and DNS/socket restrictions; no fallback. Provider/model/network errors leave local locker functional. Responses remain advisory; separate owner confirmation applies validated grouping/order/summary suggestions and audits them.

Export selected incidents includes originalbytes, PDFreport, JSONschema/index, transcript/corrections/notes, eventtimestamp provenance, hashes and labelledAI. Defaultpassword `.pgevidence`; only encrypted staging, streaming verified sources, finalGCM beforeSAF publication, cleanup oncancel/revoke/interruption. App Open encrypted export verifies before rendering/extracting; plaintextZIP requires explicit separate action. Arbitrarydocumentprovider deletion best-effort, not secure-erasure promise.

## Security and integration

Detailed shared types/interfaces/limits are in docs/release179/CONTRACTS.md. Primary captures keyidentity/authentication/generation and phoneTransferGate. Hidden uses its independent PIN-backed HiddenSession operation/commit fences. Existing originals/readEOFverification and RecentlyDeleted remain unchanged. Owner is part of encryptedcatalogue/domainkey/AAD, no cross-vault fallback. UI statesMainonly, asyncread/commit/publish and export/AI all recheck capturedauthority. Secure windows/Recents/Hide/Panic existing policies remain active; no new publicFileProvider/caches.

## Verification and release

Implement storage/OCR/grouping/timeline/search/AI/export/UI in coherent batches before AndroidCI. Localhost/JVM checks first; no automatic broad historical workflow on179. One179-focused job in existingfastworkflow, onegroupedrenderedAPI37 run with exactsource manifest/evidence, selectedaffected178regressions and mandatoryevidence/security/transport cases. Production remainsdisabled until immutable47/evidence testset/control/review bindings validated. Add179block to establishedsigningworkflow or one necessaryfocusedreleaseworkflow only, preserve178historicalbytes. Independent consolidated review resolvesCritical/Important beforeoriginal-key50→51 seed/install-r/verify/restart, finalAPKidentity/signature/digestreview andoneAPKdelivery. Maintain V1_7_9_RECOVERY.md and fullrequirements tracker; no checkpoint misrepresented asrelease.

## Primary source research

https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0
https://github.com/tesseract-ocr/tessdata_fast/tree/4.1.0
https://developers.google.com/ml-kit/android-data-disclosure
https://developers.openai.com/api/docs/models/gpt-5.6-luna
https://developers.openai.com/api/docs/models/gpt-5.6-terra
https://developers.openai.com/api/docs/guides/images-vision
https://developers.openai.com/api/docs/guides/your-data
