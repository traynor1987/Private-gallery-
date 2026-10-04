# Initial registration and isolated hold recovery IC-02

Normative local design closure, independently re-reviewed clean in
INITIAL_REGISTRATION_RECOVERY_REVIEW.md. **PHASE 3 RESULT: NO-GO.** This closes supported creation/crash windows; it grants no
production, Android, signed-candidate or owner acceptance. Frozen S1, F1,
U58/U82, legacy Primary and backup bytes remain unchanged.

## Initial canonical creation and short quarantine

Fresh registration remains exclusive CREATE_NEW at its actual-key64 path.
Validate purpose, exact planned charge, physical limits and aggregate capacity
before creation. Pin original operation, fresh attempt/namespace, creator inode,
parent/root and exact planned state. A private uninterrupted original creation
may finish its write; no disk bytes grant a permit. File+parent sync and exact
same-object full-state reopen precede its private one-shot GCM allowance.
Collision, write/sync/reopen failure, interruption, revocation or disposal ends
creation authority for that key. Never reopen a failed registration to finish
it, even when no GCM ran. Full pending state left by uncertainty supplies no
fresh permit. Retry requires a fresh attempt/key domain, with no budget refund.

Explicitly admit these regular, no-follow short entries as NONAUTHORITY
quarantine, separate from full canonical ledgers:

| Fixed namespace | Name | Short physical length | Full ledger length |
| --- | --- | --- | --- |
| Primary `vault/transfer-v1/usage` | actualKey64 lowercase hex | 0..57 | 58 |
| Hidden `transactions/media/usage` | actualKey64 lowercase hex | 0..81 | 82 |
| Hidden `transactions/proof/usage` | actualKey64 lowercase hex | 0..81 | 82 |
| Schema2/restricted Hidden `transactions/usage` | actualKey64 lowercase hex | 0..57 | 58 |

The credential exception applies only to schema2 admission and restricted A2
recovery; schema1 ordinary admission/creation remains unchanged. A full ledger
still requires its exact inherited encoding and actual-key checks before its
service. P1 full pending58 keeps PF-01's zero-hash/private-writer semantics.

Never parse a short as a ledger, select it, query/encrypt/copy from it, grant
ownership, fill/repair/resume/adopt it, or replace/truncate it to regain credit.
A required actual key with short/missing/malformed/inconsistent canonical
ALWAYS fails closed. An unrelated admissible short does not by itself deny
OTHER intact selected keys. This is no authentication of its filename/body.
Count every entry in the applicable fresh full8192/depth6 inventory or bounded
A2 projection. Pin and repeat checked identities/lengths under existing locks.
Links, directories, alternate names, oversize, inaccessible or changed checked
identities deny relevant admission. Existing q-stage16, attempt and aggregate
quotas stay binding; short entries give no free capacity or query allowance.

Preserve shorts after failure/restart. No short-canonical cleanup workflow is
added; q-stage discard/wrapper retirement cannot delete them. No replay,
reconstruction, highest-counter choice or refund. Unrelated shorts alone allow
freshly charged valid selected keys, not a claim of ordinary full admission:
all other selected-evidence, grammar, ownership and capacity checks still apply.
A2 checks ALL credential/proof usage entries with these explicit short bounds,
but charges ONLY exact valid EXISTING selected slot/A2 ledgers. It creates or
repairs no canonical, discards no quarantine and traverses no opaque media.

## Primary targeted hold-restore admission

P1's ordinary partial/malformed-ownership transfer/write/backup blockade stays
in force. Its sole recovery exception is the exact existing selected hold's
restoration, with original Primary HOLD_RESTORE operation and independently
current selected A2 proof. Authenticate CURRENT selector/bootstrap/descriptor/
catalog and its required selected reference closure, target projection/journal
and exact actual-key ledgers. Fully verify pinned held source and CURRENT
legacy index. Never authenticate a hold from a public attempt/reservation or
mount an older generation. Missing/corrupt required selected bytes, unsafe
parents, replaced identities or unavailable source/index deny restoration.

The exception still checks/pins the complete Primary transfer tree within8192
entries/depth6. Only UNSELECTED material supplying NO required current reference
may be classified as nonauthority quarantine at these exact shapes:

| Relative path | Permitted bounded quarantine |
| --- | --- |
| `attempts/<attempt32>` | Directory; only optional reservation, owner, files, selection; existing maximum16 abandoned attempts remains binding |
| attempt `reservation` | Regular0..26 bytes |
| attempt `owner` | Regular0..699 bytes (172-byte F1 overhead plus maximum527-byte S2 owner body) |
| attempt `files` | Directory, at most16 flat context filenames; no subdirectories |
| attempt `selection` | Regular0..26 bytes; P1-only selector staging, never a ledger/owner |
| `gens/<token32>` | Directory; only optional bootstrap, catalog, descriptor |
| generation `bootstrap` | Regular0..102 bytes |
| generation `catalog` | Regular0..16MiB+172 bytes |
| generation `descriptor` | Regular0..65536+172 bytes |
| context file, purpose1 | Regular0..16MiB+172 bytes |
| context file, purpose5 | Regular0..354 bytes (frozen182-byte journal body) |
| context file, purpose9 | Regular0..65536+172 bytes |

Context names are exactly `<purpose4>-<object32>-<generation16>`, lowercase
hex, supported purpose1/5/9, nonzero objectID and positive generation<=Long.MAX.
These caps apply to unselected `attempts/.../files` and existing fixed `evidence`
paths only. They are no R, manifest, ownership or authenticity proof. A selected
reference requires exact expected context/R and full AEAD, never only a cap.
Unknown children/purposes, links, excess count/size/depth, unreadability or
changed pinned quarantine reject recovery; omission never means fresh/empty.

All quarantine counts toward capacity before every new restore file/stage.
Freeze its identity/state throughout the operation; authorize only this
transaction's own exact new files/counter replacements. Fresh recovery writers
use new exclusive IDs/keys, same original-operation permits and full durable
write/reopen/authentication protocol; never adopt/overwrite quarantine. A failed
new recovery attempt remains quarantine and cannot supply authority on retry.
Quota exhaustion denies recovery while retaining held/ordinary ciphertext.
There is no infinite-availability claim or automatic capacity reclamation.

Permission is limited to this chosen hold's required catalog/intent/legacy-index
ownership writes and authoritative reopens. No generic transfer, ordinary
mutation, orphan cleanup, namespace repair, unselected-record authentication,
payload unlink, destination receipt, Hidden media/write/delete or dependency
release. Ordinary admission is never inferred from this exception. Terminal
backup/replacement rules still independently inspect resolved ownership and
authoritative inventory; quarantined material is not silently retired.

## Restricted state4 activation and state6 restart

The CURRENT eligible hold states are4 HOLD_INTENT,5 ACTIVE_HOLD and6
RESTORE_INTENT. The legal graph remains4→5→6→7; no direct4→7 is added.

For state4, authenticate exact selected intent/projection/journal and fully
verify original held payload/root identity, tags/length/SHA. Authenticate and
reopen CURRENT legacy index. Original source ID must be absent, with compatible
expected post-move relationships and no conflicting reuse/ownership. Under
fresh paired restore gates, select/sync/reopen4→5 solely to recognize already
committed source-index retirement. This activation changes no source-index
membership, unlinks nothing, needs no new Hidden media verification and grants
no cleanup authority. Then perform conflict-checked5→6→7 restoration. If source
is still ordinary, this proof cannot retire it or create a hold: retain it and
use the separate existing continuation/cancellation protocol. Missing payload,
corrupt index or conflicts deny; never rebuild Primary empty.

Proof issuance binds exact starting state as well as transfer/revision/source
identity and A2/slot/catalog/selector hashes, original Primary epoch/operation,
challenge32 and deadline<=5minutes. Reserve its challenge once for ONE private
restoration transaction before the first mutation; never concurrently claim or
retarget it. The private transaction may advance expected state/revision only
after ITS OWN exact legal selected catalog change is synced and authenticated
on reopen. Same-state6 intent-binding re-proposal is explicitly allowed only
under fresh source/current-index/M1 conflict checks and increments holdRevision;
it supplies no new challenge or public capability. Each short commit gate
rechecks expected CURRENT state/revision, original operation/epoch, independent
selected credential hashes and deadline. Unrelated changes invalidate it.

State6 restart needs NEW paired independent authentication and a NEW proof bound
to CURRENT intent. Verify held source and CURRENT authenticated index against
the exact M1 proposed item/relationships. If already committed, complete6→7
handoff without another index promotion. If source ID is absent, revalidate the
current merge, durably select/reopen a revision-incrementing state6 re-proposal
before index promotion. Conflicting ID/relationships deny and retain hold;
preserve all later unrelated writes, never swap a historical whole index.

Consume the transaction's single promotion allowance BEFORE source-index
promotion, or BEFORE6→7 handoff when exact restoration already committed.
After consumption, only completion of that exact durable intent/M1 ownership
handoff is permitted, not another promotion/retarget/retry. Failure grants no
fresh allowance. Restart/revocation destroys proof/private continuation;
authenticated disk intent grants no persisted permission. New paired proof is
required to inspect/reconcile the winner. Legacy index file+directory sync and
exact authentication/M1 reopen precede state7 catalog sync/reopen and success.
Move/restore never unlink the held payload; Hidden evidence remains retained
until separate normal paired release. L2 lock/cleanup barriers remain binding.

## Exact Primary selector stage

P1 alone adds the optional regular `attempts/<attempt32>/selection` child.
No other inherited S2 attempt shape changes. Its complete bytes are exactly
P1 Selector26 `PGTRP001[8] || U16=1 || tokenID16`;0..25-byte interrupted files
are quarantine. This is bounded opaque selection metadata, never plaintext
media. The ONLY canonical target is `vault/transfer-v1/selected`, exact26 bytes.
No root temporary namespace, alternate selector or generic path is introduced.

Only the original live scoped writer of its fresh pinned attempt may create
this stage exclusively after capacity reservation. Private stage permission
binds original operation/epoch, attempt/creator inode/parent/root, exact bytes,
authenticated complete target generation and CURRENT expected predecessor
selector token/hash/identity (or authoritatively absent initial selector under
fresh namespace admission). It grants no missing-ledger repair or ownership
from a found token. Sync stage file AND attempt directory, reopen the same exact
stage/bytes, then revalidate predecessor/target at L2's original paired short
commit gates and atomically rename ONLY this stage to canonical selected.
Large crypto/fsync/reopen remains outside authority gates. Sync BOTH affected
parents after rename: source attempt directory and destination namespace root.
Reopen/authenticate exact canonical selector/bootstrap/descriptor/catalog and
its required closure before any selected-success or next destructive step.

Failure/uncertain rename never permits blind rollback or deletion of a visible
winner. Inspect/sync/authenticate CURRENT selected state with fresh original
authority as applicable. Restart loses stage permission: preserve found stage
as bounded counted quarantine, never replay/promote/adopt it or discard it by
name/body. Unselected generations/stages do not mount themselves. A malformed
canonical selected pointer fails closed; absence is not fresh namespace when
partial transfer material exists. Required hold recovery still needs intact
CURRENT authenticated selected evidence under the preceding narrow protocol.
