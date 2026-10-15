# Task 3A plaintext ownership evidence

Continues 3fd3c623 on the existing recovery branch. Phase 3 remains NO-GO.
`verified-inputs.json` freezes 583 source/test/build/workflow inputs. Verify hashes
against the checkpoint tree; report/ledger/evidence additions are outside that set.
`focused-results.json` records 49 freshly selected tests, all passed, zero failures/errors/skips.
`task3a-review-green.log.gz` includes the focused run and Android test compilation.

Focused command (one Gradle execution at a time):

```
-Pkotlin.compiler.execution.strategy=in-process --max-workers=2
:app:testDebugUnitTest --tests '*Plaintext*Test' --tests '*OwnedDecryptedReadTest'
--tests '*EncryptedPayloadStoreTest' --tests '*ChunkedVaultVideoStoreTest'
--tests '*Phase3PhotoGenerationOwnershipTest' :app:compileDebugAndroidTestKotlin
```

The full JVM run removes both generated result directories first. Filtered Gradle
runs retain old XML, so unselected XML was never counted as fresh evidence. Full
per-test START/END logging uses a scratch init script; product Gradle/workflows
are unchanged. Final complete results and build commands are recorded separately.

Preserved REDs are genuine failures, not passing evidence:

- source/retriever/sniff admission guards failed before migration;
- handoff/read/decrypt/publication/delivery APIs were absent before implementation;
- behavior RED: queued generation body ran after source postcheck rejection;
- review RED: overlapping borrows rejected and positions beyond EOF threw.

Intermediate failed compilation/API runs are not passes. `coherent-focused` and
`final-focused` are earlier passing runs; the review-green run includes the final
review fixes. Static Python 20, vectors/design 35 and Node 3 tests passed, with raw
logs. Security/static log includes actual exit codes for secret, retired model,
VPN, backup source and immutable/identical corpus checks.

No emulator, Android instrumentation execution or physical owner acceptance is
claimed. No release APK, signer or workflow change. Full Native player/bitmap/
provider/generated-output accounting and other phases remain explicitly open.

## Complete JVM verification

Final command (after the interrupted invocation, unchanged 583 inputs):

```
-Pkotlin.compiler.execution.strategy=in-process --max-workers=2
-Dorg.gradle.jvmargs="-Xmx4g -Dfile.encoding=UTF-8" -I test-progress.gradle
:app:testDebugUnitTest :app:testPhase0EvidenceUnitTest
:app:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestJavaWithJavac
```

`task3a-full-jvm.log.gz` records all1222 completed debug cases and then an
interruption during Phase0 compilation. That invocation has no successful-build
claim and provides no passing Phase0 evidence. `task3a-full-jvm-complete.log.gz`
records the successful resumed invocation (6m51s, exit0), unchanged debug task
up-to-date, all1229 freshly executed Phase0 cases and Android Kotlin/Java
compilation. XML class totals are in `jvm-results.json`: each variant has zero
failures/errors/skips. No interrupted/stalled test is counted as passing.

Both final lints passed (exit0,4m50s), zero Fatal/Error findings. Exact warnings
and informational findings are retained in lint XML and `lint-results.json`.
Command: same compiler/worker/heap arguments, `:app:lintDebug :app:lintPhase0Evidence`.
