# Task3B fixture recovery checkpoint — PARTIAL

Parent: 1eabc5875b9c1fd805fb2f699fda9e86684754af. Task3B is NOT accepted.

The unchanged original selected-alias regression passed once and in 30 repetitions before the fix. A controlled harness then held a prior fixture's real provider close after its old close() returned. The unchanged original failed at Fixture.create line69 / original target line286 (Required value was null). This proves a concrete fixture-isolation cause; the lost historical broad-run trace cannot conclusively be attributed to it.

New regression fixtureCloseWaitsForProviderReturnBeforeDeletingItsDirectory failed before the fix (one actual test, one assertion failure). Fixture.close now releases fixture barriers, calls controller.close, and waits boundedly outside its gate for real authority.cleanupComplete before deleting files. Failed or incomplete cleanup remains visible. No production check, alias assertion or capacity was changed.

Actual verification:
- Focused Gradle :app:testDebugUnitTest selections SecondaryControllerBiometricTest, SecondaryControllerTest, SecondarySessionAuthorityTest, SecondaryAuthenticationWorkerTest: 41 tests, zero failures/errors/skips; exit0.
- Original selected-alias race after fix: 30/30 repetitions passed.
- Held-provider teardown regression after fix: 10/10 repetitions passed.
- Independent read-only review: no blocking finding in this test-only change; report included in raw archive.
- Full debug/Phase0 JVM, lint, Android test compilation and Android runtime have NOT been accepted by this batch. No physical-device test occurred.

Commands and complete logs/XML/harnesses are retained byte-for-byte in raw-evidence.zip with SHA256SUMS. RepeatJUnit runs the actual JUnit method using Gradle's captured test classpath and the built host JNI library. Configuration failures while restoring the missing JDK/SDK are not counted as test runs. Historical evidence is unchanged.

Task3A and all production files remain exactly unchanged from the parent. Biometrics remain DISABLED. A–D ownership/lifecycle/admission obligations in TASK3B_COMPLETION_CHECKPOINT_2026_10_16.md remain mandatory. Main, protected Phase3 branch and PR59 are outside this change. No encryption format changes.
