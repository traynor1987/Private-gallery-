# Phase 3 implementation evidence

**PHASE 3 RESULT: NO-GO.** PR59 remains draft and unmerged. This record
distinguishes implementation checkpoints from complete Phase 3 acceptance.

## Readiness baseline

The implementation starts from exact design head
`b4a34a9e2784f8b8497c46be8ac30d56b751c28e`, independently checked against
the report, six normative contracts, negative matrix and exit gates.
`IMPLEMENTATION_READINESS_REVIEW.md` records the initial independent review and
two explicit clarification edits. A subsequent focused counter-staging review
identified Important LS-01. `LEDGER_STAGING_REVIEW.md` independently closes
the added exact staging/restart contract with no remaining Critical/Important
design finding. Further Task2 preparation identified Important LC-01: full-tree
counter-stage capacity conflicts with A2's opaque-media recovery projection.
`LEDGER_CAPACITY_REVIEW.md` independently closes the exact bounded projection
exception and mandatory fresh ordinary full-tree re-admission, with no remaining
Critical/Important finding. Durable implementation admission is restored.
The pure codecs
grant no storage authority. The transfer state, restart/rollback,
ownership, durable-write, receipt-release and lock contracts remain binding.

Remote verification at readiness confirmed PR59 open, draft and mergeable,
with the supplied exact head. Android run480 (`36998388489`, pull-request
event) succeeded at that head. This baseline CI is not implementation CI.

## Executed local baseline checks

The unchanged baseline checkout's full `testDebugUnitTest` completed:
111 suites,501 tests,0 failures,0 errors,0 skipped; Gradle BUILD SUCCESSFUL.
The recovered local toolchain uses Gradle8.10.2,JDK17 and Android SDK36;
repository dependency versions were not changed to recover the toolchain.

The following additional baseline checks passed:

- 30 Phase 3 design-reference tests and5 frozen future-format vector tests.
- Immutable legacy fixture checksums and identical instrumentation fixtures.
- Backup source policy and Python verifier tests (the APK-dependent case was
  skipped before an APK existed; packaged verification remains a separate check).
- Retired model-weight source policy, tracked-source secret scan and
  WireGuard-only dependency/notice policy.
- Three Browser helper tests and whitespace validation.
- `lintDebug`, debug APK and instrumentation APK builds; packaged backup XML
  exclusions and packaged retired-runtime/model-weight checks.

These results preserve baseline regressions. They do not establish production
transfer safety, native resource release, filesystem power-loss behavior or
owner physical acceptance. Executable production negatives, fault boundaries,
signed candidate and owner acceptance remain pending until their specific
evidence is recorded below. No owner data was accessed or migrated.

## Checkpoint 1: canonical codecs and explicit readiness closure

The source was frozen at local reviewed commit
`836e8c5d0434444ccb1a7f62ec22805aeefd0456`. Five internal production codec
files implement bounded canonical S2/P1/R2/A2 bodies; three regression suites
cover literal fields, malformed framing, ownership/evidence relationships and
historical ciphertext-hash bindings. These byte objects grant no storage/key or
deletion authority. No transfer/import/storage/UI behavior is enabled yet.

Independent task review found two Important omissions. The fixes bind exact
original collection covers/membership timestamps in M1 and require complete
encrypted historical-index bytes with bounded exact length and receipt SHA256.
Behavioral fix RED:28 tests,5 failures before these fixes. Final targeted
GREEN:29 tests,3 suites,0 failures/errors/skips. Scoped independent re-review
closed both findings and the literal-field assertion Minor;0 remaining
Critical/Important/new Minor. `CODEC_IMPLEMENTATION_REVIEW.md` records scope
and remaining adapter responsibilities.

Parent post-fix checkpoint verification passed:

- Full debug JVM:114 suites,530 tests,0 failures/errors/skips; Gradle success.
- Phase3 design-reference30 and frozen future-format vectors5.
- Immutable fixture SHA256 manifest and identical instrumentation assets.
- `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`: Gradle success.
  Lint:0 errors,65 warnings,4 information. All nondependency diagnostics match
  pristine baseline; nine additional dependency-availability warnings reflect
  refreshed metadata. No warning suppression or dependency upgrade.
- Phase0 evidence JVM:115 suites,537 tests,0 failures/errors/skips; evidence
  lint/application APK and explicit evidence instrumentation APK build success.
- Backup source/merged manifest/packaged APK XML checks;14 Python verifier
  tests with SDK available,0 skips; retired runtime/model checks in source/APK.
- Tracked-source secret scan, WireGuard dependency/notices, three Browser
  helper tests and whitespace checks.

The Android workflow adds the exact Phase3 push branch and design-reference
tests; all existing Phase0/1/2 regression/instrumentation checks remain.
Remote push/PR CI verification is pending until the checkpoint is published;
baseline run480 cannot satisfy it. Publication requires tree equivalence to
this reviewed code plus separately checked documentation changes.

The original95 negatives,50 closure negatives,24 fault expansions,6 staging
cases and6 capacity cases remain PENDING complete production execution.
Codec regressions do not mark full transfer/restart/lock/Android cases PASS.
Permanent-signer candidate and owner physical acceptance remain pending.
No owner data was accessed/migrated; no Primary encrypted bytes changed.
