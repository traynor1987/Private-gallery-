# Phase 2 evidence ledger

This is a live work record. Pending is not PASS. No Phase 2 physical acceptance or signed candidate exists yet.

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
| 1 | Primary key rejects Secondary object | PrimarySecondaryCryptoTest.actualPrimaryAndSecondaryPayloadsRejectTheOtherMaster; final candidate admission PENDING |
| 2 | Secondary key rejects Primary object | PrimarySecondaryCryptoTest.actualPrimaryAndSecondaryPayloadsRejectTheOtherMaster; final candidate admission PENDING |
| 3 | Primary PIN rejects Secondary master | PrimarySecondaryCryptoTest.actualPrimaryAndSecondaryPinSlotsRejectTheOtherCredential; final candidate admission PENDING |
| 4 | Secondary PIN rejects Primary master | PrimarySecondaryCryptoTest.actualPrimaryAndSecondaryPinSlotsRejectTheOtherCredential; final candidate admission PENDING |
| 5 | Primary recovery rejects Secondary master | PrimarySecondaryCryptoTest.actualPrimaryAndSecondaryRecoverySlotsRejectTheOtherSecret; final candidate admission PENDING |
| 6 | Secondary recovery rejects Primary master | PrimarySecondaryCryptoTest.actualPrimaryAndSecondaryRecoverySlotsRejectTheOtherSecret; final candidate admission PENDING |
| 7 | Primary biometric slot rejects Secondary | PrimarySecondaryBiometricAdaptersTest.actualPrimaryEnvelopeAndSecondaryEnvelopeCannotCrossUnwrap (Android execution pending); final candidate admission PENDING |
| 8 | Secondary biometric slot rejects Primary | PrimarySecondaryBiometricAdaptersTest.actualPrimaryEnvelopeAndSecondaryEnvelopeCannotCrossUnwrap (Android execution pending); final candidate admission PENDING |
| 9 | Primary slot mutation leaves Secondary unchanged | Phase2DomainAdaptersTest.credentialRecoveryRepositoryAndBackupMutationsStayInTheirOwnDomain (Android execution pending); final candidate admission PENDING |
| 10 | Secondary slot mutation leaves Primary unchanged | Phase2DomainAdaptersTest.credentialRecoveryRepositoryAndBackupMutationsStayInTheirOwnDomain (Android execution pending); final candidate admission PENDING |
| 11 | Primary repository cannot enumerate Secondary | Phase2DomainAdaptersTest + fixed AndroidVaultRepository root (Android execution pending); final candidate admission PENDING |
| 12 | Secondary repository cannot enumerate Primary | SecondarySyntheticRepositoryTest; production SecondaryStore exposes no media enumeration (SOURCE FACT); final candidate admission PENDING |
| 13 | Colliding item identities remain isolated | F1RecordTest context rejection + SecondarySessionAuthorityTest foreign/ABA handles; literal repository collision coverage remains UNRESOLVED; final candidate admission PENDING |
| 14 | Colliding preview identities remain isolated | No production Secondary previews; F1 purpose/key separation tested, real colliding-preview adapter coverage UNRESOLVED; final candidate admission PENDING |
| 15 | Stale Primary epoch cannot access Secondary | SecondarySessionAuthorityTest foreign container/ABA handle + concrete typed adapters (SOURCE FACT); integrated cross-adapter case UNRESOLVED; final candidate admission PENDING |
| 16 | Stale Secondary epoch cannot access Primary | SecondarySessionAuthorityTest independence + concrete typed Primary adapter; integrated cross-adapter case UNRESOLVED; final candidate admission PENDING |
| 17 | Secondary ABA epoch cannot revive | SecondarySessionAuthorityTest.record handles and bindings reject ABA and foreign container before action; final candidate admission PENDING |
| 18 | Sequence alone provides no decrypt authority | SecondaryControllerTest.discoveryDoesNotCreateRootOrAuthorityAndExitCancelsQueuedSetup; final candidate admission PENDING |
| 19 | Wrong discovery reveals nothing | DiscoverySequenceTest wrong order + Phase2ActivityBoundaryTest (Android execution pending); final candidate admission PENDING |
| 20 | Correct discovery before auth reveals no contents | Phase2ActivityBoundaryTest pre-auth no private content (Android execution pending); final candidate admission PENDING |
| 21 | Wrong Secondary PIN rejected | SecondaryControllerTest.setupRejectsPrimaryCredentialAndRequiresPossession + PrimarySecondaryCryptoTest + Phase2ActivityBoundaryTest; final candidate admission PENDING |
| 22 | Bounded rate limiting/backoff | SecondaryAuthPolicyTest.repeated failures exponentially back off cap and eventually permit retry; final candidate admission PENDING |
| 23 | Secondary lock removes visible state | SecondaryControllerTest + Phase2ActivityBoundaryTest (Android execution pending); final candidate admission PENDING |
| 24 | Secondary lock revokes resources | SecondarySessionAuthorityTest.registry owns resources and unfinished jobs before reopening; final candidate admission PENDING |
| 25 | Secondary lock preserves independent Primary | SecondarySessionAuthorityTest.Primary and Secondary sessions remain independent + Phase2ActivityBoundaryTest; final candidate admission PENDING |
| 26 | Cold process has no Secondary authority | SecondaryControllerTest.stalePinCompletionCannotReopenAndColdControllerHasNoAuthority; final candidate admission PENDING |
| 27 | Recreation does not serialize key authority | Phase2ActivityBoundaryTest recreation + absence of Secondary serialized state (Android execution pending); final candidate admission PENDING |
| 28 | Corrupt root cannot become fresh writable | DomainInventoryTest + SecondaryStoreTest.corruptionAndUnknownSelectionFailClosed; final candidate admission PENDING |
| 29 | Inaccessible nested root blocks setup | DomainInventoryTest.linkAndPartialAndUnreadableMaterialReject + SecondaryStorageAdapterTest actual app UID (Android execution pending); final candidate admission PENDING |
| 30 | Unknown material blocks setup | DomainInventoryTest fresh/partial/link controls; final candidate admission PENDING |
| 31 | Interrupted setup cannot damage Primary | SecondaryStoreFaultTest exhaustive write/sync/selection boundaries and Primary canary; final candidate admission PENDING |
| 32 | Interrupted setup not falsely complete | SecondaryStoreFaultTest + SecondaryCredentialRetirementTest exhaustive removal/restart boundaries; final candidate admission PENDING |
| 33 | Recovery pending until possession confirmed | SecondaryStoreTest.pendingRestartAndPossessionConfirmationPreserveIndependentMaster + SecondaryControllerTest; final candidate admission PENDING |
| 34 | Primary recovery cannot mutate Secondary | PrimarySecondaryCryptoTest.actualPrimaryAndSecondaryRecoverySlotsRejectTheOtherSecret; actual mutation isolation Phase2DomainAdaptersTest pending Android; final candidate admission PENDING |
| 35 | Secondary recovery cannot mutate Primary | PrimarySecondaryCryptoTest.actualPrimaryAndSecondaryRecoverySlotsRejectTheOtherSecret; actual mutation isolation Phase2DomainAdaptersTest pending Android; final candidate admission PENDING |
| 36 | No ordinary Primary entry before discovery | Phase2ActivityBoundaryTest configured domain before discovery (Android execution pending); final candidate admission PENDING |
| 37 | Ordinary Settings no Secondary state | Phase2ActivityBoundaryTest ordinary settings (Android execution pending); final candidate admission PENDING |
| 38 | Primary search/collections/trash no Secondary | Phase2DomainAdaptersTest Primary index/items + fixed Primary repositories (SOURCE FACT); explicit configured search/collections/trash UI audit UNRESOLVED; final candidate admission PENDING |
| 39 | Production diagnostics reject marker leakage | BrowserDiagnosticsPolicyTest marker injection + source scan; candidate packaging and physical diagnostics pending; final candidate admission PENDING |
| 40 | Primary backup excludes Secondary | Phase2DomainAdaptersTest ZIP entries (Android execution pending) + packaged backup policy verifier; final candidate admission PENDING |
| 41 | Immutable Phase0/1 fixtures unchanged | SHA256SUMS, identical Android assets, no immutable-path diff; final exact candidate CI pending; final candidate admission PENDING |
| 42 | Genuine fresh setup works | SecondaryStoreTest fresh pending/confirmed + SecondaryStorageAdapterTest real Android (execution pending); final candidate admission PENDING |

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

## New recovery session

See RECOVERED_SESSION_2026-10-01.md for authoritative remote snapshot, repeated behavioral RED probes, recovered-buffer validation, credential retirement, Android inventory compatibility and late enrollment fixes. The table names evidence; it does not declare all 42 cases PASS. Rows explicitly marked UNRESOLVED need concrete coverage, and every Android case needs execution. Literal original 50 gate text is still missing from this session; do not infer admission from the derived matrix.
