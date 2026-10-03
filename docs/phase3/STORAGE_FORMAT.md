# Phase 3 canonical storage candidate S2-r1

Design-only contract. No production writer or owner migration is enabled.
F1 wire version1, its156-byte header, HKDF, purpose IDs, slots, H2 and
PGDOMP01 remain unchanged. This document versions their plaintext bodies and
storage admission. Predecessor: phase2/STORAGE_FORMAT.md; audit§12/15/19/23/24.

## Encoding rules

All integers are big endian. U8/U16/U32 are unsigned; U64 is0..2^63−1;
generation/revision is1..2^63−1. I64 is signed timestamp milliseconds.
ID is exactly16 nonzero opaque random bytes; H is SHA256[32]. S is U32 byte
length followed by strict UTF8,0..4096 bytes; names/MIME are nonempty. No BOM,
normalization, object serialization, recursion or compression. O(T) is U8=0
(absent, no following bytes) or U8=1 followed by T. Unknown values/reserved bits,
duplicate/unsorted IDs, invalid UTF8, overflows or trailing bytes reject.
Sets sort lexicographically by raw encoded IDs, not locale/string UUID order.

C (context) = purpose U16 || objectID16 || generation U64 (26 bytes).
R (immutable reference) = C || completeCiphertextLength U64 || H (66 bytes).
Identity is implicit from the scoped container, never read from a generic path.
Reference length must equal the complete F1 envelope/file; validate known
purpose and positive generation before allocation. Zero sentinel is forbidden;
optional references use O. R authenticates bytes, not permission.

Crop = four IEEE754 binary32 values left,top,right,bottom; finite, no negative
zero,0<=left<right<=1 and0<=top<bottom<=1. CROP = O(Crop).

## Schema2 bootstrap/credential proof/descriptor

Bootstrap120 bytes: same104-byte phase2 bootstrap layout, except magic
PGDOMB02 and version2; reserved U32 at100 stays0; credentialProofObjectID16
at104. Generation and all original three IDs stay at their frozen offsets.
Selector remains26-byte PGDOMP01/version1/attemptID; one selection authority.

Credential proof A2 is purpose9 at the bootstrap's proof ID/generation:
ASCII PGAUTH02[8] || U16=2 || selectedAttemptID16 || H(bootstrap120) ||
H(encrypted catalog) || U16 state(2 confirmed only) || U16 strongCode ||
U16 autoCode || U32 catalogBodyLength || exact frozen schema1 catalog plaintext.
Catalog body<=12+32*62; it contains the active PIN/confirmed recovery and optional
pending recovery, sorted exactly as frozen F1. Its generation equals bootstrap.
The encrypted catalog hash and each envelope hash must match opened selected
credential bytes. H2 is not authority in restricted recovery; no biometric
fields are needed here. A2 must never contain media/index hashes or recovery
plaintext. Normal admission verifies its encrypted hash via descriptor, without
decrypting A2; only restricted recovery consumes A2 queries. See HOLD_RECOVERY.md.

Descriptor D2: original170-byte descriptor field layout, magic PGDOMD02,
version2, otherwise same state/policies/bootstrap/index/catalog/H2 hashes;
then R(A2) || closureCount U16 || closureCount*R. Count<=256, sorted by C,
unique; purpose only1(historical verification index),5(journal),7(receipt).
Body<=65536. Exact closure equals the union of ALL RETAINED selected I2 transfers'
historical index, current journal and receipt; no unrelated entry. Historical
I2 is evidence-only: fully parse it and verify the target transfer/item, but do
not mount its other transfers, recursively resolve their references, or treat
its self-context as another dependency. Frozen journals/receipts add only that
target's named evidence. Terminal1..4 entries retain the SAME dependencies until
R2 release step4 durably drops the entry; marking terminal never permits GC.
Shared snapshots remain until ALL retained references and
owned readers release them. This rule prevents archived-index reference cycles.
D2, selected I2, catalog and A2 share bootstrap generation, with distinct IDs.
Catalog/slots/H2 remain frozen encodings. A2's copy of the catalog must agree
byte-for-byte with selected catalog at generation preparation/readback. Normal
unlock verifies encrypted A2 hash through D2 and does not decrypt A2 again.

## Media index I2 (purpose1)

U16=2 || revision U64 (=bootstrap generation) || itemCount U16 || items ||
collectionCount U16 || collections || membershipCount U32 || memberships ||
O(favouriteCollectionID) || transferCount U16 || transfers.
Limits:512 items,128 collections,65536 memberships,64 live transfers,
body<=16MiB. Every count is checked before allocation; encoded-body limit may
reject a combination below its individual limits.

Item, sorted by itemID:
ID item || U16 format(1 whole-image/purpose2,2 chunk-video/purpose10) ||
R(payload) || U64 plaintextLength || H(plaintext) || U64 metadataRevision ||
U16 state(1 active,2 trash) || O(I64 deletedAt) || S displayName || S MIME ||
I64 importedAt || U16 origin(1 imported,2 local-edit,3 remote-AI-edit,
4 local-AI-edit,5 remote-AI-generated) || U16 restrictions(bit0 vaultOnly;
all other bits0) || CROP current || CROP previous || O(R(preview purpose3)).
No URI/URL is persisted here. Actual MIME must match bounded media admission.
Trash requires a positive deletedAt; active forbids it. MetadataRevision starts1,
increments on any entry metadata change. Preview must bind to current payload
and crop revision in its authenticated body (below), never reused after edits.
Images<=48MiB; video<=8GiB. Video chunks remain exact F1 purpose10/11 bytes.

Collection: ID || S nonemptyName || I64 createdAt || O(itemID cover).
Membership: collectionID || itemID || I64 addedAt; sorted by(colID,itemID),
unique, references existent active items/collections. Favourite references an
existent collection. Cover references active member of that collection.
No Primary IDs/names are silently imported; explicit destination mapping uses
independently generated Hidden IDs. Crop/provenance/restrictions are preserved.

Transfer: transferID || U16 action(1 copy,2 move) || U16 phase(1..6 frozen) ||
itemID || sourceContainerID || sourceSnapshotID || U64 sourceGeneration=1 ||
C(receipt purpose7/gen1) || C(historical index purpose1) || C(current journal
purpose5) || U16 terminal(0 live,1 restored,2 cleaned,3 copied,4 cancelled) ||
O(H(primary terminal record ciphertext)). All live entries are phase4..6;
precommit phase1..3 remain unselected attempt material. Terminal0 forbids hash;
terminal1..4 requires the paired terminal acknowledgement protocol. Direct
provider import uses no transfer entry/paired receipt; its verified item commit
is scoped Hidden-only and never authorizes external deletion.

Preview plaintext: U16=2 || itemID || C(payload) || metadataRevision U64 ||
U16 encoding=1 JPEG || U32 byteLength || JPEG bytes. ByteLength<=2MiB;
decoded dimensions<=512x512, checked before decode. Purpose3 context comes from
selected item reference. No EXIF/source names/URI added. Decoder resources are
session-owned; arrays wiped where controllable; no complete JVM/GPU wipe claim.

## Paths, attempts and accounting

Existing root names unchanged. Add transactions/media and transactions/proof.
Selected A2 ciphertext: transactions/proof/anchors/<selectedAttempt32>.
There is exactly one anchor per selected schema2 token, with expected purpose9,
bootstrap proofObjectID and bootstrap generation. Complete F1 size is
172+authenticated body length, at most2268 bytes. Restricted credential grammar
admits this fixed path without reading D2; normal grammar binds its R through D2.
Prepare/sync/reopen anchor and ledger before selector promotion. The predecessor
anchor remains until new selection is durable/reopened and all old proof readers
have closed. Retirement may remove only an authenticated, unselected exact
anchor and its unreferenced actual-key ledger after the cleanup barrier. A
missing/corrupt selected anchor denies restricted recovery, never rebuilds it.
Old anchors cannot authorize recovery against a new selected token.
Media files: payloads/<object32>/<generation16>; previews equivalent.
Evidence: transactions/media/evidence/<purpose4>-<object32>-<generation16>.
Media ledger: transactions/media/usage/<keyId64>; A2 proof ledger under
transactions/proof/usage/<keyId64>. Neither namespace belongs to wrapper
retirement. D2/catalog/slot ledgers stay in transactions/usage.
All I2 purpose1 keys, including current index, use media usage: copying a current
index as its historical receipt snapshot shares the SAME ledger; never reset it.

Counter staging is explicit in each media/proof usage directory: regular
`q<32 lowercase hex>` files, exclusive random creation, physical size0..82
bytes, no children. Full canonical `<keyId64>` ledgers remain exactly82 bytes.
IC-02 INITIAL_REGISTRATION_AND_RECOVERY.md explicitly admits regular0..81-byte
initial failures as counted nonauthority quarantine. Schema2 admission and A2
projection similarly tolerate unrelated0..57 credential58 entries; schema1
ordinary behavior remains unchanged. Required malformed/short keys always deny
their service; unrelated bounded shorts do not authenticate or repair anything.
Ordinary service reserves whole-tree capacity and a maximum16 stages per new
usage directory before an update; quota exhaustion preserves stages and denies
new updates. Restricted A2 selected credential/proof charging instead uses the
exact credential-only projection and capacity equation in HOLD_RECOVERY.md.
That recovery-only exception neither traverses opaque media nor proves the
whole-tree bound; it cannot fund media/video service or ordinary mutations.
Existing credential `transactions/usage/q<32hex>` keeps its frozen0..58-byte
staging shape and existing aggregate limits. Root `temporary/<32hex>` is not
a new media/proof counter path. Links, alternate names, oversize, inaccessible
or replaced staging identities reject relevant admission. Incomplete bounded
stages are structurally admitted quarantine, never usable counters.

Only the current process's private updater owns its newly created stage,
binding original operation, canonical target, exact before/after state and
pinned stage/parent/root identities. File+directory sync and exact stage reopen
precede atomic canonical replacement; parent sync and exact same-object
canonical reopen precede every permitted GCM query or encryption. Video
verification requires BOTH installed ledger charges before issuing its opaque
in-memory lease. A visible installed charge stays spent after any failure.
Restart reconstructs no updater/lease: never replay/promote a stage, choose its
counter, refund charges or repair a missing canonical ledger from it. Valid
canonical state allows fresh charging despite admissible leftover stages;
missing/malformed/inconsistent canonical state denies that key service.
Ordinary current-process discard may remove only its exact pinned uninstalled owned
stage after ending the update, with no concurrent reader/updater and parent
sync. Public names/bodies never authorize discard. Restart stages remain
quarantined; no automatic discard or new restart-discard workflow is authorized.
Wrapper retirement must preserve new media/proof stages and canonical ledgers.
Restricted A2 permits no stage discard, including its own failed stage.

P1's frozen58 fresh registration/completion has the explicit P1-local pending
hashzero and private one-shot writer protocol in PRIMARY_TRANSFER_FORMAT.md.
It requires installed canonical reservation before encryption and exact
original output sync/reopen before one-way completion. This changes neither
S1 credential creation nor S2 U82 bytes and grants no restart/query authority.

Attempt: transactions/media/attempts/<attempt32>/{reservation,owner,files}.
Reservation exactly U16=2 || attemptID || targetGeneration U64;26 bytes,
synced before crypto. Owner is purpose9: U16=2 || H(reservation) || U16 action
(1 copy,2 move,3 direct-import) || itemID || O(sourceBinding) || count U16<=16 || count*C,
sorted/unique. Owner context is purpose9/objectID=attemptID/generation1,
supplied from the checked directory; never trust its header as authority.
sourceBinding=transferID16 || sourceContainerID16 || sourceSnapshotID16 ||
U64 sourceGeneration=1. Copy/Move require it; direct-import forbids it.
Owner plus its ledger is synced before any listed data writer;
fresh independent object IDs/salts/nonces. files contains only context-derived
names. Failed encryption never resumes; new attempt gets fresh domains.
Incomplete ciphertext may be0..product maximum physical length and is invisible.
No automatic deletion based on public reservation/name alone. After restart,
unknown/malformed ownership preserves bytes and denies writes; authentic owner
can mark an attempt abandoned, but incomplete/identity-ambiguous files remain
quarantined until an explicit authenticated discard with exact pinned inventory.
Completed selected objects never belong to discard.16 abandoned-attempt quota
may deny new writes; no silent GC to recover capacity. No plaintext staging.

Strict root grammar admits schema1 or2 selected generation and known attempt
shapes only. Root depth is0; each path component increases depth by1. Whole
Ordinary checked tree<=8192 entries/depth6. A2's explicitly bounded projection
is a recovery-only exception, never cached full-tree admission. Before ordinary
service resumes, a fresh complete inventory must pass this whole-tree bound;
over-limit/unavailable/invalid trees deny ordinary admission and mutation
without automatic deletion of restricted leftovers. Attempt children are exactly reservation, owner and
directory files; files contains only <purpose4>-<object32>-<gen16> from its
authenticated manifest, never subdirectories. Anchors contain token filenames
only; usage/evidence contain their stated canonical and counter-staging shapes
only. No generic
depth6 path is admitted. Aggregate quota is checked before
reservation/promotion. NOFOLLOW handles and fixed roots bind every directory;
never interpret missing/unreadable/changed inventory as fresh/empty. Single
process writer only, no distributed rollback-freshness claim.

## Durability and transitions

Authenticate predecessor → reserve/sync → seal owner/sync → write ciphertext/
charge usage → fsync data and directories → reopen/full-auth length+digest →
create I2 with allocated receipt/journal contexts → encrypt/sync I2 and retain
identical historical envelope → receipt pins its SHA → journal pins receipt →
seal D2 closure/A2/catalog/complete generation → sync each parent → original
operation's serialized atomic selected rename → root fsync → reopen selection,
D2/I2/receipt and ALL selected payload bytes before durable verification result.
Copy default; source untouched until RECEIPT_RETENTION.md's paired hold commit.

Crash before selector: predecessor wins; stage invisible. Crash at/after rename:
inspect selector under fresh auth, sync/reopen winner, never delete it because
caller threw. Missing selected references deny ordinary writes. Credential
mutations carry I2 content forward with a new index revision/envelope but retain
original receipt snapshot/context/ledger; no obsolete slot-wrapper retention.
Rollback before commit abandons exact stage; after commit retains copies and
uses explicit current-state hold restore, never reverses old whole index.

Negative/gate mapping: N013–024,N034,N060,N095; F04–16;
G04,14–17,20,28,46–48,63. Closure-specific cases DC01–DC12 accompany the
reference validator. Reference tools are not an Android storage implementation.
