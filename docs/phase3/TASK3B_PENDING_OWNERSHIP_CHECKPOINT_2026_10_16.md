# Task 3B pending-setup ownership prerequisite

Parent: 7c11f5d1b748dcd8fcb95b4cc53f09f7f1d9f8c8. Task 3B remains PARTIAL / NOT ACCEPTED. Production biometric acquisition remains disabled. Phase 3 remains NO-GO. No Task 3C work is included or authorized.

## Production delta

- PendingSetupResources reserves two independent Hidden IO children, pending-master and pending-secret, against the exact authentication attempt before initial setup/resume generates or unwraps either array. Replacement reserves one session-owned pending-secret against its original RECOVERY operation; it no longer makes an unused operation.key copy or calls postcreation ownForSession.
- Both cells start inert. ReleaseReservation.beginPendingBuffer atomically validates exact original, index and actual cell identity and performs bounded producer/borrow admission before callbacks. Original release seals ingress synchronously. Producer/consumer callbacks execute outside the cell/reservation locks. Revoked factory results cannot begin consumption. An already admitted synchronous consumer retains unwiped bytes until actual return; then retirement wipes them and acknowledges actual cleanup.
- PendingSetup retains the original resource facade instead of raw master/secret fields. Display conversion consumes the original secret synchronously and wipes that original after conversion. This does not own the resulting UI CharArray or immutable text rendering.
- Store.confirm and confirmReplacement wait for the same pending original's actual terminal retirement after leaving the storage root lock. Failed/incomplete retirement cannot return success. Replacement rechecks the original operation after waiting; controller publication still checks original sequence and epoch. The existing selected-vault, opaque-attempt and single-authentication-result final barriers remain unchanged.
- Direct-store host/Android fixtures use explicit test-only overloads that create genuine owned attempt resources. No unmanaged three-argument creation overload exists in production. Fixture teardown waits for actual authority cleanup before deleting files or reusing the shared process capacity.

## Capacity and locks

The new initial pending family occupies exactly two existing Hidden nonpresentation physical release slots; replacement occupies one. No pool size or partition changes. Each array has a separate receipt and physical child. These counts do not establish complete concurrent provider/store/worker peak fit. Original close dispatches cleanup without Native work or joining under controller/root/authority gates. Waiting occurs after the root gate exits. The one-time 32-byte original-secret wipe is bounded memory work, not Native/provider cleanup.

## Actual verification and historical failures

Initial primitive selection: four tests passed before integration. Independent review then found the need for atomic original ingress and a post-generation authority check; both were implemented before integration.

First migration execution failed compilation because two fault fixtures still directly constructed the retired raw PendingSetup representation. No tests from that run are counted as passing. After fixture migration, 45 tests ran: 43 passed and two failed (old private-field reflection and controller fixture reuse before actual cleanup). The exact-array wipe assertion was preserved against the new cell and teardown now awaits actual cleanup. Independent reviewers also found replacement success initially lacked retirement waiting; that genuine introduced production defect was fixed before the passing verification.

Final focused JVM selection: 89 tests, zero failures/errors/skips. Includes pending ownership 8, held replacement retirement 1 (both revoke variants), controller 8, controller biometric 14, store 7, store fault 7, Secondary authority 16, original terminal retirement 3, authority accounting 5, retirement accounting 10, and Task 3A OwnedPlaintext 10. Gradle exit 0, 5m9s.

Controlled compiled JUnit repetitions: late result after cancel 20/20; actual producer-return preservation 20/20; selected-confirmation/held-borrow replacement regression 5/5, each with both revocation variants. Total 45/45 pass. The replacement test awaits an armed selected-file atomicReplace observation before checking another root reader, captures all observer failures and checks every worker has returned.

Source/security Python checks with the installed SDK: 25/25 pass. An earlier SDK-unset invocation ran 17 pass/one skipped class; the SDK-enabled run actually executes those packaged checks. Both remain qualified accurately.

Android test compilation: debug Kotlin/Java PASS (41s); Phase0Evidence Kotlin/Java PASS (1m16s), in separate serial Gradle executions. The first combined variant command failed task selection because the build exposes one instrumentation testBuildType at a time. Initial separate debug compilation caught a misplaced test import before its package declaration; corrected without production changes. Neither failed invocation counts as passing.

Independent final source review found no remaining introduced blocker in this narrow batch, conditional on fresh green verification. It explicitly did not accept full Task 3B. No Android runtime/device evidence is available.

## Remaining mandatory Task 3B obligations

The two cells do not premanifest PendingSetup.masterCopy/recoverySecret copies, confirmation secretSnapshot/result copies, UI display CharArray/text, outer Executor queue/captured inputs, or complete provider/KDF/directory/store/metadata child sets. Those remain Task 3B blockers. Existing broader root-held Native work/cleanup and MainActivity coordinator ownership remain. AndroidX prompt acquisition stays denied until an independently accepted functioning lifecycle is available. The owner-approved exception remains limited to Android-internal Keystore operations without public retirement acknowledgement; this batch does not extend it or claim such retirement was observed.

Encryption/slot encodings, PIN wrapping, selected-vault identity, original encrypted media and Task 3A implementation remain intact. Main/protected Phase 3 branch/PR59 must remain unchanged. No release APK or physical acceptance is requested. Next work continues within Task 3B rather than treating this checkpoint as completion.
