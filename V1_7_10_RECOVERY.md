# NovaOS recovery — code55 release blocked

Native bridge milestone: scoped source review accepted the corrected JNI/FD/epoch and bounded-lifetime foundation; Tor-off app/test compilation plus four-ABI native merge pass locally (1m38s). Independently rebuilt native hashes match reviewed outputs. Backend/service/private readiness and runtime integration remain unfinished, so C1 remains open and no Android packet PASS/signing approval is claimed. See NATIVE_ANDROID_BRIDGE_REVIEW.md and CONTINUOUS_VPN_PROGRESS.md under docs/release17103.

Latest owner scope decision: Tor deferred for code55. New shipping guards disable the Tor launcher/search/settings/Evidence selections and manifest services, reject stale Tor requests without VPN/direct fallback, and preserve the implementation/data. Focused Home/navigation host checks pass; merged debug manifest confirms both services disabled and dependency TorService absent. App/test compilation status is recorded in `docs/release17103/TOR_DEFERRAL.md`. New two-case API37 deferral test is prepared, not yet executed. No workflow or security assertion was removed.

Independent review confirms the failed run41688397704 is a **shipping VPN continuity** DNS/native UDP leak, not a Tor-only failure. C1 remains Critical; Tor deferral cannot authorize signing. Existing native persistent-fence cores remain unwired; unfinished bridge drafts are preserved outside this shipping worktree and were not activated. No55APK or54→55 upgrade is claimed. Continue only the essential VPN enforcement correction, exact-source focused packet verification, approval and original-key release; no further Tor/UI development.

Active full-repair development: persistent-TUN native gate and WG transport cores have host race/vet proof and scoped reviews; Android/JNI/runtime integration is still pending, so Critical C1 is not claimed fixed. Primary navigation ownership, guarded restore and preserved legacy-tab recovery compile locally;3/3 encrypted-session JVM tests pass, Android execution remains pending. See `docs/release17103/CONTINUOUS_VPN_PROGRESS.md` for exact unfinished work. Development checkpoints use `[skip ci]`; no release approval/signing activation is added.

Current security correction: independent review rejected the new kernel probe-reservation helper despite113 host checks. Actual failed TCP connect releases its source binding before revocation, and real counterexamples expose inherited SOCKS/cancel paths. Root reproduced these failures; the unused helper was moved outside Android sources to `native/continuousvpn/rejected-kernel-probes/`. Do not integrate it or claim its tests prove containment. Parent-only native readiness with unconditional real-TUN denial during PROBE is under scoped design review. The separately named JNI/native build is in progress; no new Android run or signed code55 exists.

Owner UI steering (2026-11-03): immersive standalone Private Apps is recorded in `docs/PRIVATE_APPS_NEXT_UI.md` as the first planned1.7.11 UI task. It is deferred while code55 VPN/DNS containment remains unresolved; no browser presentation, version or branch change is made.

> **Current code55 status: BLOCKED by real DNS and native UDP escape during retained-child VPN teardown.** Frozen source24c29fd86e8493b00365357071d9c45e240eb3c4 has8/8 fresh Android passes plus18 compatible535 baseline passes, but the full packet gate failed. These are not release approval. No code55 production APK, activation marker or signing approval exists. The last delivered code54 record remains historical; see the latest blocker checkpoint below.

## Historical delivered release — NovaOS1.7.10.2 / code54 — 2026-11-02

The following approval applies only to the historical code54 release, not the blocked code55 candidate. Owner-approved Browser/Tor Beta scope defers advanced vault-encrypted/memory-only provider storage, with truthful one-time notice and persistent Help disclosure. It is not a storage fix. Independent security and actual production artifact reviews approved that code54 owner-acceptance release within that explicit scope. No routing/authentication waiver.

- Frozen application source:`9efd19540a1d75efa1b30f4e23c9cbb096f93aff`; release activation:`7f2c73fcaa32189b39829e829bcaecf7918ff50e`. Same release/1.7.10-private-home, packageuk.co.traynor.privategallery, API37 only.
- Focusedrun41368247494:12API37+7JVM allpass; artifact13108297290 SHAfd845324525f578d82cee2f01f7d013407e14f43dd7d26aa02755d0b34ccf073 independentlyverified. Retained25UI/3actualrouting cases remain source-scoped; nofull266single-sourceclaim.
- Productionrun41369126405/job125035601491 SUCCESS: originalkey, releasebuild/lint/package/cert/API/OCR/alignment, exactsigned53baseline, reviewedbaseline-compatiblefixture, actualinstall-r53->54 and restart/data checks. Rawseed/verify/restart eachOK1. No uninstall/clear-data/replacementcredentials.
- Artifact13108872409 archiveSHA`7e141abd9ba9c1e9e440dd0e08bf6cb6bfd869808f0d5bec226ff43fcb858574`.
- APK138323890bytes SHA`3e9497d87c074d6d49bfd42a2b44cc5f2cd46ec0b7476d35d030e75b9cd35ce2`.
- OriginalcertificateSHA`94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`. Downloadedactualv2RSA/SHA512signature+full134chunkcontentdigest independentlyverified byroot/reviewer; matchesproductionreceipt.
- Download:https://chatgpt.com/library?file_id=libfile_f8a84814a5c08191b7231c39f033986e&account_id=personal

Detailedfeaturehandoff,acceptedBetalimitation,verificationledger andFoldchecklist: docs/release17102/STATUS.md. Independentfrozenapproval: BETA_SECURITY_REVIEW.md; actualdeliveryapproval: PRODUCTION_REVIEW.md inthatdirectory. PhysicalFoldacceptance remains owner testing. Preserve this release and any newer valid GitHubwork; no newbranch/restart. Oldstrict-encryption reports below are historical scope, not proof the deferred issue was fixed.

---

# Beta source verified and production gates activated — 2026-11-02

Frozen source `9efd19540a1d75efa1b30f4e23c9cbb096f93aff`, NovaOS1.7.10.2/code54. Owner-approved Browser — Beta/Tor — Beta scope explicitly defers encrypted/memory-only provider storage; accurate one-time notice and continued Help disclosure, no transport/authentication weakening. Independent `BETA_SECURITY_REVIEW.md` Decision APPROVED exactsource, no unresolvedCritical/Important implementationfinding withinacceptedBeta scope. Historical strict-storage blocker below is superseded byexplicitownerdecision, NOT fixed.

Run41368247494/job125032936376 SUCCESS. Artifact13108297290 independentlydownloadedSHA256fd845324525f578d82cee2f01f7d013407e14f43dd7d26aa02755d0b34ccf073. Original12API37cases+7JVMcases,0fail/errors/skips; source receipt and exactBeta matrix accounting verified independently byrootandreviewer. Prior25UI/3liveVPN/Tor checks pinned andsource-scoped; nofull266single-sourceclaim.

`.acceptance17102-candidate` pins exactsource/run/review/matrix/fixture. Existingacceptance1762 workflow reviewedcode54job nowmayactivate, requiresallgatesbeforesigneraccess. Originalsigned53baselineartifact13103302785 andsourcef923d62 pinned; originalcertificate and genuineinstall-r53->54/restart/data-preservation mandatory. No54APKdelivery claimed until production jobandindependentactualAPKchecks pass. Follow currentGitHubrun, not stalehistoricalfailures. Samebranch/package/version; no uninstall/data clear/replacementcredentials.

---

# Owner-approved Beta scope / code54 release preparation — 2026-11-02

Continue same release/1.7.10-private-home frome923972e. Owner explicitly defers advanced encrypted/memory-only browserengine, accepts Browser/Tor Beta with accurate notice, and requires disabling any capability independently judged unsafe. This supersedes historical strict-profile requirement below; it does NOT assert the storage issue fixed.

Implemented Browser — Beta/Tor — Beta labels, shared V1shell retention, one-time pre-use privacy page gating Hidden runtime, one nonsecret revision acknowledgement, owner Home/Lock/Panic, and accurate Settings/ClearSession text. Existing transport/session/storage engines unchanged. New focused12-case Beta matrix usesexistingfastworkflow. Local launcherlabels RED->GREEN;11presentation/releasehosttests pass;4baselinecompilerdenial/validation tests pass; originalsigned53archive/APKpinchecks and retained25+3 XML/source-scopeevidence validation pass. Newruntime and finalindependentreview pending.

Prepared inactiveacceptance17102 job inexistingacceptance1762 workflow, exact-source/review/matrix/fixture and prior-artifact checks before signer, originalsigned53 baseline, baseline-compatible53->54 fixture with10->11Home migration, originalcert and actualinstall-r/restart/data gates. No acceptance17102marker exists; no54signing/upgrade/APKclaimed. BETA_SCOPE.md records ownerdecision/risks. Prior evidence and old blockers below remain historical, not current owner's scope.

---

# Hidden Browser storage re-investigation — 2026-11-02; code54 still blocked

Recovered authoritative remote HEAD `4351a7318ac55f665c906b222e7b93442c125fd0` on the existing release branch. Application code remains the verified `2b67f751a91961c138b4290c165415931e7c8b89`; no UI restart, version change, signing activation or new Android workflow/run.

The owner requested a final lifecycle/storage repair. Root and a fresh independent reviewer traced the actual implementation and current primary Android/Chromium API/source. The blocker is confirmed: supported Android WebView exposes no app-supplied vault-key encryption or off-the-record profile backend. Its cookie store may persist even session cookies. `LOAD_NO_CACHE`, not calling `flush`, asynchronous clear and a scheduled deletion job cannot establish no persistent plaintext across abrupt death.

Actual sensitive provider roots are `<noBackupFilesDir>/hidden-vpn-webview-v1/<lease UUID>/data/<provider subtree>` and `<cacheDir>/hidden-vpn-webview-v1/<lease UUID>/<provider subtree>`. Parent runtime also initializes an unused `hidden16-*` profile under its normal WebView bases; no evidence attributes native-child login cookies to that unused parent profile. Do not overstate this secondary issue.

Revocation and disk retention are distinct: current HiddenSession signal revokes broker authority and kills the child; Binder/proxy/upload admission is checked. Parent/child kernel locks protect active roots; death releases locks, and persisted cleanup later removes dedicated data/cache roots. If the package cannot execute cleanup immediately (force-stop, power loss, scheduling delay, IO failure), files may remain after authentication has been revoked. Moving/encrypting files only after close cannot protect the active/crash window. Disabling cookies/site storage or interactive requests would violate required legitimate browser behavior and was not done.

Independent report: `docs/release17102/HIDDEN_STORAGE_REVIEW.md`. No safe minimal repair with the existing public AndroidX WebView1.13/API37 APIs was identified. Resolution requires a browser engine/provider with a supported nonpersistent profile, or a genuinely vault-key encrypted writable filesystem/storage backend covering all provider IO. Either needs implementation and renewed exact-source browser/auth/storage routing evidence. This investigation did NOT implement such an architecture and does NOT claim exact-installed-provider plaintext scans, a repaired lifecycle, security approval or APK delivery.

Reused verified25-case API37 artifact13107287404 and unchanged3-case live routing evidence. Local archive accounting still confirms25/25. Host Hidden foundation/revocation and browser policy checks pass (373 foundation and227 browser/diagnostics checks plus supporting assertions). No new CI was launched because no application code changed; runtime cannot prove a repair that has not been implemented. Full signing approval remains withheld; no code54 original-key upgrade/signing/checksum exists. Preserve original signed53 and existing encrypted content.

---

# Final verified UI closure — 2026-11-02; code54 UNSIGNED / SECURITY BLOCKED

Frozen application source2b67f751a91961c138b4290c165415931e7c8b89. Existingrelease/1.7.10-private-home preserved. Run41365205828/job125023798148 SUCCESS: hostchecks, app/testAPKassembly andfocusedJVM7/7; originalAPI37XML25/25pass,0fail/errors/skips. Artifact13107287404 independentlydownloadedSHA2561794260bd5bbf24eb1fc5b8c5d9a06c616c8ce427086a7eb6d4f6e0ca8227056. scripts/verify_nova17102_runtime.py --matrix docs/release17102/closure-cases.json --ui-only confirms25exactoriginalcasesonceeach. Allfour07cfailurespass: actualExposureSaveCopy/originalpreservation, fullAIreplacement/regeneration/consent/discard/save/lifecycle, originalblack/readinghardwarecapture/volume/exit, Evidenceactualimport/OCR/correction/search/remove/reopen. Newconcealedtapregressionpasses. Prior07c79/83and4f4threeactualroutepassesareseparatesource-scopeevidence, NOT afull266single-sourcepass.

Finalsecuritydecision: conditional source review only, NO54 signingapproval. Provider-managedWebView cookies/DOM/sessionprofilefiles areisolatedtemporaryappprivatefiles, NOT encryptedbyHiddenvaultkey. Encryptedbookmarks/history/catalogues, Androiddeviceencryptionandcleanup do notclose this explicitrequirement. Do notclaimencryptedcookies oractivate54signinguntilownerexplicitlyresolvesscopeoractualsecurearchitectureisimplemented/reviewed/tested. Original-key53->54upgradeandphysicalFoldacceptancealsoNOTperformed. No54signedAPK/hash/certresult/linkexists. Latestdeliveredoriginalsignedcandidate remains1.7.10.1/code53 below. No replacementcredentials,uninstallorclear-data used.

Completedchanges,verificationledger,remainingblockerandFoldchecklist: docs/release17102/STATUS.md. Independentexactsourceconditionalreview: docs/release17102/SECURITY_REVIEW_CONDITIONAL.md. Resume atGitHubHEAD; preserveallnewervalidwork, no newbranch/restart. Historicalentriesbelowrecordprogressandfailures, supersededbythisfinalstatus.

## Narrow closure batch prepared — still unsigned54

Fixes after07c79/83: Editor disposal nowcapturesimmutablecompositionbuffer/bitmap snapshots instead ofreadingdelegatedstateinside deferredDisposableEffect; regenerationcouldotherwise wipe newlypublishedbytes. OriginalAIreplacement/regenerationruntimeassertionsretained. ExposureUItest startsatneutralthumbawayfromAndroidBackedge, assertspositivevalueandUndo beforeactualSaveCopypixels; rendererproofalreadygreen. Black-onlytapmodifier ownsdown/freezesinitialpoint/followsbindingandlatestcallback; swiperejectionretained, newgesturetest +unchangedactualblackcapturetest. Evidencefilteredresult materializedthroughactualtaggedlazylist, freshquery andauthoritativeencryptedcataloguesearch assertion. Existingworkflow [verify17102][engine17102][closure17102] runs25cases (fourfailedclasses+gesturetest), original83/fullmatrixevidenceretained; currentcomplete266matrixnotclaimedgreen. Signingblockerunchanged.

## Final engine batch result — unsigned54, signing still blocked

Exact source07c14135e4a2eb92ac6725e096406f8ba9bcc15d run41363619033: host/app/test compilation PASS; focused JVM7/7 PASS; API37 selected83:79pass4fail0error/skip. Artifact13106692387 downloaded/hash verified SHA2569d599e3be0d4f03c564b88bb23771bcf3114ed262f156be2f5fd2b54feebb606. Actual pixel tone/SaveCopy/rotated crop and real CameraX focus/late-revocation cases pass. Previous Evidence grouping and AI consent visibility failures now pass. Remaining4: exposure-slider SaveCopy assertion, AI source replacement timeout, black photo capture timeout, Evidence lazy search row absent. Investigating affected classes only, preserve original assertions. No new route claim;4f4 live3-route evidence retained. Cookie/profile vault-key encryption requirement remains unresolved, independent full signing approval withheld. No54 APK/signing/upgrade exists. See docs/release17102/STATUS.md and SECURITY_REVIEW_CONDITIONAL.md.

## Live transport green; final focused UI/capture/render checks — unsigned54

Source4f4dd4f15808f1aa95160da52ac49e32fd054bee run41362013309 artifact13105328926 archiveSHA256c83d50172d840c6139ffa63e9c38b03e954e29cd670bc25347616e1707af7025 independently downloaded/rawXML inspected.109UI:107pass2fail,0errors/skips;3actualVPN/Tor/networkloss cases:3pass0fail/errors/skips. Actual JavaScript/HTML/multipart bodies, WireGuard-only origin/DNS, nativeWebRTC/UDP revocation and Tor v4/v6/UDP/DNS sink/form/upload/download checks passed original runner verifiers. All earlierSAF/report/privacy/history/browser failures pass. History not reproduced; no speculativeproductfixclaimed.

Remaining2UI fixes: Evidence helper nowmaterializeslazycard then scrollsactualdeepbutton; grouping/storage assertionsretained +realoversizedcardgesturetest. CreateImage focuscontrollers nowreadinsideModalBottomSheet Dialogroot (priorattempttargetedActivityroot); consentteststrengthened visibleContinue/Cancel andzeroRequests. SourceScope closures: actualExposure/TemperatureboundedneutralPhotoEdit+renderer+SaveCopy, focusednumerical/JVM/pixeltests; guardedCameraXtapfocuswithtruthfulfeedback/realhardwareandlatecallbacktests; crop presets nowaccount EXIFandquarterturns forfinaloutputratio, host RED->GREEN andrenderdimensiontest.

Final target [verify17102][engine17102] usesexistingworkflow,compilesall,newJVM model+host checks,and83UIcases coveringchangededitor/camera+allEvidenceLocker/Modalcases/helperregression. No routingrerun:browser/transport source remainsbyteidenticalto4f4greenroutecheckpoint. Source-scope evidence isexplicit; completefullmatrix remains retained,notfalselyclaimedallcasesononesource. No54signingactivation/productionAPK. Cookie/providerown-keyencryption requirement remainsunresolved andindependentreleaseapprovalwithheld. Latestverifiedsigned53 stays availablebelow.

## First Android result and coordinated repair — unsigned54

Sourcee3c8a97f849e11b74a0240890fee447934e6424d run41359796301 compiled app/test/JVM and passed hostchecks. Original artifact13105980445 archiveSHA256c00a7dff5199f565d19907a3a306ac66bbdf50cc22245f194ef6d7ecd037f571 was downloaded and rawXML inspected:253UI cases,234passed19failed,0errors/skips. Route stage did not execute afterUI failure, so NO newroutepass claimed.

Diagnoses/fixes: staleOpenDocument fixtures now matchactualOpenMultipleDocuments + ownerapprovedOCR/group registration; originalauth/expiry/no-read/provenance retained. Sensitive report checks followactualfull-pageActivitywindow; generichostfallback now remainssecureforwindowlifetime (noeligibilityauthority), productionMain tokenpolicy unchanged. LazyEvidence matcher reacquired afterscroll/IME layout. Browsercopy/mode-label selectors corrected; obsoletepre-activationassertion updated to alreadyshippedverifiedgates withmanifest/coldrejection checksintact. Dialogfixture waitsactualfocus withinoriginal3seconds. CreateImage clearsfocus/IME beforediagnostic/consent. History timeout remainsUNRESOLVED:authorityactive andall30recordsauthenticate; onlydeepersemantics/windowdiagnostics added,no productguess or timeoutincrease.

Independentreview accepted narrowrepairandcontrolledverification. Next existingfastworkflow [verify17102][repair17102] runs109cases(entirefailedclasses+2genericwindowtests) plus3liveVPN/Tor/networkloss tests. Original255UI+3route fullmatrix retained. UI failure no longer preventsindependentrouteevidence; alloriginalexitcodes/accounting retained andjobstillfailsunlessboth+packet/DNS/bodyverifierspass. This isexplicitdiagnosticscope,notfullreleaseapproval. Cookie/providerstorageapp-keyencryption remainsblocking54signing. Latest signed53 unchanged.

# NovaOS1.7.10.2/code54 — internal-app overhaul, NOT RELEASE APPROVED

Owner-requested continuation from authoritative73e896054b942734f8c37b5717ed05a2b8679b4f. Latest signed baseline remains1.7.10.1/code53 (details below). Audited87 remote heads max53; assigned54 on same release branch. No54 signing marker or owner APK exists.

Coherent batch: eleven-app model with exact8/10 migration; Hidden-only Tor icon and Primary rejection; fixedVPN Browser/fixedTOR app admission ignoring legacy mode preference; shared themed connection/status/browser surfaces; fixedmode settings, ClearSession with checked authenticated history deletion, Tor NewIdentity retires whole session/process then requires freshlaunch (no exit-IP guarantee). Existing mode-separated PrivateApps/bookmarks/history retained, Tor search targetsTor. Browser proxy/network/JS/Form/POST/upload engines unchanged. Evidence dashboard counts/import/search/recent/shortcuts/Information; existing full-page OCR/grouping/exact-AI-consent/export preserved. Camera unobscured preview/control dock/top hardware controls/recent capture; existing capture/finalization/concealed engine preserved. Editor stable preview,collapsibleAI,source replacement,crop ratios,selectedtheme,plaintext-wiping Home/Lock/Panic; existing save/provider/externaleditor fences preserved.

Local Home launcher RED->GREEN; geometry/host/presentation checks pass; Android compilation/runtime pending one existingfast-development workflow [verify17102] with253 affectedUI cases+3 liveVPN/Tor/networkloss cases and packet/DNS/body assertions. No unrelated workflow created. Final case matrices docs/release17102. Plan docs/superpowers/plans/2026-11-01-novaos-apps.md.

Independent source reviewer found and verified fixes for failedClearSession falsely closing and inaccessible existingTorPrivateApps. No additionalCritical/Important implementationfinding identified. IMPORTANT UNRESOLVED REQUIREMENT: provider-managed WebView cookies/DOM/session files use ordinary isolated app-private transient profiles; they are NOT encrypted with Hidden vault key. Bookmarks/history are vault-encrypted. Existing Android encrypted-device storage and cleanup are not represented as app-key encryption. Encrypted cookie snapshots would not fix provider-managed plaintext state. Full signing approval WITHHELD pending owner resolution or actual secure storage architecture; do not bypass or deliver54 as complete. Finish focusedverification and checkpoint currentwork; preserve prior signed53 APK.

---

# DELIVERED — NovaOS 1.7.10.1 / code53

Completed2026-11-01. This final status supersedes historical in-progress/pending entries below.

- Frozen signed app source: f923d62de7696e731c2ec7809d772081ab0e1204; release control2519bbd5ab4b623fe33c21e9dead1aaee8d420e3. Existing release/1.7.10-private-home retained.
- API37 grouped verification run41354819734:104 mandatory cases,0 failures/errors/skips. Local original XML validation and independent reviewer agree. Artifact13102114242 archiveSHA44f83b2f5adfbe7ed8fac2ff3ee430109e5e3b4ee8ff6618e77dfc5aac759d31.
- Production original-key acceptance run41355731517/job124995274579 succeeded: immutable original signed52 baseline; reviewed baseline-compatible fixture compilation; original signer; release lint/assembly; package/certificate/API37/OCR/native alignment; genuine install-r52->53 and restart without uninstall/data clear. Seed, after-upgrade and after-restart eachOK(1 test).
- Signed artifact13103302785: https://github.com/traynor1987/Private-gallery-/actions/runs/41355731517/artifacts/13103302785
- Archive SHA256:fcc70278aca63b71e5321fdbd1f2e16235ff5fec7f3a3cb27b731280ec5e1707.
- APK:NovaOS-1.7.10.1-code53-owner-acceptance.apk;138241966 bytes; SHA256:8a13404b42720e5962478345fad964cdcc923a3115b632beb412a2813547a3a3.
- Original certificate SHA256:94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. Downloaded APK actual v2 RSA/SHA512 signature, certificate and complete chunked content digest independently verified locally, in addition to SDK apksigner and Android install-r validation.
- Package uk.co.traynor.privategallery;NovaOS1.7.10.1/code53;min/targetAPI37. No package/key/store identity migration.
- Download: https://chatgpt.com/library?file_id=libfile_95022d95c0708191a5d4c75eab1023e7&account_id=personal

Delivered: approved ringed N branding; immediate authenticated Nova Home root; ten adaptive launcher destinations; removed legacy bottom navigation; full-page existing internal apps and Private Apps settings/editor; coherent Home/Back/Lock/Panic; encrypted owner-specific Home state and Primary/Hidden separation; Evidence/browser/camera presentation; screenshot-policy consistency; Fold/light/dark/large-text presentation. Existing crypto/consent/transport authorities reused. Lock Home invalidation and dark Home text defects fixed and mandatory runtime assertions passed. TransferHistory transient timeout did not reproduce with stronger owner/30-record checks; no invented crypto diagnosis or product workaround.

Independent source/control reviewer /root/release_security_review approved frozen source and release bindings, no unresolvedCritical/Important. Physical Galaxy Z Fold acceptance remains owner testing, not remotely claimed. Install over existing signed code52 without uninstall/clear. Check Home app launches, child/root Back/Home, Hidden PIN/timeout, Fold/theme, screenshots on eligible pages and Lock/Panic; existing encrypted content must remain.

---

## Frozen code53 source and production activation
Frozen app/source f923d62de7696e731c2ec7809d772081ab0e1204 passed run41354819734:104 Android17 cases,0 failures/errors/skips; host/JVM/app/test assembly pass. Original downloaded artifact13102114242 SHA44f83b2f5adfbe7ed8fac2ff3ee430109e5e3b4ee8ff6618e77dfc5aac759d31 independently verified against original XML. Corrected Home dark screenshots inspected. TransferHistory passed unchanged product and original assertions plus owner/30-record preconditions; no claim of a diagnosed crypto bug. Independent reviewer approved exactsource including lock/contrast changes with no unresolvedCritical/Important. Marker binds source/run/review/fixture/matrix hashes. Original-key code52->53 upgrade/restart and production validation remain mandatory. NO signed code53 delivery claimed yet.

## Runtime diagnosis — code53 remains unsigned
Run41353800836 at042b747ba36da6e38da82a9c7eb2fe6625697f02 assembled app/test APKs and passed host/JVM checks. Original XML artifact13102841500 archiveSHAc76a1f3384a17db7254fc765353a33d2d7f08356cb063f885c8d12ce584ce139 contains104 tests:102 passed,2 failed,0 errors/skips. NovaNavigation line42 found observable Home retained after synthetic unconfigured Primary Lock; fixed explicit Home clearing on active-owner/global Lock, preserving independent live Hidden when only Primary locks. Screenshot inspection found inherited black Home text in dark theme; explicit themed content colour and actual TextLayoutResult colour assertion added. TransferHistory wait failed; product crypto/layout unchanged pending direct30-record/active-owner and full semantics diagnostics. No signing activation; retain all104 assertions.

## Interrupted-work recovery — 2026-11-01
Recovered authoritative remote HEAD 3d3385d02272249f419d089a1660ff27f72de804. Run41350699631 job124980010680 passed focused host checks, failed Kotlin compilation, and never executed runtime tests. Root cause: missing explicit imports for runtime.SideEffect, foundation.layout.consumeWindowInsets, foundation.layout.statusBarsPadding and material.icons.Icons. Added only those four imports; retained Compose BOM2024.12.01 and all working implementations. Existing1.7.10.1/code53 identity retained. Runtime, independent review, original-key52->53 upgrade and signed delivery remain pending; no success claimed.

# NovaOS 1.7.10.1 professional experience — implementation in progress

Continues existing release/1.7.10-private-home from authoritative d3ccfff2b433bd651c054dfc5238948b8c1eca46. Signed code52 source f301ee73320df2e366b7cd258dae5105296237a7 and APK SHA0b22f736055738f4e3f552e4939cae1ea767cbce58a4908d04666786e0c37ba2 independently reverified. Audited87 remote branch heads, maximum52: assigned patch1.7.10.1/code53. Package/original certificate/API37 policy preserved. Original release completion below is historical; NO signed code53 APK or release approval exists yet.

Implementation: ten-app compatible Home model; old bottom bar and floating Home removed; immediate post-auth Home admission; distinct phone Gallery/current-Space Vault/Favourites; shared app headers with Home/Lock/Panic; premium navy/blue adaptive Home, ten cohesive icons, current Space/search/recent/pins/context menus; approved ringed N adaptive/legacy/monochrome resources; human-facing NovaOS/Space terminology without package/AAD/key/store renames. Private Apps library/edit/settings are full-page with existing broker/controller fences, app-style Exit/Switch/PWA controls, exact-owner revocation and local settings Back. Evidence root chrome and camera/browser controls use Nova presentation, protected workflows/consent unchanged. Opaque collection position retained only in-process under exact owner/auth identity, cleared on Home/Lock/new launch. Native concealed camera modes retain existing discretion and gesture controls.

Local checks: Home/model migration/position/navigation RED->GREEN; existing Home persistence checks; approved asset/package/API37 check; Evidence model/crypto/revocation/concurrency/JSON/archive checks; Private Apps/catalogue/screenshot-policy/DNS checks; camera capture policy/journal checks all pass. New UI screen syntax and workflow actionlint pass. Runtime verification remains pending. Independent source review found two Important concealment regressions (Hidden label in the new header, and no Settings escape from concealed pages). Fixed by suppressing owner labels/features while concealed, restoring a neutral concealed Home/back path and providing a guarded Settings escape even on a concealed Browser route. Host owner-label/root-route assertions observed RED->GREEN. HideContent1761Test is now mandatory and retains restart/Panic/no-Hidden-label/sentinel preservation assertions. Direct Home stays available beside local Back on Evidence child pages; new large-text header coverage added.

Prepared one grouped API37 matrix:104 required cases across18 relevant UI/security classes, including baseline54 cases, new navigation/adaptive Home, Private Apps controls, professional presentation/modal and camera presentation. No live Tor/network or unrelated media engine suite expansion. Original-key code52->53 fixture adds encrypted Home layout/recent preference preservation to existing media/Evidence/Private Apps/browser/credentials/Recently Deleted checks. New signing job is inactive until exact-source runtime, source review and reviewed fixture/matrix hash marker are present. No replacement keys/uninstall/data clear.

Plan: docs/superpowers/plans/2026-11-01-novaos.md. Next: complete source review, push coherent source batch with [verify1710], inspect all104 originalXML cases/screenshots, fix demonstrated failures, freeze/approve source, activate code52->53 original-key job and deliver one APK. Keep previous code52 acceptance intact.

---

# FINAL OWNER-ACCEPTANCE DELIVERY — 1.7.10 code52

Completed 2026-11-01. One production-signed acceptance APK published after all gates passed.

- Frozen application source: f301ee73320df2e366b7cd258dae5105296237a7; tree48425f7ab0581bcba1b43e1c8b29f690f82bd378.
- Successful release control: fd1695f792304aba9813448538a4ce4c393b33cd.
- Android17 grouped verification: https://github.com/traynor1987/Private-gallery-/actions/runs/41332619845 —54 tests,0 failures/errors/skips across11 required classes. Host checks and app/test assembly passed.
- Signed release/upgrade: https://github.com/traynor1987/Private-gallery-/actions/runs/41336291205 —release lint/assembly, package/certificate, OCR distribution,16KB native alignment and original-key code51->52 upgrade/restart all passed. Baseline seed, post-upgrade verify and post-restart verify eachOK(1 test). No uninstall or data clear.
- Artifact13095218976: https://github.com/traynor1987/Private-gallery-/actions/runs/41336291205/artifacts/13095218976
- Archive SHA-256: b7c124eebf67aeb9d5fa5143146652250556ea184c161b38544cdce7653d0a29.
- APK: Private-Gallery-1.7.10-code52-owner-acceptance.apk;137915802 bytes.
- APK SHA-256: 0b22f736055738f4e3f552e4939cae1ea767cbce58a4908d04666786e0c37ba2.
- Original certificate SHA-256: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. Downloaded APK's actual v2 RSA/SHA512 signature, single original DER certificate and complete chunked content digest independently verified locally, in addition to SDK apksigner verification and Android install-r acceptance.
- Package uk.co.traynor.privategallery;1.7.10/code52;min/targetAPI37. Code52 was unused before assignment.
- Separate security reviewer /root/security_review approved exact-source code/runtime and final release evidence: no unresolvedCritical/Important findings. Physical Galaxy Z Fold remains owner acceptance, not claimed as remotely tested.
- Direct APK: https://chatgpt.com/library?file_id=libfile_ba8af637cf54819185d0298cccc5e0fa&account_id=personal

## Delivered feature inventory

Actual authenticated Private Home is integrated at MainActivity root with adaptive light/dark rounded-icon grid,8 existing feature destinations, long-press context actions, search/filter, pin/hide/reorder/restore, density/icon sizing and recent shortcuts. Primary/Hidden preferences and metadata search use independent encrypted owner stores and revocable access; lock/Panic clear sensitive presentation. Hidden remains PIN-only. Existing Gallery/Browser/Camera implementations are reused.

Home/startup and feature Back/root/Home navigation are integrated. Existing installs retain Gallery startup until changed in Settings > Appearance & Navigation > Startup Screen; new installs default Private Home. Preferences are per-vault. Fold/configuration route retention keeps only feature navigation and rechecks retained authenticated authority. Explicit Lock, Panic, timeout and background/foreground policies remain enforced.

Evidence Home, library, incidents/detail/timeline, OCR review, AI consent/analysis and export use dedicated Activity-owned pages instead of large workflow dialogs. Guided encrypted import supports selection/removal, offline OCR, text/time corrections, grouping/split/merge labels and explicit approved save. Existing encryption, integrity, OCR, AI and export controllers are reused; cloud requests require explicit exact-payload consent and never auto-upload originals.

Screenshot repair addresses the successful settings-auth callback guard and capture-policy priority/lifecycle interactions. Eligible app screens honor Allow Screenshots; credential/protected AI/export pages, background/Recents and lock protection remain enforced. Android/system/DRM-protected content may remain uncapturable. Runtime permission/focus assertions were not weakened; emulator's SystemUI fullscreen tutorial is acknowledged only in guarded test preflight.

Original-key upgrade verified preservation of Primary/Hidden encrypted media, Evidence records/OCR/corrections/incidents/timeline/analyses, Private Apps, browser metadata/preferences, Recently Deleted, credentials and other preferences. Only exact top-level AndroidX performance metadata files are excluded from ciphertext snapshots because package-update timestamps necessarily change; all owner-data checks remain mandatory.

## Short Samsung Galaxy Z Fold acceptance checklist

1. Install this APK over1.7.9 without uninstall/clear; verify existing Primary/Hidden media, Evidence and Recently Deleted.
2. Change Startup Screen to Private Home; launch all8 features; check nested/root Back and Home.
3. Pin/hide/reorder/search; restart and fold/unfold on cover/inner displays; check theme/density.
4. Enter Hidden byPIN; confirm independent Home/search/layout, timeout/Lock and no Primary results.
5. Review screenshot/document import, offlineOCR/group corrections, exact cloud consent and encrypted export.
6. Toggle screenshots on eligible pages; verify credential/Recents privacy and Panic Exit revoking both vaults.

Historical recovery entries below document implementation and resolved failures; the final status above supersedes older pending statements.

---

# Private Gallery 1.7.10 recovery

Status: baseline recovered; implementation has not started. No 1.7.10 APK or security approval exists.

Branch `release/1.7.10-private-home` was created from release-control commit `3fc699ae5ecfbc7678e835240c594e38daf4e1da`, preserving the controls and evidence after signed application source `19e3275ef17746e780d64e6a684d94bab2cabdc3`. The application and Gradle build source trees are identical between those commits.

GitHub Actions production run 41301316948, job 124832650609, succeeded, including original-key code 50 to 51 in-place upgrade, restart, and APK validation. Artifact 13081837273 archive SHA-256 `a7ad5e4d6c243e195a5d35db1f4a12711de1842bd6260aa318203e53ac691cb7` was independently downloaded and hashed. Its APK SHA-256 is `9f99f0e8df4dbdc6219aa147525866f0ace699b1b4ae9bab1f6b647ac4d7a178`; the APK bytes were independently hashed. The artifact reports package `uk.co.traynor.privategallery`, version 1.7.9/code 51, original certificate SHA-256 `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`, and successful synthetic upgrade/restart. Physical Fold acceptance remains owner-required. Focused run 41295673256, job 124815899187, succeeded at source 19e; its published artifact is 13079244254.

Do not rely on the older failure status at the top of V1_7_9_RECOVERY.md: the later successful production run supersedes it.

Next: verify remote branch and no competing writer; audit all issued version codes before assigning the next unused code above 51. Implement the full owner-approved 1.7.10 specification, including Primary/Hidden navigation isolation, startup migration, Home, launcher, evidence pages and guided import, screenshot lifecycle policy, and focused tests. Keep existing encryption and routing boundaries. Review CI trigger conditions before pushing implementation; batch focused API 37 checks. Perform independent security review and verify original-key in-place upgrade from code 51 before release. Never uninstall or clear data. No production signing or owner delivery is authorised until all applicable gates actually pass.

Environment observation: local Android SDK, emulator and Gradle distribution were unavailable; Gradle bootstrap failed with `Network is unreachable`. GitHub Actions provided the successful prior build and may be used for batched 1.7.10 verification after its workflow and access are reviewed. This observation does not establish that CI is unavailable.

## Launcher rules checkpoint

A small standalone immutable `HomeLayout` model now defines the eight shortcut IDs, ordered visible items, hide/show, pinning, reorder, reset and label filtering. `tests/home1710/HomeLayoutChecks.java` exercises those rules on the host using the JDK compiler entry point. This is an unintegrated foundation only: it does not persist, choose a vault, authenticate, render Home, or implement navigation. It must not be described as a completed launcher. No Android build or security test has been run on it. All remaining requirements above remain open.

## 2026-11-01 integrated development pass (not release-approved)

Remote HEAD independently recovered at 817b28998cecc2ee8f88d98aff0afd1cd1cf8bde. Audited app/build.gradle.kts at 87 fetched remote branch heads: highest issued version code 51; code 52 was unused and is now assigned to 1.7.10. Original baseline acceptance179 job 124832650609 rechecked successful.

Added actual Compose Home and activity routing for eight shortcuts, owner-bound encrypted Home preferences using existing revocable vault leases, layout serialization checks, vault-scoped metadata search, Home return controls, and activity-owned Evidence workflow pages. Sensitive AI/export credential pages retain capture protection. These changes require compilation, instrumentation, independent security review and further completion. No APK exists for 1.7.10 and no release gate is being asserted.

Local Java compiler is available via `java com.sun.tools.javac.Main`; layout and persistence host checks passed. Gradle bootstrap failed with Network is unreachable to services.gradle.org. Existing fast-development workflow now has one branch-specific grouped Home compilation job, excluding the unrelated default job for this branch.

Outstanding: complete guided evidence import review before registration, direct Startup Screen preference controls in Settings, Hidden child-process Private Apps/Home routing, robust Fold/session restoration, instrumented Home encryption/navigation/search/screenshot tests, review findings, complete API37 runtime gates, original-key code51->52 upgrade and signing. Do not treat this checkpoint as finished or deliver its APK as accepted.

### Continuation after independent review

Independent review identified stale Hidden Home authority after outside() and an overriding Evidence page Back handler. Home access now refreshes on explicit Home requests; the Home return control does not depend on a retired preference capability. Evidence pages use the hierarchical Back lambda. Home visibility/authentication startup state now remains in the existing in-process ViewModel across configuration changes. Added direct per-owner Startup Screen options, Hidden child Private Apps entry flag (UI-only; existing Binder authentication unchanged), and Home return on authenticated Hidden child return.

Added an in-memory guided import review: select/remove encrypted originals, verify bytes and run existing offline OCR, review/correct transcripts and timestamp provenance, edit group labels to split/merge, then one explicit catalogue commit. External multi-document imports preserve encrypted originals and defer evidence registration to review. Added Android Home storage isolation/encryption/startup/revocation test source; runtime execution remains pending.

First workflow attempt 41324315361 failed before any jobs started. actionlint identified a duplicate job-level `if` introduced while excluding the default fast job. Combined the branch predicate into the existing job `if`; actionlint now passes. No Android compilation or security gate passed in that failed run. Use the corrected grouped compilation next; do not repeatedly rerun the invalid source.

### Verified compilation and next grouped runtime gate

Run 41325009788 (source 49dafc82381cf6d7a19f40255f9ce45506666765), job 124903005350, completed successfully: all focused Home/evidence host checks, integrated app Kotlin compilation, Android test-source compilation, and focused JVM VaultDocumentPolicy tests. This is compilation/JVM evidence, not device or signing approval.

New follow-up tests cover actual Main Home authentication/Hidden revocation/Panic admission, encrypted owner-specific Home preference round trips and revocation, and draft import remaining unregistered until explicit approval. Existing Evidence UI tests now follow the full-page offline OCR and grouping approval sequence, retaining their original storage/integrity assertions. A grouped `[verify1710]` push runs these with existing Evidence authority/controller/store, Hidden navigation and privacy viewer cases on API37.

Screenshot Settings root cause identified: the successful `screenshots` authentication callback required `secretOpen`, while the toggle is visible only when Secret is closed. Corrected its completion guard to the active Security screen; authentication remains required. Ordinary Evidence pages now share the Activity policy; AI/export credential pages explicitly hold secure-window protection. Recents and background privacy covers remain protected.

Second review concerns addressed: Startup Settings capability is admitted only while resumed and acquired after Compose disposal effects; oversized multi-import selections are rejected with an explicit message rather than silently truncated. Original-key upgrade/signing and physical Fold acceptance are still outstanding. No signed 1.7.10 artifact exists.

### APK assembly diagnosis and guarded signing preparation

Run 41325650162 at f2d81989cacc22d8ce898f4e1a9bf3cff44e4212 failed in dexBuilderDebug before instrumentation: D8 reported `Method name '<anonymous>' in class '$$$$$NON_LOCAL_RETURN$$$$$' cannot be represented in dex format` for MainActivity.class. Replaced early `return@key` in new inline Compose key blocks with dedicated ordinary composable functions. No Kotlin/AGP/D8 dependency downgrade or security test bypass was used. Must verify actual APK assembly, not compilation alone.

External multi-import now enters the review workflow directly and selects newly imported IDs for owner deselection. Transient acquisition metadata is retained until review to preserve provider filename/time provenance. Home search now gets the exact captured HiddenSession from EvidenceAccess, not mutable current selection. Added cross-owner and revoked search assertions. Added explicit credential capture protection independent of the permission handoff tokens; PIN/recovery/auth pages remain secure even when eligible screenshots are allowed.

Downloaded artifact13081837273 independently: archive SHA a7ad5e4d6c243e195a5d35db1f4a12711de1842bd6260aa318203e53ac691cb7; APK137719190 bytes; SHA9f99f0e8df4dbdc6219aa147525866f0ace699b1b4ae9bab1f6b647ac4d7a178; internal signed application source19e3275ef17746e780d64e6a684d94bab2cabdc3. Artifact run/control HEAD3fc699ae5ecfbc7678e835240c594e38daf4e1da has the same application/build source.

Prepared inactive acceptance1710 job inside existing acceptance1762.yml plus baseline-compatible code51->52 upgrade compiler/fixture/checks. It cannot access the signer until a new `.acceptance1710-candidate` explicitly pins a successful exact-source grouped runtime run and SHA-bound independent review, and all required original Android XML reports pass. No activation marker or security approval file exists yet. Local workflow/actionlint, script syntax, baseline artifact validation, Home host checks and four runtime-gate denial/success unit tests pass. Latest source still needs assembled APK/API37 runtime verification before activation.

Independent review of inactive release gates found an inherited upgrade coverage gap: the old179 fixture seeded Evidence only after upgrade. Since code51 already supports Evidence, the1710 fixture now seeds Primary and Hidden records, OCR, corrections, incidents, timeline and labelled analyses on code51 before installation of code52, captures their ciphertext, and verifies exact content/ciphertext across upgrade and restart. This fix must compile and execute before delivery. Previous release fixtures were not changed.

### Assembled APK and emulator infrastructure failure

Run41326700214 at cf6dcbaeb98a1b537f3475174c73e5b781263408 assembled the debug app and test APK successfully: the D8 non-local-return failure is resolved. It then failed Android runtime execution with shared-storage `Transport endpoint is not connected`, activity-resolution failures, missing app-cache paths and `INSTRUMENTATION_ABORTED: System has crashed`. This is NOT a passing security run. Restored the known179 command-tools22 setup, reserved emulator disk, 30-second surface stability/crash preflight, and writable shared-storage check before tests; failure remains a hard gate.

Final navigation completion now omits old feature composition while Home is displayed (no live dialogs/WebViews beneath Home). Capabilities are acquired after old Hidden presentation disposal and keyed by Home visibility. Root Home Back backgrounds through normal session policy; long customization panels scroll. Home-launched Evidence/Camera/Private Apps return Home on explicit root exit. Independent review caught PrivateAppsLibrary's shared dismissal callback also used by successful launches/settings; added a separate explicit onRootExit callback and a focused regression assertion, preserving launch/settings transitions. Recent activity commits before Home disposal; saves are serialized. Hidden Home has explicit capture-policy priority over an underlying device Gallery exception.

### Security runtime failure diagnosed (healthy emulator)

Run41328278536 source1b7b96fb9719043edf6afb3097f21b73e1bceadf assembled successfully and passed the restored emulator preflight. Eight cases passed before the test process crashed during EvidenceAccess179Test.source_references_are_same_vault_verified_and_read_only. Actual crash: ConcurrentHashMap.KeyIterator.next -> Kotlin Collections.toList -> EvidenceAccess.cleanup. Kotlin's size==1 list conversion races with concurrent lease removal. Replaced all three concurrent retirement snapshots with toTypedArray (Collection.toArray), retaining closed/authority/commit fences. Independent reviewer confirms the fix preserves revocation semantics; the failing test stays mandatory.

Final scope review also keeps explicit Home requests independent of existing-install startup defaults, handles nested collection/selection Back before root Home, and preserves authenticated Hidden download-manager navigation rather than concealing it with Home. Latest fixed source must pass the complete grouped run before any signing activation. No security approval or APK delivery has been claimed.

### Complete runtime result and test-harness corrections

Run41329394915 at2e51b265f0f23cad4359bcd5f34a28d93b34cf1e completed54 Android17 cases:51 passed,3 failed,0 skipped. The EvidenceAccess retirement race test now passes. Remaining failures were diagnosed from raw XML/logcat artifact13093245181:

- Hidden Evidence UI test still matched a Dialog root, although the redesign intentionally uses an Activity page. Matcher now targets the exact Activity decorView; all clearing/isolation/storage assertions remain.
- Screenshot recreation fixture replaced production setContent with bare Text and removed the production foreground privacy-commit SideEffect. It now uses a real authenticated Main composition with explicit5min grace and validates recreation/secure stop/authorized resume, restoring settings/key afterward.
- Native permission denial was confirmed, but WM did not return focus after a permission request launched~200ms after initial Main display; native SPLASHSCREEN_EXIT_ANIM timeout was recorded. Test now lets initial production foreground/splash settle before installing the photo fixture/requesting permission, preserves real callback/resume/focus/no-reload checks, and records separate window/input diagnostics on timeout. Product focus policy was NOT relaxed.

Independent review confirms these test corrections retain mandatory security assertions. Product source is unchanged from the51/54 run; one grouped rerun is required to validate all lifecycle interactions. No signing activation exists.

## Final recreation and permission focus repair
Run 41331087025 assembled successfully and completed 54 Android 17 tests: 52 passed, two failed. Window/input diagnostics identify SystemUI ImmersiveModeConfirmation owning focus after the native permission callback; the app correctly stayed covered. Emulator preflight now acknowledges only this fullscreen onboarding setting, following AOSP CTS, without granting permissions or weakening assertions. The recreation failure exposed a real route-retention gap: onStop skipped retained.route during configuration changes. Feature routes are now retained before early returns; credential routes fall back to LOCK/SETUP and onCreate still requires a live authenticated session. Signing remains gated pending all 54 tests and independent review.

## Frozen source and signing activation
Source f301ee73320df2e366b7cd258dae5105296237a7, tree48425f7ab0581bcba1b43e1c8b29f690f82bd378, passed run41332619845:54 Android17 tests,0 failures/errors/skips. Artifact13094009280 SHA2409eea76e466ce4c4ce4a41acdaf876cb5735701ed0f8619bde4f99a2cf3ea2 was downloaded and rawXML independently reviewed. Separate security reviewer approved exactsource with no unresolvedCritical/Important findings. Activation marker pins source/run/reviewhash. Production originalkey compilation, certificate/package/hash and code51->52 in-place upgrade/restart remain mandatory before delivery. No signed1710 APK has yet been delivered.

## Original-key run result and narrow fixture correction
Run41333681584 passed exact-source gates, baseline-compatible fixture compilation, original signing key, release lint/assembly/package/cert/API37/OCR/alignment and baseline seed; install-r code52 succeeded. Upgrade verify failed only on top-level AndroidX profileinstaller_profileWrittenFor_lastUpdateTime.dat timestamp hash. No acceptance artifact published. Official AndroidX confirms this marker stores PackageInfo.lastUpdateTime and changes onAPKupdate. Narrow correction excludes that exact File only, no vault subpaths; independent reviewer approved fixtureSHA4f18349574aefb128ddc74009e27e5a6ad978de6689f6fdd02bde9e7e819f43f and controlworkflowhashbinding. App frozenSHA and54greenruntime remain unchanged. Retry pins reviewed fixturebytes fromcontrolcommit before frozencheckout; all preservation checks and restart remain mandatory.

## Companion AndroidX cache correction confirmed by gate
Run41335086571 passed release build/package/cert, baseline seed, install-r and Evidence content/catalogue checks, then blocked on exact files/profileInstalled hash in candidateEvidence blanket assertion. This AndroidX ProfileVerifier runtime cache stores package update time/compilation status. Independent reviewer approved only exact top-level profileInstalled in addition to exact ProfileInstaller timestamp marker; no vault paths excluded. ReviewedfixtureSHA9132a18d30a7daa03d112b7f1bc6150975fa2de01db05f00602b0681d525eb9f. Failure diagnostics artifact13095119058 contains noAPK. Retrying same frozen application/source54greenrun with bound correctedfixture, allupgrade/restart assertions intact.


## Current UI / Private App return workstream

See `docs/release17103/STATUS.md` and `ui-cases.json`. Changes are a source checkpoint, not an activated release. Preserve last signed code54 and its certificate. Google external authorization return remains blocked on a supported redirect/website handoff that preserves VPN/Tor routing; do not claim popup ownership fixes establish Google success. Use the existing focused workflow `[verify17102] [engine17102] [polish17103]`; no new workflow or release branch.

Verified UI / popup checkpoint: `b2d0239f0d40184f74a7f015bad326c80530d3b0`. Focused API37 run `41529553329` SUCCESS (3/3 failed-fixture retries; 7/7 JVM), plus verified 25 passing cases from failed initial run `41528043427` against identical production source. Exact receipts/hashes are in `docs/release17103/INITIAL_EVIDENCE.json` and `RERUN_EVIDENCE.json`. No new signed APK or new-key upgrade claimed. Last signed code54 remains unchanged. Google success/cancel and the supported external return contract are the explicit signing blocker.


## 2026-11-03 continuation — verified full-page workflows, still OAuth-blocked
Recovered newer acee549 instead of restarting from b2d0239. Source eaa518770f267957cc8383e1760b87167b5d32c9 moves AI reference selection and authenticated encrypted transfer history onto existing GalleryFullPageDialog, preserving callbacks and transfer authority. Run41629755724 SUCCESS: 2 focused API37 +7 JVM and existing host/compile gates. Receipt/hash and independent source review: docs/release17103/CONTINUATION.md / CONTINUATION_EVIDENCE.json. Reuse earlier evidence only within unchanged scope. Google success/cancel and secure supported external handoff remain unverified; no new acceptance APK or original-key upgrade performed. Do not sign as a completed Google login repair.


## Coordinated Hidden lifecycle implementation — current candidate

Supersedes the earlier diagnosis-only / missing-website blocker: the owner supplied reproduction and the shared destructive Activity/runtime/proxy/heartbeat paths are now implemented together, not an Activity-only patch. See docs/release17103/COORDINATED_IMPLEMENTATION.md and continuity-*-cases.json. Existing Primary remains the behavioral reference; Google restrictions, VPN fail-closed routing, auth/crypto boundaries and Beta disk-persistence disclosures remain. Pending candidate is1.7.10.3/code55 above last signed54. No signing marker/approval exists. Focused API37 and packet evidence, independent review and original-key54→55 upgrade remain mandatory.

## Code55 failed instrumentation recovery

Recovered authoritative17d9512 (no newer remote commit). Run41657787553 artifact13240894402 SHA4f133eaea415a48cd1707be118d61e29f620db99f0a7a4ef3a6b51c13d74effb independently downloaded and parsed:15 boundary/navigation and3 original VPN/Tor cases passed;8 new continuity cases failed before target lifecycle assertions. See docs/release17103/FAILED_RUN_DIAGNOSIS.md. Confirmed native fixture action targeted non-clickable icon rather than clickable parent. Foreign rejection status failure lacks original click/DOM diagnostics; strict website predicate passes host execution but Android result remains unverified. Test-only helper corrections and synthetic-only negative-result observability preserve origin/state, replay, cookie and routing assertions. No production source, version, branch or signing identity changes. Next gate is grouped8-only API37 with strictly hash-bound compatible reuse of18 original passes, new packet/receipt validation, then final independent review and original-key54→55 upgrade/signing. Do not count failed8 as passed or claim actual Google authentication success.

Focused8 retry802831b/run41665946639 corrected original Foreign/Private Apps setup failures; expiry and process-death passed, six later cases failed. Verified artifact13247175120 SHA1fadebcf02fd7d3bdc9a9ebb27f82d0ba5672675684d8a667afb3ce452aa9d6a. Diagnosed debug client stopping its flags0 service during init (three null Binder calls), destructive Panic helper waiting for AX Boolean after child kill, and neutral native-label/touch-exploration lookup assumptions. See FAILED_RUN_DIAGNOSIS.md for exact evidence and uncertainty. Next batch is test-only client lifetime/control lookup/one-dispatch corrections, with original terminal assertions retained and exact client path added to reuse allowlist. Production/code55 unchanged; rerun8 only after host/scoped review. Final runtime packet, security approval and original-key54→55 signing gates still pending.

fbc6388/run41669640429:4/8fresh cases pass(expiry,death,deniedPanic,touch-explorationLock);4fail. Artifact13247723836 SHA8b450d88af555e3a57b4622caa80b72f8714af5b1c8d3302d441a3b989e3d72f verified. Diagnosed obsolete Private Apps library label (actualExitApp) and leaked verification/screen-off DNS fixture gate: revoked Network made nullable finally skip release, poisoning subsequent readiness by30s delays against unchanged3s resolver deadline. First2raceiterationsran; no12-casepass exists. Nextfixture-only batch isolates exact-generation cleanup over host-loopback/runner-ownedADBcontrol, preserving actualVPNarm/proof and all assertions. Main/debug/build/code55 unchanged. See FAILED_RUN_DIAGNOSIS.md. No signing activation.

d6795a2/run41676391190:7/8fresh continuity cases pass; only12scenarioMainrace fails. Verified artifact13251582305 SHAa8cbe86632cdb2b71eefade8056a8641f4c7a1a23889c5572e53cadd20df1b13. Fifthverification/Panic child killed beforeDNS and beforeNetwork118 CONNECTED/UIDrouting; four earliergates/cleanupworked. Exactthrowunlogged; sourceprematurelyadmitsCONNECTINGownedVPN afterWGhandshake independentlyconfirmed. Coherentproductioncorrectionadds orderedframeworkready/caps/LP/unblocked admission, stale/lost/closed denial and boundedDEBUG failure diagnostics; originalauth/grace/probe/hardclose and actualoldNetworkabsenceproof unchanged. HostJava20 +Python30 and independent source review pass. Productionchange invalidatesold18reuse—DO NOTwidenallowlist. Nextflag[verify17102][continuity17103] executesfullfocused26 viaexistingworkflow, no retry/reusemarker. Code55unchanged; signinginactive.

Full focused26 source535186021598bf64fd56f2132ab4cb763332bf17/run41682334113:25PASS/1FAIL. Artifact13253433530 SHA25687f9a4a7b829346a0ad93a89c1996016868f9d193d1b8828b4673074a09094b6 verified; actual-Main12-race case and original18 all pass. Component two-site second reconnect still used raw Network enumeration instead of production readiness; worker kill17:26:18.456 precedes UID routing18.504/frameworkCONNECTED18.548, with no readiness DNS. Test-only alignment now uses existing readiness observer while preserving raw absence/security/session assertions and bounded captured-stage/class diagnostics. Production/debug remain535; code55 unchanged. Strict18reuse baseline refreshed to this535 raw archive/source/run, no protected-tree/allowlist widening; original18 proofs revalidated. Next independent-review-approved gate[verify17102][continuity17103-retry] runs all8 continuity cases fresh with complete packet/receipt checks, labelled18 compatible535 reused+8fresh. No signing marker or aggregate runtime approval exists; final artifact review and original-key54→55 upgrade remain mandatory.

Release preflight found `! grep` under `set -e` fails to enforce debuggable-APK rejection. The two17103 production/upgrade verifier scripts require explicit nonzero rejection, with actual-script RED→GREEN host tests covering production/baseline/candidate. No app/debug/runtime changes. Exact reuse additions are limited to these independently reviewed release-control scripts and existing host test; protected Android inputs remain fixed535. The dda19 focused-eight run was compiling at discovery; later outcome is unverified and may be superseded by existing concurrency when the correction is pushed. Do not claim a fresh result yet. Complete8/12-race/full-packet evidence, frozen-source review and original-key54→55 upgrade/signing remain required.

## Code55 genuine blocker — frozen24c, no further CI/signing

Source24c29fd86e8493b00365357071d9c45e240eb3c4/run41688397704/job126100834909 produced8/8fresh Android passes with zero skips, plus18 source-compatible original cases reused from535. Artifact13257432448 is122926299 bytes, SHA25611f7a4e51e016b3b20be0c657683194c936ad5b2a30d94c628f90eb74f4ceea8. All8 receipt schemas and12 exact race identities were checked; these are behavioral assertions, not a passing packet/security gate.

The full frozen capture contains two DNS packets outside WireGuard at18:02:13.089800/.089923 and six direct STUN datagrams with six ICMP rejects. Three datagrams carry the exact child-generated nv17103probe marker and occur during teardown before denied_confirmed; the whole-route invariant forbids them regardless of that timing. Resolver logs retain app Network103 but switch DNS to default101 for the app UID, consistent with AOSP default-DNS fallback after VPN removal. Host firewall rejection does not undo outbound escape. Full packet validation correctly fails. Exact times, actor attribution and PCAP SHA are recorded in docs/release17103/FAILED_RUN_DIAGNOSIS.md.

The current dead-Network pin is not an adequate OS no-egress boundary. Next task is a demonstrably effective boundary before and throughout tunnel teardown, preserving the exact current presentation/session, authentication, original grace and hard-revocation behavior. Do not weaken packet assertions, add route exceptions, trim timestamps, drop probes, retain revoked authority or call WebView/UI pause sufficient. No new CI or signing is authorized here. Keep version55, branch and evidence frozen; no code55 production APK, release marker or approval exists. The delivered54 entry above remains historical shipped status, not approval of this candidate.
