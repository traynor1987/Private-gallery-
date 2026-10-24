# 1.7.5.1 recovery checkpoint — CI stopped

Owner instruction: stop repeated Android CI; diagnose focused runs #12–#15 together before another run. No new workflow, signing, release-candidate marker or main merge is authorised by this checkpoint. Further backup commits must use `[skip ci]` until the consolidated diagnosis is complete.

## Preserved source

- Branch: `release/1.7.5.1-privacy-viewer-repair`.
- Previous remote head: `62306be088691fcf2f5f98271fc2d1039d699c12`.
- Corresponding local commit: `c5ec62a9d350b1fbcd5c2abfea586a9b077dd497`.
- Matching tree: `2396aeccb332077403f378579fb6fa7efc7becba`.
- Production main tree: `f6d126869d436274ef238a866990d3334f318af0`.
- Target: version 1.7.5.1, code 39; package `uk.co.traynor.privategallery`.
- Baseline remote: `c593215cea53d05489fb6215ee4514a9d9e7e1df`; installed code-38 signed source: `69bfade789df4a49d911091bb8cf8205c88a3a39`.
- Original certificate: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.

No owner device/media/credentials were accessed or changed. No encryption formats, keys, PINs or signing identity were replaced. Previous branches remain preserved. No code-39 signed acceptance APK exists. Full release/security/upgrade/signing gates have not run.

## Exact scope under diagnosis

| Focused run | GitHub run ID | Source SHA | Observed outcome |
|---|---|---|---|
| #12 | 40121851508 | 50e4aa35187e3a2bd4284e69c5942d9acf1a843e | Instrumentation Kotlin compilation fails; Android tests do not run |
| #13 | 40122327889 | 92963ada5b5c37670142b85c7b1e1de2e42dfb37 | API36: 47 pass; API32: 20 pass / 22 executed, 2 fail; final owned-dialog case not run |
| #14 | 40123514631 | eb5dbd357995361d593ee46f7836ee8c718158ee | API36: 47 pass; API32: 20 pass / 23 executed, 3 fail |
| #15 | 40125426839 | 62306be088691fcf2f5f98271fc2d1039d699c12 | API36: 47 pass; API32: 20 pass / 23 executed, 3 fail |

These are raw executed counts, not Gradle planned counts or continued-step conclusions. A continued step reporting success does not establish passing tests. The combined final gate failed correctly.

## Pending setup change preserved, not runtime-verified

The only checkpoint change outside documentation acknowledges Android's first-run immersive tutorial on the disposable API32 emulator using the CTS setting `immersive_mode_confirmations=confirmed`, with readback assertion. It does not change APK behavior, screenshot policy, vault authority or Android secure-capture permissions. No workflow has executed this change. First-run-guide behavior is outside that preconfirmed fixture configuration and remains an explicit limitation.

## Continuation constraints

1. Inspect the logs, raw XML, PNGs and traces for all four exact runs; verify artifact hashes and source identities.
2. Record common causes, certainty and unresolved gaps in one consolidated diagnosis.
3. Preserve existing assertions and secure-window protection. Do not accept missing/invalid screenshots as zero sensitive pixels. Do not disable meaningful release gates or request secure-layer capture.
4. Do not introduce a product fallback merely to satisfy the test harness.
5. Only consider one new focused run after all diagnosed failures have a coherent, reviewed correction. If protected-frame evidence cannot be obtained safely, retain a documented NO-GO and precise handover.
6. Full regression and original-key code38→39 debug/production upgrades remain mandatory before signing/delivery. No physical Fold acceptance is inferred.
