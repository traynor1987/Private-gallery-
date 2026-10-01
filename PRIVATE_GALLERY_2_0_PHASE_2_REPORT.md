# PRIVATE GALLERY 2.0 — PHASE 2 REPORT

Interim report, 1 October 2026. Evidence labels distinguish source, implementation, execution and acceptance. Pending is not PASS. This is a continuation of the durable checkpoint, not a restart.

## 1. Starting main SHA

SOURCE FACT: `f34db23445bd16fd45ae100474c19a2de8669695`. Phase 1 PR #57 is merged; post-merge main CI `36918293917` completed SUCCESS. Remote main was independently inspected before continuation.


## 2. Final candidate SHA

UNRESOLVED: final acceptance candidate has not been frozen. Latest independently inspected remote checkpoint: `ec0c8183cbb86b9c73cafcb76441c167c3e34f02`, descendant of durable `280bd49d137183d97f4626318be9435db89b7677`, descended from recovered `50d42a9d6c141b002268fe438380607923d28caf`. Later changes require their own commit and exact-head gates. A tested intermediate checkpoint is not a final accepted candidate.


## 3. Branch and PR

SOURCE FACT: `phase2/concealed-container-foundation`, draft [PR #58](https://github.com/traynor1987/Private-gallery-/pull/58). Keep draft until physical acceptance and all exit gates pass.


## 4. Exact files changed

SOURCE FACT: baseline-to-reconciled staged candidate inventory below; final accepted candidate must reverify its exact inventory.

```text
.github/workflows/android.yml
.github/workflows/signed-device-test.yml
PRIVATE_GALLERY_2_0_PHASE_2_REPORT.md
app/src/androidTest/java/uk/co/traynor/privategallery/Phase2ActivityBoundaryTest.kt
app/src/androidTest/java/uk/co/traynor/privategallery/core/domain/PrimarySecondaryBiometricAdaptersTest.kt
app/src/androidTest/java/uk/co/traynor/privategallery/core/domain/SecondaryBiometricKeystoreTest.kt
app/src/androidTest/java/uk/co/traynor/privategallery/core/domain/SecondaryStorageAdapterTest.kt
app/src/androidTest/java/uk/co/traynor/privategallery/core/security/Phase2DomainAdaptersTest.kt
app/src/androidTest/java/uk/co/traynor/privategallery/ui/Phase2ShellTest.kt
app/src/androidTest/java/uk/co/traynor/privategallery/ui/ProfessionalUiTest.kt
app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt
app/src/main/java/uk/co/traynor/privategallery/core/domain/DiscoverySequence.kt
app/src/main/java/uk/co/traynor/privategallery/core/domain/DomainIdentity.kt
app/src/main/java/uk/co/traynor/privategallery/core/domain/DomainInventory.kt
app/src/main/java/uk/co/traynor/privategallery/core/domain/DomainSnapshot.kt
app/src/main/java/uk/co/traynor/privategallery/core/domain/F1Record.kt
app/src/main/java/uk/co/traynor/privategallery/core/domain/F1Slot.kt
app/src/main/java/uk/co/traynor/privategallery/core/domain/SecondaryAuthPolicy.kt
app/src/main/java/uk/co/traynor/privategallery/core/domain/SecondaryBiometricEnvelope.kt
app/src/main/java/uk/co/traynor/privategallery/core/domain/SecondaryBiometricSlot.kt
app/src/main/java/uk/co/traynor/privategallery/core/domain/SecondaryController.kt
app/src/main/java/uk/co/traynor/privategallery/core/domain/SecondaryDirectoryHandles.kt
app/src/main/java/uk/co/traynor/privategallery/core/domain/SecondaryRetirementJournal.kt
app/src/main/java/uk/co/traynor/privategallery/core/domain/SecondarySessionAuthority.kt
app/src/main/java/uk/co/traynor/privategallery/core/domain/SecondaryStore.kt
app/src/main/java/uk/co/traynor/privategallery/core/security/PrimarySessionAuthority.kt
app/src/main/java/uk/co/traynor/privategallery/ui/PrivateSpaceFlow.kt
app/src/test/java/uk/co/traynor/privategallery/MainActivitySessionTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/DiscoverySequenceTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/DomainInventoryTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/F1RecordTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/F1SlotTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/PrimarySecondaryCryptoTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/SecondaryAuthPolicyTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/SecondaryBiometricSlotTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/SecondaryControllerBiometricTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/SecondaryControllerTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/SecondaryCredentialRetirementTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/SecondaryReplacementRaceTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/SecondaryRetirementRecoveryTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/SecondarySessionAuthorityTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/SecondaryStoreFaultTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/SecondaryStoreTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/SecondarySyntheticRepositoryTest.kt
app/src/test/java/uk/co/traynor/privategallery/core/domain/SecondaryTestStore.kt
docs/phase2/BIOMETRIC_EXTENSION.md
docs/phase2/EXIT_GATES.md
docs/phase2/PHYSICAL_ACCEPTANCE.md
docs/phase2/RECOVERED_SESSION_2026-10-01.md
docs/phase2/REQUIREMENTS_AND_EVIDENCE.md
docs/phase2/RESUME_REVIEW_2026-10-01.md
docs/phase2/STORAGE_FORMAT.md
docs/superpowers/plans/2026-10-01-phase2-concealed-container.md
docs/superpowers/specs/2026-10-01-phase2-concealed-container.md
```


## 5. Independent container architecture

IMPLEMENTED: concrete SecondaryStore, SecondaryController and SecondarySessionAuthority, explicit ContainerId.SECONDARY, fixed app filesDir/domain-store sibling to vault. No global active-container/root/key selector. Primary keeps its existing concrete repository and slot adapter; no root lookup fallback.


## 6. Key hierarchy

IMPLEMENTED: independent CSPRNG 32-byte master, independent opaque 16-byte container/master identifiers, purpose/object/generation-separated HKDF record keys. No derivation from, or wrapping by, Primary material. PIN and independent 32-byte recovery slots wrap this master. Optional H2 Keystore slot is separate. No plaintext master persistence or logging.


## 7. Format

IMPLEMENTED: reviewed F1-r2 canonical 156-byte PGFUTR01/PGSLOT01 headers, AES-256-GCM with full-header AAD, HKDF-SHA256, bounded exact parsing, unknown-version rejection, explicit purpose/object/identity/generation and one immutable encryption per derived key. Frozen vectors remain unchanged. Empty encrypted index/catalog only; no production media writer.


## 8. Per-item-key decision

IMPLEMENTED DESIGN DECISION: retain F1-r2 derived object-generation keys; do not introduce random per-item wrapped content keys. See decision table below. Phase 3 must not advertise per-item cryptographic erasure under this hierarchy. A different hierarchy needs a separately versioned review before media creation.


## 9. Credential architecture

IMPLEMENTED: independent 12–64 ASCII-digit PIN; setup/change rejects the current Primary PIN. F1 scrypt N=131072,r=8,p=1,32-byte output; no weaker fallback. Neutral UI explains random long PINs versus patterns and the separate high-entropy recovery key. A uniformly random 12-digit PIN has at most about 39.9 bits; human patterns have less. Length is not verified entropy. Offline resistance is bounded by secret choice and KDF, not UI rate limits. The owner Phase 2 PIN/convenience instruction supersedes H1-r2 mandatory phrase-plus-biometric entry; H2 is documented explicitly rather than rewriting frozen H1/F1.


## 10. Biometric architecture

IMPLEMENTED: own opaque pg.device.slot.v1 alias including container/master/slot identities; per-use BIOMETRIC_STRONG CryptoObject, exact Cipher/request ownership, selected envelope digest and authenticated catalog check. Missing decrypt alias never creates a key. Hardware policy must verify TEE/StrongBox and per-use authorization or fail unavailable. RESIDUAL PLATFORM LIMITATION: any accepted enrolled device biometric may authorize this slot; aliases do not identify separate humans. Synthetic adapter tests are not hardware-success evidence.


## 11. Strong-auth policy

IMPLEMENTED: default every day, options every time/day/3 days/7 days. Last successful independent PIN is in-process elapsedRealtime state only. Cold process, recovery or security changes require PIN. Biometric success never extends PIN recency. Wrong PIN backoff begins at five failures and caps at 60 seconds, without permanent lockout; process restart resets online counters. Offline guessing/root rollback is outside this throttle guarantee.


## 12. Recovery architecture

IMPLEMENTED: distinct random 32-byte secret, one-time hex display, NOT_CONFIGURED→PENDING_CONFIRMATION→CONFIRMED via re-entry/unwrap/master comparison. Pending cannot authorize ordinary sessions/recovery. Interrupted pending setup resumes only with its PIN and generates a new one-time recovery value. Replacement keeps valid confirmed recovery until possession-confirmed selection. Key-residency audit found an original pending secret copy after display; regression/fix is recorded in resume evidence. Recovery needs surviving encrypted container data; secret alone is not a backup.


## 13. Session architecture

IMPLEMENTED: independent attempts, UUID epochs, operation IDs, scopes, copied leases, resource ownership, commit/publication gate and sleep-inclusive deadlines. Discovery contains no key/capability. Secondary lock revokes only Secondary; Primary lock clears/revokes Secondary route. No Secondary authority is serialized into Bundle/preferences/ViewModel. Activity recreation closes this domain rather than retaining it.


## 14. Jenna Protocol discovery

IMPLEMENTED: Installed/details×5→Private Gallery version interaction×1→Installed/details×4. Transient state, 30-second sleep-inclusive total bound, wrong/order/navigation/background reset. No persistent discovered flag, log or analytics. Challenge starts preflight/authentication, never decrypts or issues a session. Eligible installed biometric slot automatically prompts; cold/absent slot uses independent PIN.


## 15. Zero-disclosure audit

SOURCE FACT: Secondary controller is instantiated only after scoped discovery callback in MainActivity; startup and ordinary product composition do not query its store. Auth/shell subtree is selected only by transient route. Normal settings contain no Secondary row/count/recovery status. Primary media/search/collections/trash stay in its repository. Existing Hide Content presentation controls remain distinct. AUTOMATED TEST EVIDENCE: transient settings discovery component tests compile, as do full Activity pre-discovery/authentication/lock/recreation tests. UNRESOLVED: API36 execution and owner visual audit across all normal surfaces must pass.


## 16. Setup transaction

IMPLEMENTED: authoritative preflight, scoped admission attempt, independent random key, reservation before encryption, pending PIN/recovery/index/catalog/descriptor, fsync/readback verification, possession-confirmed generation and atomic selected pointer. Original attempt gate linearizes final promotion with cancellation. Interrupted material remains pending or unavailable, never silently fresh/complete. Primary is not touched.


## 17. Setup admission

IMPLEMENTED: missing/directly empty checked root may be fresh; valid selected root ready/pending; unknown, partial, corrupted, inaccessible, link, traversal failure or changed identity unavailable. Bounded 8192 nodes/depth5, bounded files, before/after repeated inventory. SecureDirectoryStream pinned ancestor handles confine reads/writes/rename; unavailable provider fails closed. Canonical device-authenticated retirement now removes obsolete credential generations and aliases; interrupted cleanup resumes before ordinary admission. Missing maintenance key requires verified independent PIN/recovery repair under its original admission gate. Unknown/malformed material still fails closed. Trusted Android filesDir anchor and OEM durability remain platform boundaries.


## 18. Lock and revocation

IMPLEMENTED: synchronous sequence/attempt invalidation, authority revoke, resource close/cancel, controlled key/secret buffer wipe, prompt cancellation, route state clear, no Secondary media/network capabilities. Independent auto-lock immediate default, 30s/1m/5m grace options; deadline checks work without timer delivery. Queued workers retain original token/epoch. Explicit lock/screen off/process death revoke; Primary lock clears Secondary; Secondary exit preserves valid Primary.


## 19. Screenshot and Recents

IMPLEMENTED: FLAG_SECURE precedes discovered route publication and is latched for that Activity window lifetime after discovery. Exit/background cannot remove protection from stale Compose pixels. New Activity starts with no Secondary route/authority and ordinary Primary preference. API33+ Recents screenshots disabled. UNRESOLVED: integrated API36 and physical OEM transition/screenshot/Recents checks. RESIDUAL PLATFORM LIMITATION: no external-camera/root protection or universal OEM claim.


## 20. Diagnostic privacy

SOURCE FACT: new domain operations expose neutral fixed error categories, no paths/provider causes/key/PIN/recovery content, no discovery/auth logs or analytics. Existing allowlisted Primary/browser diagnostics retain their synthetic-marker regression suite. Development synthetic identities are not production existence reporting. UNRESOLVED: final source scan and production marker test/exact candidate packaging.


## 21. Backup status

IMPLEMENTED: Primary archive remains fixed vault-root allowlist; Android allowBackup=false and full/data-extraction exclusions retain Phase0/1 policy. Secondary is never automatically exported. New actual repository/archive adapter test checks ZIP entries and independent state. PROPOSED FUTURE DESIGN: independently scoped Hidden/VPS backup requires separate authorization. Lost device without surviving ciphertext/backup is not recovered by a recovery key alone.


## 22. Cryptographic erasure

IMPLEMENTED DESIGN DECISION: no physical overwrite claims. Current hierarchy supports logical removal of application references/derivatives/temporary copies/ciphertext in a future media phase, not per-item key destruction. Old ciphertext or wrappers may remain usable with a surviving master. Container-wide cryptographic erasure requires destruction of all usable master/wrapper copies, including external snapshots, and is not implemented here.


## 23. Hide Content independence

IMPLEMENTED: Primary presentation preference is unchanged by Secondary setup/authentication/lock. Presentation controls require Primary authority; their PIN/biometric never substitutes for Secondary authentication. Enabling/disabling Hide Content does not mount, reveal, delete or mutate domain-store. Discovery no longer grants presentation-setting authentication.


## 24. Key-residency audit

SOURCE FACT: Primary startup/unlock and discovery challenge do not read/unwrap Secondary master. PIN/recovery/CryptoObject result validates only the selected Secondary format. Setup pending has independently generated master but no ordinary session until confirmed. One-time recovery buffer is transferred and wiped; cancelled/stale workers wipe returned arrays without publishing authority. Session revoke closes controlled key leases. RESIDUAL PLATFORM LIMITATION: JVM Strings, GC copies, native/provider/Keystore buffers and IME/display cannot be proven erased. No production media temporaries exist in this phase.


## 25. Attacker-model review

SOURCE FACT / RESIDUAL PLATFORM LIMITATION: see A–O table below. No forensic-deniability or fully compromised OS claim. Public source/Android storage can reveal feature functionality or aggregate storage; ordinary UI concealment is the product boundary.


## 26. Negative-test matrix and results

AUTOMATED TEST EVIDENCE: docs/phase2/REQUIREMENTS_AND_EVIDENCE.md is the authoritative 42-row ledger. Local production checkpoint validation:479 JVM tests; continued revised full build492 JVM tests, zero failures/errors/skips; reconciled full498 JVM tests, zero failures/errors/skips; lint/debug/instrumentation APK build PASS. Newly added integrated tests require execution; compilation is not PASS. Full matrix must close on the final candidate before signing/acceptance recommendation.


## 27. RED evidence

AUTOMATED TEST EVIDENCE: real directory replacement redirected a selection into a Primary canary (1 failure); fix passed replacement/store suite. Biometric cancellation after unwrap created authority (1 failure); fix passed three controller tests. One-time recovery original-buffer regression failed (7 tests/1 failure); fix validation in progress. Earlier committed clock/attempt/cancellation probes remain permanent. Missing-symbol or infrastructure failures are not behavioral RED.


## 28. Primary compatibility

SOURCE FACT: no Primary ciphertext format, PGVIDEO1, envelope encoding, backup format or migration is changed. Permanent Phase0/1 suites remain present; Primary UI presentation/discovery wiring is the main touched compatibility surface. AUTOTEST EVIDENCE: full local 479-test checkpoint, existing packaging checks and immutable corpus passed. UNRESOLVED: exact candidate full API36 regression and owner in-place upgrade confirmation, including existing photos/videos/PIN/recovery/biometric/collections/trash/Browser/AI/WireGuard/update.


## 29. Immutable fixture evidence

AUTOMATED TEST EVIDENCE: legacy corpus SHA256SUMS and identical Android asset copies verified locally; frozen F1 vector SHA ff70ba6e104e4a4c219a9b80e49e39015022139309f525d4617dafc96d1edb8c. No baseline diff in immutable fixture/reference paths. Final candidate CI must repeat these checks.


## 30. CI evidence

SOURCE FACT: main36918293917 and recovered checkpoint36922800684/36922805735 SUCCESS. Checkpoint PR run36928944103 terminal FAILURE on API36 fresh admission because Android denies getFileStore; corrected provider-attribute check awaits new exact-head runtime evidence. Full final-candidate CI is UNRESOLVED: domain/matrix, Phase1/0, fixtures/vectors, backup, secret scan, WireGuard, Node, JVM, lint, debug/AndroidTest APK, evidence variant, API36 instrumentation and packaging. Local production checkpoint build succeeded in4m40s; revised full492-test build succeeded in3m55s. Reconciled final full498-test/lint/debug/AndroidTest build PASS in5m1s; push/exact-head CI follows.


## 31. Signed acceptance evidence

UNRESOLVED: no Phase2 Signed Device Acceptance Build yet. Trigger autonomously only after exact candidate automated gates pass. Verify source/build-sha, APK SHA256, package/version, permanent signer continuity, backup exclusions/security checks and uploaded artifacts. Do not publish a Release.


## 32. Physical acceptance evidence

UNRESOLVED: no Phase2 owner acceptance. STOP when a valid signed candidate exists. Install IN PLACE; no uninstall/data clear/Primary migration/owner corruption/secret disclosure/destructive biometric changes. Prepared checklist: docs/phase2/PHYSICAL_ACCEPTANCE.md. Focus checklist must cover Primary survival/concealment, wrong/correct discovery, independent setup/PIN/optional biometrics/recovery confirmation, lock/reentry/strong-auth/background, screenshots/Recents/restart. No owner media or migration is required.


## 33. Residual risks

RESIDUAL PLATFORM LIMITATION: app-private anchor trusted; root compromise/active-process code can access live keys; no hostile rollback/secure monotonic hardware checkpoint, cross-process writers, physical flash wipe or universal OEM backup/screenshot guarantee. KDF uses roughly128MiB core working memory plus overhead; supported-device time/memory needs physical measurement. Numeric PIN entropy is not recovery-key entropy. Current-disk obsolete wrappers and aliases are explicitly retired through a durable bounded authenticated plan; fault/parser/ownership regression probes are recorded in the evidence ledger. External old copies/rollback remain outside the retirement guarantee. Final exact-head runtime and fresh whole-candidate review remain required. Any Important finding must be resolved before acceptance.


## 34. Explicit Phase3 recommendation

UNRESOLVED: required automated/signing/physical gates remain open; no merge or Phase3 implementation is authorized yet.

PHASE 2 RESULT: NO-GO FOR PHASE 3

## Per-item-key decision factors

| Factor | Phase 2 decision / consequence |
| --- | --- |
| Security / deletion | Random item keys could support cryptographic item erasure only if every wrapper and snapshot is retired; current F1 does not supply that guarantee. |
| Format complexity | A wrapped item-key catalog would add a new authenticated lifecycle/version and unreviewed key-loss cases. Freeze existing F1-r2 foundation. |
| Video chunking | No video writer in Phase2; item-key/chunk nonce interaction would require separate vectors and limits. |
| Backup/recovery / VPS versions | Old snapshots may preserve item wrappers; future backup needs tombstones/retirement. No backup implementation now. |
| Transfer | Future source/destination key handoff and verified deletion require transactional review; no transfer now. |
| Performance | Avoid additional item wraps/parses until measured media requirements exist; do not weaken current KDF. |
| Metadata / blast radius | Item-key loss can destroy a single item; catalog loss can destroy many. Master compromise still exposes retained wrappers. |

## Attacker model A–O

| Case | Protection and limit |
| --- | --- |
| A Casual unlocked-phone holder | Ordinary Primary does not disclose feature state; discovery is not authentication. Live unlocked Secondary is sensitive and requires lock/background policy. |
| B Knows Primary PIN | Cannot derive independent master or unwrap independent Secondary slot; must independently authenticate after discovery. |
| C Can authenticate Primary biometrically | Primary alias cannot unwrap Secondary; if explicitly enabled/eligible, Android may accept the same human in Secondary challenge. No unique-human claim. |
| D Accidental discovery | Challenge only; no key, index/count/content or session authority. |
| E Deliberate discovery | Secrecy of gesture is not protection; independent credential/slot remains required. |
| F App-private ciphertext, no keys | AEAD/integrity/isolation; numeric PIN offline guesses possible at KDF cost. Root-level active code and storage rollback not defended. |
| G Primary recovery only | Cannot unwrap or mutate Secondary; does not know independent secret/master. |
| H Secondary recovery only | Cannot unwrap or mutate Primary. Possession plus surviving ciphertext can recover Secondary; no lost-device data recreation. |
| I Stale Primary callback | Fixed ContainerId/epoch and concrete Primary bindings cannot publish into Secondary. |
| J Stale Secondary callback | Original token/attempt/epoch rejected after revoke/re-auth; biometric cancellation probe covers returned-master race. |
| K Corrupt/inaccessible root | Unavailable, never fresh/empty fallback; no Primary write or lookup. Owner availability may require later safe repair. |
| L Process death unlocked | No serialized authority; cold process starts absent and requires discovery/PIN. OS may retain memory/storage remnants. |
| M Process death during setup | Synced selected pending/confirmed or explicit unavailable partial; no overwrite of unknown state or Primary damage. |
| N Android backup/transfer | Primary exclusion policy/package verified; Secondary no auto archive. Vendor behavior needs physical evidence, not universal claim. |
| O Diagnostic/log export | Neutral categories/allowlist, no Secondary content/state/discovery/key material; compromised OS logging/input is outside guarantee. |

## Acceptance boundary

All 50 original exit gates remain mandatory; gate-by-gate record: docs/phase2/EXIT_GATES.md. No production Hidden media import, migration, transfer, camera, browser, Social Hub, Tor, multi-hop VPN, VPS backup, Primary format migration or public release is implemented. A future GO permits Phase3 REVIEW only. Keep PR draft and stop for the owner at physical acceptance.

## Continued reconciliation evidence

Remote branch advanced to1f35ebd50dad81aed9861d094ff5b663f4ed905e while local review continued. Its completed filesystem permission correction, controller promotion guard, uncertain-rename/canceled-enrollment cases, retirement restart probes and recovery-session documentation are preserved. Current changes extend that checkpoint with canonical missing-key maintenance repair, selection durability before deletion, alias/header/reservation deletion ordering, synchronous returned recovery-display buffer revocation and actual production-container collision tests. No remote history is force-replaced. Full original owner Phase2 specification and all50 exit gates are available in this conversation; the earlier recovery session's inability to retrieve literal prior gates is not treated as an acceptance waiver.

Fresh whole-candidate read-only source review plus scoped re-review found no remaining Critical/Important source blocker after the display-array correction. This is source evidence, not Android/signing/physical PASS. Local cache-lock/copy-transform failures are infrastructure failures and never counted as security regression RED.


Remote PR run36933923884 at1f35 completed FAILURE on Activity teardown after the Android fresh setup correction passed. The canceled lifecycleScope race in launchOwned is documented with a real4-test/1-failure RED in the ledger; ownership now precedes synchronous completion cleanup. Stale leases remain rejected and no work is started before registration. Reconciled source includes later remoteec0 tests/docs without replacing completed work. Exact-head CI and signed/physical acceptance remain UNRESOLVED.


AUTOMATED TEST EVIDENCE: full reconciled local501-test JVM suite passed with zero failures/errors/skips, lint and both APK builds PASS in4m8s. This includes the corrected Activity canceled-scope regression. Instrumentation execution, exact-head remote CI, signing and physical acceptance remain pending.
