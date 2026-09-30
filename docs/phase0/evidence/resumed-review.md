# Resumed Phase 0 verification and adversarial review — 30 September 2026

## Recovery of the interrupted state

The prior worktree was recovered at local `c39bc52e1c05bbf12c7a4ef6c4b83d80186a118b`, tree `e8fb9b5633f5c9e698d330309639ef541e6aff90`. It held 18 committed Phase 0 changes and no tracked uncommitted changes. Only generated Python caches were untracked. Remote `main` and `phase0/security-admission` initially both pointed at audited `a19218479eb9b9bcdb35ff0035fc78522263c891`; no Phase 0 Actions run existed.

The recovered history was transferred as 18 logical commits through the authenticated GitHub object API. Every reconstructed commit tree was checked against its corresponding local tree, including the final tree above. Commit metadata/IDs differ; source trees do not. Recovered remote head: `a8549159af3584b6f7ccb02966836ae19681d5ca`. Draft review: [PR56](https://github.com/traynor1987/Private-gallery-/pull/56). Main was not merged or modified.

The previous worktree's persisted XML records contain 377 debug JVM tests across 88 suites and 384 evidence-variant JVM tests across 89 suites, each with zero failures/errors/skips. They are historical evidence, not a new execution. The external draft report still contained final identity/test/CI/inventory placeholders. Consequently neither the report nor the previous visible status established completion.

## Fresh review and one fix pass

A fresh, read-only reviewer checked the full baseline-to-recovered-HEAD branch, contract, future specs and negative-test coverage. It found two Important defects and no Critical defect:

1. A retained `RECOVERY_KEY_SETUP` destination survived Activity recreation while its Activity-local one-time display did not. Rendering the restored destination used `checkNotNull` on the missing display. The extracted real destination policy reproduced the defect in `PrimaryRouteRestorationTest.lostOneTimeRecoveryDisplayRequiresAuthenticationBeforeRendering`. The policy now routes to LOCK; Activity reconstruction actively revokes the session and clears the retained recovery destination. Normal destinations retain the intended live session. The pending encrypted recovery record and existing VDEK are unchanged; legitimate Primary authentication can restart pending setup.
2. A failed archive restore cleared any PIN envelope present, even one created by another flow. An empty staging directory also failed to reserve setup admission. `PrimaryVaultSetupGuardTest.emptyRestoreStageStillOwnsAdmissionWhileArchiveReadIsBlocked` reproduced the admission defect. Empty restore staging now blocks fresh setup; `PinVaultKeyStore.create` rechecks admission before save. Credential writes use the shared preferences monitor. Archive PIN installation checks that no PIN material appeared, and returns an exact-record rollback capability. Rollback can clear only bytes installed by its own transaction, never a newer PIN change. Recovery rollback already uses expected-record CAS.

Actual RED: configured Android Gradle/Kotlin 2.0.21 ran 380 JVM tests with the two named failures; `testDebugUnitTest` failed (4m41s). This was a behavioral failure, not missing API/compile evidence. The full log is retained in the resumed verification workspace. The additional Android regressions exercise actual Activity recreation, a blocked archive read interrupted after another credential flow, and a rollback receipt after a subsequent PIN change. Their execution must be reported from final CI, not inferred from compilation. The PIN rollback Android test was added before the correction but its pre-fix device execution was unavailable locally; do not claim an executed RED for that adapter test.

## Rulings and unresolved gates

- Recovery display loss locks rather than retaining/serializing or silently regenerating the displayed secret. This preserves one-time disclosure and owner-data keys. Cost: the owner reauthenticates and restarts only pending setup after recreation.
- Valid compatibility crypto objects remain unchanged. `core/crypto`, production manifest and FileProvider paths have no delta from the audited baseline. There is no owner media in this environment. Fixture hashes and immutable-reader tests prove their stated synthetic compatibility; they do not prove a same-signer owner upgrade.
- Android16 QPR2 cross-platform extraction remains unresolved. The official Auto Backup guide, rechecked on 30 September, still describes a separate iOS mode with required partner parameters and does not establish an Android-only app's safe omission/opt-out semantics. No fictitious iOS identity or unsupported XML was added. Packaged cloud/D2D exclusions and OEM measurements are separate evidence. Mandatory gate D/B02 remains NO-GO.
- A same-signer acceptance build, owner-safe restore/upgrade, Samsung/OEM extraction, real biometric/low-storage/power interruption, and Browser provider/process/residue measurements remain PHYSICAL ACCEPTANCE REQUIRED. The 22-checkpoint procedure remains authoritative. No release, production Hidden feature or Phase 1 work began.

## Fresh final verification

Final local and CI execution identities, counts, artifacts, limitations and admission verdict are recorded in `FINAL_VERIFICATION.md` and the complete Phase 0 report. Do not infer a pass from this document alone.
