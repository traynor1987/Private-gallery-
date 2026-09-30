# Frozen future Hidden credential policy H1

**PROPOSED 2.0 DESIGN — NOT IMPLEMENTED.** This policy and F1 crypto candidate are frozen for independent review on 2026-09-29. They are not tested production Hidden behavior. Phase 0 creates no Hidden keys, root, authentication/discovery UI or format writer. Existing Primary PIN, biometric, recovery and ciphertext compatibility remain governed by the security contract.

## Independence and honest threat boundary

Future Hidden master MUST be 32 independent CSPRNG bytes. It is not Primary's VDEK, a Primary-derived key, a second PIN over the same key or a shared recovery-root descendant. Its opaque namespace, local slots, key aliases, catalog, recovery secret and encrypted backup manifest are independently owned. Primary credentials, Primary recovery, a live Primary session, automatic Primary biometric unlock and the discovery gesture MUST NOT issue Hidden capabilities, unwrap Hidden or reset/replace its slots. Failure may never trigger new setup over existing ciphertext. Possession of an ID or knowledge of a gesture supplies no cryptographic authority.

Default local access requires the independent Hidden strong secret. Numeric six-digit PINs have at most roughly 20 bits before human-choice bias and are not sufficient for H1. Approved enrollment choices are at least six independently randomly chosen words from a documented list of at least 7,776 entries (about 77 bits before implementation bias), or an independently generated secret with at least 128 random bits and documented portable text encoding. Manually chosen passphrases may be accepted only under a separately reviewed strength/UX policy; length alone must not be advertised as measured entropy. UI must not imply the app can verify that a human actually sampled words uniformly. No default credential reuse or copying Primary's convenience setting. Exact input encoding and KDF bounds are in F1; the proposed scrypt memory/time profile requires supported-device benchmarks before adoption, with no weaker silent fallback.

This protects against a person who knows Primary credentials and against conditional private-file extraction better than a short shared PIN; it does not protect from malicious code executing in an already unlocked app process, compromised input/display, coercion or stolen recovery secrets. Device-backed wrapping can obstruct file-only offline guessing if its authorization/availability is actually enforced. A software Keystore key cannot be labelled hardware-backed. Android's app UID boundary, opaque filenames, concealment gesture and device file encryption are distinct from independent Hidden encryption.

## Biometric and optional device policy

H1 has no biometric-only Hidden slot and no automatic Hidden unlock. An optional standard layered slot wraps an independent-secret AEAD envelope in a separately scoped Android Keystore AES-GCM envelope, as specified in F1. Biometric convenience additionally requires a fresh BIOMETRIC_STRONG CryptoObject authorization for that device layer AND the independent Hidden secret for the inner layer on every opening. Mere callback success or a global unlocked flag is insufficient. Device credential fallback must not silently weaken the independent-secret requirement; any alternative policy is a separate reviewed product decision with its weaker threat boundary stated explicitly.

Separate aliases distinguish keys, not people. Any enrolled identity accepted by Android may satisfy the biometric factor. Do not promise that a particular enrolled person's finger unlocks only one compartment. Request user-auth-required, per-use authorization and enrollment invalidation where supported; inspect actual security level. API/provider/OEM combinations, cancellation, enrollment changes, device credential removal, timeout and downgrade must fail closed and need signed physical tests. Keystore failure or lost/inaccessible alias disables only that local device slot, never replaces the master or falls back to Primary credentials. Use the correct independent recovery path to restore/re-enroll. The layered design uses standard AEAD/HKDF and remains subject to independent composition and lifecycle review; it is not a deployed bespoke PIN+hardware scheme.

## Independent recovery and credential changes

Generate a distinct 32-byte random Hidden recovery secret, display once, retain only an encrypted PENDING_CONFIRMATION slot and require owner re-entry to unwrap and match the active same-container master. Promote a fresh CONFIRMED envelope+catalog atomically after verification; pending slots cannot be exported as confirmed. Lock/process death loses the one-time display and secret buffer, preserving ciphertext and pending envelope. Correct independently authenticated access can replace a lost pending setup. Partial/corrupt state is UNAVAILABLE/CORRUPT and blocks setup/reset, never interpreted as no container.

Hidden recovery must remain portable without Keystore material and recover Hidden only. Primary recovery cannot rescue Hidden; Hidden recovery cannot alter Primary. The owner stores separate secrets off device and separately from ciphertext-server tokens. A shared master secret or one plaintext catalog that automatically includes both compartments is excluded. An explicit future combined export could contain separately protected independent envelopes only after a separate reviewed existence-disclosure decision.

Changing a local secret rewraps that compartment's unchanged master and atomically selects new slot/catalog generations. Do not reencrypt media solely for a PIN change. Retain the prior valid confirmed slot until the replacement passes authenticated unwrap and durable commit. Recovery rotation selects new future envelopes; old exported envelopes/snapshots remain recoverable by their matching old secret. A wrapper change does not revoke an attacker-held master. Master compromise needs separately authorized full rekey/ciphertext/backup retirement design, excluded from Phase 0.

| Failure / owner action | H1 outcome |
| --- | --- |
| Wrong Primary credential in Hidden challenge | Fixed authentication failure; no Hidden key/session, count or successful Primary check exposed |
| Wrong independent Hidden secret | Fixed failure; online throttle; offline guessing still depends on secret entropy, KDF and file/device access |
| Biometric cancelled/late/wrong generation | No slot decrypt/session issue; independent secret success alone cannot override cancelled required factor |
| Keystore/enrollment/device loss | Local layered slot unavailable; correct portable Hidden recovery + intact ciphertext required |
| Forgotten Hidden secret but valid recovery | Authenticate Hidden recovery; atomically enroll new Hidden local secret; preserve master/content |
| Lost Hidden recovery, local access still valid | Independently authenticate Hidden; create/re-enter/confirm replacement recovery; old copies remain usable |
| Lost device plus no backup | Secret alone cannot recreate media |
| Lost all Hidden access secrets/device slots | No Primary backdoor; ciphertext may be unrecoverable |
| Primary lock/reset/recovery | Affects Primary authority and its own slots only; no Hidden credential coupling |

## Authentication authority and presentation

Every attempt binds containerId, purpose, random attempt ID and monotonically increasing attempt generation; enrollment/change additionally binds originating SessionEpoch and operation capability. Lock/background policy, switch, process death, cancellation or new attempt invalidates prior results. A late successful unwrap must be wiped best-effort and cannot issue a new capability for an expired/replaced attempt. Enrollment confirmation must compare the active key only after revalidating its immutable epoch. Process restart starts locked; no session key or protected route is reconstructed from Bundle/preferences.

Throttle online failures monotonically with bounded generic UI responses. Throttle storage is not a claim to resist offline guessing, root tampering or clock rollback. Error/diagnostic/notification/storage summaries default to fixed categories and Primary-scoped information; do not enumerate Hidden roots, item totals, names, prompts, URLs, slot status or recovery metadata. Unknown structural fields are discarded. Any minimal plaintext maintenance marker must be compartment-neutral and must not disclose Hidden existence or counts.

Prompt wording, clipboard policy, screenshot/IME/accessibility behavior and best-effort mutable buffer wiping need explicit UX/platform review. JVM Strings/native/provider buffers cannot be promised reliably erased. Discovery is concealment UX, not an authentication factor. Hidden Browser's engine-at-rest/profile guarantees remain a separate admission gate; independent vault keys do not encrypt arbitrary WebView storage.

## Review and evidence gates

Before production: independent crypto/composition review of F1/H1; UX decision on strong-secret entry; scrypt memory/time/OOM tests on lowest supported devices; fresh/cancelled/stale attempt tests; no shared Primary unlock/reset path; portable recovery confirmation/restore/failure tests; slot/catalog transaction fault injection; actual Android Keystore security-level/nonce/auth policy inspection; signed biometric/OEM/enrollment/migration tests. These remain UNRESOLVED / PHYSICAL ACCEPTANCE REQUIRED. Phase 0's diagnostic sensitive-marker tests do not establish these future credentials or format properties.

Primary sources: [RFC 7914](https://www.rfc-editor.org/rfc/rfc7914); [Android Keystore system](https://developer.android.com/privacy-and-security/keystore); [KeyGenParameterSpec.Builder](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder); [Biometric CryptoObject](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject), checked 2026-09-29. Platform documentation supports API semantics, not an OEM acceptance result or a promise of compartment-specific human identity.
