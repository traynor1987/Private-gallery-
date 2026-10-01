# Phase 2 evidence ledger

Final accepted-candidate ledger, 2 October 2026. The closeout below supersedes historical checkpoint PENDING/UNRESOLVED statements. Historical RED results remain recorded; they are not current failures.

## Final accepted-candidate evidence

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

## Baseline — SOURCE FACT

- PR #57 merged, remote main `f34db23445bd16fd45ae100474c19a2de8669695`.
- Post-merge main Android run `36918293917`: completed SUCCESS.
- Main tree matches recovered local tree: `d1b4d307cb7bd480aee5cfb3e5b1a9d77fe0a861`.
- Phase 2 branch `phase2/concealed-container-foundation`, draft PR #58.
- Design/plan remote commit `347eda34db21d693b51ccbdcb890a23a291cdd9b` (tree-identical local initial doc commit `82a38d3`). Git HTTPS writes lack shell credentials; authorized GitHub connector writes preserve tree identity, with local fetch/alignment. No credentials copied to shell.

## Local validation environment

Initial wrapper attempt failed before compilation because Java could not download missing Gradle. A scratch-only Kotlin2.0.21/JUnit4.13.2/bcprov1.79/coroutines runtime was subsequently obtained through ordinary permitted downloads. Tool paths/caches are ephemeral, not project dependencies. Standalone core tests can now execute. Full project testDebugUnitTest subsequently completed: 437 tests, zero failures/errors/skips on the initial core working tree. A later reviewed clock fix and three additional tests passed the focused 33-test suite (22 Secondary plus 11 Primary). Lint/APK/instrumentation/release gates still require their own actual output. No infrastructure download error counts as regression RED.

## Required negative matrix

| # | Boundary to prove | Evidence/status |
| --- | --- | --- |
| 1 | Primary key rejects Secondary object | Evidence mapped: PrimarySecondaryCryptoTest.actualPrimaryAndSecondaryPayloadsRejectTheOtherMaster (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 2 | Secondary key rejects Primary object | Evidence mapped: PrimarySecondaryCryptoTest.actualPrimaryAndSecondaryPayloadsRejectTheOtherMaster (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 3 | Primary PIN rejects Secondary master | Evidence mapped: PrimarySecondaryCryptoTest.actualPrimaryAndSecondaryPinSlotsRejectTheOtherCredential (JVM); Phase2ActivityBoundaryTest wrong Primary PIN (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |
| 4 | Secondary PIN rejects Primary master | Evidence mapped: PrimarySecondaryCryptoTest.actualPrimaryAndSecondaryPinSlotsRejectTheOtherCredential (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 5 | Primary recovery rejects Secondary master | Evidence mapped: PrimarySecondaryCryptoTest.actualPrimaryAndSecondaryRecoverySlotsRejectTheOtherSecret (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 6 | Secondary recovery rejects Primary master | Evidence mapped: PrimarySecondaryCryptoTest.actualPrimaryAndSecondaryRecoverySlotsRejectTheOtherSecret (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 7 | Primary biometric slot rejects Secondary | Evidence mapped: PrimarySecondaryBiometricAdaptersTest.actualPrimaryEnvelopeAndSecondaryEnvelopeCannotCrossUnwrap (API36 PASS); synthetic device keys, no hardware identity claim. Final accepted-candidate gate PASS (#474/#475) |
| 8 | Secondary biometric slot rejects Primary | Evidence mapped: PrimarySecondaryBiometricAdaptersTest.actualPrimaryEnvelopeAndSecondaryEnvelopeCannotCrossUnwrap (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |
| 9 | Primary slot mutation leaves Secondary unchanged | Evidence mapped: Phase2DomainAdaptersTest.credentialRecoveryRepositoryAndBackupMutationsStayInTheirOwnDomain (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |
| 10 | Secondary slot mutation leaves Primary unchanged | Evidence mapped: Phase2DomainAdaptersTest.credentialRecoveryRepositoryAndBackupMutationsStayInTheirOwnDomain (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |
| 11 | Primary repository cannot enumerate Secondary | Evidence mapped: Phase2DomainAdaptersTest actual Primary repository stays Primary and rejects Secondary-owned item handle (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |
| 12 | Secondary repository cannot enumerate Primary | Evidence mapped: Phase2DomainAdaptersTest selected Secondary empty index persists independently of Primary corruption; SecondaryStore accepts fixed empty index only (API36 PASS; no media repository exposed). Final accepted-candidate gate PASS (#474/#475) |
| 13 | Colliding item identities remain isolated | Evidence mapped: SecondarySessionAuthorityTest actual container IDs isolate colliding records; PrimaryScopeTest foreign handle before lookup (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 14 | Colliding preview identities remain isolated | Evidence mapped: SecondarySessionAuthorityTest actual container IDs isolate colliding preview identities, rejects foreign primaryName (JVM); no production Secondary previews. Final accepted-candidate gate PASS (#474/#475) |
| 15 | Stale Primary epoch cannot access Secondary | Evidence mapped: SecondarySessionAuthorityTest foreign ownership/ABA rejection; distinct concrete Primary/Secondary operation types (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 16 | Stale Secondary epoch cannot access Primary | Evidence mapped: SecondarySessionAuthorityTest foreign ownership/ABA rejection; distinct concrete Primary/Secondary operation types (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 17 | Secondary ABA epoch cannot revive | Evidence mapped: SecondarySessionAuthorityTest.record handles and bindings reject ABA and foreign container before action (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 18 | Sequence alone provides no decrypt authority | Evidence mapped: SecondaryControllerTest.discoveryDoesNotCreateRootOrAuthorityAndExitCancelsQueuedSetup (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 19 | Wrong discovery reveals nothing | Evidence mapped: DiscoverySequenceTest wrong/order/reset/deadline (JVM); ProfessionalUiTest/Phase2ActivityBoundaryTest (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |
| 20 | Correct discovery before auth reveals no contents | Evidence mapped: Phase2ShellTest.discoveryRouteIsAuthenticationOnlyAndDoesNotExposeReadyState; Phase2ActivityBoundaryTest (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |
| 21 | Wrong Secondary PIN rejected | Evidence mapped: PrimarySecondaryCryptoTest actual PIN negative; SecondaryStoreTest PIN mutation/wrong credential; Phase2ActivityBoundaryTest (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |
| 22 | Bounded rate limiting/backoff | Evidence mapped: SecondaryAuthPolicyTest repeated failures/backoff/biometric cannot bypass (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 23 | Secondary lock removes visible state | Evidence mapped: SecondaryControllerTest exit/background; Phase2ShellTest closed/exit; Phase2ActivityBoundaryTest (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |
| 24 | Secondary lock revokes resources | Evidence mapped: SecondarySessionAuthorityTest registry resources/jobs, closed lease, deadline and revoke; SecondaryControllerBiometricTest cancellation (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 25 | Secondary lock preserves independent Primary | Evidence mapped: SecondarySessionAuthorityTest.Primary and Secondary sessions remain independent (JVM); Activity authenticated exit preserves Primary (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |
| 26 | Cold process has no Secondary authority | Evidence mapped: SecondaryControllerTest.stalePinCompletionCannotReopenAndColdControllerHasNoAuthority; SecondaryAuthPolicyTest cold start (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 27 | Recreation does not serialize key authority | Evidence mapped: Phase2ActivityBoundaryTest recreation destroys old controller and creates no new authority (API36 PASS); no secondary authority in saved state. Final accepted-candidate gate PASS (#474/#475) |
| 28 | Corrupt root cannot become fresh writable | Evidence mapped: DomainInventoryTest; SecondaryStoreTest.corruptionAndUnknownSelectionFailClosed (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 29 | Inaccessible nested root blocks setup | Evidence mapped: DomainInventoryTest.linkAndPartialAndUnreadableMaterialReject (JVM); SecondaryStorageAdapterTest actual App UID inaccessible nested directory (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |
| 30 | Unknown material blocks setup | Evidence mapped: DomainInventoryTest and SecondarySyntheticRepositoryTest fixed sibling synthetic material fails admission (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 31 | Interrupted setup cannot damage Primary | Evidence mapped: SecondaryStoreFaultTest every fresh/confirmation write and fsync boundary preserves Primary canary (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 32 | Interrupted setup not falsely complete | Evidence mapped: SecondaryStoreFaultTest interruption pending/ready/unavailable only, no reset (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 33 | Recovery pending until possession confirmed | Evidence mapped: SecondaryStoreTest.pendingRestartAndPossessionConfirmationPreserveIndependentMaster; SecondaryControllerTest possession flow (JVM). Final accepted-candidate gate PASS (#474/#475) |
| 34 | Primary recovery cannot mutate Secondary | Evidence mapped: Phase2DomainAdaptersTest Primary recovery mutation leaves Secondary hashes unchanged (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |
| 35 | Secondary recovery cannot mutate Primary | Evidence mapped: Phase2DomainAdaptersTest Secondary recovery mutation leaves Primary preferences/index unchanged (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |
| 36 | No ordinary Primary entry before discovery | Evidence mapped: ProfessionalUiTest transient discovery plus Phase2ActivityBoundaryTest configured root ordinary UI (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |
| 37 | Ordinary Settings no Secondary state | Evidence mapped: Phase2ActivityBoundaryTest ordinary Settings/security before discovery (API36 PASS); source audit below. Final accepted-candidate gate PASS (#474/#475) |
| 38 | Primary search/collections/trash no Secondary | Evidence mapped: Source audit: search/collections/trash fixed Primary repository; Phase2DomainAdaptersTest actual enumeration (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |
| 39 | Production diagnostics reject marker leakage | Evidence mapped: BrowserDiagnosticsPolicyTest.recorderRejectsUnknownAndExtendedEventCodes and allowlist tests (JVM); source audit: no Secondary diagnostics events. Final accepted-candidate gate PASS (#474/#475) |
| 40 | Primary backup excludes Secondary | Evidence mapped: Phase2DomainAdaptersTest actual archive ZIP allowlist (API36 PASS); scripts/verify_backup_exclusions.py source/merged/APK checks. Final accepted-candidate gate PASS (#474/#475) |
| 41 | Immutable Phase0/1 fixtures unchanged | Evidence mapped: Permanent Phase0FrozenLegacyTest + immutable corpus and frozen F1 reference; no fixture modifications; repeat exact candidate CI. Final accepted-candidate gate PASS (#474/#475) |
| 42 | Genuine fresh setup works | Evidence mapped: SecondaryStoreTest fresh/pending/confirmed flow (JVM); SecondaryStorageAdapterTest actual Android fresh setup (API36 PASS). Final accepted-candidate gate PASS (#474/#475) |

## Official-source recheck (1 October 2026)

These documents explain platform/primitive semantics, not project test outcomes:

- https://csrc.nist.gov/pubs/sp/800/38/d/r1/2prd — revision remains a pre-draft discussion, including a proposed wider-block variant. Phase 2 retains reviewed AES-GCM with 128-bit tags, 96-bit random nonces and F1 bounds; no speculative algorithm adoption.
- https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec — authorization belongs to the key, not a UI boolean.
- https://developer.android.com/identity/sign-in/biometric-auth — use an authorized CryptoObject and strong biometric for cryptographic operations.
- https://developer.android.com/reference/kotlin/android/security/keystore/KeyGenParameterSpec.Builder — per-use authentication, enrollment invalidation and explicit accepted authenticator policy.
- https://developer.android.com/security/fraud-prevention/activities — FLAG_SECURE has platform/OEM limits; it is not protection against external cameras/root compromise.

## Future-only boundaries

Phase 3 requires separate owner authorization: Hidden media, safe verified transfers and direct camera. Before discovery/authentication Primary must never show transfer-to-Secondary actions. Future private Browser uses a separate persistent environment; future Tor Browser uses a separate ephemeral environment with verified VPN then Tor and no direct fallback if any route component fails. Social sessions belong to persistent private browsing, not ephemeral Tor. No such functionality is implemented by this ledger.

Recovery material alone cannot recreate lost ciphertext. Primary backup must not include Secondary. No independent Secondary/VPS backup is provided by Phase 2. No overwrite-pass guarantee is made for Android flash; the per-item decision and limitations are in the design.

## First implemented core milestone

IMPLEMENTED: F1 whole-record/PIN/recovery primitives, independent Secondary session/attempt/resource capabilities, bounded transient discovery and strong-auth/backoff policies. This does not yet expose a configured container or authentication UI.

AUTOMATED TEST EVIDENCE: 15 F1 tests passed using Kotlin2.0.21/JUnit, including frozen vectors, synthetic payload digest, every ciphertext/header/tag byte mutation, malformed lengths/version/KDF parameters and credential/recovery rejection. Frozen vector SHA remains `ff70ba6e104e4a4c219a9b80e49e39015022139309f525d4617dafc96d1edb8c`.

AUTOMATED TEST EVIDENCE: 33 scoped session/discovery/policy/Primary tests passed after correction. Meaningful local RED probes caught reusable malformed-key attempts (11 tests/1 assertion failure) and unsafe no-argument awake-time clock defaults (33 tests/1 failure), then corrections passed. New-feature unresolved-symbol compilation is not represented as behavioral RED.

READ-ONLY REVIEW: format/session core review found one Important clock-default issue; fix requires explicit sleep-inclusive clocks and Android elapsedRealtime injection. Scoped re-review approved after correction. Active catalogs, transaction durability, controller/lifecycle wiring, biometric hardware, key service budgets and physical acceptance remain downstream gates. Local commit evidence: `6f835cd`, `d0ec234`, `2106ed8`, `370e69d`; remote tree-equivalent identities recorded when pushed.

AUTOMATED BUILD EVIDENCE: local Gradle lintDebug assembleDebug assembleDebugAndroidTest completed SUCCESS for the core checkpoint (5m4s; lint 0 errors, 38 warnings, 4 informational findings). APKs built. These results do not cover concurrent unfinished storage/biometric files or final Phase 2; exact committed-candidate remote CI remains required.

## Continued production and retirement review — implemented; final candidate gates pending

Recovered `50d42a9d6c141b002268fe438380607923d28caf` push/PR runs36922800684/36922805735 are terminal SUCCESS. Durable production checkpoint `280bd49d137183d97f4626318be9435db89b7677` PR run36928944103 is terminal FAILURE: all source, JVM, lint, debug/AndroidTest APK, packaging and Phase0 evidence gates succeeded; the actual API36 storage test rejected FRESH. Diagnosis: Android UnixFileSystemProvider.getFileStore(Path) deliberately throws SecurityException. DomainInventory now queries provider supportedFileAttributeViews instead and still rejects failed POSIX reads. API36 passing evidence for this correction is pending. This is a real runtime regression, not a permissions bypass.

Fresh independent source review found obsolete credential envelopes retained after selected-generation changes. Direct current-disk probes reproduced RED: old PIN wrappers2, old recovery wrappers3, canceled new PIN wrapper1. Selection rejection alone was insufficient because old envelopes wrap the unchanged master. Current implementation signs a bounded canonical retirement plan before a new wrapper is persisted, binds both possible selected generations, durably syncs selection before deletion, retires obsolete Secondary biometric aliases before headers, removes generation data before reservations, and deletes the journal last. Exact relative directory handles and original-epoch promotion gates remain intact. JVM synthetic maintenance keys exist only in tests; production uses a separate Android Keystore HMAC-SHA256 maintenance key which never wraps/derives the master or creates a session.

Missing maintenance key permits read-only bootstrap only for a structurally canonical journal. Correct independent PIN/recovery and selected AEAD/index/catalog verification plus the original authentication admission gate are required to reconstruct a signed plan from actual checked files. Biometric convenience is withheld. Wrong/canceled credentials, malformed journals, unknown material and corrupted selected data cannot repair or generate fresh container keys. Device loss still requires surviving ciphertext; no Secondary backup is added.

Regression evidence: SecondaryCredentialRetirementTest3 tests passed, searching ALL current disk wrappers with old/canceled credentials. SecondaryRetirementRecoveryTest initially5 tests passed, including all unlink/fsync boundaries and missing-key repair; added malformed-journal tests require final build. Biometric pointer ownership mutation probe5 tests/1 expected failure (selected alias deleted on cancellation), while corrected ownership transfers before pointer syscall under the original admission gate. Parser-order mutation probe6 tests/1 expected failure (reserved-byte journal became READY with missing key), corrected canonical admission precedes MAC verification. These are isolated source mutations reproducing review findings, not product gate results. Full revised build/exact-head CI are pending.

Recovery display now transfers its owned plaintext buffer once and wipes it during conversion; acknowledgement destroys undisplayed residue. A direct original-buffer regression first failed before the fix. Activity instrumentation fixture now creates synthetic real Primary PIN/recovery slots and uses production UI unlock, verifying the remembered Activity callback epoch; fake authority injection is not counted as navigation proof.

## Normal Primary surface source audit

| Surface | Source fact before discovery/authentication |
| --- | --- |
| Vault/Gallery/search/collections/Favourite/Recently Deleted/import/editor/AI Edit/Create Image | Existing Primary repository and scoped callbacks; no Secondary index/media route, counts or transfer action. |
| Browser/VPN | Existing Primary/browser environment; no Secondary Browser/profile/Tor code. |
| Settings/More/About/updates | No Secondary state row; transient 5 Installed, 1 version/About, 4 Installed sequence only. Ordinary Hide Content remains separate presentation behavior. |
| Storage/backup/recovery | Primary helpers remain scoped; aggregate platform storage remains truthful. No Secondary backup/status/recovery prompt in ordinary Primary. |
| Diagnostics/errors/notifications/startup/onboarding | No startup Secondary store query, events, badges or notifications. Neutral errors are confined to discovered flow. |
| Authenticated shell/exit | Minimal route requires scoped Secondary authority; no Primary media enumeration. Lock clears route. Secure-window latch protects stale transition pixels for Activity lifetime. |

Source review does not replace API36 semantics tests or owner visual acceptance. No physical/signed/GO evidence exists yet.

## Latest local validation and fresh whole-candidate review

Full revised local `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` completed successfully in3m55s:492 JVM tests across111 suites, zero failures/errors/skips; Android instrumentation compiled, not executed locally. The earlier491-test build had one obsolete three-byte biometric fixture rejection; replaced it with a real synthetic AES-GCM envelope without weakening admission. Additional validation for the final recovery-display revocation fix and stricter pre-selection biometric schema check is pending at this record.

Fresh read-only whole-candidate production review traced MainActivity discovery/lifecycle/security-window, controller/store/maintenance/PIN/recovery/BIO/session, Primary repository/backup/diagnostics and fixture compatibility. It confirmed no Critical or further Important source issue except one controllable recovery-display CharArray relying on delayed Compose disposal. A captured-array exit probe reproduced RED8 tests/1 failure. Controller now tracks the SAME returned mutable array and synchronously wipes it on acknowledgement, exit, background, screen-off, close, replacement or promotion; UI disposal remains additional cleanup. Immutable Compose/IME/provider copies remain residual platform limitations. Focused GREEN/build and exact-head CI must pass; this review is not physical acceptance.

Backup source and seven behavior tests passed (one packaged-test class skipped without SDK environment on that invocation); WireGuard path passed; frozen future reference5 tests passed; Node browser3 tests passed; all immutable legacy SHA256SUMS passed. Final packaged backup checks and exact-head CI remain mandatory. No secret scan failure or fixture change is present.

## Remote checkpoint reconciliation

Independently fetched remote descendant1f35ebd50dad81aed9861d094ff5b663f4ed905e while local validation was running. Preserved its completed permission-view correction, original request/token promotionGuard, cancellation-after-wrapping and uncertain-post-rename tests, retirement reservation/removal tests, and recovery-session notes. Local working checkpoint now extends that remote commit, rather than replacing or duplicating its milestone. Current remote PR CI36933923884 was IN_PROGRESS at inspection; newer candidate needs its own exact-head results.

The inherited journal permitted already-removed reservations for named retirement tokens. Current deletion ordering instead removes nontransaction material before reservations, preserving ordinary admission throughout cleanup; inherited restart/regression tests are retained alongside expanded fsync cases. Correct strong-auth maintenance repair is additional functionality. Original full owner specification is now available; docs/phase2/EXIT_GATES.md records all50 mandatory conditions.

Final source re-review confirms same-array synchronous recovery-display wiping and controller request/token guard → authority commit → enrollment ownership → pointer rename lock order. No remaining Critical/Important source finding was identified; this does not replace runtime gates. Packaged backup mutation tests were rerun with SDK36 present:14 tests PASS, no skips. Source/merged/packaged APK backup exclusions and retired runtime/weights checks passed.

## Reconciled final local milestone — AUTOMATED TEST EVIDENCE

Full local testDebugUnitTest/lintDebug/assembleDebug/assembleDebugAndroidTest completed SUCCESS in5m1s on the reconciled tree:498 JVM tests across111 suites, zero failures/errors/skips. This includes all inherited remote regressions,6 new maintenance-key/parser/fsync restart cases,7 real synthetic biometric controller cases,8 controller cases including all synchronous recovery-display exit boundaries, and actual ContainerId collision/cache tests. Android instrumentation compilation passed; API36 execution remains remote CI. No outstanding source blocker was identified by the fresh reviewer. No signed/physical PASS or merge is claimed.

The controller fixture saves the synthetic reentry bytes BEFORE acknowledgement; all returned recovery-display characters are then asserted zero. Initial post-fix fixture errors reading the destroyed display after acknowledgement were corrected, without changing the production wipe. Full final build repeats the product gates. The production biometric parser now validates the owned copied envelope before selection; malformed input leaves the selected container READY and no biometric record. Full498 tests include that assertion.

Additional remote descendant ec0c8183cbb86b9c73cafcb76441c167c3e34f02 added payload/preview collision and stale adapter tests plus real ordinary search/collections/trash Activity assertions while local work was completing. These tests and recovery notes were independently inspected and preserved. Only tests/docs changed in that descendant; production code remained the reviewed/validated498-test tree. Targeted SecondarySyntheticRepositoryTest3 tests and rebuilt AndroidTest APK then passed in21s. Exact candidate CI must execute the complete combined500-test suite and new instrumentation before signing. No remote completed work is reconstructed.


## Android Activity teardown regression — exact-head gates pending

Remote checkpoint1f35 PR run36933923884 completed FAILURE on API36. Fresh setup admission and the first two Activity regressions passed; Phase2ActivityBoundaryTest then crashed while lifecycle destruction canceled a Browser cleanup job. Browser destruction emitted a best-effort metadata callback while its Primary owner was still current. launchOwned created a lazy job in the already canceled lifecycleScope, then installed a synchronous completion callback which closed the copied-key lease before ownership registration. Primary correctly rejected that closed lease; the unhandled rejection crashed Activity teardown. This is a real integration failure, not a weakened authority test.

Meaningful regression RED: MainActivitySessionTest4 tests/1 failure on the unchanged production helper with a canceled scope and valid lease. Ownership now registers before completion cleanup is attached; a finally block attaches cleanup even when stale admission is rejected. Lazy start remains after registration, and stale ownership still throws. Fresh read-only scoped review confirmed this ordering preserves revocation and cleanup. Full JVM/lint/APK validation and exact-head API36 execution are pending for this new fix.


AUTOMATED TEST EVIDENCE: the complete reconciled local tree including the Activity teardown fix passed testDebugUnitTest/lintDebug/assembleDebug/assembleDebugAndroidTest in4m8s:501 JVM tests across111 suites, zero failures/errors/skips. MainActivitySessionTest4 tests now pass, including canceled-scope no-execution/key-wipe/completed-cleanup and unchanged stale admission rejection. AndroidTest APK compilation passed; actual API36 execution remains exact-head CI. This validated milestone is being committed and pushed; signing and physical acceptance are pending.
