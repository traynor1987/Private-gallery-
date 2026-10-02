# Independent Phase 3 implementation readiness review

Review date: 2026-10-02.

**PHASE 3 DESIGN RESULT: GO FOR IMPLEMENTATION.**

**PHASE 3 RESULT: NO-GO.** No product, signed-build or owner acceptance is claimed.

## Reviewed state and scope

The independent reviewer first inspected the exact original design head
`b4a34a9e2784f8b8497c46be8ac30d56b751c28e` in the `private-gallery` checkout,
against the supplied main baseline
`93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10`. Both commit objects were checked;
the head-to-baseline diff contained documentation and nonproduction reference
files only. This does not claim a fresh remote-main or CI verification.

The reviewer then inspected the local clarification diff in the separate
`phase3-implementation` worktree, whose HEAD still equals the original design
head. The five locally modified files were PRIMARY_TRANSFER_FORMAT,
RECEIPT_RETENTION, NEGATIVE_TEST_MATRIX, EXIT_GATES and the Phase 3 spec. These
uncommitted clarifications are distinguished from the original exact head
throughout this report. This report is additional review evidence; it changes
no application source or normative contract.

The review covered the Phase 3 report, all six normative S2/P1/R2/V2/A2/L2
contracts, DESIGN_REVIEW, INDEPENDENT_CLOSURE_REVIEW, REVIEW_EVIDENCE, the
negative matrix and exit gates, the Phase 3 spec, reference constraints/tests
and relevant frozen F1/Phase 2 contracts. Relevant actual baseline sources
included SecondaryStore, SecondaryController, both session authorities,
AndroidVaultRepository, PrimaryVaultSetupGuard and VaultBackupArchive.

The scope is implementation readiness: state consistency, durable ownership
boundaries, restart/rollback/failure closure, receipt retention, resource lock
ordering and restricted paired hold recovery. Missing production implementation
is an implementation gate, not itself a residual design defect. No merge,
deployment, signed build, owner-data operation, migration or later phase is
authorized by this review. No subagents were used by this reviewer.

## Findings and clarification re-review

No concrete Critical or Important finding remains within the authorized scope.
The original head had two clarification items; the local edits close both.

1. **Terminal export versus destructive restore admission — CLOSED.** Original
   P1 lines96–97 broadly permitted backup/restore in terminal7/9..12 while R2
   lines77–91 still required Primary terminal/projection/journal evidence for
   the paired release handshake. The original R2 lines96–98 likewise lacked
   an explicit distinction between export and destructive replacement. This
   was a meaningful future-admission ambiguity, but not a demonstrated current
   loss path: baseline AndroidVaultRepository lines618 and625–634 denies a
   configured-vault restore and permits only identical-archive continuation
   over existing ciphertext. No replacement feature is authorized. The local
   P1 lines96–103 and R2 lines96–105 now permit terminal **export only**, deny
   destructive restore/replacement before any write while any retained record
   or unresolved attempt/ownership exists, and require fresh paired pruning,
   an authenticated empty catalog, resolved authoritative inventory and closed
   readers/producers before removing this transfer blockade. Existing
   fresh-install-only admission remains mandatory. N076, G35 and spec lines219–
   225 carry the same distinction. This preserves R2 steps2–5 and the unchanged
   archive-v1 allowlist: VaultBackupArchive lines37–55 includes the ordinary
   legacy index and indexed payloads only.
2. **Inventory-depth test wording — CLOSED.** Original N024 said depth5,
   contradicting S2 lines153–158 and the corrected owned attempt grammar. Local
   NEGATIVE_TEST_MATRIX line34 now says depth6. DC08 still rejects depth7 and
   unlisted depth6 children. The revision does not admit arbitrary depth6 paths.

A focused re-read and repository text search found no remaining contradiction
introduced by these five clarification edits. The initial absent-namespace
case remains governed by P1 lines23–26; the new conditions remove an existing
transfer blockade and do not require creating a catalog during fresh setup.
Preselection cancellation retains its separate fresh paired, authenticated
absence/ownership pruning branch in R2.

## Four blocker dispositions

| Blocker | Independent disposition | Primary evidence |
| --- | --- | --- |
| Exact Hidden storage formats | RESOLVED at design level. Bounded S2 bodies, contexts, immutable references, exact paths, authenticated attempt ownership and one selector define the writer/admission boundary. Generation preparation carries matching D2/I2/catalog/A2. Direct import deliberately has no paired transfer/receipt. | STORAGE_FORMAT lines8–27,31–63,95–102,113–161,165–180; VIDEO_AND_USAGE lines7–28 |
| Source/destination receipt retention | RESOLVED at design level. All retained terminal transfers keep their immutable original index/receipt/current journal and actual-key ledgers through paired release. Historical evidence is target-specific and never recursively mounted. Source ownership is resolved before terminal release; current cleanup eligibility cannot be replaced by a digest match. | STORAGE_FORMAT lines48–60,127–131; RECEIPT_RETENTION lines10–28,35–72,74–120; PRIMARY_TRANSFER_FORMAT lines35–55,113–146 after clarification |
| Primary/Hidden/resource lock order | RESOLVED at design level. Increasing ranks, forbidden reverse edges, enqueue-only revocation, outside-lock close/cancel/join and acknowledgement barriers address the actual baseline interactions. | LOCK_ORDER lines14–50,54–75; PrimarySessionAuthority lines206–221; SecondarySessionAuthority lines241–256 |
| Hold restore with blocked ordinary Hidden admission | RESOLVED at design level. A2 independently authenticates active selected PIN/confirmed recovery evidence without media/index admission and issues a narrow, one-shot, in-process proof for exact Primary restore only. | HOLD_RECOVERY lines16–40,42–51,55–89; STORAGE_FORMAT lines113–124 |

## State, durability and failure assessment

The authenticated dependency construction is an acyclic index-to-receipt-to-
journal-to-descriptor graph. I2 contains immutable contexts rather than the
receipt hash over itself. Historical indexes and Primary terminal catalogs are
evidence for one target and do not recursively mount their other entries.
Shared snapshots and actual-key ledgers survive credential wrapper retirement
until all retained references and owned readers end (S2 lines48–60,127–131;
P1 lines50–55). This explicitly addresses baseline SecondaryStore.prepare's
empty-index construction, verify's empty-index requirement and
recoverRetirement's removal of unselected generation trees/usage ledgers; those
actual implementation paths must change together.

Before destination selection, invisible stages cannot authorize source removal.
After a visible selection rename, exceptions never imply rollback; fresh
authentication inspects and syncs/reopens the selected winner. Hold state4 is
durable and authenticated before source-index removal; state5 owns unchanged
source ciphertext and requires the ordinary source to be absent. Move never
unlinks this ciphertext. R2/P1 distinguish preselection cancellation,
postcommit retained Copy, restoration, explicit cleanup and terminal release.

Restoration verifies held payload tags, length/digest and pinned identities,
then merges one exact item and relationships into the current legacy index.
Canonical M1 permits restart recognition after unrelated writes while refusing
ID, collection, cover, membership and other bound conflicts. It cannot replace
payload verification or authenticate itself. Cleanup separately requires fresh
paired authority, full current destination verification, a durable exact
intent and fenced exact unlink. Failures preserve ownership or an unavailable
intent; no historical whole-index overwrite, silent orphan cleanup, age-based
expiry or empty writable reconstruction is admitted.

V2's two-ledger full-sweep precharge checks both budgets before replacement,
syncs/reopens header then chunk charges and issues no lease before both exact
states reopen. Partial and unused charges stay spent. Restart reconstructs no
lease and resets no counter (VIDEO_AND_USAGE lines30–51). The 8 GiB/8192-chunk
budget is consistent with frozen F1 framing. Valid hostile private-state replay
remains an explicitly excluded freshness guarantee.

L2's rank order is acyclic at the contract level. Actual baseline authority
cleanup drains after leaving its own gate even when an outer caller still holds
controller/storage locks; L2 correctly requires a future refactor before long
media registration. Cache invalidation collects IDs and releases metadata/
storage before cache acquisition. Fresh authentication waits for successful
resource/job/native acknowledgements, never timeout-as-success. Abstract rank
tests do not prove future Kotlin concurrency or main-thread release behavior.

A2 requires current selected credential identity, exact anchor/catalog/slot
hashes, fresh Primary operation and hold revision before each restoration
commit. The independent master is wiped before proof return. Proofs grant no
Hidden read/write/delete/cleanup/release capability, cannot be retargeted and
do not survive restart. Durable RESTORE_INTENT can resume only after new paired
authentication. Missing or exhausted credential evidence fails closed and keeps
held ciphertext; finite recovery availability is stated honestly. Direct-import
generation preparation and later credential rotation preserve matching A2
without making media/index decryption a restricted recovery prerequisite.

## Actual execution and acceptance limits

The reviewer independently ran:

`python3 -B -m unittest docs/phase3/test_design_contracts.py`

Result: **30 tests PASS** at the original exact-head checkout, and **30 tests
PASS** again in the local clarification worktree. The latter execution returned
30 tests in0.002 seconds. `-B` was used to avoid creating bytecode artifacts.

The reference suite checks selected serializers, arithmetic, predicates, graph
and state constraints. It is not a complete new-format parser, AEAD/key-unwrap
implementation, Kotlin integration/race suite, fsync/rename/power-loss proof,
provider/native-player memory test or Android/OEM/owner acceptance. In
particular its small backup_allowed model checks terminal **export** eligibility;
it does not establish the newly clarified destructive-restore admission gate.

All product negatives, fault boundaries and mandatory production gates retain
their pending status. G54 needs implementation review; exact candidate CI,
permanent-signer artifact identity and owner physical PASS remain separate.
Phase 2 acceptance cannot satisfy them. The implementation admission decision
does not change **PHASE 3 RESULT: NO-GO**, authorize merge or permit owner-data
migration. This reviewer modified only this review report and made no commit.
