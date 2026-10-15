# Task 3 recovery verification — 2026-10-15

Task 3 is **INCOMPLETE** and Phase 3 is **NO-GO**. This evidence verifies the
recovery/lifecycle repairs and bounded ownership groundwork, not complete
production caller migration, supported producer fit, Android Native behavior or
owner acceptance.

Production source checkpoint: `e1055c6a1134ac221a3c9d2aab1c3edb4e0655a8`.
`verified-inputs.json` records every one of the 575 frozen source/build/workflow
inputs. The evidence-only follow-up changes no input in that manifest.

`verification.json` contains fresh complete JVM counts. Both result directories
were removed before the full run: Gradle retained older XML files during filtered
runs, so summing those directories without cleaning was not valid fresh-suite
evidence. The full run uses per-test START/END logging; its compressed log is
`task3-jvm-final.log.gz`.

The final build logs are `task3-build-final.log.gz` and
`task3-phase0-instrumentation-build.log.gz`. Packaged backup XML is checked with
SDK `aapt2` against both app APKs and their merged manifests; source policy,
retired-runtime/secret/VPN checks and immutable corpus/identical Android fixtures
also passed. These are host build/static checks, not instrumentation execution.

Earlier failures and interruptions are retained:

- `debug-full-progress.log.gz`: 1,196 tests and six asynchronous test-fixture
  failures before the bounded completion/finally repairs.
- `ownership-primitives-red.log.gz`: actual missing-API compilation RED.
- `ownership-focused-green.log.gz`: 47 selected passing tests before the final
  two review regressions. Unselected retained XML is not counted.
- `ownership-review-red.log.gz`: two behavioral failures, admitting an authority
  gated factory and an unattached plaintext cell backed by a same-pool token.
- `ownership-review-green.log.gz`: 12 passing primitive tests with both guards.
- `lifecycle-behavior-red.log.gz`: three actual assertion failures under temporary
  mutations of startup, upload timer cancellation and reader/key mutex binding.
  All mutated production files were restored byte-for-byte before verification.
- `task3-full-verification.log.gz`: initial broad run interrupted while diagnosing
  slow tests. It is not a pass or proof of a production hang.
- `android-build-check.log.gz`: initial build session reached final assembly
  tasks but ended without a final build verdict. Its cause is not established;
  it is not counted as successful verification.

Final runs use Gradle 8.10.2, JDK 17, the existing API 36/NDK 27.3/CMake 3.22.1
configuration, in-process Kotlin compilation and two workers. Final packaging
uses the repository CI's existing 4 GB heap setting. No dependency, product
workflow, signing identity or format was changed for these checks.

No emulator, physical Samsung/Fold, permanent-signer or release acceptance is
claimed. Main and PR #59 are not changed by this checkpoint.
