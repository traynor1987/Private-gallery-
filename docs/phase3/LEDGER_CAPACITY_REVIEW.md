# Independent restricted ledger capacity closure review

Review date: 2026-10-02.

**LC-01: CLOSED at design level. No remaining Critical or Important finding
identified in the reviewed capacity closure.**

**PHASE 3 DESIGN RESULT: GO FOR IMPLEMENTATION. PHASE 3 RESULT: NO-GO.**
The durable-ledger step may proceed within the authorized implementation scope.
No product, signed-build, Android durability or owner acceptance is claimed.

## Original finding and reviewed state

Important LC-01 identified a real contract conflict after LS-01 staging closure:
S2 required whole-tree capacity reservation before every proof stage, while A2
prohibited traversal of unavailable opaque media. A2 could not establish the
complete physical8192-entry bound without violating its recovery isolation.
No existing authenticated reserved-headroom invariant resolved the conflict.
The original detailed finding remains in the implementation plan's
`ledger-capacity-review.md`; this report records the subsequent closure.

This independent read-only re-review inspected the exact current tracked
normative diff against `836e8c5d0434444ccb1a7f62ec22805aeefd0456` in the
implementation worktree: S2, A2, V2, the LC matrix, G20/G63 and the spec's
ordinary-bound clarification. It also compared the closure with the original
finding and relevant inherited S1/DomainInventory behavior. These are local
clarifications relative to that committed baseline. Pure codecs and production
durable-ledger implementation were outside this review's scope.

## Closure evidence

| Requirement | Exact reviewed closure | Disposition |
| --- | --- | --- |
| Explicit recovery-only capacity exception | S2 lines136–141 scopes full-tree reservation to ordinary service and cross-references A2's exact projection. A2 lines59–63 rejects assumed headroom/prior census; lines110–118 explicitly states that restricted charging proves no physical full-tree bound. V2 lines89–96 repeats the narrow exception. | CLOSED |
| Frozen projection scopes and readable directories | A2 lines65–81 includes pinned root/direct names, exact selected bootstrap/catalog/A2 and ancestors, all selected slot/recovery envelopes, and all direct credential/proof usage entries. It requires no-follow checked identities and readable/executable actually traversed directories. Lines83–89 keep other generations/anchors and all media descendants opaque, count distinct paths including root/directories, cap P at8192/depth6 and pin/repeat under the existing normalized-root lock. | CLOSED |
| Exact pre-creation capacity equation | A2 lines91–99 defines current distinct E, uncreated current-process reserved R and new N; E+R+N<=8192 precedes exclusive creation. Created entries count in E rather than R. The separate proofExistingQ+proofReservedQ+proofNewQ<=16 limit remains. Existing parents must already exist; sequential updates recensus and reserve independently. | CLOSED |
| Existing canonical-only authority, no restricted repair/discard | A2 lines101–108 permits only fresh staging and exact replacement of an EXISTING valid selected slot/A2 counter. Missing parents/canonical creation/repair, lower counters/refunds, new generations/anchors/slots, media repair and cleanup are denied. Failed owned stages remain; successful rename consumes its stage as charging. S2 line164 expressly overrides ordinary updater discard in restricted mode. | CLOSED |
| Honest ordinary re-admission and bounded accounting | A2 lines110–118 and S2 lines187–199 require a fresh complete current inventory before ordinary service resumes; unavailable/invalid/over-limit trees deny ordinary admission/mutations without deletion. There is no SecondaryOperation, cached census credit, restart reservation or media/video lease. Existing sync/reopen, operation fencing and installed-charge retention remain required. | CLOSED |
| Executable gate mapping without fabricated evidence | P3-LC01..06 cover opaque media, projection/proof boundaries, transient global overflow/restart, malformed checked scopes, forbidden repair/discard and ordinary re-admission. G20/G63 require the projection distinction and narrow authority. All production cases/gates remain PENDING. | CLOSED |

## Independent consistency and failure assessment

The exception is explicit rather than a silently omitted global check. An
ordinary-full tree can acquire a restricted transient stage beyond8192, or keep
one after interruption, while the inspected credential projection remains
bounded. This does not report a global PASS. Once ordinary service is requested,
the entire current inventory must independently pass; opaque or excess entries
cannot be treated as absent and restricted leftovers cannot be silently erased.

Projection readability applies only to actually traversed directories and
checked files. Known opaque media directory identities remain checked without
opening their descendants. Selected bootstrap/catalog/A2 and both entire usage
directories remain physically bounded and pinned. Unrelated canonical usage
entries are counted and bounded without granting decryption/mutation authority;
unknown shapes, unsafe links or unavailable checked scopes fail closed.

E/R/N has no double counting of a created stage, and the proof16 cap is distinct
from the aggregate projection cap. Neither an earlier census nor restart
reservation carries forward authority. Each selected slot/A2 charge requires
fresh capacity and exact installed-state reopen before its GCM attempt. An
earlier installed charge remains spent when a subsequent charge cannot reserve
capacity or fails. Canonical-only budgets and the existing no-refund/no-replay/
no-restart-lease rules are unchanged.

The closure adds no new lock, encrypted count or reservation format. Root
serialization and L2's original lock/resource rules still apply. A2 may not use
the capacity exception for a fresh-key registration, another object's ledger,
media/video service or general cleanup. Ordinary fresh-attempt initial canonical
registration remains separate; a missing established selected counter is not
reconstructed from a stage. Original paired proof scope and Primary ownership
checks continue to fence restoration.

No new contradiction, failure-open accounting path, lock cycle or proof
ownership broadening was identified. LC-01's original capacity/isolation
conflict is resolved without claiming unproved reserved headroom or changing
cryptographic wire formats. LS-01's staging closure remains intact.

## Verification limits

No tests, Gradle builds, production edits, commits, helper agents or remote
writes were performed by this reviewer. Only this public report was written;
the original scratch finding was preserved. A local whitespace diff check
passed. The parent's reported30 passing reference tests are attributed to the
parent and do not execute LC01..06 or prove production inventory/charging.

Implementation still needs exact projection/capacity boundary tests, interrupted
stage/write/sync/replace/reopen tests, no-follow physical identity checks,
concurrency/revocation fencing, canonical accounting and fresh full-inventory
re-admission evidence. Android/OEM durability, independent implementation
review, exact candidate CI, permanent-signer APK identity and owner physical
acceptance remain separate pending gates. No merge, owner migration, later
phase or product/owner PASS is inferred from this design closure.
