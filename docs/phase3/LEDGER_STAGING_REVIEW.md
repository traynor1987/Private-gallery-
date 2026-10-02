# Independent ledger staging closure review

Review date: 2026-10-02.

**LS-01: CLOSED at design level. No remaining Critical or Important finding
identified in the reviewed ledger-staging closure.**

**PHASE 3 DESIGN RESULT: GO FOR IMPLEMENTATION. PHASE 3 RESULT: NO-GO.**
This review restores design admission for the durable-ledger step; it does not
accept production storage, Android durability, a signed build or owner data.

## Scope and reviewed state

The initial focused review found Important LS-01 against the approved design
head `b4a34a9e2784f8b8497c46be8ac30d56b751c28e`: V2 required a synced
temporary and atomic ledger replacement, while new S2/P1 strict inventories
did not specify crash-surviving ledger staging paths and recovery admission.
The original finding remains in the implementation plan's
`ledger-staging-review.md`; this report records its subsequent disposition.

This independent re-review inspected the local normative closure in
STORAGE_FORMAT, PRIMARY_TRANSFER_FORMAT, VIDEO_AND_USAGE and HOLD_RECOVERY,
plus new P3-LS01..06 requirements and G20/G63 changes. These local changes are
distinguished from the original approved head. Pure codec changes and actual
durable-storage implementation are outside this review's scope.

No production file was changed, no commit was made and no helper agent was
spawned by this reviewer. Only this public closure report was written.

## Closure evidence

| Original gap | Reviewed closure | Disposition |
| --- | --- | --- |
| New Hidden media/proof stages conflict with fixed usage filenames | S2 lines133–142 expressly admits exclusive random regular `q<32 lowercase hex>` files in each new usage directory, physical0..82-byte bounds, no children, maximum16 stages per directory, aggregate capacity reservation and failure-closed identity/shape checks. Lines180–189 integrate these shapes into strict inventory. Root temporary is explicitly excluded as an alternate counter path. | CLOSED |
| Primary58 update stages lack namespace grammar | P1 lines23–37 defines exact local `usage/q<32 lowercase hex>`,0..58-byte bounds, maximum16, canonical58-byte targets, private updater ownership, sync/reopen ordering, preserved restart quarantine and no new temporary namespace. | CLOSED |
| Partial stages or uncertain replacement might authorize a query/lease | S2 lines144–150 and V2 lines71–79 require private target/operation/state/physical-identity binding, file and directory sync, exact stage reopen, atomic target replacement, parent sync and exact installed reopen. Both video budgets are preflighted before either update; both installed states must reopen before a sweep lease. Failed/incomplete staging and uncertain replacement grant zero GCM/lease. | CLOSED |
| Restart handling could replay, reconstruct or refund counters | S2 lines151–159, P1 lines29–37 and V2 lines79–87 make canonical ledgers the only durable accounting authority. Restart has no updater/lease; leftover stages cannot be replayed, promoted, selected by highest counter, used for repair, refunded or discarded by public name/body. | CLOSED |
| Discard/retirement might infer ownership from a filename | S2 lines155–159 and V2 lines83–86 restrict discard to the original updater's exact pinned uninstalled stage after ending its update, excluding other readers/updaters and syncing the parent. Restart candidates remain quarantined; wrapper retirement preserves new media/proof stages and canonical ledgers. | CLOSED |
| Restricted A2 admission could reject safe leftovers or require media repair | A2 lines42–55 admits bounded regular proof/credential stages as nonauthority quarantine, validates their physical shape, freshly charges only selected canonical slot/A2 ledgers, and explicitly avoids media traversal/admission and proof cleanup authority. | CLOSED |

## Accounting, ownership and failure assessment

The update protocol remains under the existing serialized root/ledger locks.
It introduces neither a new lock nor a durable query reservation. File sync,
replacement and reopen must continue to follow L2's existing separation from
short authority gates; the closure supplies no permission for reverse lock
acquisition, callbacks or resource joins under storage/controller/authority
locks. Original operation validity still governs actual GCM/publication.

An installed first video charge remains spent when the second update fails,
and no partial lease escapes. A stage which was never installed permits zero
GCM attempts; arbitrary candidate bytes cannot establish a budget or authorize
replacement. Restart reads canonical state and freshly precharges again. If
an established key's canonical ledger is missing, malformed or detectably
inconsistent, its service is denied despite any complete-looking stage.

Fresh-attempt initial ledger registration remains authorized by V2 lines14–17
and the authenticated owned-attempt protocol: full pre-encryption reservation
is durable before the first invocation. The missing-canonical prohibition
applies to an established key; it does not prohibit initial exclusive creation
for a new reserved actual key. No stage reconstructs either case and no
interrupted encryption resumes or receives a refund.

The discard rule requires actual current-process ownership and positive
uninstalled-stage identity. Uncertain replacement cannot justify deleting an
installed target or treating a newly discovered filename as owned. Restart
quarantine deliberately has no discard workflow; quotas can deny further
updates rather than inventing reclamation authority. This bounded availability
cost is explicit and does not weaken source/hold preservation.

A2 distinguishes existing quarantined candidates from the fresh updater's own
new charging stage. It cannot replay or discard discovered leftovers. Valid
selected canonical counters may fund a new recovery despite admissible
leftovers; exhausted counters or insufficient staging capacity deny affected
recovery while retaining the hold. The proof never inspects opaque media or
gains a ledger-cleanup capability. The existing finite credential/evidence
limits and hostile private-state rollback limitation remain intact.

No new contradiction, failure-open path, lock-accounting inconsistency or proof
ownership expansion was identified in these clauses. The exact paths and
restart behavior now resolve the initial framing gap without another encrypted
reservation format or recursive accounting service.

## Evidence limits and implementation gates

P3-LS01..06 explicitly require restart-shape, malformed-path, canonical-versus-
stage, every update interruption, quota/discard/retirement and restricted-proof
tests. G20 requires exact staged/installed reopens and no replay/refund; G63
requires credential/proof quarantine independent of opaque media. All remain
PENDING production execution.

No tests were run in this focused re-review. The earlier30 passing reference
tests are not evidence for these staging-path/restart conditions. Actual
implementation must prove exclusive creation, no-follow pinned identities,
serialization, interrupted file/directory sync and replacement behavior,
canonical-only charging and original-operation fencing. Android/OEM durability,
exact candidate CI, signed APK identity, independent implementation review and
owner physical acceptance remain separate gates. There is no product or owner
PASS, merge authorization, migration authorization or later-phase admission.
