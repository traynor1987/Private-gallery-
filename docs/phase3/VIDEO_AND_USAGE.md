# Phase 3 video and usage candidate V2-r1

Design-only. Frozen F1-r2 purpose10/11 header/HKDF and chunk rules are unchanged;
this versions local accounting and admission, not cryptographic wire bytes.
Audit§12/19/25 and phase2 total-query cap remain binding.

Usage ledger U2, exactly82 bytes:
U16=2 || encryptionInvocations U64 || GHASHblocks U64 || chargedQueries U64 ||
chargeSequence U64 || H(immutable complete ciphertext) || attemptID16.
Actual-key ID=SHA256(F1 header bytes[12,104)), excluding nonce/chunk index.
Header purpose10 and purpose11 have separate actual-key IDs. All chunks of one
purpose11 domain share one ledger. ImmutableCiphertextHash is zero while writing;
once COMPLETE, pins the entire video (both ledgers), never edited later.
Fresh attempt pre-reserves the FULL planned invocation/block budget before its
first GCM encryption: header(1 invocation,11blocks), chunks count n,
sum(10+ceil(chunkLength/16)+1). Persist ledger/file+parent sync before invocation;
interrupted encryption abandons it, never resumes or refunds. Fresh salt on retry.
Bounds:invocations<=2^20,blocks<=2^32,queries<=2^20; sequence positive and each
update increments without overflow. Queries count successes AND failures before
GCM. The complete physical-size formula is172+n*172+totalPlaintextLength.

Whole schema2 media and A2 use U2 with one encryption invocation. Existing
schema1/credential58-byte ledgers remain readable; never convert a copied key to
a fresh budget. Credential/proof normal service refuses at queries>=(2^20−64),
reserving64 for restricted recovery; restricted service still closes at2^20.
A2 is not decrypted on normal unlock. A missing/malformed/detectably inconsistent
ledger denies crypto, never reconstructs counters. Private-state valid hostile
rollback is not detected: same explicit Phase2 limitation, no trusted checkpoint.

Full video verification must reserve n chunk queries and1 header query under
the shared root/ledger lock, atomically precharge each ledger's complete cost,
fsync/reopen, then run exactly those attempts. There is NO durable query
reservation record or restart lease: U2 chargedQueries and chargeSequence are
the only durable accounting authority. Under the shared lock, check both
budgets first, replace/sync/reopen header ledger, then chunk ledger; issue no
lease until BOTH exact post-charge states have reopened. A partial failure
keeps every visible charge spent and issues no lease. Same-process leases are
opaque, in-memory only: random operationID, expected key IDs, before/after
sequences, ciphertext hash and remaining per-key costs, bound to the original
operation and reader. No lease is reconstructed from sequence/ciphertext files.
One lease consumes one query before each permitted GCM attempt, never public.
Restart/closed lease loses unused allowance; no refund/resume. Other playback
requests cannot spend that lease; either precharge their own queries or deny.
If either ledger cannot fund complete sweep, reserve neither: keep source/hold,
deny move/cleanup, offer paired hold restore. At8GiB,n=8192;128 full chunk sweeps
consume2^20 before other reads. Header has its own budget. Do not claim sweep
cost is1 or reset counters to finish. Whole/image verification also precharges.

Budgets are safe upper bounds on attempts, not a claim every prepaid query ran.
Two-ledger charge failure can consume an allowance without operation success;
recovery never refunds either visible charge. It cannot cause source deletion.

Writer/decrypt ownership: source descriptor and same-object reader belong to
original operation; one1MiB plaintext chunk plus bounded cipher/64KiB transport.
Authenticate header before reads and each chunk before output. Full sweep checks
all chunk tags, exact count/size/order/no trailing, length and independent SHA256.
Producer final-GCM failure is not EOF; join producer before receipt issuance.
Unknown-length selected provider requires bounded measurement then exact second
read/hash/EOF. Each pass:20minute total/30second no-progress monotonic deadline,
byte quota and overflow checks; uninterruptible/unclosable provider denied.
No plaintext spool and no implicit Primary video re-encryption. Whole-GCM
Primary<=64MiB and provider-memory evidence still required at implementation.

Crash: charged but unused reservations remain spent; malformed update preserves
last good state or denies key service, never regenerates. Ledger updates use
fresh synced temporary and atomic replacement+parent sync+same-object reopen.
Cancellation denies further publication and closes/join resources outside
application locks. Selected video stays ciphertext; restore held Primary does
not need video queries. Rollback never decrements accounting.

LS-01 staging closure: use S2/P1's exact local usage `q<32 lowercase hex>`
shapes and physical0..82/58-byte bounds, with16 stages maximum per new usage
directory. A private current-process update binds target, original operation,
before/after counters/sequences, expected ciphertext and pinned identities.
Sync file and directory, reopen exact stage, atomically replace canonical target,
sync parent, then reopen the exact installed canonical state. Failed/incomplete
staging and uncertain replacement grant zero GCM attempts/leases. Preflight
both video budgets before either update; no lease until both installed states
reopen. Installed partial charges stay spent. Canonical ledgers alone govern
restart; leftover stages are preserved quarantine, never replayed, refunded,
promoted, selected by highest counter or used to rebuild a missing ledger.
Fresh charging from valid canonical state is permitted with bounded admissible
stages. Only the original updater may discard its own exact pinned uninstalled
stage after ending the update and excluding other readers/updaters, then sync
the parent. No restart discard or wrapper retirement of media/proof stages.
Reaching quota denies new updates without automatic GC. This preserves the
explicit hostile private-state rollback limitation.

LC-01 capacity closure: ordinary charges retain complete S2/P1 inventory and
capacity checks. ONLY restricted selected existing slot/A2 charges use A2's
frozen credential projection, E+R+N<=8192 and proof-stage<=16 checks instead of
whole-tree reservation. They prove no global count, permit no canonical creation,
repair or stage discard, and cannot fund media/video leases. Fresh complete
ordinary inventory must pass before ordinary service resumes; unavailable or
over-limit trees remain denied with leftovers preserved. All original durable
charging and no-replay/refund/restart-lease rules remain unchanged.

Mappings:N025–039,N048,N053,N060,N095;G06,19–23,46,63;F05–08.
DC13–18 reference arithmetic/preflight tests; Android/OEM validation remains
PENDING. Frozen vector file and Primary format sources remain unchanged.
