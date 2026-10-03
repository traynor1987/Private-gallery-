# Phase3 authority factory checkpoint (2026-10-03)

**DRAFT / Phase3 NO-GO. Task3 production migration is incomplete.**

Parent:81360252e0f48a36cc6c9864a1d01de39d56dc5a. This checkpoint supports original-operation pre-creation factories in the Primary and independently partitioned Hidden authorities. It migrates the Activity protected Job launcher only; no store, transfer, picker, media presentation, credential/controller-attempt or owner migration completion is claimed.

## Scope and provenance

The nine previously verified helpers came from durable parent8136025. Supporting factory API/test postimages were compared against remote a8f6467 and reconstructed where necessary. Only explicit new authority registration/retirement/API blocks were applied to the original authorities. The surviving cached cleanup scheduler was rejected; no cached/unbounded executor was adopted. Unpublished preceding-session runtime success is not represented as recovery evidence.

Complete manifests reserve fixed physical release slots before invoking factories, with a second original-operation check before registration. Factories attach partial children immediately, and verify the original result without allocating iterators after child creation. Original reservations can transfer to session ownership only from their live creating operation. Jobs are created inert/lazy; actual completion and cancellation invocation return remain separate obligations. Registry removals must return before pending counts acknowledge retirement.

Process pool barriers now cover actual retired/failed tickets, normal release dispatch and authority snapshot dispatch. They become visible before local revocation and any fallible snapshot. A dispatch/snapshot failure pins its existing partition for the process; a new authority cannot reset it. No pool gate is held across child dispatch or supplied callbacks. Primary and Hidden use separate fixed partitions. The existing raw APIs and synchronous legacy drain remain transitional until all production callers migrate; these paths do not satisfy the complete CB contract.

## Actual regression and independent review evidence

- Process-wide cross-authority regression:3 tests/2 failures before barrier correction;3/3 afterwards, including Primary/Hidden partition independence.
- First runtime review: actual4/4 failed tests exposed revoked snapshot failure and post-construction Job completion-hook installation failure. Both corrected.
- Concurrent snapshot window: actual6 tests/2 failures exposed admission before the failing snapshot returned. Dispatch accounting now precedes the snapshot.
- Normal release iterator fault: actual7 tests/1 failure exposed invisible undispatched retirement. Indexed release removed iterator allocation.
- Final normal dispatch/attachment audit: actual10 tests/3 failures exposed the pre-first-ticket context-switch window and iterator allocation losing a just-created attached/malformed-result child. Preallocated original pool dispatch accounting and indexed attachment/result verification fixed all three.
- Final targeted helper/API suite:94 tests,0 failures/errors/skips.
- Activity capacity test actually failed1/1 before migration; afterwards5/5 passed including the four existing Activity tests compiled from their exact source/functions in the isolated JVM harness. This is component evidence, not Android execution. The existing cancelled-lifecycle test now waits at most5 seconds for acknowledged asynchronous cleanup before retaining the same positive assertion; key-wipe/negative assertions are unchanged.
- Independent read-only final re-review: no unresolved Critical/Important finding in these scoped changes, no Minor notes. Complete raw-caller, guard, credential-attempt, native/device and product review explicitly excluded.

The XML and source snapshot are in `evidence/authority-factories/`. Failed-release tests use isolated synthetic pools and retain their original failed obligations; they never reset production process pools. Separate process-barrier tests use the actual default pools without reset. Fault injection is synthetic JVM concurrency/allocation evidence, not a claim of device/OOM behavior.

## Remaining Task3 and Phase3 work

Every remaining raw resource/Job caller must migrate before Task3 acceptance. This includes controller authentication/enrollment exact-attempt handoff, network disconnect/producer/native reader children, key buffers, provider/backup and vault storage, playback/bitmap/crop and UI lifecycle paths. Remove raw ownership APIs and the legacy synchronous drain only after supported callers have complete pre-creation manifests and independently acknowledged cleanup. CB01–CB12 and lock-order tests remain pending.

Tasks4–7 remain selected schema2 Hidden media storage, full authenticated Primary sources/encrypted holds, paired Copy/Move/receipts/restart/restricted restoration, and concealed media UI. Task8 requires the complete final exact-head push/PR CI, full integration security/regression review, permanent-signer artifact identity and owner physical acceptance. No later phase, merge or owner-data migration.

## Frozen exact candidate verification

Fresh debug JVM723/723 and Phase0 JVM730/730 passed, all0 failures/errors/skips. Debug/Phase0 lint and all four debug/Phase0 application/instrumentation APK assemblies passed. App/source/workflow hashes were checked before each command and after the complete sequence. A preflight whitespace correction stopped the first invocation before Kotlin/test compilation; it is not counted as a test result. Earlier718/725 runtime results preceded the final normal-retirement/attachment and launcher corrections and are not substituted for this final candidate.

Reference contracts30, vectors5, all14 SDK-backed backup mutation tests/no skips, immutable fixture hashes and identical Android copies, secret/source-backup/VPN/model-distribution and browser helper checks passed. Build/static summaries and frozen source hashes are checked in. Native/Android execution is delegated to exact new remote CI, not claimed locally.

Lint severity counts:

{"lint-results-debug.xml": {"Information": 4, "Warning": 38}, "lint-results-phase0Evidence.xml": {"Information": 4, "Warning": 60}}

Local development-signer artifacts (not permanent-signer acceptance):

| APK | SHA256 |
| --- | --- |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `72b9308240bced901f8873e9c8885d2f03fa879b07dd4e0145b76fb8c4385669` |
| `app/build/outputs/apk/androidTest/phase0Evidence/app-phase0Evidence-androidTest.apk` | `92b25365460afed97efb3939ccf57d2c8b8387147eeb225f9770918c318ba633` |
| `app/build/outputs/apk/debug/app-debug.apk` | `253e03c801f532233b99ccf88b0afa0dc553e5eac8f3f6c33b58765ce63fed23` |
| `app/build/outputs/apk/phase0Evidence/app-phase0Evidence.apk` | `86d571be700f39575f49c07e7b0cfb1d13b6f936029d47ca855894c31185a0d7` |
