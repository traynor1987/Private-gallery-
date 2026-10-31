# Evidence 1.7.9 contextual grouping — initial scoped review

Date: 2026-10-31. Scope: `review179-context-signals.diff` only, against the already reviewed foundation Fix 1. This is a native-domain delta review, not Android/Main, egress, or release approval.

## Verdict

- **Spec: PASS within this delta.** Bounded OCR date/time, nonidentical transcript similarity and existing-incident association hints are now present. They remain advisory until an explicit acceptance mutation.
- **Security/quality: PASS with one minor API usability note.** No Critical or Important finding was identified.
- Existing foundation I1/I2 closures and native authority verdict are unchanged; this review does not reopen untouched implementation.

Read the actual two-file diff, `docs/release179/work/context-signals-report.md`, the relevant grouping and mutation contracts, and the current implementation/test lines. Mechanically reconstructed the changed files from the original foundation package, Fix 1, and this delta; both current files matched exactly. Source code was not edited. No passing suite was rerun, and no CI, subagent, commit, push, signing, or Android build was performed.

## Reviewed behavior

**Signals and uncertainty** — `EvidenceCatalog.java:168-184`

The new transcript signal examines up to 64 Unicode letter/number tokens and emits at most 32 distinct-token three-word fragment signals per record. It uses the displayed/latest corrected transcript and describes similarity as unverified, without deriving participants or facts. Existing exact transcript/hash/explicit participant/source capture signals remain.

At most 32 time claims from the latest OCR extraction are considered. Resolved positive epochs use same-hour buckets. Unknown epochs can only produce an exact normalized raw date-claim match; the reason retains explicit uncertainty and does not synthesize a date, year, timezone or timeline event. Raw date keys are limited to 128 characters; extraction fields already have model bounds. This is a conservative hint mechanism, not a general conversation classifier or independent verification of OCR dates.

**Determinism and bounds** — `EvidenceCatalog.java:186-206`

Signals and candidate sets are traversed through ordered maps; candidate record IDs are sorted before hashing. Existing-incident sets use stable insertion order from immutable catalog lists. Repeated evaluation of the same catalog therefore yields stable IDs/order. The contract does not require permutation invariance when a catalog's stored ordering changes at a capacity boundary.

The implementation retains the 8192 signal-key and 128 records-per-signal caps, limits candidate groups to 256, and enforces a single combined 256-output limit across GROUP and ASSIGN suggestions. No all-pairs evidence comparison is introduced. Associations are drawn from the already bounded catalog/incident reference collections. No maximum-size wall-clock benchmark was claimed or performed in this review; bounded cardinality is established by code inspection.

ASSIGN IDs bind the target incident UUID and sorted currently unlinked evidence IDs. They are deduplicated across signal candidates. Accepted/rejected IDs are persisted in the existing bounded rejection list, so an accepted hint cannot be immediately replayed and rejection remains stable. Reasons stay within the model limit: there are only a small fixed set of signal labels plus the one association explanation. The final fixed short title avoids overflowing the suggestion-title bound with a maximum-length incident title.

**Current-state validation and explicit acceptance** — `EvidenceCatalog.java:146-155, 188-201`

Acceptance recomputes current suggestions, finds the same digest ID and requires equality with the supplied suggestion. It therefore rejects a disappeared/changed hint, removed evidence, removed or archived target, already-completed association, prior rejection, and a forged payload. An ASSIGN contains only evidence not already directly linked to its target, so a currently valid acceptance performs a real change rather than fabricating a no-op audit.

This gate validates a currently available local relationship suggestion. It is not a frozen transcript/catalog-revision consent ticket: changes that leave the same relationship and reason list valid can leave the suggestion valid. That is consistent with this local grouping contract and must not be reused as AI/export consent.

Generation is pure. ASSIGN acceptance retains existing target event/order/archive/content fields, unions new reference IDs, retains all prior target fingerprints and adds every selected live original fingerprint. It uses the existing `changed` path once, producing one revision and audit entry. The evidence list, original fingerprints, OCR and corrections are passed through unchanged. Other incidents are not merged, reassigned, archived or removed. Existing catalog validation rejects any conflicting source identity or quota overflow before a successor is returned.

## Minor finding

**Q1 — ASSIGN retains an irrelevant new-incident-ID precondition.** `EvidenceCatalog.java:147-148` validates `newIncidentId` and rejects any existing incident UUID before branching on the suggestion kind. ASSIGN then ignores that argument and operates on `actual.incidentId()`. The current test works by supplying a fresh throwaway UUID, so this is not a blocking defect or lineage/security issue. A caller naturally supplying the target UUID will nevertheless receive an unrelated “Incident already exists” error.

Prefer moving new-incident UUID/uniqueness validation into the GROUP creation branch, or explicitly document the current fresh-unused-UUID calling convention until that cleanup. The fixed title is deliberately generic; the UI should resolve the displayed target name from the explicit `incidentId` and present its own owner approval action, as the work report already plans.

## Test evidence and limits

The added `contextualSuggestions` test uses different original hashes, no participants and no capture timestamps, so its new OCR/similarity signals are not accidentally satisfied by the prior hash/participant/capture heuristics. Its seven assertions cover unverified OCR time reasons, nonidentical transcript fragments, existing-incident new-reference selection, reference-only application without duplicate incidents, exactly one revision/audit with evidence unchanged, no automatic mutation, and persisted rejection.

The work report records the initial RED (`OCR visible date signals missing`) and final-label GREEN. Supplied final evidence is 124 catalog/store assertions, 21 captured-authority checks, and timestamp-hint checks PASS. Root reports the final title-adjusted host run passed. The original 117 foundation assertions remain in the source. These passing suites were not repeated in this review.

The seven new assertions do not separately exercise the unknown-epoch raw-date branch, stale ASSIGN acceptance after target retirement, or restricted incoming-source lineage. Those paths were inspected above and no defect was found. Adding those focused cases with the next relevant integration validation would make the new behavior's evidence more explicit; this is a nonblocking coverage note, not a new foundation finding.

## Scope ledger

Controller publication and owner authentication, live-original revalidation, UI target-name/approval presentation and AI/export consent remain separate integration gates. No source I/O, network, credential, cipher, lease, or Android behavior is changed by this delta. The existing original-byte/credential/API 37 certificate preservation obligations remain unchanged.

## Hashes

| Reviewed item | SHA-256 |
|---|---|
| review179-context-signals.diff | fd972735a0061cf4cca03a83af42e20d564577816f549e31a680bca0ba3d58a9 |
| EvidenceCatalog.java | c9965f709ffb55e158edefdbdb16f250fc69943d5014eeea48918ad1038b98fb |
| Evidence179Checks.java | 9bee7b7550e71c663ad04cc1de2ec5e0efcab10b57b7ac2578d0bf112b7aeef2 |
