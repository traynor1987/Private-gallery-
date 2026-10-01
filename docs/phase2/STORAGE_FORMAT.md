# Independent storage schema 1

SecondaryStore always resolves `Context.filesDir/domain-store`; it cannot select Primary or accept a container selector. The fixed capability identity is `ContainerId.SECONDARY`. Cryptographic container/master identities are independently random 16-byte values. Primary's sibling `vault` and all Primary formats are untouched. This foundation has no production media, import, export, backup or transfer API.

## Layout and selection

The root contains exactly the directories `descriptor`, `slots`, `index`, `payloads`, `previews`, `transactions`, `recovery`, `temporary`, `deleted`, and, after selection, the file `selected`. Payloads, previews and deleted are empty in this phase. Generation/attempt names and slot names are exactly 32 lowercase hex characters encoding 16 random bytes. Usage names are 64 lowercase hex characters; interrupted counter-update files are `q` followed by a 32-character attempt name. Arbitrary names, directories, links, inaccessible entries or missing file identity fail closed. Bounded inventory never traverses a link, checks direct parent/root identity, checks all nested identities before/after listing, and repeats the full inventory. Every file read is physically bounded, NOFOLLOW regular-file checked, and identity/size/time checked before/after the same immutable snapshot read. There is no exists/read-failed-as-absence path.

For attempt `a`:

| Path | Content |
| --- | --- |
| `transactions/a/reservation` | Schema u16=1, generation u64>0; synced before encryption |
| `descriptor/a/bootstrap` | Fixed untrusted public bootstrap (104 bytes) |
| `descriptor/a/descriptor` | F1 purpose 9 encrypted setup/state descriptor |
| `descriptor/a/biometric` | Optional separately versioned device extension |
| `slots/a/<slotId>` | Exact F1 PIN envelope |
| `recovery/a/<slotId>` | Exact F1 pending or confirmed portable recovery envelope |
| `index/a/index` | Independent F1 purpose 1 authenticated empty index |
| `index/a/catalog` | F1 purpose 8 canonical schema-1 active envelope catalog |
| `transactions/a/complete` | SHA256 of bootstrap + descriptor + index + catalog; completeness check, not authority |
| `transactions/usage/<keyId>` | Durable whole-record/slot key usage ledger |
| `temporary/a` | Unselected pointer bytes, staged before atomic rename |

All integer fields are big endian, fixed-width and have positive u64 ceiling `Long.MAX_VALUE`. No path/string is authenticated as a selector. Generations have independently random descriptor/index/catalog object IDs and fresh record salts/nonces. Existing slot/envelope copies are immutable bytes and never invoke encryption again. Failed attempts retain their reservation/material and are abandoned; they are never resumed as an encryption invocation. A new attempt receives new IDs and fresh salts/nonces. No garbage collection is implemented; bounded inventory eventually fails closed rather than silently losing history.

`selected` is exactly 26 bytes: ASCII `PGDOMP01`, u16 schema=1, attempt ID[16]. It is staged, file synced, directory synced, atomically renamed, root synced, then reopened and authenticated. It is untrusted and never issues authority. Retained generations remain available until the next pointer selection is durable. The last original-epoch SecondaryOperation gate covers final predecessor validation and atomic promotion; preparation/KDF/readback take place outside the authority gate. All stores targeting the same fixed normalized root share one process transaction gate. Cross-process/distributed writers and hostile rollback are not supported.

## Bootstrap and authenticated descriptor

The bootstrap is 104 bytes: ASCII `PGDOMB01`, u16 schema=1, containerId[16], masterId[16], u64 generation, u16 state (1 pending, 2 confirmed), descriptorObjectId[16], indexObjectId[16], catalogObjectId[16], u32 reserved=0. Public F1 header/context/version/length checks reject malformed records before authentication; the bootstrap still remains untrusted.

Purpose-9 descriptor plaintext is exactly 170 bytes: ASCII `PGDOMD01`, u16 schema=1, u16 state, u16 strong-auth code, u16 auto-lock code, SHA256(bootstrap)[32], SHA256(index envelope)[32], SHA256(catalog envelope)[32], u16 biometric-present, biometricSlotId[16], u64 biometricGeneration, SHA256(complete device-extension bytes)[32]. Absent biometric fields are zero. This is the authenticated setup transaction state; frozen purpose 5 media-transfer journals are never used for setup.

Strong-auth codes: 1 every time, 2 day (default), 3 three days, 4 seven days. Auto-lock codes: 1 immediate (default), 2 thirty seconds, 3 one minute, 4 five minutes. Codes are explicit constants, not persisted enum ordinals. No strong-auth timestamp is persisted.

The index plaintext is exactly u16 schema=1, u64 empty index revision=0, u16 itemCount=0. Catalog plaintext is the frozen F1 schema-1 encoding: u16=1, u64 matching header generation, u16 slotCount, entries sorted by raw slotId, each slotId[16], u64 slotGeneration, full-envelope SHA256[32], u16 type, u16 recoveryState, u16 policy. Exact reconstruction from immutable slot snapshots rejects duplicates, alternate ordering, mismatched digests/states and trailing bytes. The descriptor authenticates the exact catalog and index envelopes.

## Setup, recovery and device extension

Fresh means a missing or directly empty checked root, under the Secondary transaction lock. Any remaining material blocks new setup. The required attemptGuard validates current transient authentication admission before setup and before promotion. Creation persists and authenticates a complete pending generation but never makes it confirmed. PIN authentication may unwrap a pending master; the caller must inspect `confirmed` before creating an ordinary session. Restart resumption requires that PIN and generates a fresh pending recovery secret/generation; the prior one-time secret is never redisplayed.

Confirmation snapshots the reentered 32-byte secret, unwraps the exact pending envelope, compares its master with MessageDigest.isEqual, then creates a new confirmed envelope with fresh salt/nonce and incremented generation. Only a synced/reopened/verified confirmed selection transfers the master. Recovery authentication accepts only the selected confirmed slot. Replacement adds a pending slot alongside the existing confirmed slot; the old confirmed secret remains usable through interruption. Possession confirmation commits a new confirmed slot and removes the predecessor from the selected catalog. Old exported snapshots are not revoked (there is no export feature in this phase). Closeable PendingSetup discards plaintext/master buffers without deleting durable state.

Optional biometric extension schema=1 is u16=1, deviceSlotId[16], u64 deviceSlotGeneration>0, opaque device envelope[1..4096]. It is independent from the frozen PIN/recovery catalog and never uses F1 types 3/4. Purpose-9 descriptor binds its exact bytes, independent ID and generation. `biometricRecord()` exposes untrusted bounded bootstrap only; after device unwrap callers must use `validateBiometric(master, record)` so a stale/removed envelope cannot authorize a session. `validateAuthenticated(master)` validates current domain state for an already authenticated master but alone cannot prove the origin of a biometric unwrap: callers must retain the exact device record. Device cipher/provider/callback checks belong to Task 4.

## GCM service admission and evidence limits

Each fresh F1 whole-record/slot derived key is used for exactly one encryption. The durable reservation precedes encryption; retries abandon it and obtain fresh identity/salt/nonce. A usage ledger is created before encrypted files become selectable. It is exactly 58 bytes: u16 schema=1, u64 encryptionInvocations=1, u64 GHASH blocks, u64 total verificationQueries, SHA256(original encrypted bytes)[32]. The block charge is `10 + ceil(ciphertextBodyBytes/16) + 1`, including full padded 156-byte AAD and the length block. Purpose limits prove this single-invocation charge fits 2^32 and invocation count fits 2^20 before encryption. A missing ledger for a retained slot forbids further encryption/copy admission; it is not regenerated. Byte-for-byte immutable copy verifies the ledger's original digest.

Ledger identity hashes the actual derivation domain/salt, excluding nonce, ciphertext and mutable authenticated state: record header bytes [12,104); slot header bytes [12,70) + [72,74) + [92,124). A changed nonce/tag/body/state cannot receive a reset budget for the same derived key. Each attempted verification increments and durably syncs its ledger BEFORE GCM; service closes at 2^20 total queries, stricter than F1's failed-tag cap. Both successful and failed queries count. Counter update uses a fresh synced file and atomic replacement. Parse/context rejection without GCM is not a verification query. Missing/malformed/out-of-range usage state fails closed.

These process-local services rely on application-private durable files; hostile rollback of all private storage, external device copies and trusted monotonic hardware checkpoints are not solved. Plaintext usage counters are not a cryptographic freshness source; an attacker able to rewrite private state can roll them back. This is explicit rather than a claim of cross-device lifetime aggregation. No restore/import can reset usage because neither feature exists. Android directory-fsync/atomic-provider and OEM power-loss proof require later real-device instrumentation; JVM real-file write/sync/rename fault tests do not prove flash behavior. JVM wiping covers controllable arrays, not all VM/provider copies or flash secure erase.
