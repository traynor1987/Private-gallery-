# PrivateGallery 1.7.9 foundation — fix round 1 review

Date: 2026-10-31. This review is limited to I1/I2 from `REVIEW179_FOUNDATION.md` and breakage introduced by the four-file fix diff. It is not Android/Main integration or release approval.

## Verdict

| Finding / scope | Verdict |
|---|---|
| I1: historical source policy/identity substitution through replacement lineage | **ADDRESSED** |
| I2: retired codec backing arrays retain plaintext | **ADDRESSED** |
| New Critical or Important defects introduced by this fix diff | **None identified** |
| Task 1 foundation spec/security/quality after these fixes | **Pass within the reviewed foundation scope** |
| Native Task 2 lease factories | **Prior scoped pass retained; unchanged** |

The two Important findings from the initial foundation review are closed. The original exclusions and integration obligations remain in force.

## I1 evidence and reasoning

Reviewed `EvidenceCatalog.java:168-182` and the new successor-lineage tests in `scripts/tests/Evidence179Checks.java:120-140`.

The source map now admits all previous live originals, previous incident fingerprints, previous analysis fingerprints, and successor live originals. Admission rejects a reused source UUID with any changed fingerprint component. Every successor incident and analysis lineage entry must then match the canonical map, regardless of whether its derived-object ID existed previously. This closes the replacement-ID bypass reproduced in I1, including changed policy/hash and entirely unexplained source identities.

Analysis validation additionally requires its fingerprint set to equal the selected live evidence fingerprints plus, only for an existing analysis, its previously retained lineage. A new analysis cannot carry arbitrary unselected lineage; removal of record references from an existing analysis does not declassify its retained content. Existing immutable-analysis, selection, audit-prefix and monotonic-lineage checks remain present.

The regressions exercise replacement incident/new-analysis changed policy and hash, unknown sources, extra verified-but-unselected analysis sources, unexplained additions to surviving incidents, canonical retained lineage, and original/incident/analysis registration in one update. They also use the actual encrypted store admission path and compare exact ciphertext bytes after rejection. This verifies rejection happens before persistence, rather than testing only a helper. The previous tests remain intact.

## I2 evidence and reasoning

Reviewed `EvidenceCodec.java:13-31` and the new buffer checks in `scripts/tests/Evidence179Checks.java:142-149`.

Both byte and array write overrides now manage capacity directly without calling superclass writes. Growth allocates bounded storage, copies the used prefix, wipes the entire previous array in `finally`, then installs the replacement. Copy failure wipes the replacement too. Allocation failure leaves the previous allocation attached, so the existing try-with-resources path closes and wipes it. Close wipes the full current allocation, clears count, and rejects subsequent writes.

The capacity calculation preserves the exact 4 MiB limit; bounds precede allocation/copy, and growth does not preallocate the maximum for small catalogs. The existing per-string UTF-8 wiping and ownership of returned encoded arrays remain unchanged. The production encoder only supplies external byte arrays to these writes; it does not expose its backing array.

The focused tests retain references to actual backing arrays through test-only reflection. They cover single-byte and inherited bulk-write dispatch, unchanged encoded bytes after growth, wiping retired arrays before close, wiping all retained arrays on close, over-quota cleanup, exact-limit acceptance and one-byte rejection. Allocation/copy error cleanup is supported by code inspection; the tests do not claim to inject an out-of-memory condition. The module-opening argument is confined to the host test command, with no production testing hook.

## Runner and regression evidence

`scripts/check_evidence179.sh` now durably compiles/runs foundation, authority and timestamp-hint checks, resolving the initial review's minor maintenance note. The existing authority and hint test sources were not modified by this fix. Only the foundation process receives `--add-opens java.base/java.io=ALL-UNNAMED` for backing-array inspection.

Read the appended Fix round 1 report, including the separate I1 and I2 observed RED outputs and final GREEN evidence:

```text
Evidence179 host checks passed: 117 assertions (model/codec/lineage/search/real crypto/revocation/concurrency)
Evidence179 captured authority 21 checks PASS
Evidence179 text hints: explicit-offset provenance, ambiguous/relative/clock uncertainty, unconfirmed names, malformed/hostile text and bounds PASS
```

Root independently reran that focused host entry point after the fixes and reported PASS. The original 91 assertions are retained. I did not rerun these already-passing suites, legacy programs, or Android builds. I inspected the real diff/code and regression assertions, and mechanically reconstructed the fixed files from the original review package plus the fix patch: all four changed files matched exactly; the three remaining original review files (store, authority factory, authority checks) were byte-identical. No product edits, CI, signing, commits or subagents were used for this review.

## Retained ledger / limits

This scoped pass does not certify controller/source-reader/egress behavior, cancellation and UI publication, resumed-vault callback tickets, or caller lock ordering. Future integration still needs live-original policy/hash revalidation and the captured authentication commit boundaries described in the original review and contracts. No new broad review of untouched code was performed. Existing original-byte, credential and API 37 certificate preservation obligations are unchanged.

## Hashes

Fix package SHA-256: `9867e208d39e4321863eb5c3e07bd0444b805050442b59220b8fa394a90fdabd`.

| File | SHA-256 |
|---|---|
| EvidenceCatalog.java | dbb04c2e02ae690ac285bdc61edac7141e34259dd97a7cc0c2e41a7f84f0f4af |
| EvidenceCodec.java | 6d41754a0de19f9f3156d08c5e8b9e49db3ee37c9ddd5c70fa81b3cf947426d4 |
| Evidence179Checks.java | d357bf069a862b6e81e87868785312aff1385ce0f94c4d685b55ce2cb501795d |
| check_evidence179.sh | 70649b619222d359098f70bd775ec4083f38b72082312cd5d61fabb491d51d89 |
| EvidenceAuthorities.java (unchanged) | eab05a6ab1f2501fe5625c2671a146a77181b501693196be5eccec93de265704 |
