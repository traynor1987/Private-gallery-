# Paired restricted hold recovery A2-r1

Blocker4 closure contract; design only. Baseline SecondaryStore authenticates
descriptor/catalog/index before issuing any session. This proposed narrow path
does not turn a failed ordinary unlock into an empty/writable Hidden container.
Predecessors:audit§22/24,Phase2 active-slot/catalog admission,DR-07.

## Proof path and ownership

Expose only inside transient discovery/authentication route; no Primary menu,
notification, count or diagnostic advertises holds. User explicitly chooses
recover held item, independently authenticates Primary and Hidden PIN OR
confirmed portable recovery. No biometric convenience/Primary credential
fallback. Require original fresh Primary operation with HOLD_RESTORE scope.

1. Authenticate Primary catalog/projection; snapshot exact hold revision,
   expected Hidden container/master, transferID and current Primary epoch/op.
2. Under fixed Hidden root lock, pin current selector/120-byte bootstrap and
   selected credential paths. Validate known root/credential grammar and no
   links/replacement. Do not enumerate/decrypt media/payload/index or consult
   damaged full descriptor/complete-generation hash to infer credentials.
   Opaque media subtrees may be unavailable; no ordinary media/write capability
   is possible in this mode. Unknown root-level/credential material fails.
3. Compare expected cryptographic identity before any unwrap. Bound/inspect one
   selected PIN or CONFIRMED recovery envelope; charge its existing ledger,
   perform exact independent unwrap. Pending recovery/H2/old unselected slot
   never suffices. A2 and its dedicated ledger must exist and be bounded.
4. Decrypt A2 under derived independent master; verify exact bootstrap hash,
   selected token/generation,state2,policy and canonical embedded active catalog.
   Open/hash current encrypted catalog WITHOUT decrypting it; compare A2 hash.
   Hash opened selected slot bytes, require exact catalog entry ID/gen/type/
   state/policy/envelope digest; read all listed selected envelopes to verify
   exact set. This proves active selected credential, not mere successful unwrap.
   Recheck selector/identities, fsync root/reopen selected credential projection.
5. Issue in-process opaque one-shot HoldRestoreProof, scoped to tuple
   (Hidden identity,selected token,A2 ciphertextSHA,catalogSHA,slotSHA,
   transferID,holdRevision,Primary epoch/operationID,challenge32,monotonic
   deadline<=5minutes). No key/secret in proof; wipe unwrapped master/secret/
   catalog plaintext before returning. Separate narrow proof registry/gate:
   does not call completeAuthentication or mint SecondaryOperation.

Credential-only staging grammar admits regular proof-usage
`q<32 lowercase hex>` candidates of0..82 bytes (maximum16) and inherited
credential-usage `q<32hex>` candidates of0..58 bytes under existing S1 bounds.
They are nonauthority quarantine, including incomplete bounded candidates;
validate pinned identities and shape, never replay/promote/discard them.
Authenticate and freshly charge ONLY selected canonical slot/A2 ledgers, with
S2/V2's stage/installed-state sync and exact reopens. Valid canonical counters
can support fresh recovery despite leftover admissible stages; a missing,
malformed or exhausted canonical ledger cannot be repaired from stages.
Unknown names, links, oversize, inaccessible/replaced identity or stage quota
exhaustion deny affected recovery service while preserving the hold. Do not
inspect media stages, traverse opaque media subtrees, require I2 admission or
give the proof cleanup authority. Stage quota is an explicit finite recovery
availability limit alongside credential/query/evidence limits.

### Restricted capacity projection (LC-01 closure)

Restricted charging cannot prove S2's physical whole-tree count without violating
media isolation. It uses this explicit recovery-only exception, not assumed
reserved headroom or a prior inventory count. Freeze checked projection P as the
union below. Count every distinct root-relative path once, including root and
directories; names alone authenticate nothing.

- Pinned root and all direct child names, with exact known root grammar and
  no-follow type/identity checks. Opaque fixed media directories need not have
  readable descendants; never enumerate them. Root and every actually traversed
  directory must be readable/executable and pinned before/after use.
- `selected`, `descriptor/<selectedToken>/bootstrap`,
  `index/<selectedToken>/catalog`, `transactions/proof/anchors/<selectedToken>`,
  and their fixed existing ancestors. Read only these bounded selected files;
  omit ordinary descriptor/I2/complete marker and other generation files.
- `slots/<selectedToken>` and `recovery/<selectedToken>`, their ancestors and
  ALL direct selected envelope children, with canonical names/types and the
  inherited32-envelope bound. Exact active catalog membership still governs;
  no other-generation credential fallback.
- `transactions/usage` and `transactions/proof/usage`, their ancestors and ALL
  direct children. Canonical64-hex files are exactly58/82 bytes respectively;
  stages are exact q32-hex regular files of0..58/0..82 bytes. Count and physically
  bound unrelated canonical files without decrypting or changing them. No links,
  children, alternate names or changed/inaccessible checked identities.

Only these scopes are traversed. Other generations/anchors, temporary/deleted
descendants, payloads/previews and ALL `transactions/media` descendants remain
opaque. Omitted descendants are never empty/valid media, owned discard material
or evidence of full-tree success. Checked P<=8192 entries/depth6 with root depth0;
stop enumeration before allocation exceeds remaining projection capacity.
Pin/repeat this projection under the existing normalized-root lock; do not call
full-tree snapshot for restricted admission.

Under that same serialization, E is the current distinct projection entries,
R is authorized current-process stage entries reserved but not yet created,
and N is the current update's new entries. Require E+R+N<=8192 BEFORE exclusive
stage creation. Each stage adds one entry; its usage parent already exists.
Created stages count in E, not R. Additionally proofExistingQ+proofReservedQ+
proofNewQ<=16. Credential stages retain S1 names/physical bounds, bounded by P;
do not invent a new S1 per-directory cap. Each sequential slot/A2 update takes
its own fresh census/reservation; earlier installed charges remain spent if
a later update fails. Reservations never survive restart or authorize a lease.

Restricted permission is ONLY a fresh stage followed by exact atomic replacement
of an EXISTING valid selected canonical slot/A2 counter. No missing directory or
canonical creation/repair, lower counter/refund, generation/anchor/slot creation,
media repair or cleanup. No restricted discard, including a failed owned stage;
successful atomic replacement consumes its stage as charging, not cleanup.
S2/V2 original-operation fencing, sync/stage reopen/replacement/parent sync/exact
installed reopen remain required before GCM. This exception cannot perform fresh
key registration or fund a media/video lease.

Projection charging does not verify or guarantee physical full-tree<=8192.
Its transient stage may exceed that ordinary cap, and interrupted stages remain.
Before ordinary unlock/media/write/credential-generation/cleanup resumes, fresh
complete no-follow inventory must satisfy S2's whole-tree<=8192/depth6 and exact
grammar. Over-limit/unavailable/invalid state denies ordinary admission and
mutations; restricted recovery never creates SecondaryOperation or cached census
credit. Never silently delete leftovers to recover quota. Capacity exhaustion
denies affected recovery while retaining the hold; restoration still needs the
original one-shot paired proof. No new encrypted count or reservation format.

Normal admission hashes A2 via D2 but does not decrypt it. At generation creation
the writer constructs A2 from exact catalog, precharges/readbacks it and checks
its semantics before D2 selection. Thus media-index/catalog-decryption query
failure does not consume A2's dedicated service. Existing envelope58-byte ledger
is retained; normal unlock reserves its final64 queries for restricted recovery;
no reset or unaccounted GCM. Exhausted PIN may use intact confirmed recovery.
If ALL relevant slots/A2/ledgers are exhausted/missing/corrupt or selected
credential evidence lost, fail explicitly preserving held ciphertext. This
finite-service/evidence limit is not a promise of infinite attacks or recovery
without credentials. No fresh key, older-slot scan or master rescue bypass.

## Restore authorization, transitions and crash

Proof admits ONLY Primary hold restoration from state5→6→7. It cannot read
Hidden media, create/delete/migrate records, change slots/policy, clear ledgers,
clean a hold, issue a destination receipt or release Hidden dependencies.
Primary adapter verifies held source full tags/length/digest plus immutable
projection/root/payload identity and conflict-free current metadata merge.

Before selecting RESTORE_INTENT and before source-index promotion, acquire
Primary storage→metadata→Hidden root→short proof/controller→Primary authority→
proof authority gates in LOCK_ORDER.md order. Re-read selected credential hashes
and hold revision. Original Primary lease and proof must be current; verify
fresh one-shot challenge. A valid proof reserves one restoration transaction;
it cannot concurrently restore another hold or be retargeted. Ordinary index
promotion consumes it; durable state6 ownership permits restart ONLY through
new paired authentication, never persisted authority.

File/directory sync and reopened authenticated legacy index with exact restored
item/relationships precede Primary state7 and successful restoration. No held
payload unlink. Before index promotion cancellation keeps active hold; after
promotion exception/restart inspects current authenticated index and intent,
finishes exact ownership handoff or reports conflict. No old whole-index swap.
No successful claim until reopens; no plaintext file. Registration/process
death/revocation invalidates proof. Proof deadline checked at commit independent
of timer. Credential rotation or pointer/A2/catalog/slot replacement invalidates.

Hidden dependency release remains deferred to normal authenticated Hidden
service after repair. Primary terminal7 permits ordinary verified item reads and
legacy backup again; it does not discard Hidden evidence. Trash/missing video or
chunk budget exhaustion never requires destination authentication to restore the
valid held source; cleanup remains impossible until full destination verifies.

Failure closure: incorrect secret, stale proof, foreign hold, pending/unlisted
slot, missing catalog/anchor/ledger, selected projection corruption or unsafe
credential path produces neutral unavailability, retains all ciphertext and
does not create a general session. Adequate credential evidence is a recovery
precondition; full hostile selected-state rollback remains outside freshness.
Ordinary Primary still operates on its intact legacy index even when Hidden
media unavailable. No privilege broadening from backup or retirement HMAC.

Mappings:N001–012,N068,N072,N095;G08–09,33,48–49,63;F18–24;
DC41–50 specify credential projection, narrow proof and restart consumption.
