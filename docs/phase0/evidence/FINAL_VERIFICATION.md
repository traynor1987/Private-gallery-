# Final local Phase 0 verification — resumed 30 September 2026

Tested source: remote `a14dbb39338aeca4b5a983f1c9e46088606dce85`, tree `8eb914cf4e0b1fdecd3e3fb0fbc11e510814fdd7` (identical to local fix commit `466588780fe327fd1dadfd16c5b2936281f534c1`). The later evidence-only commit containing this record does not change production or test sources.

## Executed local results

| Check | Executed result |
|---|---|
| Debug JVM | 380 tests / 89 suites, 0 failures/errors/skips |
| Evidence-variant JVM | 387 tests / 90 suites, 0 failures/errors/skips, including 7 Browser-policy tests |
| Normal Gradle gate | `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`, BUILD SUCCESSFUL, 2m30s |
| Isolated evidence gate | `-PPRIVATE_GALLERY_PHASE0_EVIDENCE=true :app:testPhase0EvidenceUnitTest :app:lintPhase0Evidence :app:assemblePhase0Evidence :app:assemblePhase0EvidenceAndroidTest`, BUILD SUCCESSFUL, 2m20s |
| Exclusion mutations | 10 Python tests passed with real API36 `aapt2`/platform; 18 domain-removal cases plus compiled policy mutations |
| Actual debug APK policy | Source + merged manifest + binary packaged rules passed all nine domains in declared cloud/D2D modes |
| Future reference vectors | 5 Python tests passed |
| Frozen corpus | All 27 SHA-pinned objects passed; archive/expected Android assets byte-identical |
| Browser helper | 1 Node test file passed |
| Privacy/dependency checks | No-secret scan, WireGuard-only/notices, retired runtime/source and actual debug APK checks passed |
| Crypto/manifest baseline delta | No changes in core/crypto, production manifest or FileProvider paths |

Toolchain: Temurin JDK17.0.20.1, Android platform36/build-tools36.0.0 (AGP also selected build-tools34), Gradle8.10.2, Kotlin2.0.21/AGP8.7.3. Full runs used bounded workers and in-process Kotlin. JDK/SDK/cache were recovered after the interruption. The system trust store was required for downloads. No local hardware-accelerated emulator or permanent signing credentials were present.

Regression RED before correction: 380 JVM tests, 2 named behavioral failures in recovery-route restoration and empty-restore-stage admission. After correction both pass in the full suites. Android Activity/PIN adapter regressions compiled; their actual device execution is governed by the CI result below. No local device execution is claimed.

## Exact GitHub checks

[Android push run 36693121391](https://github.com/traynor1987/Private-gallery-/actions/runs/36693121391) and [PR run 36693127341](https://github.com/traynor1987/Private-gallery-/actions/runs/36693127341) target the tested fix head above. They were launched and their build/exclusion stages inspected during recovery. At this record's commit, final emulator completion was still pending; the complete external Phase 0 report records terminal outcomes or explicitly leaves CI admission unresolved. Readers can inspect the linked executions; pending is never PASS.

The workflow compiles both variants but executes normal debug instrumentation only. Browser evidence-package profile/process/persistence measurements are PHYSICAL ACCEPTANCE REQUIRED. Emulator instrumentation exercises the synthetic frozen restore rehearsal, scoped repositories/resources, recovery adapter, stale-result checks and existing UI/Browser regression suite. It does not prove Samsung transfer, real biometric policy, physical power loss or owner upgrade.

## Local artifact identity (debug/evidence only)

| Artifact | SHA256 |
|---|---|
| debug APK | `b0f8f3835e23e9e60162ce1834d63d4d9cc18a9b3af17f5608d8edad6539488e` |
| debug test APK | `0ddaf6c9bd890aa0991b0b8c32e296454fa9c8c17acd0accc5ac0574a2208e78` |
| evidence APK | `3ffdc21873a629e7fb3f8fb284735f5c4eb4ef7567303bd1f36f4354ce0ba725` |
| evidence test APK | `26abbbcf117ef22226b15339efb19628fe7b739f24e9d15bef90166ad9304a5b` |
| fixture SHA256SUMS | `cd26479fe9b077d81cd52b0c674900f0c475508e7081324cee396512770cc7bc` |

These are local debug-signed artifacts, never an owner upgrade or replacement signer. Evidence package is `uk.co.traynor.privategallery.phase0evidence` with version1.0.27-phase0-evidence/code28; production remains1.0.27/code28. CI artifacts are separately identified by their own run/build hashes.

## Admission boundary

NO-GO: signer/owner-safe upgrade and separate restore, real lifecycle/biometric/durability/OEM measurements remain unperformed. B02/API36.1 cross-platform extraction policy is unresolved. Hidden Browser provider/storage/process measurements and policy remain separately CLOSED. Follow PHYSICAL_ACCEPTANCE.md; do not infer future-key or owner-data safety from synthetic counts. No Phase1, production Hidden key/Vault, migration, release or publication began.
