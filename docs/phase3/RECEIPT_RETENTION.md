# Phase 3 receipt and recovery-hold retention R2-r1

Design-only. Frozen purpose5 journal182-byte body and purpose7 receipt228-byte
body remain byte-for-byte unchanged (phase0/FUTURE_CRYPTO_FORMAT.md§journals).
OperationID in a journal is historical evidence, never current authority.
Source object is fresh snapshotID/gen1 bound by Primary projection, not a fake
numeric legacy revision. Default COPY; MOVE retains unchanged encrypted source.

## Current versus historical evidence

Before source retirement, reopen/authenticate selected Hidden D2/I2, immutable
receipt, original encrypted index snapshot and complete destination payload.
Receipt hashes must match full ciphertext/index bytes, plaintext length/digest,
transferID, source snapshot/container and destination context. Both journals pin
the same complete encrypted receipt SHA. Historical index must contain exactly
that payload/item/transfer context at commit. Its index revision is the receipt's
destinationIndexGeneration. Credential rotation must retain that exact envelope
and its shared actual-key query ledger, not old slots/catalog/master wrappers.

Current cleanup predicate is exact:
same itemID AND same payload C/R(cipher hash+length) AND same plaintext length/
SHA AND ACTIVE state AND same origin AND restrictions contain every original
restriction bit. Name, crop/previous crop, metadataRevision, collection/favourite
membership and covers may change through independently authorized Hidden
metadata operations; they do not replace original bytes. Payload replacement,
generation/context/hash change, trash, removal or restriction weakening denies
retirement/cleanup. No digest-only substitute or dedupe. Metadata-only changes
are allowed because cleanup preserves original authenticated media bytes;
the complete original source metadata remains in the Primary hold for restore.
Readback walks ALL video chunks with fresh query budget. Budget failure keeps
ordinary source/hold; rollback of a hold needs only verified held Primary bytes.

## Durable state transitions

Frozen PREPARED→COPYING→DESTINATION_VERIFIED→DESTINATION_COMMITTED→
SOURCE_DELETE_PENDING→COMPLETE. COPY skips source retirement and completes with
source unchanged. Source changes after destination commit produce a retained
Copy plus explicit conflict, never a silent Move. Every retry joins exact ID.

MOVE local hold states in the Primary catalog (separate from frozen enum):
1 PREPARED,2 COPYING,3 DEST_COMMITTED,4 HOLD_INTENT,5 ACTIVE_HOLD,
6 RESTORE_INTENT,7 RESTORED,8 CLEANUP_INTENT,9 CLEANED,10 COPIED,
11 CANCELLED,12 RELEASE_ACK. Legal edges:
1→2→3;3→4→5;1/2→11 only before destination selection;
3→10 for Copy/conflicted Move;4→5 or4→11 only if exact source still ordinary;
5→6→7;5→8→9;7/9/10/11→12.
State4's intent is synced/authenticated/reopened before source-index removal.
State5 requires source absent and unchanged ciphertext owned by hold. No source
ciphertext unlink during Move. Source-index fsync+directory fsync+reopen precede
state5 success. Both fresh operations are valid at each paired commit.

RESTORE_INTENT binds exact source item/projection/payload and proposed item+
relationship merge hash, plus prior current Primary encrypted-index hash. Verify
held source tags/length/SHA and frozen payload identity before creating intent.
Merge into CURRENT Primary index; refuse item-ID reuse, changed collection
definition/cover or unexpected membership conflict. Existing unrelated writes
survive. Missing original collection is an explicit conflict, not recreation.
Restore original covers only if unchanged expected post-move cover and compatible
collection definition; never overwrite later edits. After authenticated restored
index is durable/reopened, state7 transfers payload ownership to ordinary item;
never unlink it. Hidden need not contain a usable destination to restore.

CLEANUP_INTENT binds fresh full selected destination verification, exact source
hold revision, receipt hash and current destination-selection hash. Intent must
be durable/reopened before unlink. Final unlink uses current paired gates plus
source/Hidden storage locks and current cleanup predicate; root replacements,
revocation or source ownership change deny it. Then payload-parent fsync;
authenticated catalog state9/reopen; only then cleanup success. A crash after
unlink never repeats an unrelated deletion: state8 names exact owned payload,
revalidate fresh destination and ownership or retain unavailable intent. No age
expiry, no `.deleting`, `.legacy` or ordinary Trash. Physical flash erase is not
claimed. Cleanup is irreversible and requires the owner's explicit action.

## Terminal dependency release handshake

Never infer release from absence, timeout, old epoch or Primary-only assertion.
1. Primary selects/reopens terminal7/9/10/11 with holdRevision+catalog hash;
   legacy item/payload ownership is resolved. Preserve its projection/journals.
2. With fresh paired operations, Hidden reads authenticated terminal through the
   Primary adapter (no Primary key export), selects I2 terminal1/2/3/4 + exact
   Primary terminal encrypted hash. Keep receipt/index/journal dependencies.
3. Primary reads that exact authenticated Hidden terminal selection through
   Hidden adapter, selects/reopens state12 with H(Hidden encrypted index) and
   H(its own prior terminal encrypted catalog). No Hidden key or plaintext index
   enters Primary; only fixed IDs/hashes/terminal kind.
4. Hidden verifies current Primary state12 through paired adapter and selects a
   generation dropping that transfer and its now-unshared dependency references.
   Root fsync+reopen precede physical retirement. Remove only exact authenticated
   unreferenced files, preserving any other live transfer/index/preview reader.
5. Primary can prune its closed terminal/projection/journals after fresh paired
   verification that current Hidden selection has no such transfer/dependency.

Every crash leaves a retained dependency or a terminal source whose ownership
already resolved; repeats are idempotent. Restricted recovery can complete
Primary RESTORED but cannot write Hidden terminal/release; preserve Hidden refs
until normal admission repairs. Backup-v1 export may resume once Primary is terminal
7/9/10/11/12 (no unresolved/unindexed source), even if Hidden evidence awaits
release. Destructive restore/replacement must still refuse before ANY write
while a retained record or unresolved attempt/ownership exists: terminal source
ownership does not release the Primary evidence needed by steps2–5. Removal of
that blockade requires an authenticated empty catalog AFTER fresh paired
pruning, authoritative resolved inventory and closed readers/producers. Existing
fresh-install-only restore rules still apply; no replacement feature is added.
Old valid storage replay remains outside trusted-freshness guarantees.

`terminalSequence` is not an additional field: it means the entry's holdRevision
at its first terminal selection. Start holdRevision at1; every catalog-entry
state/binding mutation increments it (no overflow/reuse); unrelated entries do
not change it. State12 increments it again and pins the exact prior terminal
catalog ciphertext hash. The adapter compares the expected transfer/revision,
terminal kind and selected catalog hash. No isolated sequence authorizes release.
Canonical restore-merge bytes are frozen in PRIMARY_TRANSFER_FORMAT.md.

Special case: pre-selection cancellation (state11 without receipt) has no
selected Hidden transfer/evidence to acknowledge. After both fresh operations,
no active producer/lease, authenticated owned-attempt inventory and current
Hidden index proving no selected item/context/transfer, Primary may prune that
closed record directly. Do not manufacture a receipt/COMPLETE frozen journal
with zero receipt fields. Invisible ciphertext remains abandoned or is explicitly
discarded under authenticated exact ownership; ambiguity retains it and denies
pruning. This branch never deletes an ordinary source.

## Deletion, rollback and failure closure

While a live move/hold references a destination, Hidden can trash it but cannot
physically retire its payload/index/receipt; must first paired restore the hold
or complete verified cleanup while destination ACTIVE. Copy terminal similarly
keeps dependencies through release; later deletion is normal logical deletion.
Preview eviction has no source-deletion authority. Source/destination proof
failure retains bytes, no empty writable index, no latest-generation guessing.
Selection rollback never swaps a whole historical index over later writes.

Mappings:N055–074,N087–093,N095;F09–24;G16,25–35,38–39,46–49,63.
DC19–28 pin current predicate, shared dependency and terminal crash states.
