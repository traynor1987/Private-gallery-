# Primary-only transfer namespace P1-r1

Additive design justification: backup-v1/index-v6 cannot represent an unindexed
private recovery hold. A new independently authenticated namespace owns that
unchanged ciphertext; it does NOT change legacy VDEK/envelope/payload/preview/
PGVIDEO1/index-v1..6/archive-v1 bytes or AAD. No new namespace is written in this
milestone. All primitive S/O/C/R encodings are STORAGE_FORMAT.md's exact rules.

Fixed root vault/transfer-v1, only concrete Primary transfer adapter. Random
containerID/masterID identify this new Primary-derived domain, never Hidden.
F1 HKDF uses Primary VDEK and fresh salt/IDs under its own purpose context;
no derived key is substituted into a legacy cipher. Domain masterID is an
opaque identity of that Primary VDEK domain, not a new Hidden/Primary rescue key.

Selector26: PGTRP001[8] || U16=1 || tokenID16.
gens/<token32>/{bootstrap,descriptor,catalog}; usage/<actualKey64>;
evidence/<purpose4>-<object32>-<gen16>; attempts/<attempt32>/{reservation,owner,files,selection}.
The optional P1-only selection child stages Selector26; its exact ownership,
sync/both-parent rename/reopen and quarantine rules are IC-02. Other attempt
children retain S2 encodings. No root temporary namespace is added.
The attempt reservation/owner/file grammar is S2's exact encoding under this
Primary domain (Copy/Move only, target item is destinationItemID); depth6 and
8192-entry bounds apply. Single-invocation Primary records use the frozen58-byte
F1 usage ledger under usage, including archived catalogs; no counter reset.
No links or generic path factory. Same Primary setup/storage+metadata locks.
Primary counter staging is regular `usage/q<32 lowercase hex>`, exclusive
random creation, physical size0..58 bytes, no children and maximum16 stages
in this usage directory (reserve aggregate capacity before creation).
Full canonical `usage/<actualKey64>` stays exactly58 bytes; IC-02 expressly
admits regular0..57-byte initial failures as counted nonauthority quarantine.
A required short key denies its service; unrelated bounded shorts do not
poison OTHER intact selected keys. Apply S2/V2's private
current-process target/operation/pinned-identity ownership, file+directory
sync/stage reopen, atomic replacement/parent sync/canonical reopen before GCM.
Installed charges remain spent. Restart stages are nonauthority quarantine:
never replay, promote, reconstruct a missing ledger, refund or delete by public
name/body. A valid canonical ledger permits freshly charged service despite
admissible stages; malformed/missing canonical state denies its key service.
Current-process discard is limited to its own exact pinned uninstalled stage,
with no concurrent reader/updater and parent sync; restart stages remain.
Quota exhaustion preserves bytes and denies updates. Unknown names, links,
oversize, inaccessible or changed identities fail closed. This does not add
a temporary namespace or weaken the unresolved-inventory replacement blockade.

### Fresh Primary58 reservation and completion (PF-01 closure)

P1 alone gives the same frozen58 bytes an explicit non-established pending
state: U16=1 || U64 encryptions=1 || U64 exactPlannedGHASHblocks || U64 queries=0
||32 zero hash bytes. Complete P1 state has a nonzero immutable SHA256 of its
entire produced ciphertext. This changes no offsets, actual-key identity,
existing S1 Hidden credential behavior or legacy Primary encrypted format.

For a new single-invocation F1 record precompute
10+ceil(plannedCiphertextBodyBytes/16)+1 blocks (156-byte padded AAD; tag excluded).
Validate exact purpose/body/physical bounds and per-key limits before reservation.
The original authorized live scoped writer chooses fresh context/salt/nonce/key
under existing namespace/attempt locks, reserves ordinary full-tree capacity,
exclusively creates canonical pending58, syncs file+parent and reopens the exact
same object/state BEFORE GCM. Never overwrite an existing canonical key. Collision
or failed/missing/corrupt/sync/reopen state gives no new budget.

Only a private same-process nontransferable fresh-writer permit authorizes that
one encryption. Bind original operation/epoch, pinned live attempt/namespace,
full expected header/context/key ID, planned length/blocks, exact pending state
and file/parent/root identities. Consume its sole allowance BEFORE invocation;
failure never restores it. Disk pending bytes, attempt ID, receipt or a new
operation cannot mint a permit. For encrypting the owner itself, permission
comes from original scoped writer+pinned newly reserved attempt, not circular
authentication of the owner ciphertext before it exists. Existing interrupted
attempts cannot become fresh. No second invocation/retry/resume under this key.

Pending state grants NO verification query, immutable copy, selectable object,
established key service or generic hash completion. Only the same still-live
original writer may complete after its successful producer finishes: persist
exact original output, sync ciphertext and parents, reopen same pinned output,
check exact physical length/EOF/context and producer digest. Replacement/found
bytes cannot substitute; no uncharged decrypt is permitted during this check.

Completion replaces ONLY hashzero with that nonzero ciphertext SHA, preserving
encryptions1/exact blocks/queries0. Use approved owned q-stage, file+directory
sync, exact stage reopen, atomic canonical replacement, parent sync and exact
installed completion-state reopen. Hash is then immutable. Failed/uncertain
completion grants no query/selection; never downgrade visible completion to zero
or reset counters from stale memory. Full authentication afterward is a SEPARATE
freshly precharged query; completion is no receipt or verification authority.

Restart/revocation/disposal destroys permit/finalizer authority. Bounded pending
zero58 remains quarantine; queries!=0/invocations!=1/invalid planned cost deny
service. Complete-looking ciphertext beside zero58 never authorizes completion,
resume, reconstruction or refund. Retry requires entirely fresh attempt/key.
An actually installed complete state may be re-admitted with fresh authorization
and ordinary readback/charging, even if its caller threw after replacement;
never infer success from an exception or remove the selected winner. No public
name/hash-based cleanup; existing exact ownership/discard rules remain. Restricted
A2 cannot create/register/repair a canonical ledger. S1 creation is unchanged.

Absent namespace means no hold only after no-follow authoritative inventory;
partial/corrupt namespace blocks ordinary transfer/destructive Primary mutations/backup,
but permits verified ordinary legacy reads where the ordinary index is intact.
Never fresh setup over partial transfer material. No generic orphan cleanup.
INITIAL_REGISTRATION_AND_RECOVERY.md is normative IC-02: ONLY targeted paired
HOLD_RESTORE may traverse bounded unselected quarantine to restore CURRENT
authenticated state4/5/6 ownership. Selected required corruption still denies;
ordinary admission is not inferred. It freezes exact P1 selector-stage paths
and private revision/one-shot continuation, with no payload unlink or repair.

Bootstrap102: PGTRB001[8] || U16=1 || containerID16 || masterID16 ||
catalogObjectID16 || descriptorObjectID16 || generation U64 || tokenID16 ||
reserved U32=0. Both expected record contexts come from this bounded bootstrap;
never choose descriptor identity from its own untrusted envelope header.
Descriptor(purpose9): PGTRD001[8] || U16=1 || H(bootstrap) || R(catalog) ||
count U16<=256 || sorted unique count*R(projection/journal).
Catalog(purpose1): PGTRC001[8] || U16=1 || generation U64 || count U16<=64 ||
sorted entries. Entries are:
transferID16 || localState U16(1..12 R2-r1) || action U16(1copy,2move) ||
terminalKind U16(0 live,1 restored,2 cleaned,3 copied,4 cancelled) ||
holdRevision U64 || R(source projection purpose1/gen1) || R(journal purpose5) ||
HiddenContainerID16 || HiddenMasterID16 || destinationItemID16 ||
O(C(receipt purpose7/gen1)) || O(H(receipt envelope)) ||
O(H(current verified Hidden selection)) || O(H(proposed/restored Primary merge))
|| O(H(prior Primary index)) || O(H(Hidden release-ack index)) ||
O(R(prior Primary terminal catalog purpose1)). Optional fields are mandatory according
to state:receipt/hash/verified-selection for3..10/12; merge/prior-index for6/7;
ack/prior-terminal for12; omit fields not meaningful for state. State12 retains
terminalKind and the terminal state's receipt/merge bindings. terminalKind must
be0 in1..6/8,1 in7,2 in9,3 in10,4 in11, and1..4 in12. State11 retains
receipt fields only when cancelling post-intent with selected destination. All
generation changes are new immutable envelopes; no in-place state editing.
State12's prior-terminal catalog is retained as immutable evidence under its R,
with its shared actual-key ledger; descriptor closure includes this reference.
Read it only as evidence for the target entry, never recursively mount its other
entries. Current-entry pruning retires it only after all references/readers end.
Catalog<=16MiB, descriptors/journals<=64KiB; max64 retained records, writes deny
until authenticated terminal pruning frees capacity. No silent retention expiry.

Projection(purpose1,snapshotID/gen1), max65536 plaintext bytes:
PGSRC001[8] || U16=1 || S sourceItemID || S MIME || S displayName ||
I64 importedAt || U64 plaintextLength || H(plaintext) || nonce12 ||
U16 legacyFormat(1 wholeGCM,2 PGVIDEO1) || U16 sourceState=1 complete ||
O(S sourceURI) || U16 origin(1..5 S2 mapping) || U16 restrictions ||
O(I64 deletedAt) || CROP current || CROP previous ||
U16 collectionCount<=128 || sorted source collections ||
O(S favouriteCollectionID) || payloadIdentity || primaryRootIdentity.
Source collections, sorted by UTF8 source ID:
S ID || S name || I64 createdAt || U16 pinned(0 none,1 legacyFavourite) ||
O(S coverItemID) || O(I64 sourceMembershipAddedAt).
Include every collection having source membership OR cover==sourceItemID;
favourite ID is recorded even when source is not its member (explicitly maps
global context, never changes it during restore). No unrelated items are copied.
payloadIdentity = U64 device || U64 inode || U64 encryptedSize || I64 mtimeNanos
|| H(full ciphertext); rootIdentity=U64 device||U64 inode. Unsupported stable
file identity forbids move/hold; names are never filesystem selectors. Source
ID must resolve one existing canonical UUID filename under fixed legacy root;
ambiguous/unsupported IDs are safely refused, not renamed/migrated.

Fingerprint=SHA256(exact projection plaintext). Source snapshot ID independently
random; receipt sourceGeneration=1. Before destination commit and hold intent,
reconstruct projection from CURRENT authenticated legacy snapshot and pinned
payload; compare exact fingerprint and physical identity. Unrelated Primary
writes are allowed; any changed source metadata/cover/favourite context denies
retirement. Limit failure leaves source ordinary. Projection keeps sourceURI
privately in Primary for rollback; it never enters Hidden metadata.

Hold payload stays payloads/<original ID>.vault; reserve that ID. All existing
Primary setup/index/delete/dedupe/import/video-migration/reconcile/backup/restore
paths must consult the authenticated catalog under shared locks before a write
can touch reserved ownership. Primary-only reads expose ordinary indexed items
only. This is enforcement to implement later, not a claim current code has it.

Selection protocol: authenticated predecessor → random synced reservation →
immutable encrypted projection/journal/catalog/descriptor → sync files/parents →
reopen/hash/authenticate → paired original-operation rename → namespace fsync →
reopen selection/authentication. Source-index removal additionally syncs its
file AND vault directory and reopens/authenticates exact new legacy index.
Backup/restore blockade is checked before any archive/restore write, while
holding Primary storage/metadata; states1..6/8 block export; terminal7/9..12 permit export only. Destructive
restore/replacement remains denied while ANY retained record (including terminal
and RELEASE_ACK) or unresolved attempt/ownership exists. Only an authenticated
empty catalog after fresh paired dependency release/pruning, an authoritative
resolved namespace inventory and closed readers/producers remove this transfer
blockade. This does not authorize replacement of an existing vault: the existing
fresh-install-only restore admission remains mandatory.

All hold states, prior/proposed bindings, restart/rollback/removal order and
dependency-release rules are R2-r1. A crash after legacy index promotion may
already have committed removal; state4 must account for it. Missing expected
payload in state8 is an intent to inspect/reverify, never proof cleanup completed.
RESTORED recognizes exact proposed item/relationship hash in current index;
unrelated later writes preserved, matching restored membership required before
ownership handoff. State12 never authorizes deleting an ordinary restored item.

## Canonical restore-merge projection M1

The proposed/restored merge hash is SHA256 of these bytes (maximum65536):
PGMRG001[8] || U16=1 || U32 itemBodyLength || itemBody || U16 collectionCount
|| collectionRows || O(S favouriteCollectionID).
itemBody is EXACT source projection bytes beginning at S sourceItemID and
ending at CROP previous, inclusive, with no projection magic/version, collection
section or physical identity. This binds MIME/name/time/plaintext/nonce/legacy
format/state/sourceURI/provenance/restrictions/deletion/crops. Complete sources
must have absent deletedAt. The restored legacy item must match every field;
this is an additive hash projection, not a change to legacy index encoding.
Rows, sorted by strict UTF8 source ID, are:
S collectionID || S name || I64 createdAt || U16 pinned(0/1) ||
O(S restoredCoverItemID) || O(I64 restoredSourceMembershipAddedAt).
Count/IDs/definitions come from the frozen source projection. The row's cover
and source-membership fields are the proposed post-restore values. Membership
set of the restored item must exactly equal these rows with present membership,
with no extra current memberships. All unrelated members/items are omitted.
Favourite ID is the current global favourite ID at RESTORE_INTENT; restoration
never changes it. Later change to this field or an affected definition/cover/
membership conflicts; unrelated other items/collections remain outside M1.

Move removes only source memberships; if source was a cover, expected post-move
cover is absent (no automatic replacement); otherwise preserve its cover.
Restore requires those expected post-move covers and original definitions before
restoring original covers/membership timestamps. Missing/conflicting collections
deny. RESTORE_INTENT syncs M1 hash and prior current encrypted-index hash before
promotion. On restart, a current item matching the entire M1 projection proves
the proposed item/relationships committed even if unrelated later writes changed
the whole index hash. A missing item permits a fresh revalidated proposal;
conflicting item/relationship or unreadable ordinary index denies and preserves
the hold; never rebuild Primary empty. Source identity/full payload verification
is still required. M1 alone is neither authentication nor deletion authority.

Mappings:N046–054,N063–079,N095;G05,10–13,30–36,46–49,63;F01–03,17–24.
DC29–35 validate projection binding, state guards and backup refusal.
