# PRIVATE GALLERY 2.0 — PHASE 2 REPORT

Final accepted-candidate report, 2 October 2026 (Europe/London). Evidence distinguishes implementation/source facts, executed automated checks and aggregate owner acceptance. The accepted production source is frozen; closeout changes documentation only. Historical checkpoint failures below are superseded by exact-head green CI.

## 1. Starting main SHA

SOURCE FACT: `f34db23445bd16fd45ae100474c19a2de8669695`. Phase 1 PR #57 is merged; post-merge main CI `36918293917` completed SUCCESS. Remote main was independently inspected before continuation.


## 2. Final candidate SHA

Accepted signed production candidate: `e78bccb1e3ac9e17a7c8dfa0a84456c512b0bf5f`, descendant of the recovered checkpoints. Signed Build #54, push #474 and PR #475 all refer to this exact SHA. Any subsequent closeout commit updates documentation only; it does not replace the accepted APK or change application/test/build/workflow bytes.


## 3. Branch and PR

`phase2/concealed-container-foundation`, [PR #58](https://github.com/traynor1987/Private-gallery-/pull/58), base `main`. Owner explicitly authorizes final documentation and merge after acceptance gates pass. The merge SHA and terminal post-merge main Actions run are recorded in PR #58 after integration. No Phase3 implementation is included.


## 4. Exact files changed

SOURCE FACT: starting main to accepted e78bccb candidate comparison independently reverified: 54 changed files, inventory below. No Primary core/crypto, frozen legacy fixture/reference or app/build.gradle changes. Closeout modifies only this report and the three evidence documents.

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

SOURCE FACT: Secondary controller is instantiated only after scoped discovery callback in MainActivity; startup and ordinary product composition do not query its store. Auth/shell subtree is selected only by transient route. Normal settings contain no Secondary row/count/recovery status. Primary media/search/collections/trash stay in its repository. Existing Hide Content presentation controls remain distinct. AUTOMATED TEST EVIDENCE: exact-head #474/#475 API36 component/Activity pre-discovery/authentication/lock/recreation tests PASS. Owner physical acceptance is aggregate PASS; no individual surface observation is inferred.


## 16. Setup transaction

IMPLEMENTED: authoritative preflight, scoped admission attempt, independent random key, reservation before encryption, pending PIN/recovery/index/catalog/descriptor, fsync/readback verification, possession-confirmed generation and atomic selected pointer. Original attempt gate linearizes final promotion with cancellation. Interrupted material remains pending or unavailable, never silently fresh/complete. Primary is not touched.


## 17. Setup admission

IMPLEMENTED: missing/directly empty checked root may be fresh; valid selected root ready/pending; unknown, partial, corrupted, inaccessible, link, traversal failure or changed identity unavailable. Bounded 8192 nodes/depth5, bounded files, before/after repeated inventory. SecureDirectoryStream pinned ancestor handles confine reads/writes/rename; unavailable provider fails closed. Canonical device-authenticated retirement now removes obsolete credential generations and aliases; interrupted cleanup resumes before ordinary admission. Missing maintenance key requires verified independent PIN/recovery repair under its original admission gate. Unknown/malformed material still fails closed. Trusted Android filesDir anchor and OEM durability remain platform boundaries.


## 18. Lock and revocation

IMPLEMENTED: synchronous sequence/attempt invalidation, authority revoke, resource close/cancel, controlled key/secret buffer wipe, prompt cancellation, route state clear, no Secondary media/network capabilities. Independent auto-lock immediate default, 30s/1m/5m grace options; deadline checks work without timer delivery. Queued workers retain original token/epoch. Explicit lock/screen off/process death revoke; Primary lock clears Secondary; Secondary exit preserves valid Primary.


## 19. Screenshot and Recents

IMPLEMENTED: FLAG_SECURE precedes discovered route publication and is latched for that Activity window lifetime after discovery. Exit/background cannot remove protection from stale Compose pixels. New Activity starts with no Secondary route/authority and ordinary Primary preference. API33+ Recents screenshots disabled. AUTOMATED TEST EVIDENCE: exact-head API36 integrated Activity protection/recreation test PASS. Owner requested focused acceptance is aggregate PASS; no individual OEM screenshot/Recents observation is inferred. RESIDUAL PLATFORM LIMITATION: no external-camera/root protection or universal OEM claim.


## 20. Diagnostic privacy

SOURCE FACT: new domain operations expose neutral fixed error categories, no paths/provider causes/key/PIN/recovery content, no discovery/auth logs or analytics. Existing allowlisted Primary/browser diagnostics retain their synthetic-marker regression suite. Development synthetic identities are not production existence reporting. AUTOMATED TEST EVIDENCE: exact-head no-secret/source policy scan, full JVM diagnostics allowlist/marker suite and packaging checks PASS.


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

All 42 rows in docs/phase2/REQUIREMENTS_AND_EVIDENCE.md are closed against exact e78bccb. Independent-domain and full JVM tasks, full API36 suite (172 tests) and targeted Primary slot admission suite (10 tests) passed in both #474/#475. Mapped source-only boundaries remain identified as source evidence, not invented runtime tests. Reconciled local JVM result: 501 tests / 111 suites, zero failures/errors/skips. Hardware-success/person-identity/OEM guarantees are not inferred from synthetic tests or aggregate physical acceptance.


## 27. RED evidence

Recorded meaningful RED probes include root replacement writing a Primary canary, biometric cancellation after unwrap issuing authority, original recovery buffer retention, displayed CharArray delayed disposal, enrollment ownership on cancellation, malformed maintenance journal repair, and canceled Activity-scope lease registration. Each associated correction and permanent regression passes the accepted candidate's full automated suites. Historical probe counts and correction evidence remain in the ledger. Compilation/infrastructure errors are not behavioral RED.


## 28. Primary compatibility

No Primary ciphertext format, PGVIDEO1, PIN/recovery envelope encoding, backup format or migration changed. Independent base-to-candidate comparison confirms core/crypto and frozen fixture/reference paths unchanged. Permanent Phase0/1 JVM and API36 regressions pass in #474/#475. APK package/certificate/version continuity is verified. Owner confirms exact Build #54 installed with existing app/data retained and reports aggregate PASS; no individual photo/video/PIN/biometric/browser observation is added.


## 29. Immutable fixture evidence

Accepted-candidate #474/#475 independently verified SUCCESS for immutable legacy corpus SHA256SUMS, identical Android fixture asset copies and frozen F1 reference checks. F1 vector SHA256 remains `ff70ba6e104e4a4c219a9b80e49e39015022139309f525d4617dafc96d1edb8c`. No baseline diff changes immutable fixture/reference paths.


## 30. CI evidence

Accepted production candidate: `e78bccb1e3ac9e17a7c8dfa0a84456c512b0bf5f`.

- Exact-head push #474: [run 36937435472](https://github.com/traynor1987/Private-gallery-/actions/runs/36937435472), SUCCESS.
- Exact-head PR #475: [run 36937439593](https://github.com/traynor1987/Private-gallery-/actions/runs/36937439593), SUCCESS.
- Both passed the independent-domain and complete JVM tasks, lint, debug/AndroidTest packaging, frozen legacy corpus/asset and F1 vector checks, backup mutations/package policy, no-secret scan, WireGuard-only audit, Node tests, isolated Phase0 evidence JVM/lint/package tasks, and API36 instrumentation. Full Android suite: 172 tests PASS; separate targeted Primary slot admission suite: 10 tests PASS. The latter is not Phase0 Browser instrumentation.
- Reconciled local JVM evidence: 501 tests across 111 suites, zero failures/errors/skips. CI success is independently verified from completed exact-head runs and their job logs.
- Signed Device Acceptance Build #54: [run 36940495096](https://github.com/traynor1987/Private-gallery-/actions/runs/36940495096), SUCCESS.
- [Acceptance artifact 11200525476](https://github.com/traynor1987/Private-gallery-/actions/runs/36940495096/artifacts/11200525476); ZIP SHA256 `f097c1e8f9565aacf845642f13e928408c1f35229a49835c68c899c7d7e35031`.
- APK SHA256 `59150ea612111b4451f62ccf2450e76eb6031ed8cdbeb2ed47b903f9ca11321f`. Downloaded bytes and recorded checksum agree; `build-sha.txt` equals the accepted candidate.
- SDK36 apksigner cryptographic verification PASS: one RSA4096 signer; permanent certificate SHA256 `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`, matching independently verified owner-accepted Phase1 build #53. Recorded signature and actual verification agree.
- Package `uk.co.traynor.privategallery`, versionName `1.0.27`, versionCode `28`: same package/certificate and no version downgrade, suitable for in-place installation. Packaged backup exclusions and absence of retired runtime/model weights PASS.
- Owner physical acceptance: **aggregate owner-reported PASS**, reported 2 October 2026 (Europe/London). Owner confirms installing this exact candidate / Build #54 and reports **"Green and it works."** Existing app/data remained in use. No uninstall/data clear is authorized or reported. No individual test observation, hardware result or secret is inferred.
- Final recorded whole-candidate/scoped source reviews identify no remaining Critical/Important finding; no unresolved PR review threads. Primary crypto formats, immutable legacy fixtures and app version configuration are unchanged from the starting main.

**PHASE 2 RESULT: GO FOR PHASE 3 REVIEW**

All 42 matrix rows and 50 acceptance exit gates are closed on the accepted production candidate using their mapped automated/source evidence and the explicitly authorized aggregate owner PASS. This permits final Phase2 integration and verification only. Phase3 implementation, Hidden media migration/transfer and public release remain unauthorized. The merge SHA and terminal post-merge main CI are recorded in [PR #58](https://github.com/traynor1987/Private-gallery-/pull/58) after integration; GO is not a claim that an in-progress main run passed.


## 31. Signed acceptance evidence

Signed Device Acceptance Build #54 / run `36940495096` completed SUCCESS at exact e78bccb. Artifact `11200525476`, exact build SHA, APK/ZIP hashes, permanent signer/package/version and packaged security policy checks are independently verified as detailed above. SDK36 apksigner verifies the actual downloaded APK, not merely its text record. Same certificate/package/versionCode28 as accepted Phase1 #53 supports in-place replacement. Artifact-only build; no public Release.


## 32. Physical acceptance evidence

**Aggregate owner-reported PASS**, 2 October 2026 (Europe/London). Owner confirms installing exact signed candidate `e78bccb1e3ac9e17a7c8dfa0a84456c512b0bf5f`, Build #54, and reports **"Green and it works."** Existing app/data remained in use. No uninstall/data clear is authorized or reported. This is aggregate acceptance of the requested focused checklist, not fabricated individual observations. No recovery secret was requested or recorded. Full evidence: docs/phase2/PHYSICAL_ACCEPTANCE.md.


## 33. Residual risks

RESIDUAL PLATFORM LIMITATION: app-private anchor trusted; root compromise/active-process code can access live keys; no hostile rollback/secure monotonic hardware checkpoint, cross-process writers, physical flash wipe or universal OEM backup/screenshot guarantee. KDF uses roughly128MiB core working memory plus overhead; supported-device time/memory needs physical measurement. Numeric PIN entropy is not recovery-key entropy. Current-disk obsolete wrappers and aliases are explicitly retired through a durable bounded authenticated plan; fault/parser/ownership regression probes are recorded in the evidence ledger. External old copies/rollback remain outside the retirement guarantee. Accepted exact-head runtime and recorded whole-candidate/scoped reviews pass; no remaining Critical/Important finding is identified. Residual platform limits above remain, without expanding aggregate owner acceptance into individual measurements.


## 34. Explicit Phase3 recommendation

All 42 required negative cases and all 50 original acceptance exit gates are closed for the exact accepted signed candidate. Owner authorizes Phase2 final review, documentation, merge and verification of post-merge main. Final merge SHA and terminal main CI evidence are recorded in PR #58 after integration.

**PHASE 2 RESULT: GO FOR PHASE 3 REVIEW**

This result permits Phase3 REVIEW only. Do not begin Phase3 implementation, Hidden media migration/transfer or public release.


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

All 50 original exit gates remain mandatory; gate-by-gate record: docs/phase2/EXIT_GATES.md. No production Hidden media import, migration, transfer, camera, browser, Social Hub, Tor, multi-hop VPN, VPS backup, Primary format migration or public release is implemented. Owner physical acceptance is now aggregate PASS and the result permits Phase3 REVIEW only. Final Phase2 merge/main verification is authorized; Phase3 implementation remains excluded.

## Historical reconciliation evidence (superseded by final accepted-candidate sections)

Remote branch advanced to1f35ebd50dad81aed9861d094ff5b663f4ed905e while local review continued. Its completed filesystem permission correction, controller promotion guard, uncertain-rename/canceled-enrollment cases, retirement restart probes and recovery-session documentation are preserved. Current changes extend that checkpoint with canonical missing-key maintenance repair, selection durability before deletion, alias/header/reservation deletion ordering, synchronous returned recovery-display buffer revocation and actual production-container collision tests. No remote history is force-replaced. Full original owner Phase2 specification and all50 exit gates are available in this conversation; the earlier recovery session's inability to retrieve literal prior gates is not treated as an acceptance waiver.

Fresh whole-candidate read-only source review plus scoped re-review found no remaining Critical/Important source blocker after the display-array correction. At that checkpoint this was source evidence only; final executed Android/signing and aggregate physical PASS are recorded above. Local cache-lock/copy-transform failures are infrastructure failures and never counted as security regression RED.


Remote PR run36933923884 at1f35 completed FAILURE on Activity teardown after the Android fresh setup correction passed. The canceled lifecycleScope race in launchOwned is documented with a real4-test/1-failure RED in the ledger; ownership now precedes synchronous completion cleanup. Stale leases remain rejected and no work is started before registration. Reconciled source includes later remoteec0 tests/docs without replacing completed work. Exact-head CI and signed/physical acceptance remain UNRESOLVED.


AUTOMATED TEST EVIDENCE: full reconciled local501-test JVM suite passed with zero failures/errors/skips, lint and both APK builds PASS in4m8s. This includes the corrected Activity canceled-scope regression. Instrumentation execution, exact-head remote CI, signing and physical acceptance remain pending.
