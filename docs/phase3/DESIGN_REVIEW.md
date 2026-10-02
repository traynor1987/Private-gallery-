# Phase 3 independent design review

**PHASE 3 RESULT: NO-GO**

Review date: 2026-10-02. Scope: the proposed specification, 95 negative cases,
24 fault boundaries, 63 gates and design-admission plan, checked against actual
Primary/Secondary baseline contracts. Two independent read-only reviewers
covered Primary/hold and Hidden/selection/video/authority boundaries. Neither
edited files, built software or dispatched workflows.

## Verdict

The high-level architecture is feasible. **Production implementation is not
admitted.** New canonical formats and two load-bearing ownership/authority
contracts remain incomplete. No proved Critical architectural flaw was reported;
the Important issues below cannot be silently delegated as unspecified behavior
to a production writer. Test-only canonical-format admission work is the next
bounded step under the current authorization, not a reason to change Phase3 GO.

## Findings and rulings

| ID | Severity | Finding | Disposition |
| --- | --- | --- | --- |
| DR-01 | Important | Spec rejected unknown length; plan permitted measuring/reopening; matrix/gate differed | ADDRESSED — both independent re-reviews confirm bounded first pass and exact reopened second pass; non-reopenable/changing/over-quota/over-deadline inputs rejected |
| DR-02 | Important | Negative case implied detection of arbitrary valid usage-ledger rollback, exceeding inherited threat guarantee | ADDRESSED — independent Hidden re-review confirms corrected matrix/spec: detectable failures fail closed; valid hostile private-state rollback remains an explicit limitation |
| DR-03 | Important — OPEN | Historical receipt pins original encrypted index, but current membership/metadata supersession and terminal dependency release are not exact | Must freeze predicate and paired durable acknowledgement before media/hold cleanup; retain immutable index/context/actual-key ledger across credential retirement; do not retain obsolete credential wrappers |
| DR-04 | Important — OPEN | Proposed lock subset omits controller gates, resource/cache locks and cleanup-complete mapping | Must map controller→authority subset against root→controller biometric promotion, and existing preview-cache→metadata ordering. Revoke/clear synchronously; cancel/close/join outside locks needed by workers/callbacks; test cleanup barrier before fresh auth |
| DR-05 | Admission prerequisite — OPEN | Hidden schema2, Primary catalog/hold and video multi-invocation ledger are only field-level designs | Freeze exact canonical bytes, counts, ordering, context and ownership/restart grammars with independent reference vectors before any production writer |
| DR-06 | Availability prerequisite — OPEN | Playback/preview consume the same video verification budget as a fresh cleanup sweep | Define complete-operation query preflight and safe exhaustion behavior; 8GiB has8192 chunks, so at most128 full chunk-key sweeps fit before other reads. Never reset budget to finish cleanup; preserve source/hold |
| DR-07 | Recovery prerequisite — OPEN | Paired authentication is required for hold restoration, but media-index corruption or exhausted selected-metadata verification ledger can prevent ordinary Hidden admission | Specify a restricted, independently authenticated hold-recovery route that cannot serve media/issue write/delete authority, or another reviewed recovery protocol. Do not fall back to Primary-only restoration or make the valid hold silently unusable |

DR-01 ruling cost: providers may require two complete reads and must support
bounded descriptor cancellation; changed/unbounded providers are refused rather
than spooled. DR-02 ruling cost: hostile replay of valid private state is not
detected; it remains outside the guarantee, as in Phase2. No new freshness
mechanism is invented. DR-07 is an additional controller review observation,
not a claim that a shipped hold already exists or lost data.

Scoped re-review: both reviewers confirm DR-01 addressed; the Hidden reviewer
also confirms DR-02 addressed and DR-07 follows from actual store authentication
calling descriptor/catalog/index verification before controller session
promotion. Exhausted purpose11 video budget alone does not demonstrate login
failure: it prevents a required full destination cleanup verification (DR-06).
Do not conflate that with selected-metadata admission failure. DR-03–07 remain
open; no production gate is implied by corrected policy text.

## Confirmed consistent portions

- One Hidden authority and an acyclic initial index → receipt → journal →
  descriptor dependency graph; no receipt/index hash self-reference.
- Full immutable Primary source fingerprint, read-only source boundary,
  unchanged legacy AAD/formats and no implicit whole-video migration.
- Durable encrypted hold intent before ordinary Primary index removal, no
  ordinary Trash/.deleting/.legacy reuse, explicit conflict-checked restoration.
- Primary backup/restore refusal while unresolved holds/transfers exist, rather
  than silent archive-v1 omission or silently changing frozen archive bytes.
- Fresh independent operations and full selected destination verification;
  persistent IDs, filenames, matching digests and receipt-shaped DTOs are not
  removal authority.
- Purpose10/11 actual-key accounting, honest hostile-rollback limits, no
  plaintext media spool, revoke-before-picker and fresh-unlock return.
- Any exception after promotion requires authoritative disk inspection; never
  infer that a failed caller means nothing committed.

## Admission steps

The design-admission plan's Tasks1–3 freeze the missing grammars/vectors and
explicitly own DR-03/05/06/07; Task4 checks DR-04 and all integrated ownership
invariants. A later production task plan cannot guess these contracts. Current
application source, frozen fixtures, build configuration and Phase2 evidence
remain unchanged. There is no Phase3 signed candidate or owner acceptance.

**PHASE 3 RESULT: NO-GO**
