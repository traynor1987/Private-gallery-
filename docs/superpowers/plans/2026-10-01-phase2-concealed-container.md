# Phase 2 Concealed Container Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Independent encrypted Secondary foundation, concealed discovery and independent authentication, with Primary compatibility.
**Architecture:** Concrete fixed-domain adapters with no global container/key selector. Exact reviewed whole-record format and independently scoped slots, transaction/session controllers, then minimal protected UI.
**Tech Stack:** Kotlin/JVM, Android API26–36, Compose, AES-GCM/HKDF/scrypt, Android Keystore, JUnit and API36 instrumentation.
**Spec:** docs/superpowers/specs/2026-10-01-phase2-concealed-container.md; full owner Phase 2 request remains binding.

## Global Constraints

- Starting main f34db23445bd16fd45ae100474c19a2de8669695; post-merge run 36918293917 SUCCESS.
- Primary ciphertext, VDEK, envelopes, frozen fixtures, formats v1–v6 and PGVIDEO1 remain unchanged.
- No production Hidden media/import/migration/transfer/camera/browser, Tor, VPS or release.
- F1 156-byte whole-record encoding and frozen vectors remain byte-identical; explicitly version biometric policy extension.
- No Primary UI disclosure; discovery creates no cryptographic authority.
- Local Gradle dependency download unavailable. Use CI evidence and do not invent test passes or RED results. Push no known-broken product checkpoint.
- Stop for owner physical acceptance before merge; no Phase 3.

## Review Focus

- Late authentication result after background or replacement attempt must wipe its key and issue no session (Task 3/4).
- Filesystem observation failure cannot become empty; check inaccessible nested entries, links, change and interrupted journals (Task 2).
- Equal numeric epoch/item IDs cannot authorize another domain; validate concrete authority and container before any lookup (Task 3).
- Same enrolled biometric is not a distinct person; cipher/alias/context must still be independently scoped (Task 4).
- Existing Hide Content discovery/preferences must not create a persistent new-domain entry point (Task 5).

### Task 1: Reviewed F1 whole-record and credential primitives

**Files:** Create core/domain/F1Record.kt, F1Slot.kt, DomainIdentity.kt; JVM tests in core/domain/F1RecordTest.kt and F1SlotTest.kt, same app/src/main/java/uk/co/traynor/privategallery prefix. Preserve legacy crypto.
**Interfaces:** DomainIdentity(container: ByteArray, master: ByteArray); F1Context(identity, purpose: Int, objectId: ByteArray, generation: Long). F1Record.encrypt(master: ByteArray, context: F1Context, plaintext: ByteArray): ByteArray; decrypt(master, expected, encoded): ByteArray. F1Slot.createPin(identity, pin, master, generation): ByteArray; unwrapPin(identity,pin,envelope): ByteArray; createRecovery(identity,secret,master,generation,confirmed): ByteArray; unwrapRecovery(identity,secret,envelope): ByteArray. All random fields use SecureRandom; functions wipe temporary KDF/key copies.
- [ ] Write tests first: exact frozen context/HKDF vectors; roundtrip/digest; wrong key/context/purpose/object/generation, all header bytes/tags, length/version/KDF bounds; distinct credentials/recovery; pending/confirmed context.
- [ ] Implement exact reviewed format and parser bounds, independent RNG, constant-time key checks, fresh nonce/salt, strict supported parameters.
- [ ] Run targeted JVM tests and compile through available validated execution. Review; commit/push coherent milestone.

### Task 2: Independent root and transactional setup/catalog

**Files:** Create core/domain/SecondaryStore.kt, DomainInventory.kt, DomainSnapshot.kt; JVM tests DomainInventoryTest.kt, SecondaryStoreTest.kt and Android adapter tests where required.
**Interfaces:** Fixed SecondaryStore root only. Fresh setup returns pending configuration; resume requires its PIN; confirmation selects complete state. Snapshot includes opaque identity, authenticated empty index/catalog and exact slot digests. Store accepts only Secondary authority for authenticated mutations; low-level test fixture primitive never selects Primary.
- [ ] Write fresh/admission tests, inaccessible nested/unknown/link/change controls, interrupted-setup fault tests and no-Primary-mutation canaries.
- [ ] Implement checked inventory, stage/journal, sync and authoritative selection with explicit pending/confirmed lifecycle; verify readback and exact catalog/slot binding before opening.
- [ ] Add synthetic payload test repository proof, root/colliding identity/no-fallback/backup isolation tests. No production media API.
- [ ] Validate, review and commit/push.

### Task 3: Independent session, discovery and authentication policy

**Files:** Modify core/security/PrimarySessionAuthority.kt only to add ContainerId.SECONDARY. Create core/domain/SecondarySessionAuthority.kt, DiscoverySequence.kt, SecondaryAuthPolicy.kt with corresponding JVM tests.
**Interfaces:** Concrete SecondaryScope/SecondaryOperation/SecondarySessionBinding mirroring scoped lifecycle but never accepting PrimaryOperation; containerId always SECONDARY. DiscoverySequence consumes INSTALLED/VERSION/OTHER with monotonic time; completion returns a one-shot challenge signal, resets immediately, 30-second total deadline. SecondaryAuthPolicy stores monotonic last strong auth and bounded backoff, intervals 0/1/3/7 days, default 1 day; no process serialization.
- [ ] Write tests first for independent concurrent sessions, stale lease/read/publication/ABA, expired deadlines, registry cleanup, no cross-session revocation, cross-handle rejection, correct/wrong/expired/reset discovery and cold-start PIN requirement/backoff.
- [ ] Implement with explicit ownership; do not generalize Primary into selectable container authority.
- [ ] Validate, review and commit/push.

### Task 4: Android independent credential/biometric controller

**Files:** Create core/domain/SecondaryBiometricSlot.kt and SecondaryController.kt; instrumentation SecondaryCredentialAdapterTest.kt.
**Interfaces:** Controller owns store, session, current authentication attempt and scoped settings. Discovery begins preflight/challenge only. PIN/recovery/biometric async completion checks attempt generation before session open. SecondaryBiometricSlot uses independent per-use CryptoObject alias and versioned authenticated envelope; decrypt never creates keys.
- [ ] Write synthetic real-adapter cross-slot/cross-mutation/recovery/biometric-policy/attempt-cancellation tests.
- [ ] Implement PIN setup/unlock/change, pending possession confirmation, safe recovery replacement, recovery unlock/reset, optional biometric enable/disable, strong-auth/auto-lock policies.
- [ ] Validate primitive/Android adapters, key residency and interruption; review, commit/push.

### Task 5: Concealed entry, minimal shell and lifecycle

**Files:** Modify MainActivity.kt/ProtectedSessionState.kt minimally; create ui/PrivateSpaceFlow.kt. Add API36 Phase2ConcealmentTest.kt.
**Interfaces:** About sequence launches transient controller UI; secure window before auth/shell; shell consumes Secondary controller, never Primary repository. Lifecycle cancels auth, clears transient route and revokes according to policy. Primary lock revokes visible Secondary route; Secondary exit preserves valid Primary.
- [ ] Write semantics tests for ordinary Primary surfaces, wrong/correct discovery without auth, shell after auth, settings scoping, lock/background/recreation/reset and Hide Content independence.
- [ ] Implement neutral setup/auth/recovery UX and shell; no media controls. Remove persisted-discovery coupling to new domain.
- [ ] Validate full Primary regressions and device UI instrumentation; review, commit/push.

### Task 6: CI, review, report and signed acceptance

**Files:** .github/workflows/android.yml, PRIVATE_GALLERY_2_0_PHASE_2_REPORT.md, docs/phase2/*.
- [ ] Map every owner negative/exit gate to concrete test/evidence or UNRESOLVED; add meaningful missing tests.
- [ ] Run full inherited and Phase 2 exact-head CI; fresh read-only adversarial review of call chains, fix material findings with regression tests.
- [ ] Produce report covering all 34 requested categories plus exact changed files, attacker model, key residency, erasure decision and future-only Browser context.
- [ ] Trigger signed build after exact candidate green; verify permanent signer/build SHA/APK SHA/package/exclusions/artifacts.
- [ ] Stop with non-destructive focused owner physical checklist. No merge or GO until required acceptance.
