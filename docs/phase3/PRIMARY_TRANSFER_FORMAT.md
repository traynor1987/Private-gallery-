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
evidence/<purpose4>-<object32>-<gen16>; attempts/<attempt32>/{reservation,owner,files}.
The attempt reservation/owner/file grammar is S2's exact encoding under this
Primary domain (Copy/Move only, target item is destinationItemID); depth6 and
8192-entry bounds apply. Single-invocation Primary records use the frozen58-byte
F1 usage ledger under usage, including archived catalogs; no counter reset.
No links or generic path factory. Same Primary setup/storage+metadata locks.
Absent namespace means no hold only after no-follow authoritative inventory;
partial/corrupt namespace blocks transfer/destructive Primary mutations/backup,
but permits verified ordinary legacy reads where the ordinary index is intact.
Never fresh setup over partial transfer material. No generic orphan cleanup.

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
holding Primary storage/metadata; states1..6/8 block, terminal7/9..12 permit.

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
