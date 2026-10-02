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

## Historical 193cd05e local candidate verification

| Check | Observed result |
| --- | --- |
| Cleanup foundation targeted JVM | 27 tests, zero failures/errors/skips |
| Complete debug JVM suite, including Phase 1/2 regressions | 655 tests, zero failures/errors/skips |
| Isolated Phase 0 JVM suite | 662 tests, zero failures/errors/skips |
| Debug and Phase 0 lint | Success; zero Error/Fatal findings (55/72 warnings respectively) |
| Debug and Phase 0 app/instrumentation APK builds | All four builds succeed |
| Phase 3 reference contracts / Phase 0 literal vectors | 30 / 5 tests pass |
| Backup exclusion verifier behavior tests | Seven source tests pass; packaged class skipped without SDK environment (corrected below) |
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

## Duplicate-result correction and fresh recovery

The foundation above was published at
`193cd05e3ee1126b9de58d43ba824d35277f6a92`, tree
`01f661a2924d2778add2f33ef8db8d9374af76c9`, with direct parent51f6266.
Exact-head push [Android485](https://github.com/traynor1987/Private-gallery-/actions/runs/37066403408)
and PR [Android486](https://github.com/traynor1987/Private-gallery-/actions/runs/37066409657)
completed SUCCESS: all27 steps succeeded and actual logs record172/172 complete
emulator instrumentation tests for each. PR test-merge b8da71c has the same
tree as193cd05e. These remain foundation/component CI observations.

Scoped re-review then found an Important bypass: `verifyResult` could assign
another entry's actual referent before rejecting the malformed factory result.
Rollback could consequently close the same actual resource twice. A real
close-counter regression observed28tests/1failure, then28/0 after protecting
result verification with the same reservation gate and rejecting another
ticket's exact referent before assignment. Additional tests preserve valid
same-ticket verification and funded cleanup of a distinct malformed result.
A Job test now waits for the independently observed occupancy before asserting
it, removing a latch/publication race. Independent scoped re-review accepted
both fixes with no new Critical/Important finding. The native hook-installation
adversarial schedule noted above remains a separate runtime obligation.

During broader correction validation, the environment removed the older source
worktrees and JDK. The interrupted Phase0 run's five failures were an actual
missing JDK `tzdb.dat` and cascading `ZoneRulesProvider` initialization failures;
that run is not a Phase0 PASS. Source editing and validation paused for recovery.
The published193cd05e, original34-file archive, exact three-file correction
diff, direct logs/XML and source hashes survived in the current workspace.
The correction was restored into separate current-session validation and
runtime worktrees without discarding the archived runtime work. No independent
repository writer was found before resuming.

Reviewing the restored static logs also exposed an earlier local evidence
overstatement: the packaged backup-test class had been skipped because
`ANDROID_HOME` was unset. Seven local source tests ran, not fourteen. Both193
CI logs independently show all14 backup tests ran. The restored local runner
now supplies the SDK environment; a fresh explicit backup run completes14/14
with zero failures/skips, including all binary APK mutations. The historical
table above is corrected rather than treating the skipped class as a PASS.

Unavailable newer runtime postimages are being reconstructed with fresh
behavioral tests and review; they are not claimed byte-exact recovered or
included in this correction. Accepted Tasks1/2 remain unchanged. The rebuilt
toolchain uses verified official archives, base Android36r2 and AGP build-tools34;
all required correction checks are rerun on this frozen restored source.

Corrected source SHA-256:

- `ReleaseReservation.kt`: `44f78a7784ad861ec73a505caefe157d2c3ca38555854fbef65fb43db839a66e`
- `ReleaseCapacityTest.kt`: `798f9a5375cd4a3321a634c9455badcc4089b90e62595fabc2c523adaecc9b81`
- `ReservedJobReleaseTest.kt`: `312a1249ddb337b7ed6811ef90a37f4a204dc10b9c053debed2a9218bab955c9`

Fresh corrected-candidate checks on the restored frozen source:

| Check | Observed result |
| --- | --- |
| Targeted cleanup JVM / complete debug JVM / isolated Phase0 JVM | 30 / 658 / 665 tests; zero failures/errors/skips |
| Debug / Phase0 lint | Success, zero Error/Fatal; 38 / 60 Warning and four Information findings each |
| Debug / Phase0 app and instrumentation APKs | All four builds succeed |
| Phase3 reference contracts / Phase0 literal vectors / backup verifier tests | 30 / 5 / 14 tests pass |
| Immutable fixtures and instrumentation copies | All hashes and byte comparisons pass |
| Source, merged manifest and packaged backup exclusions | Pass |
| Secret scan, WireGuard-only path, retired runtime/model source and package absence | Pass |
| Browser helper regressions | Three tests pass |

Corrected app APK SHA-256:

- `app/build/outputs/apk/debug/app-debug.apk`: `ae174f0c0edce4e7f9b4276c2fafca69671f03b2f1c1546adc394a183791e77c`
- `app/build/outputs/apk/phase0Evidence/app-phase0Evidence.apk`: `509e411dad7248a92ea22c058bb45608e6fee7d4ed179e928b44957dbf5c0311`

Both instrumentation APK hashes remain exactly those listed above. These local
debug artifacts are not the permanent-signer Device Acceptance Build.
All401 source/resource input hashes were frozen before this run and are compared
again before publication; Tasks1/2 and immutable fixtures remain unchanged.
The helper foundation still has no production callers; complete CB01–CB12,
Task3 runtime migration and full product/signed/owner acceptance remain pending.
**PHASE 3 RESULT: NO-GO.**
