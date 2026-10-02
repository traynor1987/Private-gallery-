# Recovered cleanup foundation checkpoint

2026-10-02. **PHASE 3 RESULT: NO-GO.** PR59 stays draft and unmerged.

## Recovery and scope

The owner-requested recovery found surviving source at local74fd791 with the
same application tree as published51f6266. No other repository writer or Gradle
process was active. Modified and untracked work was archived before changing
the index; no worktree postimage was overwritten. Older source and documentation
worktrees were retained. A separate validation checkout started from the exact
published tree5210d48749a43d6c8f7683b173e46e4bedfb2e16.

This checkpoint publishes only the inert bounded release foundation:
`AcknowledgedCloseable`, `ReleasePool`/`ReleaseTicket`, `ReleaseReservation`,
`OwnedResource` and `ReservedJobRelease`. Every child has its own original
pre-funded worker; failure or missing acknowledgement retains its obligation.
Normal retirement and revocation share exactly-once accounting. Partial factory
children release without waiting for their enclosing factory to return, while
unused manifest entries remain reserved until construction finishes. A Job's
actual finalization and the return of its cancellation invocation are separate
required events. Failed finalization and failed completion-hook installation
are never positive teardown acknowledgement.

The helpers have no production callers in this checkpoint. The provisional
cached `SessionCleanup` scheduler and authority/MainActivity integration remain
excluded. They are preserved locally for reconciliation, not accepted as a
bounded runtime. Current Task3 caller migration remains incomplete.

## Independent review and regression evidence

A read-only independent concurrency review found no Critical finding and one
Important finding: constructor admission ignored a previously failed original
worker. Two new tests exercised the real ticket's worker-termination notification
and observed21tests/2failures before the fix. The fix refuses both single-ticket
and complete-manifest construction before any resource exists when the original
worker is failed/unavailable. The same26-test foundation suite then passed with
zero failures/errors/skips, including five direct reserved-worker/Job tests.
This is synthetic worker-failure notification evidence, not an Android process
or OS-thread viability claim.

A subsequent runtime caller-migration regression found that two manifest entries
could attach the same actual resource and acquire duplicate close authority.
A direct actual-close-counter test observed22tests/1failure, then22/0 after
the minimal original-reservation duplicate check. This fix is included in the
inert foundation; it does not import the newer runtime manifest/retirement APIs.
Final candidate checks are rerun after this source change; the earlier654 debug
and661 Phase0 JVM passes are qualified as pre-duplicate-fix observations.

The independent review noted that the earlier native-acknowledgement test does
not deliver acknowledgement during blocked completion-handler attachment;
that additional adversarial scheduling remains deferred to the final review.
Its lack of independent Job-boundary coverage was addressed by the five direct
tests in this checkpoint. Distinct malformed factory return objects remain a
mandatory caller-audit boundary: supported factories cannot construct a second,
unreserved object. No emergency/unbounded cleanup fallback is introduced.

Fresh final-candidate verification follows. Android execution remains remote CI evidence,
not local device acceptance.
Foundation latch/worker/Job tests exercise portions of CB01–CB05/CB07/CB09 only;
they do not accept complete runtime matrix rows. All full product matrix gates,
Android/main/provider/native capacity and acknowledgement measurements remain
pending.

## Remaining work

Fresh recovery of the complete surviving Task3 work ran43tests and retained
five failing `MediaResourceOwnershipTest` cases. They identify unfinished media
ownership/callback migration and were not included in the inert candidate.
The runtime implementation must reconcile them into real ownership/revocation
behavior tests, migrate all generic production ownership callers and remove the
provisional cached scheduler before Task3 can pass.

Selected Hidden storage, direct imports, Primary Copy/Move through encrypted
recovery hold, paired evidence/recovery, concealed media UI, full restart/failure
matrix and signed/device/owner acceptance remain pending. No Primary encrypted
format change, owner bulk migration, merge, public release or Phase4 occurred.

## Final local candidate verification

| Check | Observed result |
| --- | --- |
| Cleanup foundation targeted JVM | 27 tests, zero failures/errors/skips |
| Complete debug JVM suite, including Phase 1/2 regressions | 655 tests, zero failures/errors/skips |
| Isolated Phase 0 JVM suite | 662 tests, zero failures/errors/skips |
| Debug and Phase 0 lint | Success; zero Error/Fatal findings (55/72 warnings respectively) |
| Debug and Phase 0 app/instrumentation APK builds | All four builds succeed |
| Phase 3 reference contracts / Phase 0 literal vectors | 30 / 5 tests pass |
| Backup exclusion verifier behavior tests | 14 tests pass |
| Source, merged manifest and packaged backup exclusions | Pass |
| Immutable fixture hashes and instrumentation-copy comparison | Pass |
| Public-source secret scan and WireGuard-only dependency path | Pass |
| Retired native/model runtime source and packaged absence | Pass |
| Browser video helper regressions | 3 tests pass |

The published helpers have no production call sites. CB01–CB12 and full product
negative/restart matrix rows remain PENDING; these tests are foundation evidence.
The two accepted-component suites retain unchanged frozen format/accounting source
in this checkpoint. Narrow runtime ownership fixes remain a separate next chunk.

### Final APK SHA-256 (local unsigned/debug artifacts)

- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`: `72b9308240bced901f8873e9c8885d2f03fa879b07dd4e0145b76fb8c4385669`
- `app/build/outputs/apk/androidTest/phase0Evidence/app-phase0Evidence-androidTest.apk`: `92b25365460afed97efb3939ccf57d2c8b8387147eeb225f9770918c318ba633`
- `app/build/outputs/apk/debug/app-debug.apk`: `a4c556d3ac3da5e4ac8c1cda59362a938f4c3e3ba6dc069bcfb50ed8ba784c6b`
- `app/build/outputs/apk/phase0Evidence/app-phase0Evidence.apk`: `a3d0fdf9b6d7f43b76bd86dcd31d8456be8e871dd83e77cfc925739c59568965`

These are debug artifacts, not the permanent-signer Device Acceptance Build.

### Tested source SHA-256

- `app/src/main/java/uk/co/traynor/privategallery/core/security/AcknowledgedCloseable.kt`: `8dc92f3cb7a6a3324efe5607997cba5f0ad3ba43ba000323c4708e05365ea768`
- `app/src/main/java/uk/co/traynor/privategallery/core/security/OwnedResource.kt`: `d231256a99e73f552b1a93603f5dd6c49d6c1fb555f2ac3baecd79fde86b6b14`
- `app/src/main/java/uk/co/traynor/privategallery/core/security/ReleaseCapacity.kt`: `69f6392d88c558eeb86d247f6ef76f75c910d68c3bdf0755d9e8ca69850e669f`
- `app/src/main/java/uk/co/traynor/privategallery/core/security/ReleaseReservation.kt`: `020948348109b940a1788fea28896ddbcc02c38e34c7b759fa89b8bd12fe58b4`
- `app/src/main/java/uk/co/traynor/privategallery/core/security/ReservedJobRelease.kt`: `be848221803c07a29f0c1ed7a9a0fb5a3bc5c84c46d23454192402666be4b57d`
- `app/src/test/java/uk/co/traynor/privategallery/core/security/ReleaseCapacityTest.kt`: `89b337acb1f0fbb45df150e554b3bf2e1f2726973800e3e2be4b78b13231af44`
- `app/src/test/java/uk/co/traynor/privategallery/core/security/ReservedJobReleaseTest.kt`: `77e6e8e302b8c17fe6866b6fea28f5502685167fffcf12d713875848eec23856`
