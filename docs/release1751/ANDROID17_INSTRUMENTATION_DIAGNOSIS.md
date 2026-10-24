# Android17 focused run16 — diagnosed test dependency failure

Decision: **NO-GO pending a reviewed instrumentation correction and fresh supported-platform gates**. No full release/signing/APK. Minimum Android16/API36 remains the owner's authorised support policy; Android12 remains unresolved/unsupported.

Run40131917872/job120985237857 tested sourcef98d9938e88df5ebc67c56963ca5539ec8b4f381/tree78a5f0fe7da825f4d7b064db23189978da1a630b. Artifact12576115650 SHA25683b2ec6fa3b8859a7522db8ff63feb4f9a3cbb22088c1905897527fe62eea486 was downloaded, checked against GitHub digest and ZIP CRC, and independently recounted. RUN_16_EVIDENCE.json retains exact identities, fingerprints and failed method names.

| Gate | Executed | Passed | Failed | Skipped |
|---|---:|---:|---:|---:|
| JVM |349|349|0|0|
| Android16/API36 |47|47|0|0|
| Android17/API37 |47|8|39|0|

The combined gate correctly failed. Both continued platform-step conclusions said success, which is not evidence of passing outcomes. No result was accepted from those summaries.

All39 Android17 failures contain the same Espresso.onIdle → InputManagerEventInjectionStrategy.initialize → NoSuchMethodException: android.hardware.input.InputManager.getInstance[]. They affect21repair,11video,5media and2capture cases during shared Compose/Espresso setup, before their app assertions. All5PrivacyShield and3SmartUnlock cases passed. This proves an instrumentation dependency incompatibility, not39production defects or complete Android17 acceptance. The code's app/src tree remains byte-identical to the recovery checkpoint.

The declared test dependencies are Espresso3.6.1, runner1.6.2 and extJUnit1.2.1. Official AndroidX Test release notes for stable Espresso3.7.0(July30,2025) explicitly record replacing reflective InputManager.getInstance with getSystemService. The upstream fix is fddcda4adc8eaba515fbf2d790cf8dfe5c1b722d. Google Maven source archives independently confirm:3.6.1 eagerly reflects the missing method;3.7.0 selects Context.getSystemService(InputManager.class) for API>=23 and retains a legacy reflection fallback only below23. Supported API36/37 therefore select the public service path.

Primary sources:
- https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0
- https://github.com/android/android-test/commit/fddcda4adc8eaba515fbf2d790cf8dfe5c1b722d
- https://dl.google.com/dl/android/maven2/androidx/test/espresso/espresso-core/3.6.1/espresso-core-3.6.1-sources.jar — SHA25628daca7633543ab01ee20401a84ca160f8404a0b3693a3c7101737eaa25665a5
- https://dl.google.com/dl/android/maven2/androidx/test/espresso/espresso-core/3.7.0/espresso-core-3.7.0-sources.jar — SHA256528c9e1986979fd6cd2f32e86507ffb847f3e88c0be2f039a03fb41de3642cff

Independent read-only review verified archive hash/CRC, counts and all39 shared traces. Official Maven POMs show Espresso3.7.0 itself already depends on runner1.7.0/core1.7.0/monitor1.8.0; extJUnit1.3.0 aligns with that cohort. A narrow stable androidTestImplementation-only alignment(Espresso3.7.0, runner1.7.0, extJUnit1.3.0) is justified; no production dependency or test assertion change is justified. Exact correction, graph resolution/compilation and final review remain pending at this diagnosis checkpoint.

Next safe action: preserve this diagnosis checkpoint without triggering CI, apply one coherent test-only correction, inspect the resolved dependency graph and compile, independently review the exact diff, then execute one affected supported36/37run. Keep all six classes, actual-outcome gate, security assertions and historical evidence. Only after both supported phases pass may complete regression/original-key debug+production38→39upgrade/restart/signing proceed. Do not hide the Android17 failure by dropping that available platform or accepting missing evidence.
