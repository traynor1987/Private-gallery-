# AI179 fix round 1 scoped rereview

Date: 2026-10-31. Disposition: **R1/R2/R3 addressed at source level; Android/runtime gates remain open. No release approval.**

Reviewed the frozen `review179-ai-fix1.diff` against the reconstructed `ai179-before-fix1` package, the current changed product/test files, new strict validator/host checks, AI179 documentation and appended Task4 report. Scope is the three initial findings, approved additive Details/receipt contract, and fix-introduced breakage. Main/UI/Controller/coordinator/startup integration is excluded. No product edits, subagents, passing-check reruns, Android build/tests, CI, commits, pushes or signing were performed in this review.

## Original finding dispositions

| Finding | Disposition | Evidence and remaining verification |
| --- | --- | --- |
| AI179-R1 — depth/grammar mismatch | **ADDRESSED** | `EvidenceStrictJson.java:13–17,28–92` validates full strict grammar and consumption, bounded nesting before recursive descent, values/characters, decoded duplicate keys, escapes and surrogate pairing. Comments and single quotes cannot change parser state. `EvidenceAi.kt:247` validates before the outer JSONObject; `:306` validates before the only structured JSONObject at `:309`, including Details reads. The old scanner is gone; no stack-error catch substitutes for prevention. The reported production-Java host checks include the exact 12,000-level exploit. Actual Android integration is pending. |
| AI179-R2 — wrapped structured-ID bypass | **ADDRESSED** | `EvidenceAi.kt:259–264,305–342` requires one exact strict object, one output_text, exact keys/types/counts, distinct nonempty citations contained in the captured selected IDs, and no model `_receipt`. There is no prefix-based return that accepts unchecked fences/prose/arrays/multiple blocks. `EvidenceAi179Test.kt:174–185` adds valid selected structure plus foreign/wrapper/duplicate/multiple-part rejection cases. They remain unexecuted. |
| AI179-R3 — shared alias creation race | **ADDRESSED** | `AiCredentialStore.kt:49–65` puts lookup, existing-entry decision and generation under companion `KEY_GATE`, shared by all store instances/owners/providers. Existing aliases are returned or fail; they are not regenerated. The public Context constructor, default alias, files and AAD are retained. Per-owner generation/removal gates remain separate. Disposable-alias concurrent first-use and incompatible-key tests cover the intended correction without deleting the real alias; actual AndroidKeyStore behavior remains pending. |

These dispositions close the reviewed source-level causes, not the associated device verification gates.

## Approved Details and receipt delta

No new product defect was found in this scoped delta.

- Provider content accepts exactly summary/suggestions/events. The provider cannot submit `_receipt`; parsing rejects extra keys before receipt creation. The app constructs status/refusal/cache/advisory/label fields itself after accepted provider parsing (`EvidenceAi.kt:268–281`). Refusal wording is discarded in favor of a fixed summary. No automatic application operation was introduced.
- The final serialized form, including receipt, is checked against the 100,000-character limit and the active key echo guard, then independently revalidated under the stored schema. Model, input digest, time, selected IDs, lineage, usage and applied=false still come from the existing local Analysis construction.
- `details(analysis)` reuses the same bounded grammar/schema validator with `analysis.evidenceIds()` as its citation allowlist. It validates receipt types, allowed status, cache usage range, fixed regenerated label and refusal consistency. Returned lists are defensive unmodifiable copies and entries expose read-only values. Date quotations/provenance/uncertainty remain strings; no epoch inference or owner approval is fabricated.
- `Analysis.text` deliberately changes to strict JSON with an app-owned receipt. Invalid legacy/plain-text payloads fail Details parsing. This is the explicitly approved storage/API change, not an unreviewed compatibility fix. Root still must verify consumers use this read-only interpretation and preserve full Analysis lineage during any separately approved application.

## Fix-introduced test finding

### AI179-F1-T1 — Low: key-echo regression is now masked by mandatory schema rejection

Location: `EvidenceAi179Test.kt:151–155`.

`actualKeyEchoIncludingEscapedJsonNeverEntersAdvisory` still supplies raw prose (`Unsafe fixture-\u006bey`) as output_text. Raw prose was accepted before this fix, but now necessarily fails strict structured parsing. Therefore this test would still pass if the key-echo checks were removed. It no longer distinguishes working echo protection from ordinary schema rejection. This is a test-coverage regression, not evidence that the current product echo guard fails.

Required focused test correction before treating key-echo regression coverage as passed: use an otherwise valid exact summary/suggestions/events object containing the fixture key in summary; cover a literal key, outer-envelope Unicode escape, and Unicode escape inside the nested structured JSON that becomes the key on parsing. Include a refusal containing the fixture key and assert rejection rather than a successful fixed refusal receipt. Keep a valid non-key version of the same schema as a positive control. Assert the fixed sanitized failure and absence of an Analysis; do not log test payloads or use real keys.

No other actionable fix-introduced product breakage was identified by this source review.

## Evidence and pending gates

1. The supplied handback records an observed missing-helper javac RED followed by **52 passing checks executing the actual production Java validator**, including the original exploit. I inspected the helper, harness and script without rerunning them. That evidence is stronger than the original Python transcription, but is not Android JSONObject, Kotlin, Keystore, image, cancellation or network execution. The RED was missing implementation, not an observed device crash.
2. **All 32 authored Android methods remain uncompiled/unexecuted in the reported environment**: 20 AI, 8 HTTPS route, 2 credential and 2 alias methods. Grouped Android37 Java/Kotlin compilation and execution must validate private-constructor/reflection fixtures, production parser integration and actual Keystore alias/concurrency behavior. Correct the masked echo case above before interpreting the results.
3. Newly authored barrier tests cover simultaneous one-shot attempts, key removal/replacement, authenticated-reader return cancellation and call admission versus close. HTTPS tests add reset/408/503 no-retry, unknown-length overflow and upload/read cancellation. They are prepared assertions, not observed concurrency/packet evidence. The response-read cancellation test establishes an intended cancellation/completion assertion; its fixture does not directly assert peer EOF before release, so do not describe its name alone as independent peer-disconnect proof.
4. Root's previously pending real VPN/Tor packet/DNS/no-fallback, capability-loss, private-socket/authenticated-loopback and service-retirement gates remain unchanged. No route product code changed in this fix. Root's secure UI, exact confirmation/retirement, authoritative digest/publication and offline-operation review remains separate and open.

No release/security approval is granted. Record actual grouped build/runtime and route evidence before closing the corresponding gates.

## Test-only follow-up — AI179-F1-T1

Disposition: **ADDRESSED at source-coverage level; execution pending** (2026-10-31).

Reviewed only the revised `actualKeyEchoIncludingEscapedJsonNeverEntersAdvisory` method and the appended Task4 follow-up report. The test now accepts a valid non-key summary/suggestions/events object as a positive control. Its literal-key, outer-envelope Unicode and nested-structured-JSON Unicode cases all otherwise satisfy that same schema; decode assertions establish the intended escape layer and common key-bearing summary. The refusal case would yield a successful fixed refusal receipt if its echo guard were absent. Thus these rejection assertions can no longer pass merely because the input is schema-invalid. They require the fixed sanitized failure, no Analysis, one fake POST and wiped response bytes. No product delta was reviewed or required.

The low-priority finding is closed as a source-coverage correction. No test, host-check rerun or Android compilation/execution was performed for this follow-up. All 32 Android methods and the existing runtime/packet gates remain pending; no release approval is implied.
