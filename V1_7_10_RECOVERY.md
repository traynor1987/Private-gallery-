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
