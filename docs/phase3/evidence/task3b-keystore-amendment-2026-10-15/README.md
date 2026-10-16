# Task3B owner-amendment continuation evidence

Baseline bfd64caac5738e2d907697eb227ab21e487c3eaf. Task3A checkpoint5124de13 remains
in history. This is host/source evidence, not Android/biometric/native/device acceptance.

- source-freeze.json: SHA256 of606 non-generated source/build/workflow inputs before
  final broad execution. Interpreter caches excluded; their removal is not a source edit.
- focused-summary.json/focused-junit.zip: fresh cleared-result116-test focused selection,
  zero failures/errors/skips. Actual Android test Kotlin compilation also passed.
- execution-evidence.zip/logs/: actual successful and unsuccessful commands. SDK installation/JRE compiler
  failures are environment failures, not test RED/PASS. The claim() API compile failure
  is not behavioral RED. Actual behavioral RED files are identified below.
- review-red-*.xml: two actual review regression failures (rerouted crypto and outer
  closed request); subsequent green log includes both and relevant regressions.
- independent-review.md: fresh-context whole-Task3B findings and fix dispositions.
- execution-evidence.zip → unaccepted-prompt-candidate.patch: withdrawn executable candidate, retained only for
  recovery/history. It has unresolved review findings and MUST NOT be applied as accepted
  implementation. It is NOT part of final compiled source.
- execution-evidence.zip/adb-devices.txt: no attached runtime device; no internal Keystore retirement observed.

Actual behavioral RED logs: platform-cipher-red-jdk-recovered.log (two tests),
attempt-identity-red.log (two tests), temporary-key-rejected-use-behavior-red.log
(one test), task3b-review-rerouting-red.log (two tests). Their green counterparts and
source-freeze focused run are distinct completed executions. The first
 task3b-final-focused.log failed Android test compilation due the candidate fixture
API; repaired run passed, and the candidate API/fixture changes were subsequently
withdrawn. No failed/interrupted execution is counted as passing.

Toolchain: Gradle8.10.2, AGP8.7.3, Temurin17.0.20.1+1, Android platform36 and build-tools
36/34; restored official JDK asset SHA256
3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e.
Gradle invocations used --no-daemon, Kotlin in-process, --max-workers=2,
-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8. One Gradle execution at a time.
Logs contain deprecation/compiler warnings; no warning-free claim is made.

Final exact commands, broader raw JUnit/lint archives and totals are recorded in
verification-summary.json; those executions completed successfully. No whole-Task3B acceptance,
3C admission, release APK, signing/release workflow or owner physical acceptance.
