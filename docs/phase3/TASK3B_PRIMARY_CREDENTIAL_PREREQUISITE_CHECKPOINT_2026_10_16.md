# Task 3B Primary credential prerequisite checkpoint — 2026-10-16

Task 3B remains PARTIAL / NOT ACCEPTED. Phase 3 remains NO-GO; biometrics remain DISABLED. This is a bounded implementation checkpoint, not completion of the full authentication migration or authorization to begin Task 3C.

Remote parent: `abb834abff8bfa8994ac4db646885309e0ab04fd`. Exact independently reviewed local implementation: `61189d15774483edec57b63976dab42396097071`, tree `6c16139114ae75fbe8fd1e7b536853307bfb0c6b`, including implementation `14307170d7f4909bf581c8d19c7b9eb7e2344935` and its independently requested correction. The incremental Git bundle in evidence preserves both local commits exactly. The GitHub preservation commit also includes this documentation/evidence; its remote SHA must be independently read after pushing, not inferred from the local implementation SHA.

## Production changes

- `PrimaryCredentialMutation.kt`: one fixed production process registry; bounded opaque mutation metadata retains only the same original's success-only terminal marker. Explicit durable decision plus actual original retirement is required before replacement. Ambiguous writes remain quarantined.
- `PreparedPrimaryPinWrite.kt`: once-only, thread-bound exact encrypted PIN command, independently prefunded restore rollback, exact predecessor comparison and explicit durable outcome. Commands prepare/execute outside authority/storage/preference gates. No crypto/provider ownership is implied by wrapping a preference command.
- `PrimarySessionAuthority.kt`: typed metadata-only admission checks the original operation, epoch, CREDENTIALS scope, exact attached child, partition and mandatory failed/incomplete retirement barriers. Direct outside-gate metadata admission is denied.
- `PrimaryBiometricPreferenceWrite.kt`: corrects an existing production failure-accounting defect. Entered commit false/throw now quarantines the existing slot and fails the original receipt, retaining its physical charge. Known unentered preparation failure can retire normally. Production factories remain hardwired to fixed process registries.
- `ReleaseReservation.kt`: narrowly typed, bounded cleanup-link identity check for an already attached rollback child during its original constructor. It grants no Native use, acquisition, registration, callback or retirement acknowledgement. The existing live-use check remains unchanged.

The new PIN command core is a prerequisite, NOT an active global guard: production PIN setup/change/recovery/restore/rollback and conflicting recovery-slot/biometric writers have not all migrated. The existing BIO helper failure correction is active production code. No format, crypto algorithm, PIN compatibility rule, pool quota or biometric enablement changed.

## Independent review and correction

First review found one Important introduced partial-construction defect: revoke after rollback child attachment but before linkage rejected the live-use check before retaining the child, leaving its known-unentered receipt pending. A genuine deterministic RED reproduced this on unchanged production (1 test/1 failure). The fix first validates and retains the exact attached child for cleanup, then preserves live-use rejection. Both before/after-attachment revoke timings now require zero editor/commit acquisition, actual positive original terminal retirement, zero occupied children and admissible independent retry. No pool reset or fabricated ACK is used.

Final independent review accepted the bounded batch at exact local HEAD61189d1, checked all nine source hashes and actual saved XML, and closed the original Important finding. No remaining actionable Critical/Important/Minor finding in this batch. This does not accept whole Task3B; complete reports and historical failures are preserved in the evidence archive.

## Actual verification

- Final coherent focused debug JVM selection: 72 tests, zero failures/errors/skips (six fresh XML files); BUILD SUCCESSFUL1m45s. Earlier coherent68 and repeat9 are separately qualified, not extra unique coverage.
- Existing factory checks: JUnitCore `OwnedFactoryScopeTest`, 5/5, current compiled classes. A nonexistent `OriginalFactoryTest` selector in the focused command is disclosed and NOT counted.
- Task3A regression: JUnitCore `OwnedPlaintextTest`, 10/10 on current compiled debug source; exit0. Existing Task3A implementation is unchanged.
- SDK-backed source/security Python checks: 25/25; no-secret scan PASS; diff check clean. Earlier SDK-free18/1skip is preserved separately.
- Final debug Android instrumentation Kotlin/Java compilation: PASS,43s, exact61189d1 source.
- Final Phase0Evidence Android instrumentation Kotlin/Java compilation: PASS,1m2s, exact61189d1 source.
- No new complete debug/Phase0 JVM or lint acceptance claimed for this prerequisite batch. Mandatory broad exact-final-source checks remain before Task3B acceptance. No runtime/instrumentation execution, physical-device acceptance, release APK or signing claim.

Every Gradle execution held the sole build/source lease. A Kotlin daemon-session-marker warning did not correspond to active competing Java workers at freeze; it is retained rather than described as warning-free or assumed contamination. Failed/initialization/missing-symbol runs remain preserved with their actual qualifications.

## Preference-provider boundary; no new exception

Pinned official Android14/AOSP source demonstrates that interrupted public SharedPreferences commit can return false while queued disk work remains. Therefore Java call return, reference clearing, later readback or successful compensating rollback cannot positively acknowledge that original provider operation. Entered false/throw keeps the original failed/charged and a sticky mutation quarantine even after a test's delayed fake disk worker finishes. This deliberately denies unsafe retry; it does not invent an additional platform exception.

Commit true acknowledges persistence under the public contract, separately from actual app invocation return/reference retirement; it does not prove whole-framework queue quiescence or universal provider-memory zeroization. Credential preference listeners/apply are unsupported in these prepared commands. The existing approved exception remains ONLY Android-internal Keystore retirement without public positive ACK; no such retirement was positively observed.

## Mandatory remaining Task3B work

Full blockers A–D remain open: supported production biometric lifecycle and runtime evidence; complete exact-attempt worker/input/result/provider/store manifests and fixed16 route peaks; root/storage/provider cleanup outside prohibited gates; and final original-attempt/epoch/selected-vault/durable-command admission after mandatory cleanup. These are Task3B obligations, not Task3C deferrals.

Concrete remaining paths include `PinVaultKeyStore.create/changePin/replacePinForRecoveredVault/installPinForRestoredVault/clearIfOwned`; `PrimaryKeySlots` recovery and `MainActivity` generation/outer coroutine coordination; `RecoveryVaultKeyStore`/`RecoverySetupLifecycle`; legacy `withPrimaryBiometricRecoveryDisable`; `AndroidVaultRepository.restoreBackup`; and the previously recorded SecondaryController/SecondaryStore/directory/biometric provider ownership migration. All conflicting writers must share the completed transaction guard before calling it global. Root serialization must not be removed without a concrete safe replacement.

The next bounded implementation slice is real Primary PIN unlock ownership and typed original-attempt/result handoff, followed by replacement-PIN preparation, complete writer integration and restore integration. Source-only feasibility reports in evidence are proposals, not implemented/accepted behavior. Task3C must not begin automatically or be described as safe while these obligations remain.

Main remains93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10; protected Phase3 branch remains79bb1928f3fbe16e935e587a3fe594158cb3f529. PR59 remains OPEN/DRAFT/UNMERGED, unchanged updated_at2026-10-04T05:20:31Z (independently re-read this execution). No original media/credentials deleted.
