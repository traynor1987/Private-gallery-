# Task3B Keystore-only amendment checkpoint — 2026-10-16

**Task3B: PARTIAL. Full acceptance BLOCKED. Task3C must not begin. Phase3 NO-GO.**
This is a coherent implementation checkpoint, not completion or owner/device acceptance.
Continues actual remote bfd64caac5738e2d907697eb227ab21e487c3eaf, preserving verified
Task3A5124de138a3bcb9f3e5fec186a8442297459e654 and historical failures/evidence loss.
The exact new remote SHA is the recovery commit containing this report; parent bfd64caa.
Branch: recovery/phase3-task3-wip-2026-10-06. No reset to an older checkpoint.

## Implemented

- CLEANUP_CAPACITY/LOCK_ORDER record the owner-approved exception for inaccessible
  Android-internal Keystore retirement only. No internal retirement positively observed.
  All supported app-owned acknowledgement/capacity/wiping/lock requirements remain binding.
- PendingEnrollment/PendingUnlock deny fresh Cipher access after consumption/cancellation,
  retain admitted-use barriers, and detach their app reference after actual use return.
  Outer SecondaryBiometricRequest delegates to the pending original, retaining no second
  publicly exposed Cipher after retirement. Selected-vault/store/encryption formats unchanged.
- App attempts use opaque reference identity instead of counters that alias between owners.
  A one-shot claim binds Primary success to its exact originally supplied Cipher before
  provider use, denying an old result rerouted by Activity-shared AndroidX callbacks.
  This is not proof of prompt lifecycle retirement or isolation of terminal error events.
- OwnedTemporaryKey preattaches a bounded32-byte comparison original. Revoke/exception/late
  results retire the exact original, preserve admitted borrowers, wipe the actual array and
  retain physical accounting until release invocation return and original acknowledgement.
- Settings PIN callback uses its original UI binding rather than a generic Native-under-gate
  binder or ambient replacement session. Comparison awaits actual temporary-key retirement
  outside its authority gate and rechecks its original operation before success. Interrupt
  status and PIN wiping are preserved. This owns its comparison key, not the implicit
  PIN KDF/store/provider/input resources; controller's ambient comparison adapter remains open.

Production: MainActivity.kt, SecondaryBiometricSlot.kt, SecondaryController.kt (request
access only), BiometricUnlockAttemptState.kt, OwnedTemporaryKey.kt, ReleaseReservation.kt,
ScopedIoGuard.kt. Exact source/test paths and blobs: evidence implementation-files.json.
No Task3A reimplementation or cryptographic-format changes.

## Actual final verification

| Check | Actual result |
| --- | --- |
| Source-freeze focused JVM selection |116 passed;0 failures/errors/skips |
| Full debug JVM |1267 passed;0 failures/errors/skips,199 classes |
| Full Phase0 JVM |1274 passed;0 failures/errors/skips,200 classes |
| lintDebug |0 Fatal/Error;42 warnings,4 information |
| lintPhase0Evidence |0 Fatal/Error;60 warnings,4 information |
| Debug Android test Kotlin/Java compilation |PASS |
| Phase0 Android test Kotlin/Java compilation |PASS with evidence flag |
| Python script/source/security tests |23 passed;0 skipped |
| Format vectors and Phase3 design tests |35 passed |
| Node regressions |3 passed |
| Backup exclusion / retired local AI / secret checks |PASS source/synthetic scope |
| Source/build/workflow freeze |606 non-generated inputs unchanged |
| Android runtime, OEM/Keystore, physical biometric tests |NOT RUN; no attached adb device |

One Gradle execution at a time. Broad final Gradle exit0,15m12s; separate Phase0 Android
compilation exit0,15s. No release APK, signing/release workflow or physical acceptance request.
Historical RED, API/fixture compilation and toolchain failures remain archived, not counted as
passing. Current frozen-source runs have no failing tests. Raw JUnit XML archives, lint XML,
exact commands/results, source hashes and original unmodified logs are retained under
[evidence/task3b-keystore-amendment-2026-10-15](evidence/task3b-keystore-amendment-2026-10-15).
Earlier missing raw evidence remains historically missing; these are fresh verification records.

## Independent review and remaining blockers

Fresh-context gpt-6-astra whole-Task3B review remains **acceptance BLOCKED**. Actual cached
AndroidX biometric1.1.0 bytecode uses Activity-shared callbacks/fragment; old saved success
and errors can be dispatched to the replacement callback, and old cancellation can affect
its current fragment. Exact Cipher matching closes stale-success admission only for the
Primary path migrated here. Errors lack Cipher identity; a complete prefunded app prompt
lifecycle coordinator/barrier is still mandatory. Sensitive and cross-purpose paths remain.

The candidate broader Main prompt rewrite was withdrawn after review found a newly exposed
delayed double-cancel and operation leak on throwing replacement cancellation. Its exact
unaccepted patch is retained inside execution-evidence.zip, never executable source.
Candidate fixture API changes were restored to baseline. No lifecycle-only cleanup waiver.
Two review regressions failed before fixes and passed afterwards; held-release and reentrant
key-use tests prove completed stages/wipes do not discharge an unreturned release callback.

Still required for Task3B, without any further security waiver:
1. Full shared prompt/request lifecycle precreation ownership, real terminal quiescence,
   exact replacement/cancellation and app-owned acknowledgement across all auth paths.
2. Complete worker/provider/KDF/store/input/result manifests before acquisition; eliminate
   raw Hidden key promotion and unfunded workers, including recovery/restore/sensitive paths.
3. Detach bounded state then perform Native/provider/store/request cleanup outside every
   prohibited outer ranked gate; remove controller-held request closes and Native commits.
4. Final same-original short admission only after all mandatory app resources acknowledge;
   broaden deterministic tests for those actual migrated production paths, then reverify.

No entire mixed runtime row closes. Source integrity, fixed process capacity, Task3A and
vault compatibility are preserved; incomplete app-owned cleanup cannot be treated as accepted
under the Keystore exception. Main remains93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10;
PR59's protected head remains79bb1928f3fbe16e935e587a3fe594158cb3f529, draft/open/unmerged.
No modification to main, protected Phase3 branch or PR59 is authorized by this checkpoint.
