# Independent Phase 3 closure-contract review

Review date: 2026-10-02. Baseline provided: PR59 head `942edb2050514e19502b5c6560466b8cc1b91b9e`, main `93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10`. This is a read-only design review of the newly supplied S2-r1, V2-r1, R2-r1, P1-r1, A2-r1 and L2-r1 contracts and their reference model/vectors. No application edits, builds, tests or subagents were used. This report is outside the repository. Prior OPEN dispositions are not evidence against these new contracts.

## Final re-review verdict

**GO FOR IMPLEMENTATION — DESIGN ONLY. All four design blockers are resolved at the contract level. No remaining Critical or Important design findings identified.**

This final verdict follows an independent re-read of all six updated contracts, `reference/contracts.py`, `test_design_contracts.py` and `design-vectors.json`, including the final D2 retention correction. It supersedes the initial conditional NO-GO in this report. It does not authorize production changes in this documentation-only milestone or claim production acceptance, Android/OEM durability, AEAD implementation, or owner sign-off.

## IC-01 correction and closure

The first pass identified an Important residual DR-05 framing omission, consisting of contradictory attempt depth, an unspecified A2 file location, an unframed durable video-query reservation, and undefined terminal sequence/restore-merge hash bytes. The corrected contracts close these points:

- S2 defines root depth 0, maximum depth 6 and exact manifest-owned child paths. The stated nonempty attempt path now fits the inventory bound; depth alone never admits arbitrary paths.
- S2 fixes A2 at `transactions/proof/anchors/<selectedAttempt32>`, supplies expected context and a 2268-byte physical cap, and defines preparation, current-token admission, predecessor retention and authenticated retirement. Restricted recovery can locate it without D2; old anchors cannot authorize a new selected token.
- V2 removes the durable encrypted reservation entirely. Both ledger budgets are checked under the shared lock; header then chunk charges are atomically replaced, synced and reopened. No in-process lease exists before both exact post-charge states reopen. Partial or unused charges remain spent, and no restart lease is reconstructed. This supplies a complete safe protocol without inventing another durable format or its recursive accounting burden.
- R2 defines terminalSequence as the entry's first terminal holdRevision, with monotonic mutation rules and no authority from an isolated sequence. P1 adds explicit terminalKind and retains the exact prior terminal catalog R plus its original key ledger as nonrecursive evidence.
- P1 defines bounded canonical M1 bytes for the restored item and affected relationships, including deterministic post-move covers and current favourite binding. Whole-index hashes are no longer the ambiguous substitute for exact item/relationship recognition after unrelated writes. A public synthetic M1 vector and negative framing constraints accompany the rule.

One re-review wording issue was corrected before this final verdict: D2 now includes evidence for **ALL RETAINED selected transfer entries**, including terminal1..4 until durable R2 step4 removes them. Marking terminal alone cannot retire evidence. Historical I2 and prior Primary terminal catalogs are parsed as target-specific evidence, not recursively mounted authority. Shared evidence remains until every retained reference and owned reader is released.

These are documentation/reference changes, not changes to F1 wire bytes, legacy Primary formats or application behavior. IC-01 is CLOSED.

## Substantive closures accepted

### Receipt/index evidence and credential rotation

S2's I2 transfer entries use contexts rather than receipt hashes, permitting construction of immutable index, then receipt over that exact index, then journal over the receipt, then D2 references. This preserves the authenticated hash dependency DAG. R2 requires the full original encrypted historical index and its actual-key ledger across credential mutations, while current I2 can advance. S2 moves all purpose-1 ledgers into media usage and explicitly separates evidence from credential wrapper retirement, addressing the actual `SecondaryStore.recoverRetirement()` behavior that otherwise removes old generation trees and unselected usage keys.

R2 defines the current cleanup predicate precisely: exact item/payload context, ciphertext hash/length, plaintext length/digest, ACTIVE state, origin and no restriction weakening. It explicitly allows metadata-only crop/name/relationship changes while refusing payload replacement, trash, removal or weaker restrictions. This resolves the difference between historical receipt evidence and current deletion eligibility without digest-only substitution.

The five-step terminal handshake retains dependencies through Primary terminal selection, Hidden terminal selection, Primary RELEASE_ACK and finally Hidden reference removal. Physical retirement follows durable selection and checks shared dependencies/readers. The explicit pre-selection cancellation branch avoids manufacturing a receipt for a transfer that never selected a destination. Whole-index rollback is forbidden.

### Source sole-copy invariant and hold state machine

P1/R2 add a separately authenticated Primary namespace without changing legacy VDEK, payload, index or archive bytes. Projection binds complete source metadata, relationships, full ciphertext identity/digest and root identity; unsupported stable identity or source IDs are refused. Current-source fingerprint comparison precedes destination commit/hold intent. State 4 is durable before source-index removal, and state 5 owns unchanged source ciphertext rather than unlinking it.

Restoration verifies held source bytes, merges into the current authenticated legacy index, and rejects conflicting IDs/collections/covers. It never overwrites the whole old index or unlinks restored payload. Cleanup requires explicit action, current paired authorization, fresh full destination verification, a durable exact intent and a fenced exact unlink. Backup/restore refusal while ownership is unresolved addresses the fact that archive-v1 cannot represent the hold. Ordinary source ownership is resolved before terminal dependency release.

These are implementation changes still required across setup/import/delete/dedupe/migration/reconcile/backup/restore. The current repository does not enforce them, and the contracts accurately say so. Their absence from unchanged application code is an implementation gate, not a fresh design omission.

### Video and query accounting

V2 uses frozen purpose-10/11 header/chunk framing and distinguishes the header key from the shared chunk key. The 8 GiB charge, 8192 chunk queries per sweep, 82-byte U2 layout and complete physical-length arithmetic agree with frozen F1 inputs. Full-operation preflight and a nontransferable in-process budget lease prevent counting an entire sweep as one query. Interrupted/prepaid but unused charges are never refunded or reset. Exhaustion retains source/hold and allows paired hold restoration without destination verification. The final sequential precharge protocol closes the original IC-01 reservation-framing issue without weakening that accounting design.

### Lock/resource/cleanup DAG

L2 adds cache, storage, metadata, normalized Hidden root, resource, controller, paired authority and enrollment gates in one order. It explicitly handles the actual root-to-controller biometric promotion and controller-to-authority lifecycle edges. It also identifies the important current `locked()` implementation in both session authorities: cleanup drains after the local gate exits even while a caller may still hold outer controller/storage locks.

The mandated future enqueue-only revocation, outside-lock cancellation/close/join, resource acknowledgements and cleanupComplete barrier close that design hole. The current Primary delete path performs preview-cache removal inside metadata locking; the specified collect-IDs/release-storage/invalidate rule therefore needs implementation auditing, as the contract acknowledges. Concrete Kotlin latch/deadlock and lifecycle tests remain acceptance gates; the abstract rank test alone proves none of them.

### Restricted hold recovery

A2 directly resolves ordinary `SecondaryStore` admission's dependence on descriptor/catalog/index decryption. It authenticates the selected bootstrap/token, exact encrypted catalog and active selected independent PIN or confirmed recovery envelope; successful unwrap alone is insufficient. It does not consult media/index evidence, issue SecondaryOperation, or use H2/Primary-only fallback. The independent master is wiped before the opaque proof leaves the authentication path.

The proof binds transfer, hold revision, selected credential hashes, fresh Primary operation, one-shot challenge and monotonic deadline. It only authorizes restoration and becomes invalid on credential replacement, cancellation, process death or revocation. Restart requires new paired authentication. Hidden terminal release stays deferred to ordinary service after repair. The reserved final query allowance and explicit all-credential/evidence-exhaustion failure limit are honest bounded-service constraints, not claims of indefinite recovery. The final S2 anchor placement and grammar close the original IC-01 concern about locating this proof independently of D2.

## Evidence limits and remaining implementation gates

I read the updated `reference/contracts.py`, `test_design_contracts.py` and `design-vectors.json`; I did not run them. The author reports 30 passing reference tests and the new vector test first failing with KeyError before its vector was added; that execution report is attributed to the author, not independently reproduced here. They are small arithmetic, predicate, graph and boolean state constraints, with a few exact serializers. They do not parse most new wire bodies, exercise AEAD, unwrap credentials, drive current Android storage/authority classes, or prove fsync/rename/power-loss behavior. Their published scope is appropriately limited.

With IC-01 corrected and independently checked, GO FOR IMPLEMENTATION is supported as a design-only decision. Production work still requires canonical parser/negative vectors, full state-machine fault coverage, source-ownership/retirement integration, deferred-cleanup concurrency tests, provider/native-player resource evidence, and real-device durability/acceptance. None of those pending implementation gates requires marking the accepted substantive design resolutions OPEN again.
