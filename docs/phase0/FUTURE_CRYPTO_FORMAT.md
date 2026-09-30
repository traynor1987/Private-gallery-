# Frozen future crypto format candidate F1

**PROPOSED 2.0 DESIGN — REVIEW ONLY.** Frozen on 2026-09-29 for independent design review against audit sections 12, 15, 19, 21–25. F1 is not a shipped format, security proof or permission to write production ciphertext. There is no production F1 parser, key derivation, Hidden master, migration or key slot in Phase 0. Any substantive change requires a new candidate revision and regenerated vectors. Existing Primary VDEK, AES-GCM payload/index/preview/PGVIDEO1 bytes and authenticated contexts remain unchanged. F1 keys must never be substituted into legacy readers/writers.

## Primitive and identity rules

AES-256-GCM with a 12-byte nonce and a 16-byte tag is the sole F1 AEAD (algorithm ID 1). HKDF-SHA256 is RFC 5869 extract then expand; it separates purposes and does not add secret entropy. Hidden, if later authorized, receives an independently CSPRNG-generated 32-byte master, unrelated to Primary or either recovery secret. Each master has an independently random 16-byte master ID. ContainerId is a random opaque 16-byte namespace, never an enum ordinal or the plaintext label Primary/Hidden. ObjectId is an independent random 16-byte immutable identity; generation is a monotonically increasing positive unsigned 64-bit revision within that identity. IDs select expected context, never grant authority. No global lookup or try-other-container fallback.

All integers below are unsigned big endian. Bytes[n] means exactly n octets. Concatenation preserves the field order in its table. No JSON, platform serializer, UUID text, enum ordinal, variable integer, native-endian number, omitted default, padding or terminator appears in canonical encodings. The supported integer ceiling is 2^63−1 for every u64, allowing checked arithmetic on JVM Long. A future implementation must reject larger encodings before allocation.

Purpose IDs are frozen: 1 index; 2 media whole; 3 preview; 4 browser record; 5 operation journal; 6 backup manifest; 7 destination receipt; 8 key-slot catalog; 9 container descriptor; 10 video header; 11 video chunk. Purpose 4 covers app-owned browser records only, not WebView engine storage. Metadata names/MIME/prompts belong inside authenticated ciphertext; opaque IDs and lengths still reveal structure to file access attackers.

## Record header and authenticated context

Every F1 record is `H || C || T` where H is the 156-byte canonical header, C is exactly plaintextLength ciphertext bytes, and T is exactly 16 bytes. CiphertextLength counts C and T. GCM AAD is **the exact 156 bytes of H**, including magic, version, algorithm, key salt, nonce and every length/chunk field. The parser must compare expected container/master/object/generation/purpose supplied by a scoped authenticated caller, not merely trust header context.

| Offset | Field | Encoding / constraint |
| --- | --- | --- |
| 0 | magic | bytes[8] ASCII `PGFUTR01` |
| 8 | formatVersion | u16 = 1 |
| 10 | headerLength | u16 = 156 |
| 12 | algorithm | u16 = 1 (AES-256-GCM) |
| 14 | purpose | u16 from registry |
| 16 | containerId | bytes[16] |
| 32 | masterId | bytes[16] |
| 48 | objectId | bytes[16] |
| 64 | generation | u64 in 1..2^63−1 |
| 72 | keySalt | bytes[32], independently random for each new immutable object generation |
| 104 | nonce | bytes[12], fresh random for this AEAD invocation |
| 116 | plaintextLength | u64, current record body length |
| 124 | ciphertextLength | u64 = plaintextLength + 16, checked addition |
| 132 | totalPlaintextLength | u64, complete logical object's plaintext length |
| 140 | chunkIndex | u32 |
| 144 | chunkCount | u32 |
| 148 | chunkSize | u32 |
| 152 | chunkLength | u32 |

Whole records: chunkIndex/chunkCount/chunkSize/chunkLength are all zero; totalPlaintextLength = plaintextLength. Maximum whole body is 64 MiB (index/catalog/journal/receipt smaller limits below). Every rewrite creates a new immutable generation and fresh keySalt and nonce; never retry encryption by reusing an invocation's nonce. Byte-for-byte copy of an already authenticated immutable ciphertext is not a new encryption.

Video is one purpose-10 header record (plaintextLength=0, ciphertextLength=16, chunkIndex=0xffffffff, chunkLength=0), followed by exactly chunkCount purpose-11 records in ascending index order 0..chunkCount−1. All records agree on container/master/object/generation/keySalt/totalPlaintextLength/chunkCount/chunkSize. Header has its own nonce and purpose-derived key. chunkSize = 1,048,576; totalPlaintextLength <= 64 GiB; chunkCount = ceil(totalPlaintextLength/chunkSize), including zero for an empty video; each chunk length is min(chunkSize, remaining length), plaintextLength = chunkLength, ciphertextLength = chunkLength + 16. No zero final chunk, duplicate, reorder, trailing bytes or omitted chunk is accepted. Each chunk's full H authenticates its own nonce, length/index and whole file generation/context. Validate header tag before servicing reads; validate each selected chunk tag before returning that chunk's plaintext. Random access authenticates only the chunks read; whole-object validation/durable receipts require every chunk. No unverified output is published or used for a source deletion decision.

## Standard key derivation and random nonce budget

For each record purpose, PRK = HKDF-Extract(SHA256, salt=keySalt, IKM=that container's 32-byte master). K = HKDF-Expand(PRK, info=I, L=32), where I is canonical:

`u16(labelLength) || ASCII("private-gallery:future:key:v1") || u16(formatVersion=1) || bytes[16](containerId) || bytes[16](masterId) || u16(purpose) || bytes[16](objectId) || u64(generation) || u16(algorithm=1)`.

labelLength is 29; I length is 93 bytes. Nonce, lengths and chunk index do not enter I; they enter authenticated H. Chunks for one generation share the chunk-purpose K; other purposes, objects, generations, masters, salts and containers derive different domains. A new random keySalt on every writing attempt also separates fork/retry encryption domains, even if an uncommitted generation number is reused. Never derive Hidden from Primary, derive content keys from a PIN directly, XOR a password with a Keystore key, or invent a new MAC/KDF.

Use a platform cryptographic random generator for all random fields; fail closed if generation fails. Random 96-bit GCM nonces have a finite collision risk. Frozen conservative limits per actual AES key, across all devices/copies: at most 2^20 encryption invocations AND at most 2^32 total 16-byte plaintext blocks, whichever comes first; tag verification failures capped at 2^20 before closing that key's service and demanding operator action. The invocation bound gives a birthday collision upper bound below 2^−57 for independent uniform nonces. This is a design calculation, not RNG evidence. A 64 GiB video's 65,536 one-MiB chunks reaches the plaintext-block ceiling exactly; smaller content/body limits apply to all other keys. Source review must separately evaluate GCM forgery bounds including AAD and lifetime aggregation; these limits are not a universal 256-bit security claim.

At most one object generation may use a keySalt+domain tuple for encryption. Persist immutable generation/attempt identity before writing; crashes abandon unfinished encryption and start with new keySalt/nonces. Restore imports existing immutable bytes without encrypting; any later modification uses fresh domain and generation. No invocation counters are reset by restoring a backup. A future device-layer key also has a durable shared usage ledger; missing/rolled-back usage state forbids further encryption until a fresh device key is established through an authenticated slot transaction. This budget and fork handling require implementation/fault evidence before F1 admission.

## Versioned key slots and recovery state

Key slots have a separate envelope format, `S || encryptedMaster || tag`, or the layered form below. S is a 156-byte canonical header, authenticated in full as AAD for every wrapping layer.

| Offset | Slot field | Encoding / constraint |
| --- | --- | --- |
| 0 | magic | bytes[8], ASCII `PGSLOT01` |
| 8 | envelopeVersion | u16 = 1 |
| 10 | headerLength | u16 = 156 |
| 12 | containerId | bytes[16] |
| 28 | masterId | bytes[16] |
| 44 | slotId | bytes[16], fresh random for each replacement |
| 60 | slotGeneration | u64 positive |
| 68 | slotType | u16: 1 strong-secret; 2 portable-recovery; 3 secret-plus-device; 4 secret-plus-biometric-device |
| 70 | recoveryState | u16: 0 not-applicable; 1 pending-confirmation; 2 confirmed |
| 72 | wrappingAlgorithm | u16 = 1 AES-256-GCM |
| 74 | kdfId | u16: 1 scrypt; 2 HKDF-SHA256 |
| 76 | kdfN | u32 (scrypt N) |
| 80 | kdfR | u32 (scrypt r) |
| 84 | kdfP | u32 (scrypt p) |
| 88 | outputLength | u16 = 32 |
| 90 | reserved | u16 = 0 |
| 92 | kdfSalt | bytes[32] random |
| 124 | secretLayerNonce | bytes[12] random |
| 136 | deviceLayerNonce | bytes[12], random for layered slots; all zero otherwise |
| 148 | wrappedLength | u32: 48 ordinary; 64 layered |
| 152 | policyId | u16 = 1 independent-secret-required |
| 154 | reserved | u16 = 0 |

For secret slots, input is exact valid UTF-8 without silent normalization, trimming, case conversion or truncation, maximum 1024 bytes. Enrollment rejects malformed UTF-16/UTF-8 and shows the chosen policy; future UX must resolve input-method differences and accessibility. Candidate scrypt profile is N=131072, r=8, p=1, output 32 (about 128 MiB core working memory); reader accepts only that tuple in F1. That cost remains UNRESOLVED until lowest-supported-device benchmarks and independent review; no automatic weaker fallback is allowed. KdfId=1 requires those parameters; all other tuples are rejected before running the KDF. Recovery slots use an independently random 32-byte recovery secret, HKDF-SHA256 with kdfSalt; N/r/p=0. Portable encoding is exactly 64 lowercase hex characters displayed once, entered case-insensitively for hex only, with no trimming beyond an explicitly documented UI delimiter policy; decoded secret is exactly 32 bytes. Secret possession is verified by authenticating the slot and comparing its unwrapped master with the active same-container capability.

Both paths domain separate wrapping keys: first compute B = scrypt(secret,kdfSalt,N,r,p,32) for a secret or B = raw decoded recovery bytes for recovery; then HKDF-Extract(kdfSalt,B) and Expand with `u16(30) || ASCII("private-gallery:future:slot:v1") || u16(envelopeVersion=1) || containerId || masterId || slotId || u64(slotGeneration) || u16(slotType) || u16(policyId)`, L=32. The slot label is exactly 30 bytes and the slot info is 94 bytes. Recovery kdfId denotes this HKDF path; secret kdfId denotes scrypt followed by this HKDF. PRK is never reused between slots. The envelope catalog (purpose 8) body is `u16(schema=1) || u64(catalogGeneration) || u16(slotCount) || entries`, where 0..32 entries are sorted lexicographically by raw slotId bytes, each exactly `bytes[16](slotId) || u64(slotGeneration) || bytes[32](SHA256(completeEnvelopeBytes)) || u16(slotType) || u16(recoveryState) || u16(policyId)`. Duplicate IDs, unknown enums, unsorted entries and trailing bytes are rejected; zero slots alone is insufficient evidence to create a container if extant ciphertext exists. Header generation equals catalogGeneration. This authenticates active slot IDs, generations, exact SHA-256 envelope byte hashes, state and policy. Standalone envelope authentication alone cannot prove a slot is still active.

Ordinary slots wrap exactly the 32-byte master with secret/recovery-derived key and nonce, AAD=S; body length 48. Layered slots wrap that ordinary 48-byte AEAD result with an independent Android Keystore AES-256-GCM device key using deviceLayerNonce and AAD=S; body length 64. Initialize the device encryption operation first, obtain its fresh provider-generated IV, then serialize S and supply S as AAD; do not disable Keystore randomized encryption merely to inject a caller-selected IV. Device decrypt yields only the inner ciphertext: independent secret is still required. Keystore aliases are random, container/slot scoped and referenced only from the encrypted catalog; no shared Primary biometric alias or password-derived hardware key. Biometric slot additionally requires per-use strong biometric CryptoObject authorization for the outer layer and the independent Hidden secret for the inner layer. Device authentication is not proof of a distinct human. This standard-AEAD composition is a review candidate, not a claimed implemented PIN+hardware solution; key availability, authentication modes, nonce source and actual hardware security level require device evidence.

Portable recovery omits the device layer, includes only the corresponding container, and remains usable after Keystore/device loss. Pending recovery envelopes are excluded from confirmed exports. State is NOT_CONFIGURED (absence proven by authenticated catalog), PENDING_CONFIRMATION (encrypted envelope only; secret shown once), CONFIRMED (owner re-entry unwrap matches live master; a NEW envelope/generation with confirmed state is atomically committed). Changing authenticated S.state requires fresh nonce/encryption; never edit its bytes under an existing tag. Failure/death retains old confirmed slot until new slot+catalog transaction is durable. Partial/corrupt catalog or envelope yields UNAVAILABLE/CORRUPT, not NOT_CONFIGURED/new setup. Portable restored confirmed envelope is verified before installation; its bytes remain immutable. Recovery replacement does not revoke already exported old snapshots.

## Authenticated journals and durable receipt identity

Journals are purpose-5 records inside their own container root, never a global plaintext catalog. Each canonical journal body begins schema u16=1, operationId bytes[16], transferId bytes[16], localContainerId bytes[16], localObjectId bytes[16], localGeneration u64, state u16, selectedAction u16 (1 copy, 2 move), expectedPriorIndexGeneration u64, proposedIndexGeneration u64, counterpartContainerId bytes[16], counterpartObjectId bytes[16], counterpartGeneration u64, receiptId bytes[16], receiptHash bytes[32]. No operation authority/session key is persisted. Zero receiptId/hash is allowed only before DESTINATION_VERIFIED. States: 1 PREPARED, 2 COPYING, 3 DESTINATION_VERIFIED, 4 DESTINATION_COMMITTED, 5 SOURCE_DELETE_PENDING, 6 COMPLETE. Schema/enum/version/transition are checked; retry joins the same IDs and revisions rather than performing global naked-ID lookup. Cross-container counterparts are visible only after decrypting their own journal.

Destination receipts are purpose-7 records: schema u16=1, receiptId bytes[16], transferId bytes[16], sourceContainerId bytes[16], sourceObjectId bytes[16], sourceGeneration u64, destinationContainerId bytes[16], destinationObjectId bytes[16], destinationGeneration u64, destinationIndexGeneration u64, objectPlaintextLength u64, verifiedPlaintextSHA256 bytes[32], committedCiphertextSHA256 bytes[32], committedIndexSHA256 bytes[32], verificationMode u16=1 full-object-authentication. Header objectId=receiptId, generation=1. ReceiptHash is SHA256 of its COMPLETE encrypted H||C||T bytes; exact identity/hash is pinned in both journals. Receipt possession is not an authority token: source deletion revalidates source and destination capabilities and authenticates the destination receipt/index/object at its pinned identity/generation/context. A filename, receipt-shaped blob, server ack or index row is insufficient.

Destination staging remains invisible until full source authentication, destination read-back authentication, length/digest checks, data sync and tested directory sync occur. Commit immutable destination index generation, receipt and journal reference with an atomic authoritative-root selection plus parent-directory sync; reopen authoritative selection and receipt before returning durable success. Only then can an authenticated source transaction logically delete the same source revision. Late lock/expiry keeps both copies. Replaying a receipt for a different transfer/container/object/revision or replacing index/ciphertext hash fails. Faults at every file/index/receipt/root promotion boundary need restart evidence. Generic platform rename or stream sync alone is not power-loss proof.

Backups are purpose-6 manifests selecting immutable committed generations and exact ciphertext hashes/lengths. Publication of a complete manifest happens last, retention never destroys the last verified recoverable snapshot, and independent recovery slots stay compartment scoped. AEAD detects tampering but accepts an old authentic complete snapshot. Recognizing rollback needs a separately trusted checkpoint or explicit authenticated version selection; this candidate does not provide remote freshness or distributed atomicity.

## Bounds, unknown values and corruption policy

Before decrypt/KDF/allocate: require exact fixed header sizes/magic/version/algorithm/known purpose/slot/KDF/policy, checked length arithmetic, nonce/tag sizes, supplied expected context, exact physical length and configured disk quota. Reject reserved nonzero bytes, unknown enums/versions, duplicate identity/revision, trailing bytes, truncation, overflowing totals and invalid chunk relations. No fallback to legacy parsing merely because an F1 authentication fails; dispatch format from explicit caller ownership/format marker and immutable source format. An unrecognized future version is UNSUPPORTED; malformed known version or tag/context failure is CORRUPT; missing expected file is UNAVAILABLE. All are fixed categories without names/counts/raw errors, and all block writes/fresh setup/destructive cleanup.

F1 whole record <=64 MiB; index/catalog <=16 MiB; journal/receipt/descriptor <=64 KiB; at most 100,000 index items, 10,000 collections, 32 active key slots, 1,024 outstanding journals, 1,048,576 manifest entries (and <=64 MiB encoded manifest). Per-string UTF-8 <=4,096 bytes, per-array count <=100,000 unless narrower rule given; no recursion, compression or attacker-controlled object deserialization. Authenticate into bounded private staging before parsing plaintext collections; streaming media uses <=one 1 MiB plaintext chunk plus bounded cipher buffers. All parse failures preserve originals and authoritative root/slots. A corrupt snapshot must never produce an empty writable container.

## Synthetic vectors and admission limitations

`app/src/test/resources/phase0/future-format-v1-vectors.json` pins only public synthetic inputs and expected canonical context/HKDF outputs. Test-only tooling recomputes RFC 5869 SHA256 case 1, all purpose-separated domain vectors, and key-slot contexts. It proves the specified HKDF serialization agrees with two test providers; it proves no production AEAD/storage/slot implementation. Deterministic vector salts/nonces are forbidden in production.

Required future implementation tests: wrong container/master/purpose/object/generation/algorithm; every authenticated header byte; empty/max/overflow lengths; chunk reorder/substitution/truncation/header mismatch; nonce budget/fork/restart; unknown versions/KDF cost bounds; slot/state/catalog tamper; wrong independent secret/recovery; layered Keystore loss/enrollment invalidation; receipt replay and every durable commit boundary. Independent cryptographic review, parser fuzzing, fault injection and signed physical acceptance remain gates. F1 does not solve process compromise, plaintext WebView disk storage, JVM/native zeroization, flash secure erase or hostile rollback.

## Primary sources checked 2026-09-29

- [RFC 5869](https://www.rfc-editor.org/rfc/rfc5869), §§2–3 and Appendix A: HKDF algorithm, context and known-answer inputs.
- [RFC 7914](https://www.rfc-editor.org/rfc/rfc7914), §6: scrypt parameters and memory-cost construction. F1's tuple is a local proposed policy, not an RFC endorsement.
- [NIST SP 800-38D](https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf), §§5.2, 8 and Appendix A: GCM nonce/tag requirements and finite usage bounds. F1 chooses narrower budgets; current NIST revision activity must be reviewed before implementation.
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore) and [KeyGenParameterSpec.Builder](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder): key authorization, hardware variation and biometric CryptoObject. Actual provider/OEM behavior remains physical acceptance.
