# Root integration source status

All Task6 productfiles in review179-integration.diff are sourceimplemented and frozenforindependentscopedreview. No Kotlincompilation/Android/UI/SAF/routingruntime pass claimed. LocalGradlebootstrapuncached/networkblocked; hostdomain124+authority21+hints PASS aftercheckerScopefix, strictJSON52/archiveformat66/OCRmodel8 reportedpassingindependentscopedmodules; crypto/nativeUIruntimepending. Actualcomponenttestsource covers localimport/correct/search/incident/refdelete/reopen, Hiddenlockstateclearing, groupingaccept/reject/manualtimeline, cover/inner/landscapefont2, exactAIpreview/fakeonePOST/separateapply, actualMainrole/epoch/Hide/Panic factory, Activitylifecycle+SAFcontractfresh/staleauth withrealAndroidsyntheticproviderFDs, storecipher/tamper/revocation, startuporphancleanup and coordinatorfailclosed. Those tests areUNEXECUTED. RealVPN/Torpacket/providercancel/upgradeproof remainTask7gates, notfakedPASS.

DesignchoicesrecordedCONTRACTS/PROGRESS. No copiedvaultkeys/PW/catalogueinpickerticket; freshcontrollerafterresumeonlysameoriginalauth. Openedreport/keydiscardedbeforeplaintextdestinationpicker, PWreentry+samecipherdigest afterreturn. Anyasynchronousopenedarchivecleanup preservesnativeborrowlifetime withoutMainblocking; immediatealive/currentfencepreventsnewuse. Genericpublicfilename. Referencesneverinvokeoriginaltrash/delete; cleanupwhitelistedonlynamedowncipherstages.

No new workflow, CI, signingorversionbumpafter51. This report andscopedreview areNOTcompletion/releaseapproval. Furtherwork: fixreviewfindings, exactfocusedcase/packet/upgradeCI, completeindependentwholeimplementationreview, freeze/test/sign/deliveroriginalproductionAPK.


## Fix round 1 — independent implementation handoff (2026-10-31)

This round implements the binding I1–I5/M1–M2 findings in `reviews/INTEGRATION179_INITIAL.md` and `work/task-6-fix1-brief.md`. This is a source handoff, **not Android verification or release approval**. No Git, CI, signing, version change, delegation, or original-media deletion was performed by this implementer.

- **I1 / M2:** `EvidenceRouteCoordinator` applies the complete browser admission, dedicated-process absence, Tor-process absence and isolation-release fence before VPN preparation. It repeats the fence across VPN waits and readiness/current checks. Owned WireGuard reuse remains allowed only after that browser boundary. Tor retains its existing isolation lifecycle. Independent fresh route fixtures cover forbidden Hidden System, revoked-before-start, browser admission and process outstanding, revocation during preparation and startup cancellation. They observe zero preparation/network-observation/release side effects when denied. Optional constructor dependencies default to the actual broker/process/permission observations; synthetic observations do not establish OS transport or packet behavior.
- **I2:** `moveEvent` destination indices now refer to `timeline(incidentId)`, the actual displayed order. A first move starts from sorted known timestamps with stable unknowns last; subsequent moves start from persisted manual order. Same-position boundary moves return the immutable snapshot without audit/manual-mode changes. UI arrows use the displayed index, disable the first/last boundaries and recompute the current visible order inside the mutation. The host regression was observed failing before the model fix. Component coverage uses deliberately different insertion/chronological order plus an unknown time.
- **I3:** existing-original registration is now an explicit multi-checkbox review/confirmation (1–128 distinct IDs) invoking `registerMany`. Each verified original commits independently. On a partial failure the controller rereads authoritative storage and reports how many selected originals are actually registered; it neither silently skips remaining IDs nor deletes originals. Cancellation retires the screen, and reopening reads any committed prefix. Library and incident-list export actions open a bounded explicit selection of multiple incidents and standalone evidence. `EvidenceExportSelection` carries the two ID lists into the existing encrypted-default dialog; the plan resolves incident attachments and preserves all live egress checks. No temporary incident is created and no selection is truncated to fit limits.
- **I5:** separately approved AI application saves the analysis if needed, applies the incident edit, and returns `associateAnalysis` in the same store mutation. It no longer substitutes a global applied flag for an association. Incident detail resolves only `incident.analysisIds()`, interprets saved structured body/receipt through `EvidenceAi.details`, and maps structured references through immutable `sourceReferences`. Historical/unattached references show their full source fingerprint without reattaching originals or copying unrelated annotations. The AI component case now performs actual apply → reference removal → reopen, verifies retained summary/structured event/fingerprint, preserved original, and retained live egress rejection after the source becomes vault-only.
- **I4:** the actual Host/dialog suite now uses a synthetic in-process `DocumentsProvider` with real Android FDs, cancellation signals, closure/deletion counters, blocked read/write pipes, Activity lifecycle and ActivityResultRegistry results. It covers encrypted stage → picker → fresh controller → byte-exact ciphertext; output cancellation; independent explicit plaintext consent; blocked-write Cancel/Lock/Hide/Panic with prompt Main return and partial cleanup; permission return with fresh authority and no automatic route/preview; archive expected-owner rejection; password re-entry; ciphertext-digest binding; plaintext re-export; source/destination identity protection; report security flags; Hidden grace/expiry/reauthentication after real foreground reconciliation. Native-return tests call the real `VaultPdfPreview.page` proxy-FD/PdfRenderer, hold its borrowed return boundary, and then Lock/Hide/Panic to require no late publication, recycled unpublished bitmap, eventual ciphertext-stage cleanup, and nonblocking retirement. The renderer and permission-intent dependencies default to unchanged production functions; the test wrappers hold only external boundaries and do not substitute fake report output.
- **M1:** viewport matching is case-insensitive; the landscape case asserts effective resource width 840dp, width greater than height, and font scale 2 before exercising the actual incident form.
- **Additional binding boundaries:** changing an accepted OCR timestamp labels it USER while retaining the raw OCR quote/provenance. Re-export establishes a different destination **before** opening `wt`. Exact normalized URI, same-authority document ID (including document/tree aliases), and available canonical identities protect the incoming archive. Distinct declared document IDs/providers establish logical document separation under the provider contract; same-provider opaque URIs without sufficient canonical identities fail closed. Cleanup applies the identical admission before requesting provider deletion, and does not delete an unknown/protected source. This does not claim protection from a malicious provider violating its document-identity contract. Local-v1 near-quota snapshots stay readable; quota failure reports the unsuccessful change and preserves displayed/authoritative annotations, without data truncation, retry or downgrade.

API notes: `EvidenceLocker.onExport` now takes `EvidenceExportSelection`; `EvidenceExportDialog` accepts separately selected `evidenceIds` (default empty). Main's existing Host trailing callback remains source compatible. Host's optional permission factory defaults to `VpnService.prepare`; ArchiveDialog's optional renderer defaults to `VaultPdfPreview.page`. `EvidenceOwnerSession`, staging policy and Main authentication architecture did not require rewriting.

The three compiler diagnostics handed off by Root from diagnostic run `41230519016` at frozen `c732d894` were corrected narrowly: Provider.metadata map/if whitespace, and `AiCredentialStore.syncDirectory` now uses Android-supported `O_RDONLY`, verifies `fstat`/`S_ISDIR`, and preserves `fsync` plus descriptor closure. That root diagnostic is not a compile result for this round. No credentials/authentication commit boundary was changed.

### Observed commands and results

1. Before the timeline model fix: `bash scripts/check_evidence179.sh` — **exit 1**, `AssertionError: boundary move must not switch chronology to insertion order`, from `Evidence179Checks.firstManualMove`.
2. After the model fix and at handoff: `bash scripts/check_evidence179.sh` — **exit 0**. Exact result: `Evidence179 host checks passed: 130 assertions (model/codec/lineage/search/real crypto/revocation/concurrency)`; `Evidence179 captured authority 21 checks PASS`; text-hints provenance/uncertainty/bounds PASS. A final parallel JVM launch emitted one `/tmp/hsperfdata_root/7 ... locked by another process` performance-data warning; assertion results and exit status remained successful.
3. `bash scripts/check_evidence179_archive.sh` — **exit 0**. `EvidenceArchiveFormatHostTest PASS 66`; `EvidenceHistory179HostTest PASS 40`; `EvidenceWipingBufferHostTest PASS 19`; near-limit Primary v1 4,188,265 bytes and Hidden v1 4,188,264 bytes under unchanged 4,194,304 maximum; `EvidenceMigration179HostTest PASS 486`.
4. `./gradlew :app:compileDebugAndroidTestKotlin --offline` — **exit 1 before Gradle/build/test compilation**, wrapper attempted `https://services.gradle.org/distributions/gradle-9.5.0-bin.zip`, then `java.net.SocketException: Network is unreachable`. No Kotlin/Android compiler, emulator, component, SAF, native or transport PASS is claimed. Android red/green could not be observed locally; test source is authored pending the grouped build/run.

### Exact focused Android test inventory — all UNRUN in this round

`ui/EvidenceLocker179UiTest.kt` (7 cases):

- `primaryActualImportExtractCorrectIncidentSearchRemoveAndReopenPreservesOriginal`
- `ownerAcceptsAndRejectsActualGroupingHintsAndOrdersUncertainTimeline`
- `firstManualMoveUsesVisibleChronologyAndDisablesBoundaries`
- `bulkOriginalRegistrationAndMixedExportSelectionAreOwnerReviewed`
- `editedExtractedTimestampRemainsUserEnteredWithRawQuotePreserved`
- `landscapeDarkLargeTextIncidentFormAndExitRemainReachable`
- `hiddenInnerLockClearsSearchSelectionsAndDialogsWithoutDeletingEncryptedEvidence`

`ui/EvidenceSaf179Test.kt` (29 cases):

- `importPickerRecreatesAuthorityOnlyAfterResumedOriginalAuthentication`
- `latePickerAfterReauthenticationCannotReadOrImportItsDocument`
- `hiddenWithinGracePickerCapturesFreshAuthorityAfterForegroundReconciliation`
- `hiddenExpiredPickerCannotReadDocumentAfterRealForegroundReconciliation`
- `hiddenReauthenticatedPickerCannotReviveOriginalAuthentication`
- `encryptedPreparePickerFreshControllerCopiesExactSealedCiphertext`
- `cancelledOutputPickerDiscardsOnlySealedCiphertextAndPreservesOriginals`
- `plaintextOutputNeedsSeparateExplicitConfirmation`
- `cancelBlockedProviderWriteClosesAdmissionWithoutBlockingMain`
- `lockDuringBlockedProviderWriteRevokesAndRequestsPartialCleanup`
- `hideDuringBlockedProviderWriteRevokesAndRequestsPartialCleanup`
- `panicDuringBlockedProviderWriteRevokesAndRequestsPartialCleanup`
- `permissionReturnUsesFreshControllerAndRequiresNewPreviewWithoutAutoConnecting`
- `permissionReturnAfterReauthenticationCannotReopenAiOrStartTransport`
- `archiveReexportFreshPasswordAndSeparateConfirmationProducePlaintextOnlyAtDestination`
- `lockDuringBorrowedNativeRenderRejectsAndRecyclesUnpublishedBitmap`
- `hideDuringBorrowedNativeRenderRejectsAndRecyclesUnpublishedBitmap`
- `panicDuringBorrowedNativeRenderRejectsAndRecyclesUnpublishedBitmap`
- `lockClearsAuthenticatedNativeReportViewAndCipherStage`
- `hideClearsAuthenticatedNativeReportViewAndCipherStage`
- `panicClearsAuthenticatedNativeReportViewAndCipherStage`
- `archiveReexportRequiresPasswordAgainAndChangedCiphertextNeverPublishesReport`
- `archiveReexportRejectsSameDocumentAliasBeforeTruncateAndNeverDeletesIncomingArchive`
- `archiveReexportRejectsExactIncomingUriBeforeTruncateAndCleanup`
- `archiveReexportFailsClosedWhenProviderIdentityCannotBeEstablished`
- `wrongOriginatingOwnerIsRejectedBeforeArchiveReportPublication`
- `lockWhileArchiveProviderReadIsActiveCancelsAndPreventsReportPublication`
- `hideWhileArchiveProviderReadIsActiveCancelsAndPreventsReportPublication`
- `panicWhileArchiveProviderReadIsActiveCancelsAndPreventsReportPublication`

`ui/EvidenceAi179UiTest.kt` (1 cases):

- `actualPreviewNeedsExplicitSendAndSeparateOwnerApplication`

`core/evidence/EvidenceRouteCoordinator179Test.kt` (5 cases):

- `hiddenCannotChooseSystemWithoutAnyTransportSideEffects`
- `independentlyLiveRevokedStartupHasNoTransportSideEffects`
- `vpnAdmissionAndDedicatedProcessFenceBothPrecedePreparationAndReadiness`
- `revokedDuringPreparationCannotProbeReadinessOrPublishRoute`
- `startupCancellationDuringPreparationCannotProbeOrPublishRoute`

`core/evidence/EvidenceController179Test.kt` (9 cases):

- `editsPreserveOriginalFingerprintAndOcrHistory`
- `boundedReadRejectsOversizeAndLeavesNoPlaintextOutputFile`
- `scoped_bytes_are_wiped_when_outer_reader_rejects_after_consumer_returns`
- `cancellation_during_io_return_cannot_abandon_owned_bytes`
- `acquisition_claims_are_bounded_bound_to_original_and_unavailable_for_existing_reference`
- `refresh_flags_live_missing_changed_policy_and_fingerprint_without_full_read`
- `controller_state_paths_clear_at_retirement_and_reject_late_operation`
- `import_register_retains_provider_claims_and_reference_removal_preserves_original`
- `bulk_registration_reports_committed_prefix_on_failure_and_never_removes_originals`

### Remaining gates / freeze boundary

The 51 named focused cases above, all other mandatory Evidence179 classes, affected existing-feature regressions and final application/test APKs require grouped Kotlin/Android/API37 compilation and actual execution at the exact frozen source. In-process provider/permission fixtures and state-observation route fixtures do not prove real Android VPN grant behavior, owned VPN/Tor packets or DNS, OS browser-process retirement, or absence of leakage; those remain the mandatory live transport gates. Actual secure-window/Recents imagery, accessibility and physical Fold owner acceptance remain separate. Independent scoped re-review must assess these source changes, followed by whole-branch review, original production-key code50→51 upgrade without uninstall/data clear, distribution/signature/identity checks and signing gates. No release readiness is implied by JVM passes or authored Android source.


## Fix round 2 — F1 named-argument correction (2026-10-31)

Read `reviews/INTEGRATION179_FIX1.md` in full. Corrected its new Important F1 with exactly one production-line change in `EvidenceLockerHost.kt`: the ordinary export wrapper now calls `it.write(uri, consume=write)`. The output callback therefore binds to `consume`, while `protectedSource` retains its intended default for newly created ordinary encrypted/plaintext export destinations. This corrects the source-level argument mismatch introduced by the extra optional URI parameter; no successful compiler run is claimed.

Observed source verification: captured the pre-edit Host with `cp app/src/main/java/uk/co/traynor/privategallery/ui/EvidenceLockerHost.kt /tmp/EvidenceLockerHost-task6-fix2-before.kt` (exit0); inspected all production `EvidenceDocumentIo` uses with `rg -n 'EvidenceDocumentIo|io\.write\(|it\.write\(uri' app/src/main/java/uk/co/traynor/privategallery` (matches include corrected wrapper and unchanged protected archive call); compared with `diff -u /tmp/EvidenceLockerHost-task6-fix2-before.kt app/src/main/java/uk/co/traynor/privategallery/ui/EvidenceLockerHost.kt` (exit1, exactly the expected one-line difference). The archive re-export still calls `io.write(destination,protectedSource=source){...}`. Its rejection, retirement, disposal and partial-cleanup paths continue carrying the protected source; this round does not change them.

Only that Host line and this report append were edited. No new tests, broader product changes, Git, CI, signing or delegation. Kotlin/Android compilation and actual Host/provider tests remain pending the grouped build/run; the earlier local Gradle bootstrap limitation is unchanged and was not retried. Prior host-suite passes are not evidence that Kotlin compiles. F1 requires scoped re-review of this exact line and grouped compiler verification before closing the build gate.
