# 1.7.5.2 narrow change review

Independent read-only reviewer examined the baseline, added fixtures, actual baseline XML/logs and the production correction through b9b68d527f1c77829c12bc80087ad0e5358b896e. This is a narrow source review, not a full release admission or physical acceptance.

## Confirmed issue and correction
HiddenVaultDialog retained requireReopen across an ON_STOP which occurred on the grid, even though no media selection was being viewed. The next explicit selection inherited that stale flag. Both API36/37 baseline tests fail specifically on unexpected Preview hidden; retained-photo background concealment passes.

The production diff is confined to HiddenVaultDialog.kt: only set the retained flag when a selection already exists; clear it before an explicit fresh grid selection. The reviewer found no new authority bypass in that change. A retained selection remains concealed on stop/remount; later native concealment epochs still invalidate the viewer. Authentication/editor read checks, session expiry, native curtains/secure flags, screenshot policy, Panic, storage/encryption and networking are unchanged. No claim is made that this explains a Primary/device-Gallery report: that path accepts the current epoch on fresh selection and must be assessed from its own runtime evidence.

## Test review findings
- Correct instrumentation-APK asset ownership before interpreting browser rendering tests.
- Place the dialog beneath the transformed ancestor and synchronize provider visual state before sampling compositor pixels.
- Hidden ComponentActivity tests isolate the selection latch; they do not establish MainActivity/Launcher pixels or real gestures.
- Add MainActivity fresh-selection and denied-reopen checks without resetting accepted epochs on recomposition.
- API37 native-confirm tap was rejected by InputDispatcher at window opacity0.364502. Bounded accessibility-idle synchronization before the SAME native Espresso click is a justified test-only hypothesis; retain animations and result assertions.
- Require actual XML test names/counts and no required skips. GitHub's continue-on-error step conclusion success does not prove test success; final outcome gate must remain strict.

## Remaining admission gates
Affected focused results, generic-modal diagnosis, real Recents/Home/app-switch/window interactions, screenshot preferences, complete supported-platform security/regression and original-key39->40 synthetic upgrades remain required. No private signing key was probed or used for1752. No code40 reservation, signed APK, owner-data access, direct-network fallback, consent bypass or main merge occurred. Public Activity callbacks do not uniquely classify Recents versus Home; do not remove concealment using timing alone. If reliable distinction is unavailable, retain protection and disclose that limitation.

Recommendation at review time: NO-GO for a complete1.7.5.2 release. Proceed with the single affected test run, not full/signing or speculative browser settings.

## Follow-up lifetime review
Affected50-test suite found the missing child-retirement boundary on both SDKs. Reviewer confirmed that HiddenRepository/HiddenEditorAccess capture session authority and that operation revocation retains authentication but invalidates those owners. Two-phase stop-generation removal/re-entry was accepted provisionally: generation persists above early returns, increments on every stop, and is acknowledged by a captured-generation LaunchedEffect only when content presentation is allowed. Old disposal therefore precedes fresh authority capture. Do not use a plain key(generation) around the child, relax revocation checks, or clear future privacy epochs. Fresh runtime verification remains required.

## Admission review after focused GREEN
Independent reviewer verified9/9browsercases onbothSDKs, zeroerrors/skips; newevidencegate3/3, old1751gate7/7 andnewconfig2/2 hosttests. No securityregression identified in threeproductionfiles. GO ONLY for full134focused/74preflight/401unfiltered perSDK, namednewregressions, exactsource/APKidentity andoriginal-key39->40upgrade/restart gates. Commitversion1.7.5.2/code40 andtriggeronce. No physicalacceptance ormainmerge authorization inferred.

## Supplemental read-only review

The reviewer independently checked artifact `12595971956`, its ZIP SHA-256, raw 2/2 results on both supported SDKs, all four upload/resize metric records, synthetic-only file handling, preference restoration, absence of network upload and retained WebView identity. Production source and reviewed build inputs match frozen candidate `ec05b23ec28a44730439bd1090c1e64afff8f140`. No new security issue was identified in this test-only addition. Full/signing/upgrade and physical acceptance remain separate gates.

## Final independent admission review

Full workflow 40182954140 completed successfully. The independent read-only reviewer verified both downloaded artifact hashes; exact 134/74/401 focused/preflight/full results and required names on each SDK; frozen source ec05b23; actual APK SHA-256 deddec834271291704806a25cb986aacd285b89f4346f10bad12f0e450577990; original production certificate; package, minimum SDK 36 and version code 40; and both original-key 39→40 seed/verify/restart proofs from baseline 00509aa7. Synthetic independent credentials, encrypted media, collections and settings remained accessible. Recommendation: **GO for owner acceptance only**. Actual Fold gestures/posture, external website behavior and owner data require physical acceptance. No approval to merge main or claim exhaustive security review is implied.
