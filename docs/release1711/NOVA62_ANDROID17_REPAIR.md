# Code 62 Android 17 failure recovery

Recovered source: `847e1e5334856200e5e7985e6c9826d312621c08`.
Original evidence: [run 42305717931](https://github.com/traynor1987/Private-gallery-/actions/runs/42305717931), job `128194392814`, artifact `13547226663`.

## Observed failures and corrections

| Failure | Root cause and correction |
| --- | --- |
| AutomaticBackupRuntime62Test.encryptedOptInRunsOnEligibleForegroundWithoutManualBackupAndVerifiesUniqueFiles | Reconstructed canonical document URI discards the selected tree grant. Reopen through buildDocumentUriUsingTree with the persisted system-picker grant. |
| BackupSafRuntimeTest.retirementCancelsRealProviderOpenAndPreservesOriginalsInBothSpaces | Android DocumentsProvider typed-read fallback opens a document without forwarding CancellationSignal. Read binary backups with ContentProviderClient.openFile(uri, r, signal), close descriptors on authority retirement, and retain per-read authority checks. |
| BackupUi62Test.cancelledPickerAndLateDuplicateDoNotInstallARecoveryInput | Fake registry treats rejected duplicate dispatch as an error. Assert rejection explicitly and retain the UI assertion that no recovery input appears. |
| BackupDocumentsUiTest.androidCreateDocumentResultResumesAndWritesActualGrantedDestination | Replacing LocalContext hides ComponentActivity from activity-result discovery. Supply the real activity registry explicitly. |
| BackupDocumentsUiTest.actualTreePickerRequiresRecoveryAcknowledgementBeforeArmingEncryptedAutomaticSchedule | Same registry ownership defect. Keep the real system picker, explicit recovery acknowledgement and persisted-grant assertions. |
| CalculatorUiTest.longPressSequenceHasNeutralSemanticsNeverEvaluatesAndStaleCallbacksCannotAct | combinedClickable's updated callback holder lets retained accessibility actions invoke a newer authority epoch. Override semantics with callbacks capturing their original composition's authority; retain lifecycle/active/Space checks. |
| HomeOwnedWindowPanicTest.activeBackupIoStopsAtRealMainLockPanicAndBackgroundInBothSpaces | DocumentsUI root title mistaken for selectable navigation row. Use an actionable visible root row, preserve an already-selected root title, or open the drawer on narrow layouts. Cancellable binary reads also restore real blocking-read retirement. |
| HomeOwnedWindowPanicTest.realForegroundHomeExecutesScheduledPrimaryBackupWithoutManualBackupAction | Same canonical-versus-tree URI error as automatic runtime. Preserve the selected tree when reopening. |
| HomeOwnedWindowPanicTest.realSettingsRestoreLocksAndReloadsHomeMindAndWidgetsBeforeFurtherEdits | The Activity Home method does not navigate Nova Mind’s remembered internal screen. Exercise the real Nova Home control and assert Home before opening Backup; obtain any first persisted picker grant before fixture unlock. |

Android platform source for the typed-read fallback: [DocumentsProvider.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/DocumentsProvider.java), openTypedAssetFileImpl calls the two-argument openAssetFile for binary fallback. The untyped openFile overload forwards the cancellation signal.

## Verification order and boundaries

The existing CI job now executes `scripts/run_backup_repair62.py` before `scripts/run_home2_runtime.sh`. The first requires exactly the nine observed test methods, no duplicate/missing/error/failure/skipped cases, and copies original XML reports before the full run overwrites them. Any failure stops the job before the complete mandatory runtime matrix.

The complete 253-case/54-class matrix, all eight genuine process-restart phases, release evidence validators, production package/version and signing controls remain unchanged. Backup, Arcade and Calculator features are preserved. The synthetic provider still requires MANAGE_DOCUMENTS and actual picker grants. No production signing authorization is added.

Local checks so far: Calculator89, Arcade126, automatic policy12, restart control1, capture control1, preservation control1 and emulator preflight3 passed. V1 preservation passes. Native execution is pending CI; there is no local emulator/KVM. Build results and CI evidence must be recorded after completion. This document is not a signing approval.

Final local verification: app and instrumentation APKs built successfully; focused Backup/Calculator JVM tests passed **36/36 across six suites**, with zero failures/errors/skips. Final Gradle invocation completed in 2m7s (`assembleDebug assembleDebugAndroidTest testDebugUnitTest --tests uk.co.traynor.privategallery.core.backup.* --tests uk.co.traynor.privategallery.core.calculator.*`). Original provider manifest/implementation, signing controls, release gate and complete runtime matrix are byte-identical to 847e1e53. Android runtime verification remains pending.


## Focused CI round 1: 604f6a18

Run42311370749 built both APKs and passed all **499 JVM cases**, host/preservation checks and **eight original killed-process phases** (validated locally from the original receipt/transcripts). Focused Android cases: **5/9 passed**; complete253-case matrix was correctly blocked.

Remaining four failures were traced from original XML and per-test logcat:

- `BackupSafRuntimeTest.retirementCancelsRealProviderOpenAndPreservesOriginalsInBothSpaces`: even ContentResolver.openFileDescriptor for read internally uses openAssetFileDescriptor/typed fallback. The direct public ContentProviderClient.openFile API forwards cancellation without that fallback. The client is retained until descriptor closure, and failures release it. Every read still checks its captured authority.
- `BackupDocumentsUiTest.androidCreateDocumentResultResumesAndWritesActualGrantedDestination`: encryption/verification actually succeeds; its final test-only read reconstructs another canonical URI without the granted tree prefix. Verify that same document through the existing persisted tree, without granting any extra permission.
- `HomeOwnedWindowPanicTest.activeBackupIoStopsAtRealMainLockPanicAndBackgroundInBothSpaces`: root navigation now works, but DocumentsUI document labels have no clickable accessibility ancestor. Send real touch events to the visible enabled row and still require the real result/Recovery UI, actual provider IO and cancellation assertions.
- `HomeOwnedWindowPanicTest.realSettingsRestoreLocksAndReloadsHomeMindAndWidgetsBeforeFurtherEdits`: the Activity Home helper does not reset Nova Mind's remembered shell screen. Use Nova's actual Home navigation control at both transitions; retain all lock, stale-authority and reopened-setting assertions.

This round preserves the same nine-case first gate and unchanged253-case full matrix. Production signing remains unapproved and unchanged.


## Focused CI round 2: 35120e29

Run42313397508 passes **6/9 focused cases**, including actual provider-open retirement in both Spaces/read and write modes. Full verification remains correctly blocked. The remaining failures now occur after successful real picker taps: two queries throw `No compose hierarchies` during the system-Activity return transition; wait only for that specific transient state while retaining the Recovery UI requirement and deadline. Every other exception remains fatal. The create-document test reaches Verified UI but reads fixture-latest immediately after asynchronous SAVE, before createDocument has updated it; obtain the identity after actual verification, then assert destination bytes. Provider permissions and all production source remain unchanged in this test-only follow-up.
