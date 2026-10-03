# Original biometric enrollment deletion outside its monitor

**PR59 DRAFT/unmerged; Phase3 NO-GO.** This scoped production correction starts from exact remote98642176dbec93d5721e66cbc19158110289d329, tree9989f5e43897bb81116f4ebf1859ae27a0ce5924. It addresses PendingEnrollment alias deletion and duplicate-close outcomes, not complete preauthentication ownership, controller/request teardown or Native Cipher/provider acceptance.

## Production correction

Normal PendingEnrollment.close previously executed backend.deleteOwned while its synchronized method held the enrollment monitor. Failed finish reentered close while still holding the same monitor. Close also set closed before deletion: after a deletion failure a subsequent close returned successfully, concealing that original failure.

Close now captures closed/consumed/header wipe and the exact installed winner under the same monitor, then invokes deletion outside it. markInstalled retains its original atomic relation to close: installed aliases never dispatch deletion; closed enrollments cannot be installed. A private completion future is allocated before backend.create, so this new acknowledgement metadata does not add a fallible allocating step after alias acquisition. Duplicate close waits outside the enrollment monitor for that same original deletion outcome. Native failure/Error identity remains sticky and is never retried. An interrupted duplicate preserves interruption and denies successful completion while the original deletion remains outstanding. Same-thread deletion-provider reentry and any inherited enrollment monitor deny before waiting, mutation or Native dispatch.

Finish preserves its consumed/closed entry rejection outside the failure-cleanup region. A rejected repeat does not close/delete a completed but uninstalled enrollment. Admitted failure cleanup executes after the synchronized finish block unwinds. The Native Cipher work inside that block is unchanged and remains a separate integration obligation.

## Actual regressions and evidence scope

Against exact preceding production, three new regressions failed3 of9 cases: normal deletion held the enrollment monitor; failed-finish deletion held it; repeat close after failed deletion silently succeeded. Parent source, raw log and failed XML are preserved. Corrected initial9 enrollment plus7 existing controller biometric cases passed16/16.

Independent review identified inherited-monitor entry as another denial boundary. A new test actually failed1 of1 against the initial correction before the entry guard was added. The retained preimage was reconstructed by reversing only that guard addition; it was not independently hash-frozen before that test run. Corrected expanded14 enrollment plus7 controller cases passed21/21; adding the interruption fixture passed15+7=22 component cases. This component execution preceded moving the private future allocation before backend.create. Final frozen checks below execute the final production allocation ordering and all final tests; the preceding component qualification is retained.

All six original JVM enrollment tests and all seven controller biometric tests remain intact, including selected-alias/cancellation retirement races. Nine new cases add held-provider monitor availability, actual duplicate return ordering, same-thread reentry, Error identity, preserved completed-installation eligibility and interrupted duplicate waiting. Nine matching ART fixtures use public synthetic JCA keys and a synthetic backend. They exercise Android runtime Java monitor/future/cipher-format behavior, not actual Keystore authorization, owner storage or owner credentials.

One initial intended green invocation ran from the scratch directory and failed Gradle build-layout validation before testing. That log is retained separately; it is not a test failure or a green result. It was corrected by invoking Gradle from the actual build project. No failed record is overwritten.

## Remaining acceptance

Independent final read-only source review reports no scoped Critical/Important/Minor findings. The future allocation precedes the only provider creation and is passed unchanged to the sole PendingEnrollment constructor. The reviewer ran no builds or tests.

Outer SecondaryController/SecondaryBiometricRequest ranked gates, backend process-global aliasLock/provider work, Native Cipher invocation under the finish monitor, copied masters/unlock buffers and complete original preauthentication/producer manifests remain pending in RUNTIME_MIGRATION_LEDGER.md. This correction does not establish enqueue-only lifecycle invalidation, complete cleanup-capacity fit or whole Task3 acceptance. Tasks4–8 and all63 product exit gates remain pending. No owner migration or later phase.

Exact6d setup push545 and PR546 subsequently completed all27 successful steps and274 decoded Android PASSED cases each, including13 setup cases. Their merge6846789752631085be4d49164e00ee4e5a791f57 shares exact source tree17535b85531e04fe31be2c90f0f2f5fd240c4192 with ordered main93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10/source6d2283d46eaae31012bfd8be517d5fb5c42da2f5 parents. This observation supplements earlier publication-pending records. New source986 push547/PR548 were running when this candidate began; merge097edb014b619bde3293bf43a48267a3c11a34c5 has exact source tree9989f5e43897bb81116f4ebf1859ae27a0ce5924 and ordered main/source parents. New nine ART fixtures require final exact-head execution; expected298 cases is not a pass claim.

Original487 attempt1 remains a genuine unexplained packageDebug failure. Same-source retry/488 success and separate later511 heap evidence do not establish its cause. Historical failure records and all negative security tests remain preserved.

## Final frozen-source local verification

All488 exact final application/build/script inputs remained unchanged through281 targeted,932 complete debug JVM and939 complete Phase0 JVM cases, each with zero failures/errors/skips; both lints and all four application/instrumentation APK assemblies exited0. Full-suite XML attributes/hashes, selected raw XML, source hashes, APK hashes and lint reports are retained. Existing lint warnings/informational findings remain qualified rather than accepted as product gates. Fresh final APK backup/model checks, all14 SDK-configured packaged backup mutation tests, public vectors/design/browser/VPN/secret/legacy corpus checks passed. The final full executions include the future allocation before provider creation and all15 final enrollment cases. Nine ART fixtures compile; final exact-head execution remains pending at publication.
