# Phase 2 recovery checkpoint — 1 October 2026

## Verified remote snapshot

GitHub connector independently verified main `f34db23445bd16fd45ae100474c19a2de8669695`, branch `phase2/concealed-container-foundation` and draft open PR #58 at `280bd49d137183d97f4626318be9435db89b7677`. Comparison from that SHA to branch was identical, zero later commits. This is a snapshot, not a claim that remote state can never change.

All five Phase 2 runs returned by the branch query (total_count=5):

| Run | Trigger | SHA | Observed result |
| --- | --- | --- | --- |
| 36928944103 | PR | 280bd49 | IN_PROGRESS, API36 instrumentation step |
| 36928938856 | push | 280bd49 | IN_PROGRESS, API36 instrumentation step |
| 36922805735 | PR | 50d42a9 | SUCCESS |
| 36922800684 | push | 50d42a9 | SUCCESS |
| 36921224067 | PR | 347eda34 | SUCCESS |

Subsequent retrieval confirmed both `280bd49` runs completed FAILURE. PR job `110593138358` ran the targeted Primary slots suite successfully (10 tests), then the complete API36 suite ran 167 tests with one failure: `SecondaryStorageAdapterTest.realAndroidPinnedStorageConfirmsRestartsAndAtomicallyReplacesSelection`, expected FRESH but actual UNAVAILABLE. This is actual Android behavioral RED for the unsupported filesystem admission path, not just an inference from platform source.

Recovery implementation was pushed as `1f35ebd50dad81aed9861d094ff5b663f4ed905e`, parent `280bd49`, tree `2aa98cf03419972949f0a1b340b4f40f7d47dc0a`. Tree-identical local commit is `6778920927c39e7da3ed661820f4d481882f6f15`. Both new runs (`36933923884` PR, `36933918709` push) were IN_PROGRESS when checked; no Android GREEN is inferred yet.

Surviving uncommitted source and synthetic tests were preserved as scratch patch/archive and copied to a separate recovery worktree. No production work was restarted. Original remote checkpoint and Primary formats/fixtures were retained. Local commits are not remote completion evidence.

## Behavioral regression evidence

- Original compiled credential-retirement probe executed again: 3 tests, 3 assertion failures. Old PIN/recovery and canceled new PIN could unwrap retained unselected local envelopes. The recovered proposed retirement implementation was unfinished.
- Recovered source retirement/controller run: 10 tests, 8 failures. Cleanup compared directory size/mtime after deleting its children, breaking confirmation. Stable identity/type plus verified emptiness corrected this. The same focused suite then passed 10/10.
- Android filesystem privilege probe: 12 focused tests, 1 assertion failure (fresh expected, unavailable actual). Android AOSP rejects Files.getFileStore(Path); querying the POSIX path attribute view preserves fail-closed permission reads without that API. See official source below.
- Enrollment pointer-promotion cancellation: 5 tests, 1 failure. Selected envelope referenced an alias deleted by concurrent cancellation. Added uncertain-post-rename result probe: 6 tests, 2 failures. The controller's original token/request gate now encloses the short authority promotion, and the signed journal takes alias ownership before rename.
- Corrected focused regression suite: 28 tests passed, zero failures; includes recovered original recovery buffer wipe/possession confirmation, filesystem privilege, retirement restart, enrollment/unlock cancellation and current store cases.
- First broad run during review: 486 tests, one failure in staleRemovedBiometricRecordAndInsufficientScopeNeverAuthorize. That old fixture used three arbitrary bytes as an envelope. It was replaced with a real synthetic AES-GCM envelope; current schema remains strict. Final broad rerun is recorded below when complete.
- Added permanent exhaustive before/after retirement removal restart test with real durable IO, surviving selected master validation and Primary canary. No owner data was used.

## Independent review and limits

Read-only fresh reviewer substantiated Android getFileStore admission failure, changed-directory-metadata cleanup failure, interrupted missing-reservation restart failure, and enrollment pointer-promotion cancellation. No Critical finding was substantiated. Fixes require fresh suite/API36 evidence. VM immutable strings, native/provider keys, OEM durability and external snapshots remain explicit limitations. There is no claim of physical flash overwrite or per-item cryptographic erasure.

Official Android implementation: https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-constraintlayout-release/android-35/sun/nio/fs/UnixFileSystemProvider.java — getFileStore(Path) always throws SecurityException; SecureDirectoryStream is conditional on openat/O_NOFOLLOW support. Official source is platform explanation, not an executed device pass.

## Unresolved original acceptance text

The recovered repository includes a derived spec, 42-row matrix, 34-category draft report and references to 50 original gates. Two targeted Personal Context searches found references to the original 54-section owner prompt but returned summaries rather than the literal 50 gates. Do not reconstruct or invent those gates. The complete original gate text must be available for the final gate-by-gate recommendation. This does not prevent regression repairs within the already-authorized scope.

## Status

PHASE 2 RESULT: NO-GO FOR PHASE 3

No migration/transfer/media functionality, public release or merge. Signing requires final exact-candidate automated admission; physical acceptance remains owner-only and has not been requested or reported.

## Final local verification for this recovery checkpoint

AUTOMATED TEST EVIDENCE: full `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` completed SUCCESS in 4m50s. XML totals: 489 tests, zero failures/errors/skips. All five retirement tests passed, including exhaustive before/after removal interruptions; all six biometric controller tests and the inventory/controller recovery ownership cases are included in the full suite. Instrumentation APK compilation is not Android execution.

Additional checks passed: source/merged/packaged backup exclusions, WireGuard-only source/notices audit, immutable legacy SHA256SUMS, byte-identical instrumentation assets, frozen future-format Python vectors (5 tests), backup mutation suite (7 tests, 1 platform-dependent skip), Node suite (3 tests), retired runtime/weights absent from debug APK, and staged-source secret scan. Existing compiler/deprecation warnings remain; no new lint error was admitted.

This validation uses the recovered Gradle 8.10.2/Kotlin 2.0.21/JDK17/Android36 scratch toolchain. Required remote Phase0 evidence variant and API36 instrumentation are not covered by this local command and remain CI gates.

## Additional matrix coverage after the implementation checkpoint

Added tests crossing the actual Primary encrypted payload store and preview cache with test-only Secondary F1 fixtures under the sibling root, forcing identical ids/filenames and checking cross-key rejection, missing-file no fallback and independent retirement/eviction. Added real Primary/Secondary adapter checks for stale operations and foreign-container handles even with a current epoch. Extended the Activity test to visit configured Primary search, collections and trash before discovery with a real synthetic Primary index. No production Secondary payload/preview writer or media API was added.

AUTOMATED TEST EVIDENCE: focused synthetic repository suite passed three tests; latest full `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` completed SUCCESS in 4m49s with 491 JVM tests, zero failures/errors/skips. Android additions compiled but have not executed locally. Independent narrow test review found a snapshot assertion that incorrectly included legitimate authentication's durable usage updates; it was corrected before Android execution. No further Important test assertion/fixture cleanup finding was substantiated. First attempted local additional build was blocked by a busy shared Gradle cache before tests; it is infrastructure failure, not behavioral RED.
