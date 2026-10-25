# Private Gallery 1.7.5.1 — consolidated diagnosis of focused runs #12–#15

**Decision: NO-GO. Android CI is stopped. No new run, signing or acceptance APK is authorised by this report.**

This report supersedes earlier chronological hypotheses, planned-count summaries and progress messages. It separates observed failures from interpretations. Production source remains preserved; no speculative production correction is added here.

## Recovery and verification

The initial freeze checkpoint is remote `3f6696c3aeae5eeb9dbc9798d1e99f1e60c99daa`, corresponding to local `60f3cafd31b2d1c9692d17f65558fd48a8a26f56`, tree `0e7500ff97cbd3596092fc4c465c42251131b4ec`. GitHub ref/commit reads and independent `git ls-remote` agree. The production main tree remains `f6d126869d436274ef238a866990d3334f318af0`, identical to runs #14 and #15. The freeze commit launched zero workflows.

Branch: `release/1.7.5.1-privacy-viewer-repair`. Version 1.7.5.1/code 39, package `uk.co.traynor.privategallery`. Original release and recovery branches are preserved. No force push, main merge, owner-device access, media modification, data clear, uninstall, key/PIN reset or replacement signing material occurred.

The original certificate remains `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`. No code-39 production signing has run. No signed acceptance APK exists.

## Exact results

All four artifact ZIPs were checked against GitHub's SHA-256 digests, passed ZIP CRC checks and had their raw XML recounted. Machine-readable identities, counts and API32 case names are in [RUNS_12_15_EVIDENCE.json](RUNS_12_15_EVIDENCE.json).

| Run | GitHub ID | JVM | API36 | API32 |
|---|---|---:|---:|---:|
| #12 | 40121851508 | 349 passed | Not run | Not run |
| #13 | 40122327889 | 349 passed | 47/47 passed | 20/22 executed passed; 2 failed; last planned case not run |
| #14 | 40123514631 | 349 passed | 47/47 passed | 20/23 passed; 3 failed |
| #15 | 40125426839 | 349 passed | 47/47 passed | 20/23 passed; 3 failed |

There were no skipped cases in these raw reports. The missing #13 owned-dialog Recents case is **not a pass**. A continued GitHub step's successful conclusion is **not** its successful outcome. The combined final gate correctly failed throughout.

## Common causes and classification

### 1. Test-double compilation, not application compilation — proven

Run #12's fake `ActivityResultLauncher` implemented `getContract()` against a dependency defining Kotlin `val contract`. Instrumentation compilation failed. Application compilation and 349 JVM cases completed; no Android case ran. The property override was corrected in #13. This was a test-source interface error, not a production permission, vault or encryption defect.

### 2. Foreground preconditions mixed with first-run system UI — proven confounder; complete causation unproven

The permission test repeatedly combined lifecycle state, native focus, the result callback and preview assertions without isolating all setup transitions. In #14 it failed **before the permission request**, at `assertNormalWindow` line 349. A native `ImmersiveModeConfirmation` window was present during these fullscreen fixtures. In #15, the combined result/focus-restoration assertion timed out; its message does not identify which Boolean remained false.

The guide can take focus outside the app-owned window set. Its presence is a concrete emulator setup confounder. It is not proof that a normal owned menu regressed. Nor is it proof that every permission failure is explained solely by that guide.

Earlier claims that the observed permission dialog kept MainActivity RESUMED were incorrect. Full LifecycleMonitor logs for the cited interaction show PAUSED at 18:27:16.193 and RESUMED at 18:27:17.796. Generic PGPrivacy log samples omitted that pause. Real Quickstep multi-resume evidence from earlier runs is separate and remains valid.

The explicit permission contract, per-root handoff tracking and 1.5-second known-return grace remain preserved, with API36 coverage. These runs do **not** establish that further timer changes are needed. Do not increase timeouts or modify vault authority on that inference.

### 3. Protected-transition capture is unavailable — proven evidence gap, not a pixel result

Run #14 saved the primary cause: `UiAutomation.takeScreenshot()` returned null. Run #15's ordinary `screencap -p` produced missing/invalid PNG data during both protected transitions. The same backend had just captured real foreground fixture pixels: 9,604 photo samples and 6,491 owned-dialog samples.

This rules out treating those missing frames as valid zero-pixel screenshots. It does **not** establish exposed Recents pixels, and it does **not** establish safe visible task-card pixels. The exact native capture rejection reason/exit status was not preserved. Android secure-capture policy is a candidate mechanism; a precise native status is still required before calling it an expected protection result.

AOSP verification establishes that ordinary `screencap -p` does not request secure-layer capture (`captureSecureLayers=false`, `allowProtected=false`). Therefore a readable PNG cannot simply be demanded by relaxing Android protection. No secure-capture bypass was used or will be introduced.

The current strict visual gate remains unsatisfied. Keep foreground-positive, actual Launcher/overview/task-card/app-label readiness and valid transition/settled evidence requirements. Do not turn a generic capture error into a privacy pass or fabricate a black frame.

### 4. Cleanup exceptions obscured the primary failures — proven instrumentation problem

Direct App→Recents transitions leave MainActivity paused/resumed under a remote animation. Back-key cleanup previously caused a no-focused-window ANR. Later ActivityScenario cleanup timed out waiting for DESTROYED while the last state was PAUSED, replacing the primary error in XML.

The saved per-case trace is authoritative for the primary capture exception; the teardown timeout is secondary. Direct fixture `finish()` and bounded destruction did not fully resolve #15 cleanup. This is not evidence that the owner app crashed during ordinary use or that encrypted media was damaged.

Future fixture cleanup needs a deterministic end to the disposable Recents animation/task, with the original failure retained independently of JUnit rule teardown. It must occur after evidence collection and must not modify application privacy policy.

## Production defects versus remaining verification

The original owner defects and earlier genuine app findings are preserved as implemented work: owned-menu shielding, screenshot policy including independent Hidden preference, intentional covered-preview/reopen state, discoverable Panic controls and pointer-stream handling. Earlier review also corrected screenshot scope and window-handoff races. They are not newly diagnosed #12–#15 failures.

Runs #13–#15 provide repeated API36 passing evidence for the current repair family. They do not replace full regression or owner testing. These four runs demonstrate no new encryption/credential/media corruption, but they also do not prove an in-place signed upgrade or complete Android12 Recents privacy acceptance.

## One consolidated correction, with limits

The recovery checkpoint contains one unexecuted, emulator-only correction: preconfirm the first-run fullscreen guide using `adb shell settings put secure immersive_mode_confirmations confirmed`, then require a matching readback before API32 instrumentation. AOSP CTS uses this setup; Android12L defines the same key/value. This changes no APK behavior, screenshot flags, vault permissions or Android secure-capture privileges. First-run-guide behavior remains outside that preconfirmed fixture.

No further production change is justified by the present evidence. The capture/cleanup problem is still unresolved, so running only the guide correction would knowingly repeat an incompletely diagnosed gate. **Do not launch another focused workflow now.**

## Precise continuation handover

1. Recover this branch and verify the remote SHA/tree. Read this report before chronological recovery notes. Preserve the source, assertions and original signer.
2. Retrieve artifacts by IDs/digests in the JSON manifest. GitHub artifacts expire on 22 January 2027; retain raw archives before then if needed. Traces distinguish primary errors from teardown errors.
3. On a disposable synthetic emulator, identify the native capture exit status, stderr and SurfaceFlinger reason at the protected transition. Save build fingerprint, display configuration and actual focus/lifecycle state. Keep ordinary capture permissions; no root or secure-layer capture.
4. Confirm first-run guide acknowledgement and separately log permission callback completion, lifecycle and actual owned focus. Do not infer their individual states from a combined timeout.
5. Resolve teardown independently of the security assertions. A cleanup error must not replace the primary test result. Review any replacement fixture before execution.
6. Decide whether valid, equivalent visual evidence is safely obtainable under Android's protection. Do not remove FLAG_SECURE, relax required classes/assertions, count unavailable frames as safe, or introduce a product Recents fallback just to obtain green CI.
7. Only after those diagnosed issues have one coherent, independently reviewed correction may one new focused run be considered. If evidence remains unobtainable safely, keep NO-GO and hand over rather than repeat the loop.
8. Full security/UI regression, original-key signing, package/hash verification and synthetic code38→39 debug **and production** upgrades/restarts remain blocked and mandatory before an owner APK. Galaxy Z Fold physical acceptance remains pending.

## Owner-authorised platform-support change — 24 October 2026

The owner explicitly changed the minimum to Android16/API36, with Android17 Fold compatibility. The Android12 failures above remain unresolved on a now-unsupported platform; no previous failure is relabelled passing. Its mandatory matrix entry is removed under that authorisation. Supported-platform Privacy Shield, screenshot/Overview, Panic and authentication assertions remain mandatory. See ANDROID_SUPPORT.md for the current matrix and ANDROID12_RECOVERY_ATTEMPT.md for the superseded local diagnostic attempt. Fresh supported focused, full regression, original-key signing and both38→39 upgrades/restarts remain pending. No release GO or physical acceptance follows solely from changing the minimum.

Current supported-platform evidence: focusedrun16 atf98d9938e88df5ebc67c56963ca5539ec8b4f381 passed349JVM and47API36 cases; API37ran47 with8pass39sharedEspressoinitialization failures. See ANDROID17_INSTRUMENTATION_DIAGNOSIS.md andRUN_16_EVIDENCE.json. NO-GO pending the diagnosed test-only correction andfreshsupported gates; no full/signing/APK. This does not change any historical Android12 failure classification.

Run17 atd96da3bbeb5caeb4270f671b2b8bc22123208faa independently passed349JVM/47API36/47API37 cases, all0fail0skip. This resolves the separately diagnosed Android17 Espresso initialization fault, not Android12's capture/permission/cleanup failures. SeeRUN_17_EVIDENCE.json andSTABLE_CANDIDATE.md. Fullsecurity/upgrade/original-key signing andowneracceptance remain pending; the historical runs12–15 report remains unchanged.


## Full candidate 1 blocked — no speculative rerun

Run40135349594/sourceb15dd211eb7dd1ee355c509f2a29e538745c778e failed in unfiltered API36 instrumentation. Raw artifact12577258000 SHA256300f21ba55f4fd227721424ad638019674f4d4afa76fc9691e1efd0143e526a7 passed ZIP CRC. Counts and exact five failing methods/frames are preserved in FULL_CANDIDATE_1_EVIDENCE.json. Focused API36/API37 success remains valid, but full release/signing is NO-GO. No signed code39 acceptance APK exists; debug and production upgrade completion is not established.

All five failures are ProfessionalUiTest: settingsLight/settingsDark cannot find Updates & About after clicking a scrolled-off Back button; three protected-setting tests assume ordinal toggle positions or visible Hide content despite the preceding Panic Exit section. Source and raw traces identify stale test navigation/selector assumptions; independent classification and correction review are pending. Production Privacy Shield/authentication must not be changed to accommodate fixtures. Next: complete consolidated diagnosis, preserve every security assertion while targeting intended visible controls, compile/review, then one affected focused verification only if evidence supports it. Android12 remains unresolved on an explicitly unsupported platform. No additional workflow has been launched for this recovery backup.


## Reviewed UI fixture correction ready for affected verification

Recovery5c85a32ca9759a140250830c1c5a876ee48d3f10/tree d350f188e1f87cbaa1da3e61d6b3b3355278d41a independently verified. Full candidate1 has349JVM,115broadfocused,74preflight passes; unfiltered384/389 withfiveProfessionalUi fixturefailures, not proven production defects. Details PROFESSIONAL_UI_CORRECTION.md and FULL_CANDIDATE_1_EVIDENCE.json. Two Switch test identifiers, label-independent exact selectors, visible Back/controls, and snapshot/restored neutral synthetic Panic preferences preserve all original auth/privacy assertions and handlers. Reviewer caught draft layout-parent ambiguity and required fresh per-invocation XML and strict method skip checks; all corrected before CI.37hosttests, YAML/shell/diffchecks pass. Clean compileDebugAndroidTestKotlin passes32executed tasks; prior incremental duplicate-declaration diagnostics and successful unmodified clean compile are retained with hashes in professional-ui-verification/VERIFICATION.json; exact stale-state mechanism unproven. No production workaround or assertion weakened. Next: independently verified coherent checkpoint then ONE focused run executing the exact five affected methods first, followed69cases, on both36/37. Full regression on bothsupportedplatforms, original-key signing and debug/production38→39/restart remain pending. No signed39 APK/physical acceptance.


## Android16/17 full release gates prepared, not executed

Focused source18fa461daed3c300f982e206208a75ab3ecb0132/treee5b0a565230b957332f45ceb4361e57dbadf5cce was independently GitHub/gitverified; focusedrun18/40140036506 is in progress. No runtime pass is inferred. Per latest owner instruction, complete full regression and debug/original-key production upgrade/restart proofs are mandatory onboth36/37. Reviewed preparation usesfourdistinctAVDs, freshseparateSDKarchives, strict115/74/389rawcounts, source/SDK/fingerprint checks, cookie/camera process boundaries, debugseed/verify/restart plus testedAPKhash binding, thenoriginalsigner andbothproductionupgrade/restarts beforeupload.44hosttests andshell/YAML/code-diff checks pass;7newplatformevidence cases independently reviewed. Reviewer refinements all implemented; no app/test/assertion change since18fa461. Fullworkflow remains marker-triggered; this recovery backup uses[skip ci] and must not launch or duplicate Androidworkflows. Nextsafeaction: inspect focused18rawfive-first+69bothSDKs; ifallpass andindependentlyaudited, advance marker once andrunonefull36/37workflow. Full/signing/ownerAPK/physicalacceptance remain pending. See SUPPORTED_PLATFORM_RELEASE_GATES.md.


## Run18: five corrections verified; new Android17 player precondition blocks release

Focused18/40140036506 at18fa461daed3c300f982e206208a75ab3ecb0132:349JVMpass; allfiveaffectedmethodsPASSonbothSDKs;API36 69/69PASS;API37 68/69 withonefailed15-secondreal-playerREADYprecondition atPrivacyShield175Test:102,0skips. Artifact12578739317 SHA256c16f523289ef04e1d90fcbbf57872f687180f32c266f7d056f5d67c3e2258677 independentlydigest/CRC/rawXML/source/SDKverified. PanicbodywasNOTreached. BriefownedDialog/nativefocusloss coincideswithplayerrelease; actualcover/epoch/playererror/disposalreason absent, so neitherproductioncausenorfixture-only/flakyclassification isproven. IndependentreviewrecommendsNO-GO, no timeout/assertion/privacy weakening andno unchangedrerun. Fullgatespreparedatdc0e63efe367a201c68d305dda1ef0111e948185 remainUNLAUNCHED; no upgrades/signing/APK. Exactnewblocker/evidencegaps/nextboundeddiagnostic in RUN_18_PLAYER_PRECONDITION_NO_GO.md; durableXML/loghashes RUN_18_EVIDENCE.json andrun18/. Preserveverifiedfive-testfixes andallapplication/securitywork. Nextsafeaction reproducewithactualplayerstate/errorcode,cover+epoch,ownedwindow/lifecycle/focustrace beforecleanup; diagnosecausalitybeforecorrection/CI. Recovery-onlycheckpoint[skip ci], noforcepush. Android12unresolvedunsupported;physicalFoldacceptancepending.


## Owner physical video defect — diagnosed correction pending supported runtime

Recovered4808b08195aa7b011fb4026956c23af7ef61feff/tree5fa317e536eff9919cb09a1b09bf3de7fc42b064. Owner reports realvideo play/loadingloops oninstalled38. Independentreviewconfirms focusablebufferingDialog→Mainfocusloss→registeredplayer.pause feedback defect in38/current39. Source/playerprogressnowinlineSurface, Mainprivacy/auth/Recents/screenshotpolicy unchanged; memorycompatfactory explicitlyowned-buffer-scoped. SeparateactualcryptoRED reproducedPrimaryin-flightrevocationreturningplaintext andopenReaderkey/cache survivingSessionclose;GREEN withpostreadgate/outputwipe+trackedreaderretirement passesrealAEADtampertoo. Noencryptedformat/key/PIN/mediachange ornewplaintextcache. HiddenhostbenchmarkshowsperreadcryptoamplificationbutdoesnotproveAndroidstarvation;no speculativecachechange. Evidence/limits/currenttestcontract docs/release1751/video-repair/DIAGNOSIS.md andhashedsecurity-evidence.tar.gz. FivepreviousUIfixes/testassertions preserved. Added4continuity+2securitytests→395fullsupportedcases; affected13thenoriginal69bothSDKs. ActualAndroid17readyfailurecausalchainnotretrospectivelyproved; newactualencrypted15secREADY/firstframe/progress/Panic fixtureand30secsteadytests mustverify. Localcompile/JVM349passedbeforelastfixturehardening;finalcompile/reviewpendingbeforeONEfocusedrun. Full/signing/upgrades/APK/physicalacceptance stillpending. NoCIstartedfornewvideochanges.

Final pre-CI video correction checks: exact finalAndroidtestcompilePASS26s, production+349JVMmatching-sourcePASS41s,0fail/skip;44Python+8UIreleasehostchecksPASS;13camera policy/18journal, Hidden20/100/500MiB integrity/seek/revocation/lowstorage and53browserpolicy checksPASS. Independentsecurityactual-source RED/GREEN bundlehash verified anddurablysaved; UI/factory/readerchange andfixture cleanup/30secondsteadyassertions reviewed. Finalfixturechecks actualREADY/FIRST_FRAME/progression atsame15sec, uninterrupted30secREADY/isPlaying/noBUFFERING/focus/epoch/identity/loadchurn, separatecontrols, andPaniccover+surface-detach+freshauth. NoMain/PrivacyWindowTracker/ProfessionalUi selectorchanges. Reviewed ONEfocused13affectedthen69bothSDKs eligibleonlyaftercoherentremoteSHA/treecheckpoint. Full395bothSDKs+debug/originalkey38→39/restart and signing remainmandatory. No ownerphysicalperformanceclaim ornewAPK. Exactsource/loghashes video-repair/PRE_CI_VERIFICATION.json.


Run19/40147008491 source1bd929fadd5d982767789d5953e8241b5bb50a43:349JVMpass, affected13each12pass1Hiddenfailure0skip; retained69NOTRUNboth. OriginalAndroid17 encryptedREADY/Panic nowpasses; Primary30sbuffer0,1load/open,positions30695/30576 andfallback30spassboth;3reader-securitychecks passboth. Hiddengenericerror2000/no focusloss/cover/epochchange matches source-provenfixtureorderinghazard (oldHiddenFoundation disposal legitimatelyrevokesrepositoryauthoritycapturedtooearly); exactdisposal event notlogged, notclaimedproof. CorrectedTESTONLY: neutralcompositionSideEffectcommitbarrierbeforeauth/repo/import, explicitpositiveHiddenauthoritycheckbeforeplayercatch. No productionchanges/renewal bypass/timeoutincrease/assertionweakening. Independentreviewapprovedconditionalcompile/checkpointONEcorrected13+69bothSDKs run. Exactartifact12581234539 SHA3b33da536ca7734d0bcd620ddcf90336c04341d46c36520060892d8e99064ffe/rawXML/metrics video-repair/RUN_19_EVIDENCE.json; actualproductionappsource remainsidenticalto1bd. Full395both,upgrades/signing/APKpending.


## Verified focused video recovery — run 20

Tested source105d45dab3e6a5c9117774d302e394b36663da2a; focused run40148525467, artifact12582104290 SHA256 e6849c00374ab49ff47c4eb976abfa44065b85ef8d9adba5f9f013f9a0c080b9. JVM349/349 and affected13/13 plus retained69/69 on each API36/37 passed with zero failures/errors/skips. Raw XML and identities are in video-repair/RUN_20_EVIDENCE.json and run20/. Independent recovery and security reviewers verified the downloaded ZIP digest/CRC, source identity, raw counts and actual playback logs.

Primary and Hidden encrypted playback each progressed for30 seconds with zero buffering transitions, one authenticated load, one source open,1271 reads and37950 bytes. The small silent single-chunk fixture establishes lifecycle continuity, not high-bitrate/cross-chunk throughput, audio synchronisation or physical Fold acceptance. Run19's stale Hidden fixture authority was repaired only in instrumentation: commit/dispose the previous composition before authenticating/importing. No authority renewal or production security assertion was weakened. Android12 failures remain unresolved on an unsupported platform.

Both reviewers approve one existing full release gate from a marker/docs-only checkpoint. Production source, tests, scripts, workflows and Gradle inputs remain identical to tested105d45d. Full115 broad UI +74 security preflight +395 unfiltered cases, debug and original-key production38→39 upgrade/restart are mandatory on both36/37. Signing remains pending; no APK authorised for delivery yet. Next safe action: one full candidate run; diagnose any actual failure before considering further execution.
