# Phase 3 media and verified transfer — design candidate

**PHASE 3 RESULT: NO-GO**

Status: review candidate, not a shipped storage format or passing implementation.
Baseline: `traynor1987/Private-gallery-`, main
`93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10`, inspected 2026-10-02.
Implementation is conditional on this design having no unresolved Critical or
Important findings. Automated, exact signed-build and owner physical acceptance
remain mandatory before Phase 3 can close. Phase 2's owner PASS does not accept
Phase 3.

## 1. Authority, recovered specification and scope

The current owner instruction is authoritative. No standalone Phase 3
specification was found in the inspected repository. The recovered original
`PRIVATE_GALLERY_2_0_ARCHITECTURE_AUDIT_2026-09-29.md` (715 lines, 136582 bytes;
project copy in `/Private Gallery`) supplies sections 10–12, 15–16, 19, 23–27.
Its section 26 originally put independent media persistence in Phase 3 and
verified transfers in Phase 4. The current instruction brings those transfer
requirements into this Phase 3. It does not bring later Browser, camera,
network or optional Primary bulk-format migration work into scope.

Existing frozen contracts are `docs/phase0/FUTURE_CRYPTO_FORMAT.md`,
`docs/phase0/SECURITY_CONTRACT.md`, the Phase 1 compatibility requirements,
`docs/phase2/STORAGE_FORMAT.md`, `docs/phase2/BIOMETRIC_EXTENSION.md`, and the
Phase 2 42-case matrix and 50 gates. This document extends storage admission
explicitly; it does not silently change F1 headers, purposes, HKDF inputs,
slots, H2 biometric bytes or any legacy Primary encrypted format.

Source facts at the baseline:

| Evidence | Verified state |
| --- | --- |
| PR #58 | Merged; merge commit is the inspected main SHA |
| Accepted Phase 2 source | `e78bccb1e3ac9e17a7c8dfa0a84456c512b0bf5f` |
| Signed Device Acceptance #54 | SUCCESS; run `36940495096` |
| Post-merge Android #478 | SUCCESS; run `36943586835`, exact main SHA |
| Phase 2 report / 42 negatives / 50 gates | Final; GO FOR PHASE 3 REVIEW |
| Owner evidence | Aggregate PASS, “Green and it works.” No invented per-case observations |
| Checkout | Fresh clone, clean main before creating a Phase 3 branch |
| Old Phase 2 branch | Merged historical branch; no unmerged Phase 2 work reused |

### Authorized behavior

| Topic | Phase 3 contract |
| --- | --- |
| New media | Selected images/videos may be copied directly into Hidden after independent Hidden authentication |
| Existing media | Explicit Primary → Hidden copy or move, with both independent operations |
| Default | Copy; keep the Primary/external source |
| Move | Owner-selected **Recovery hold**: hide the item from ordinary Primary views only after verified Hidden commit; retain its unchanged encrypted Primary payload and complete rollback metadata |
| Permanent removal | Separate explicit cleanup of a recovery hold, requiring both fresh authentications and new full verification of the selected Hidden copy; never automatic or age-based |
| External source deletion | Not offered. Direct import deliberately leaves the selected provider/device original in place |
| Rollback | Explicit restore of the held Primary item, merging into current Primary state with conflicts denied; never restore an entire old index over new writes |
| Duplicates | A new request creates a distinct Hidden item, even if its digest matches. A retry of the same transfer joins its exact ID/revision and never duplicates its destination |
| Metadata | Preserve original bytes, sanitized display name, actual MIME, import/capture time where known, crop/previous crop, provenance and restrictions. All app metadata remains encrypted |
| Relationships | Hidden-local favourites, collections and trash. Never reuse Primary collection IDs or silently copy collection names. Explicit mapping to a chosen Hidden collection/favourite is allowed |
| Previews | Hidden-only encrypted previews and scoped in-memory decoded resources; no plaintext thumbnail file or Primary cache |
| Video | F1 purpose 10/11 authenticated chunks, full-object verification for commit/removal, authenticated random access for playback |
| Editing | Local non-destructive crop metadata only; preserve original payload. No new remote AI/editor/export integration |
| Interruption | Cancel on lock/background/screen-off/exit; restart locked, recover only after fresh required authentication |
| Low storage / power loss | Preserve originals and prior authoritative selection; no success from rename alone; fail closed when directory sync cannot be established |
| Key domains | Separate adapters and keys throughout. Only bounded plaintext transport and opaque transaction evidence cross the bridge |

Production UI continues to say “Private space”, not the internal project name.
Ordinary Primary UI must not advertise Hidden, including a new menu item,
count, notification, error, backup hint or search result. Transfer source
selection occurs only inside the authenticated private-space route. Primary
locking revokes/clears that route; Hidden exit does not unnecessarily revoke
valid Primary authority.

## 2. Threat model additions

Preserve the original audit A–W and Phase 2 threat coverage. Add malicious or
changing content providers, source metadata races, paired-operation ABA,
physical root/file replacement, forged/replayed receipts, stale selected index
generations, duplicate callbacks, partial video authentication, abandoned
staging, recovery-hold orphaning, backup omission, and playback/native-resource
survival after revocation.

| Attacker / fault | Required boundary |
| --- | --- |
| Has Primary PIN/key/biometric/recovery | Cannot unlock/decrypt/select Hidden; cannot restore/clean a hold through Primary alone |
| Has Hidden credentials only | Cannot enumerate/decrypt Primary source or hold; cannot authorize source removal |
| Casual unlocked Primary user | No Hidden advertisement, media, caches, relationships or hold details |
| Files-only tamper | Authenticated context/hash/ownership checks; missing/corrupt material is unavailable, never fresh/empty |
| Source changes while copying | Complete source fingerprint and pinned physical identity rechecked before selection and move |
| Locks/re-unlocks during callbacks | Old operation remains dead; no ambient fresh operation substitution |
| Provider sends early EOF, extra bytes or different second read | No selection or source mutation; no plaintext spool |
| Video has a bad unread chunk | Random playback is insufficient evidence; receipt walks every chunk |
| Crash at any transaction boundary | At least one valid ordinary/held source remains unless an independently verified committed destination already exists |
| Storage becomes full or sync fails | No source mutation; a visible but unconfirmed destination is reconciled from authenticated disk, never guessed away |
| Backup/restore while a hold exists | Fail closed before publication/replacement so the hold cannot silently disappear |
| Process/native/GPU memory | Own/cancel/close/wipe controllable resources; do not claim complete runtime zeroization |

Not claimed: rooted/app-UID compromise resistance, forensic deniability,
physical flash erasure, arbitrary OEM power-loss proof, cross-device monotonic
freshness, recovery after loss of all secrets/ciphertexts, or revocation of old
external authenticated snapshots. File counts/sizes and feature code may reveal
the feature. Logical deletion is honest; F1 derived keys do not provide per-item
cryptographic erasure while a usable master survives.

## 3. Architecture and storage extension

### Single Hidden authority

Use the existing `PGDOMP01` selected generation, not a second independent media
pointer. Introduce **independent storage schema 2** in bootstrap/descriptor and
**media index body schema 2**, with schema-1 empty foundations still readable.
The first media commit upgrades only an authenticated confirmed empty schema-1
selection. Every later PIN/settings/recovery/biometric mutation preserves the
authenticated media index and all reachable receipt/journal references.

`SecondaryStore.prepare()` currently writes an empty index; `verify()` requires
that exact empty body; inventory rejects media; credential retirement removes
non-credential usage ledgers. Those paths must change together, with regression
tests. Merely allowing files or adding a separate media store is unsafe.

Retain fixed `filesDir/domain-store`, immutable media generations, no-follow
parent/root/file handles, shared root transaction lock and bounded repeated
inventory. Separate versioned media usage/attempt ownership from credential
usage, so credential retirement cannot delete it. Cleanup must distinguish
wrapper retirement from authenticated media reachability.

The schema-2 selected descriptor binds the complete encrypted media index,
slot catalog, bootstrap, optional H2 envelope and exact encrypted receipt/
journal references needed by that selection. Media entries bind opaque object
ID/generation/purpose, plaintext length/SHA256, full ciphertext length/SHA256,
metadata revision, optional preview generation/hash and active/trash state.
Objects are selected by authenticated membership, not naked filenames.

**Hash dependency rule:** never put a receipt's encrypted hash in the index
whose encrypted hash that receipt authenticates. Allocate receipt/journal IDs
first; the index records their exact immutable contexts. Encrypt index; create
the receipt binding that index's encrypted hash; create journals binding the
receipt; seal exact index/receipt/journal encrypted hashes in descriptor schema
2; select that generation last. No self-referential hash or second selector.
The canonical schema-2 bytes and exact retained-evidence closure are frozen in
[STORAGE_FORMAT.md](../../phase3/STORAGE_FORMAT.md). Historical indexes are
evidence-only, never recursively mounted. Terminal-marked transfers retain
all dependencies until paired durable release.

Product limits for this review candidate: 512 Hidden items, 128 Hidden
collections, 64 outstanding transfers/holds, 16 concurrent abandoned attempts,
48 MiB whole images, 8 GiB chunked video, and 64 MiB legacy whole-GCM Primary
source input. Encoded index <=16 MiB, each journal/receipt <=64 KiB, UTF-8 string
<=4096 bytes. Ordinary entire checked tree remains <=8192 entries/depth six (root depth0; only enumerated fixed shapes). A2 restricted recovery uses its exact credential-only projection/capacity exception and proves no whole-tree count; subsequent ordinary admission requires a fresh complete passing inventory. Reject ordinary writes
before exceeding the aggregate inventory quota, even when individual quotas
fit. Limit expansion requires measured memory/inventory evidence.

### F1 video and usage

Implement a separate `F1Video` parser/reader/writer; do not route purpose 10/11
through the whole-record parser. Keep frozen 156-byte headers, full-header AAD,
HKDF inputs, 1 MiB chunks, exact count/length/order and per-chunk authentication.
Header and chunk purpose keys are distinct. Full verification computes total
length and SHA256 of all authenticated plaintext and complete ciphertext.

Persist an attempt before encryption; interrupted encryption is abandoned and
retried with fresh salt/nonces. All chunks share their actual purpose-11 derived
key: account aggregate invocations and GHASH blocks, not a per-chunk key ledger.
Pre-charge each invocation and verification durably; keep the Phase 2 stricter
2^20 total-query limit. Enforce both <=2^20 encryptions and <=2^32 GHASH blocks,
charging ceil(AAD/16)+ceil(ciphertext/16)+1. No reset by missing/replaced/restored
ledger. Replay of an otherwise valid older private-storage ledger is outside the
freshness guarantee, as in Phase 2; canonical bytes/authentication cannot detect
arbitrary hostile rollback without a trusted checkpoint. Missing, malformed or
detectably inconsistent state must never regenerate a budget. The U2 schema and sequential durable precharge/no-restart-lease protocol are
frozen in [VIDEO_AND_USAGE.md](../../phase3/VIDEO_AND_USAGE.md).

Selected provider input must be reopened: first bounded streaming pass obtains
verified length/digest; second pass encrypts and must match exactly, with final
EOF. An initially unknown length is allowed only when a bounded first pass
establishes it and a reopenable second pass matches exact length/digest/EOF.
Non-reopenable or changing sources are rejected. Each pass stops at the product
byte quota, a 20-minute monotonic total deadline or a 30-second no-progress
deadline; integer overflow, persistent zero reads and unavailable cancellation
of an owned source descriptor deny admission. No plaintext disk spool. These
are required implementation behaviors, not measured provider guarantees.
GCM providers can internally buffer legacy whole objects;
do not claim bounded provider memory without actual supported-device evidence.
Oversize legacy whole objects are safely refused without Primary re-encryption.

### Primary transfer boundary and recovery hold

Add a fixed Primary-only source adapter. Do not use Browser export, whole-array
view/editor reads, `MoveToVaultUseCase`'s Boolean, or `openVideoSession` (which can
migrate legacy video). Stream legacy whole-GCM with its unchanged item-ID AAD,
or PGVIDEO1 through the same pinned opened object. Check final authentication,
length and independently calculated plaintext SHA256 before accepting producer
completion. Never mistake producer failure for normal pipe EOF.

A source fingerprint includes complete item metadata, ciphertext format/hash,
nonce, digest/length, crop/previous crop, provenance/restrictions, state,
memberships/favourite/covers and pinned physical identities. Existing handles
use only plaintext digest and are insufficient. Freeze a new random source
snapshot ID/generation=1 and bind it to that full fingerprint in the Primary
encrypted transaction catalog. The frozen receipt uses that snapshot identity;
it must not pretend legacy item IDs have an existing numeric revision scheme.

The Primary transfer namespace is an explicitly versioned addition under the
fixed `vault` root, authenticated only with Primary-derived transfer-purpose
keys. Its random cryptographic namespace identity and canonical authenticated
catalog bind source snapshots, unchanged payload ownership, journals and holds.
Primary key-slot, index v1–v6, payload/PGVIDEO1, preview and backup-v1 bytes remain
unchanged. The canonical namespace/bootstrap/selection/usage and restore-merge hash are
frozen in [PRIMARY_TRANSFER_FORMAT.md](../../phase3/PRIMARY_TRANSFER_FORMAT.md). Hidden never receives a Primary key;
Primary never receives a Hidden key or a Hidden decryption service.

Move retains the unchanged original `payloads/<id>.vault`, removes that item and
its ordinary relationships only after an authenticated hold intent is durable,
and durably marks the hold active. Do not use ordinary Trash, `.deleting` or
`.legacy`. Retain complete original metadata, membership timestamps and cover
context. Remove stale active covers explicitly. An intent must explain an
absent normal item on restart before ordinary enumeration/mutations proceed.
Missing/corrupt/ambiguous ownership blocks destructive recovery and preserves
ciphertext. Hold IDs reserve the old Primary item ID against new writes.

Backup-v1 cannot include unindexed holds. This phase will **block Primary backup
export AND destructive restore/replacement while source ownership is unresolved; destructive restore/replacement also
remains blocked for terminal/ACK records until fresh paired pruning and
authenticated empty catalog/resolved inventory**, before writes or export publication. Inside the authenticated
private-space transfer view, offer rollback/cleanup to resolve it. Ordinary
Primary reports a neutral “Operation unavailable” without revealing Hidden or
hold names/counts. No silent hold omission, archive-v1 change or automatic
cleanup. Android platform backup continues to exclude all app vault material.

Restore merges one held item into the current index. Changed/reused IDs,
deleted/renamed collections or edited covers are explicit conflicts; never
overwrite later state. Cleanup requires both fresh independent operations and
a newly fully verified durable Hidden destination. A trashed, removed,
superseded or corrupt destination cannot authorize cleanup. Hold removal and
its catalog selection are recoverable/idempotent; no age-based expiration.

### Authority, locks and resources

Concrete Primary and Secondary operations remain separate; IDs and persistent
journals are not capabilities. `checkValid()`/scope checks, not `isCurrent` or
`publish` after lease close, authorize each read/write. Handles bind original
epoch, container, cryptographic identity, item/object generation and complete
selected revision; reject foreign/stale handles before lookup.

The full resource/controller/cache/storage/authority order is normative in
[LOCK_ORDER.md](../../phase3/LOCK_ORDER.md). Its paired commit subset is Primary
storage → Primary metadata → Hidden root → Hidden controller → Primary authority
→ Hidden authority. Reader/player and cache paths use their listed subsets;
never reverse-acquire. Revoke/detach/enqueue under gates; all close/join/cache
callbacks run after releasing outer locks. Fresh auth waits for acknowledged
cleanup, never a timeout treated as completion. Large crypto/readback/sync runs
outside authority gates. Final mutations use original operations and pinned
physical/selected identity; no Boolean/DTO can mint a VerifiedDestination.

The bridge carries bounded plaintext buffers and an internal verification
result, not raw keys or generic root/container selectors. Own source streams,
readers, buffers, jobs, preview bitmaps, Media3 player/native retrievers and
pending results through their original operation/session. Revoke admission
first, cancel producers, close transports/readers, join jobs, wipe controllable
buffers and clear UI. No hidden foreground service or plaintext file-backed
player/cache. Hidden-only DataSource must deny and close after revocation.

## 4. Transfer state machine

Frozen purpose-5 state/action bytes remain unchanged. `SOURCE_DELETE_PENDING`
means **logical source retirement into recovery hold** here, not ciphertext
unlink. Extra hold lifecycle belongs to the separately versioned Primary
catalog; do not add enum values to the frozen journal.

| State | Durable evidence and next permitted action | Source invariant |
| --- | --- | --- |
| PREPARED | Both scoped intents, random transfer/snapshot/destination IDs, action and metadata policy; attempt reservation | Ordinary source unchanged |
| COPYING | Invisible encrypted stage; stream and authenticate source; cancellation owns both ends | Ordinary source unchanged |
| DESTINATION_VERIFIED | All destination bytes reopened/authenticated, length/digest equal; synced data/directories; exact encrypted receipt prepared | Ordinary source unchanged |
| DESTINATION_COMMITTED | Selected index+descriptor atomically binds destination, receipt and journal; sync/reopen authoritative selection, receipt and full selected object | Ordinary source unchanged; Copy may complete |
| SOURCE_DELETE_PENDING | Both original operations valid; source fingerprint matches; durable Primary hold intent owns original ciphertext | Ordinary source remains or recoverable held copy exists |
| COMPLETE | Copy leaves source, or Move durably removes ordinary item/relationships and marks hold active; journals agree or explain safe asymmetry | Move retains encrypted recoverable original until separate explicit cleanup |

Cancellation/lock/expiry before source retirement keeps source. A failure after
selection may have committed data: inspect authoritative state, never assume an
exception undid promotion. A late source change means destination may remain as
a Copy, but the source must not be moved. Report that outcome explicitly.

Idempotency is transfer-ID + exact source snapshot/fingerprint + destination
context. Same ID with differing policy/source is corruption/conflict, not retry.
Digest similarity never authorizes deleting a distinct source.

## 5. Crash, interruption and recovery

Restart has no operations or saved unlocked route. Persist no session key,
recovery secret, URI grant or reusable authority. Reconciliation is authenticated
and scoped; no filesystem-wide search or resume using an old epoch.

| Interruption boundary | Authenticated restart behavior |
| --- | --- |
| Before paired intents | Source unchanged; no transfer |
| One intent only | Keep source; authenticate both domains to join exact ID or cancel owned invisible stage |
| During source decrypt/encrypt | Stage invisible; producer failure cannot issue receipt; abandon encryption attempt and use fresh salt on retry |
| After data sync, before verified receipt | Reopen all bytes or discard exact owned stage; source unchanged |
| Receipt/index written, before selection | Prior selected state authoritative; unselected files are not evidence for source removal |
| Selection rename visible but sync/reopen failed | Do not move source; after fresh auth inspect and sync selected winner, fully reverify or preserve unavailable state |
| Hidden selected, Primary journal behind | Reauthenticate both; join exact receipt/context, independently reverify; issue new current-operation result |
| Hold intent durable, source still indexed | Source remains ordinary; exact revision checks permit finish or cancel intent without deleting payload |
| Source index changed, hold intent not active | Journal owns original ciphertext; reopen ordinary index and hold metadata, mark active or explicit recovery; never GC original |
| Hold active, final journal update missing | Idempotently complete from authenticated ownership and destination selection; no duplicate destination |
| Restore interrupted | Before source-index selection, keep hold; after authenticated restored item selected, reconcile ownership without erasing its payload |
| Cleanup interrupted | If payload gone but catalog old, authenticated cleanup intent explains missing payload; finish only exact intended hold; never touch unrelated IDs |
| Root/file replaced, missing journal/ledger or corrupt selected references | Unavailable, preserve bytes, no fresh index, no destructive cleanup |

Physical power-loss testing must use expendable synthetic fixtures, including
reboot/process-kill/low-storage at each write/sync/rename boundary. Generic Java
rename or JVM fault injection alone is not OEM flash proof. If durable directory
sync is unsupported/fails, copy/import may not claim durable success and Move
cannot retire the source. Same-signer forward recovery must read new selections
and holds; installing an old APK/downgrading or clearing data is not rollback.

Restricted hold restoration when ordinary Hidden admission is blocked by media
index corruption/selected-metadata query exhaustion is frozen in
[HOLD_RECOVERY.md](../../phase3/HOLD_RECOVERY.md): active independent PIN or
confirmed recovery authenticates fixed A2 credential evidence without media
admission; a one-shot opaque proof permits only verified Primary hold restore.
Both independent authentications remain required; no Primary-only fallback or
general media/write/delete capability. Missing/exhausted credential evidence
fails closed, retaining the hold; no infinite availability claim.
Video-chunk budget exhaustion alone blocks full destination verification/cleanup,
not necessarily normal credential admission; preserve the hold and use its
paired-auth restoration path instead of resetting a ledger.

### Picker lifecycle

Immediately before launching the system picker, synchronously revoke Hidden
and clear its presentation regardless of auto-lock grace. An in-memory request
nonce may retain at most 16 opaque selected URI references for 10 minutes; no
names/media/keys, saved state, persistent URI permission, logging or automatic
import. A late/duplicate/foreign result cannot replace a pending request. Return
to the ordinary route; rediscovers and independently unlocks, then explicitly
confirms the pending selection under a new Hidden operation. Process death,
expiry, cancellation and replacement drop/release the ticket. Grants and
streams fail closed if unavailable. No picker return silently revives authority.

## 6. Negative matrix and exit gates

The complete proposed matrix is `docs/phase3/NEGATIVE_TEST_MATRIX.md`; the gate
ledger is `docs/phase3/EXIT_GATES.md`. These are pending requirements, not test
evidence. Keep all Phase 0/1 suites and the original Phase 2 42-case/50-gate
regressions. Every transfer durability boundary has restart tests, not just
event-order mocks. Actual Android provider, filesystem, video and lifecycle
instrumentation remains necessary. Candidate acceptance must identify exact
source SHA, both CI events, signed run/build, downloaded APK SHA256/package/
version/signer and owner evidence.

## 7. Explicit exclusions

- Primary bulk re-encryption, implicit legacy video migration, changes to legacy
  Primary ciphertext/AAD/key slots/index or backup-v1 encodings.
- Hidden → Primary transfer, public export/share, MediaStore/device-original
  deletion, automatic move, age-based hold cleanup and duplicate-based deletion.
- Browser, WebView profiles/downloads/uploads, camera, Social Hub, Tor, VPN,
  VPS/cloud, remote AI, network synchronization and unrelated UI/features.
- New random per-item key hierarchy, claimed per-item crypto-erasure, plaintext
  temporary/preview/video files or reuse of Primary cache/readers.
- Hidden portable backup/restore in this review scope; recovery credentials
  unlock extant authenticated ciphertext, not absent media. No claim that
  Primary backup contains Hidden or its recovery holds.
- Public Release, merge on green CI alone, owner-only-copy fault testing,
  uninstall, data clear, forced downgrade or destructive owner migration.

## Implementation admission

The six normative closure contracts are STORAGE_FORMAT (S2), VIDEO_AND_USAGE
(V2), PRIMARY_TRANSFER_FORMAT (P1), RECEIPT_RETENTION (R2), HOLD_RECOVERY (A2)
and LOCK_ORDER (L2), all in docs/phase3. They supersede the earlier field-level
admission gaps. The final independent disposition is in DESIGN_REVIEW.md;
reference constraints are not full production parsers or durability evidence.
This milestone ends at design closure. Do not begin production implementation,
owner-data transfer/migration, signed builds or later phases in this session.
All 63 product exit gates and signed/physical acceptance remain independently
required. Copy is default; Move retains unchanged encrypted Primary ciphertext.
That held copy remains cryptographically Primary-decryptable to a holder of its
key and bytes; concealment is enforced by the authenticated application flow,
not a new cryptographic erase/isolation promise for the original source copy.

**PHASE 3 RESULT: NO-GO**
