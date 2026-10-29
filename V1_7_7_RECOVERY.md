# Private Gallery 1.7.7 — recovery

Status: PRODUCTION-SIGNED OWNER-ACCEPTANCE APK VERIFIED / ORIGINAL-KEY UPGRADE AND RESTART PASS / PHYSICAL OWNER ACCEPTANCE REQUIRED.

Baseline branch release/1.7.6.2-ui-browser-polish at 8915d2e6f3915573afeaf850205f302c07eab7eb.
Code45 application source 97e94452ceb9c5262521d7552f0c25eeb07374eb; release control a19d19603c8ccbfaa1449113ba61592f5249c19e.
Original-key production run40852039088 and artifact12887938200 are recorded in docs/release1762/CODE45_OWNER_ACCEPTANCE.md. APK SHA256 ad39f9b3a4d04d75621eeb21969f131fd96deae0620a5e34325a9bf400359aff. Code45 physical acceptance remains OWNER_REQUIRED; the owner's new reports are unresolved.

Fresh clone and fetch on 2026-10-29; no competing local git/Gradle process observed. Remote branch checked unchanged at 8915d2e6. No existing 1.7.7 branch. Inspected all fetched remote app/build.gradle.kts files: maximum versionCode45. Code46 is provisional until release-time recheck. Metadata unchanged so far.

Confirmed existing basic trash in both vault repositories, with fixed30-day retention and single-item list UI. Restore currently flips state without payload verification. Primary thumbnail callback rejects Hidden-selected cross-vault Primary gallery even though loadItems admits that context. Remaining Hidden thumbnail and browser address/handoff causes still under investigation.

Local Gradle unavailable: wrapper distribution9.5.0 is not cached and download fails java.net.SocketException: Network is unreachable; Android SDK absent. Existing GitHub focused workflow can supply Android build/runtime verification after coherent changes; do not treat host tests as Android proof.

No app code changed, signing credentials accessed, workflow launched, or acceptance claimed at recovery.

## Draft implementation checkpoint — NOT release approval

1.7.7/code46 prepared. Existing stores extended with encrypted independent7/15/30/60/never retention; payload verification before restore; bulk themed trash grid; Primary preview ownership repair; authenticated Hidden cache regeneration; shared connection chrome and empty-WebView/clipping candidate. No physical acceptance claimed.

Independent draft review found6 Important issues. Corrections drafted: expiry-only periodic paths and single lifecycle timer; current COMPLETE guards on ordinary Primary reads and derivative publication; orphan encrypted-preview reconciliation and expiry cache invalidation; expensive presentation cleanup outside authority locks; cancellable Primary restore verification; bounds-aware legacy thumbnail repair. These fixes require runtime verification and final review. Startup recovery remains distinct from periodic expiry.

Local200 Python host tests pass. Android tests are authored but not executed locally (SDK/distribution unavailable). Existing focused workflow expanded to18 selected affected cases, preserving original transport packet gates. New release identity test retains historical code45 identity test against immutable source. Original-key45→46 fixture is prepared, not executed. Signing workflow is still pinned to historical code45 and MUST NOT be triggered for this candidate.

## Grouped run1 — compile failure, preserved

Run40883312782/job123480301984 on0a87491ea7d01400539022166bc1c4d3646bc249: host gates PASS; Kotlin compile FAIL (activity referenced composable-only cropRevision; four optional callback lambdas inferred Unit?). No Android instrumentation ran. Fix uses existing activity cameraRevision and explicit Unit callbacks. Cleanup step also failed because no transport fixture was started; do not mislabel as transport failure.

Final draft review identified an additional expiry-cache publication race in retention-setting/load-triggered expiry. All Primary expiry call sites now share serialized expiry and short preview publication/revocation gate. Three focused behavior tests added for mid-read restore revocation, interruption after logical deletion/orphan-preview cleanup, and active-import staging preservation. Still NOT_APPROVED_FOR_SIGNING; future runtime results must be attributed to exact source. No signer access.

## Grouped run2 — build passes; branch guard blocks runtime

Run40884745904/job123485336262 on4405081dd3d46dbd54bc9646c834ab8963544033: host checks, compilation, JVM tests and lint PASS. Owned-VPN setup exited at its exact-branch guard before any fixture or instrumentation started; setup and cleanup still admitted only1.7.6.2. Both guards now explicitly admit1.7.7 in addition to the preserved old branch. No transport behavior failure or pass is inferred.

Next grouped candidate includes exact expiry-ID revocation under the repository metadata snapshot, the original two-argument repository constructor ABI for baseline-compiled upgrade instrumentation, and an actual-touch initial Hidden VPN address-bar assertion. Existing production workflow prepared for1.7.7 with frozen source deliberately unset; signing stays blocked until exact-source runtime evidence and independent approval exist. No credentials accessed.

## Grouped run3 — retained UI failure

Run40887523337/job123495116634 on8281bd84704e7a66e9ab7917e4a4ea39eb09d208 passed host/compile/JVM/lint and both controlled transport-fixture startup gates. Android instrumentation crashed in the first RecentlyDeletedUi177Test when its third (retention) dialog opened, after cancel and confirmed permanent-delete interactions. Actual logcat shows DialogLayout measurement failure. Retention added its own verticalScroll inside the shared GalleryAlertDialog's scrolling body; removed that redundant inner scroll so the shared modal owns the bounded scrolling body. Test also exercises retention confirmation/apply. Runtime recheck remains required. Failed evidence artifact12903598122 SHA2569ced33668eeb94f7e984a4a0d76767d24c77212e816b13e47989203ec822754e retained; no runtime pass inferred.

Independent review resolved expiry and approval bindings at source level. Two targeted legacy/corrupt-thumbnail cases added on reviewer request, total23 selected cases, including authentic legacy large-image/video repair and corrupt-source refusal. Production gate now requires an exact machine-readable independent decision and matching run/artifact references. Host201 tests PASS. Source remains unsigned and NOT_APPROVED_FOR_SIGNING.

## Grouped run4 —22/23 pass; Fold web-content bounds failure

Run40890071125/job123504139163 on c759becfa8d198bc2552227835d86e9af4546aff passed host/build/JVM/lint/fixture startup and22of23 Android cases. The retention modal and both legacy/corrupt-thumbnail cases passed. HiddenVpnLive1762Test failed at line477 after392x840 viewport transition: native WebView bounds were0,152–392,784 but virtual document/HTML controls had zero height. Evidence artifact12904669447 SHA25670d5d1a32b2b63d7cfd5dfeb623d945b572c68c5ea388aa84493cfae5065e952 retained. No final transport/revocation pass inferred from an incomplete method. Next correction replaces Compose layer clipping with native FrameLayout clipping and MATCH_PARENT WebView sizing; exact WebView, profile and transport remain retained. Existing physical-touch assertions unchanged.

Independent review identified Important I-9: periodic Hidden expiry could retain decoded pixels or publish a queued preview until UI refresh. Added per-session process-local preview epoch, weak bitmap tracking, immediate pixel invalidation before mutation, UI epoch refresh and stale preview/list-delivery rejection. No keys or media identifiers are persisted in this state. Wiping stays outside the authentication commit monitor; concurrent tile recycling cannot prevent epoch advance or other cleanup. Actual activity expiry test now uses coloured Primary/Hidden pixels, a held preview callback and already-disposed preview. Reviewer accepted source correction; runtime pending. Host201 checks PASS. This is still NOT_APPROVED_FOR_SIGNING.

## Grouped run5 — native Fold fix passes; test fixture colour precondition fails

Run40893460085/job123516113892 on81489def5a2bc2cf06416dc7726a08ac67c3cd10 passed build/JVM/lint and22of23 Android cases. NativeTor, full HiddenVpnLive including Fold real-touch transitions and revocation, network-loss and Main browser lifecycle methods passed. The sole failure was PrimaryGalleryDeviceRepair1762Test line83 before exercising Hidden expiry: decoded opaque magenta differed from exact source RGB by one channel (-65281 expected,-65282 actual), consistent with thumbnail encoding. Test-only correction asserts initial alpha255, while retaining exact transparent-pixel, stale-delivery and cache-identity checks after expiry. No production source change for this correction. Hidden expiry runtime gate remains pending because its precondition stopped the method. Full packet verifiers did not run after Gradle's failed test exit; no final gate waiver. Evidence artifact12907276849 SHA256c5519c9a2f274eae3830d30cd7150b4affa230a114f0f19636ce0aa454104049 retained.

## Frozen production candidate — focused verification and review pass

Frozen source25aae6d59f79c455e47acc73a5c99f9c8f15242a, treee443653b0c1126f9916cc43a2db8bdbd875ac787. Run40896458172/job123526775805 SUCCESS: host201, compile/JVM/lint,23exact Android cases with zero failures/skips, VPN packet/body/revocation verifier and nativeTor/sink verifier. Evidence artifact12906529709 SHA25693e197a109026af32d2558806b2994698ca065344e27854787e13d5966b9f64b; downloaded/hash-checked and both parent and independent reviewer reran the evidence validators. I-1 through I-9 are source-corrected with affected runtime proof; final report APPROVED_FOR_SIGNING,0Critical/0Important.

Fresh85-remote-branch version check: onlythisbranch uses46; nextothercode45. Preserved1.7.6.2 HEAD remains8915d2e6f3915573afeaf850205f302c07eab7eb.

Existing production workflow now freezesexact25 and requires immutable successful run/artifact, exactreviewdecision/reporthash beforeoriginalkeyaccess. Release-control preserves/hashes its reviewedvalidator and upgradefixture independently of frozen application source. Upgradefixture extends existing credential/media/hash checks to Primary encryptedtab/history/bookmark records and independent Hiddenmode bookmarks/VPNhistory/Torhistory-disabled preference; verified atseed,afterupgrade,andafterrestart. This fixture is copied onlyafterAPKbuild, does not changeappsource, and is archived withits hash/controlSHA.

Production signing and original-key45→46 upgrade/restart are next; noAPK delivery or physicalacceptance claimed yet.

## Delivered build — automated release gates complete

Productionrun40899438953/job123537370227 SUCCESS, release-control48a788a546375fca9c0c8bd5b32fd9b79aa0a7bb, frozenappsource25aae6d59f79c455e47acc73a5c99f9c8f15242a. Artifact12908558722 (private-gallery-1.7.7-code46-owner-acceptance), archiveSHA25616873a3b24abcb311d21ca11e93e1af1707be42946a817770e4b9d629e790869.

One productionAPK,104979026bytes, SHA256696e80071497501d8da6cd8dd3e2c9728a63b0d4abc9e88c4ab46338fef97a2e, packageuk.co.traynor.privategallery, version1.7.7/code46,min/targetAPI37. OriginalcertificateSHA25694f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. No replacementsigner or credentials generated.

Original-key code45baseline97e94452ceb9c5262521d7552f0c25eeb07374eb seeded successfully; candidate installed in-place; verify and after-force-stop/restart eachOK(1test). Existing encryptedpayload/ciphertext, independentPINs/keys, collections/favourites/dates/settings, Primarybrowserrecords, Hiddenmodebookmarks/VPNhistory/Torhistory-disabled preference survived. Exact reviewedfixture/validator and their hashes are archived withcontrolSHA. Parent and independentreviewer separately checked downloadedAPKhash andidentity/upgradeevidence; approvedreport unchanged.

Deliveryfilename: Private-Gallery-1.7.7-code46-owner-acceptance.apk. OwnerphysicalSamsungGalaxyZFold acceptance remains NOT_PERFORMED. Code45's priorphysicalacceptance remains OWNER_REQUIRED. No1.8development or general-public release was started. See docs/release177/CODE46_OWNER_ACCEPTANCE.md and OWNER_ACCEPTANCE.md.
