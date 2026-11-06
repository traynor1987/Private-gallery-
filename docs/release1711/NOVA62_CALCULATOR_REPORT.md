# Checkpoint C — Nova Calculator 1.0

## Source and scope

Started from clean `69b44c8c8b79e719e87dea69446d6938523c11b3`, the reviewed/pushed Arcade checkpoint. Task 2 is complete: remote `69b44c8c`, local implementation/review sequence `f6e1b82d..f006204c`, independent review `/tmp/nova62-arcade-r1-review.md` had no blocking findings. Arcade's M2 engine-formatting suggestion remains deferred. The minor Backup scheduling checkbox follow-up remains for the release writer. C does not change release/version/signing/workflows or existing Arcade codecs.

C adds the native Home app `nova-calculator`, keeping the existing Store website `calculator` separate. Existing layouts are retained; the new app appears in the existing drawer/catalogue and can use its existing Home placement/recents model. The calculator runs in both independently encrypted Spaces, offline, without an account or network request.

## Capabilities and numerical rules

- Standard addition, subtraction, multiplication, division, decimals, parentheses, sign, clear/delete, memory (`MC`, `MR`, `MS`, `M+`, `M−`), result copy and up to 50 history entries.
- Scientific powers, square root, sine/cosine/tangent, base-ten/natural logarithms, π/e, number exponent entry (`E`), factorial and DEG/RAD selection. Angle changes invalidate a previously displayed result so it cannot be mistaken for a calculation in the new unit.
- `BigDecimal` DECIMAL128 uses 34 significant digits and half-even rounding. Standard decimal arithmetic avoids binary floating display artifacts. Transcendental functions/constants/fractional powers are approximate; `StrictMath` makes their results reproducible for strict history validation across runtimes. Square root uses decimal arithmetic.
- Percent is uniformly postfix `/100`: `200+10% = 200.1`; `200*(1+10%) = 220`. Precedence is parentheses, postfix factorial/percent, right-associative powers, sign, multiplication/division, addition/subtraction. Thus `-2^2 = -4` and `2^3^2 = 512`. Multiplication is explicit.
- Bounds: 512-character expressions, 256 tokens, 32 parser levels, integer or fractional power exponents within ±1000, whole-number factorial 0–1000, result exponent within ±10000, finite double-domain scientific inputs, and trig angles at most 10^9 radians. Undefined operations produce bounded user-facing errors. Number formatting groups ordinary large numbers and uses engineering notation outside the ordinary display range.
- Discoverable Help explains these rules and memory/copy/history. Portrait uses a scrollable keypad; unfolded width places scientific controls beside standard controls. Controls have neutral descriptive accessibility labels and at least 52dp key height.

History, memory, angle/mode and history preference use `nova_calculator_v1` through real captured `SpaceContext` encryption. History can be cleared or disabled; disabling clears persisted history. Unfinished expressions are transient, cleared on background/disposal, and are never restored from intents/saved instance state. Writes and callbacks require current Space authority; malformed local data is retained and reported rather than silently reset.

Copy is explicitly requested, publishes only the result and marks the Android clip `EXTRA_IS_SENSITIVE`. There is no pre-existing app-owned clipboard cleanup/ownership helper; media-details/browser Copy already use ClipboardManager directly. Calculator follows that policy. Help explicitly states that Panic does not erase a value already copied to the system clipboard. This is an intentional external-copy limitation, not a claim of clipboard erasure.

## Concealed entry and authentication

The optional sequence is **five separate long-presses of equals within 15 seconds**, with any ordinary calculator action resetting the sequence. It is disabled by default. Its accessible long-click action is only “Hold equals”; Primary calculator UI/help does not advertise Hidden. These are control events, not expression characters: the sequence is never evaluated, copied, put into memory or stored as history. No PIN or configurable PIN-like code is stored by the calculator.

Only independently authenticated Hidden Settings composes the enable/disable control. The write is bound to the exact Hidden authentication identity, foreground/settings predicate and current callback generation. Stale callbacks after pause, retirement or reauthentication fail closed. The one-byte enabled value is a bounded AES-GCM device-bound record under `hidden-v1-<namespace>/calculator-entry-v1`, with purpose/namespace AAD and the existing device key. The existing credential record is not rewritten. This auxiliary setting is excluded from both Spaces' portable Backup categories and defaults off after replacement-device restore.

MainActivity consumes the one-use completion even when rejecting dispatch. It rechecks the captured Primary SpaceContext, foreground/resumed state, Home presentation, current Primary authority, concealment, enabled setting and callback generation. It then calls the tiny `HiddenVaultState.selectLocked()` operation, which locks any retained Hidden authentication before selecting the existing Hidden PIN presentation. It never calls an unlock/complete operation. Existing version-number entry is unchanged. No intent/restoration route, biometric shortcut or network path is added. Existing Panic/key retirement remains authoritative.

### Added shared PIN throttle

The baseline had the Hidden browser broker's local one-second interval, but no shared persistent throttle at the credential store. C **adds** a shared throttle and preserves the existing broker interval, PIN envelope, Android Keystore alias/key, mandatory PIN flow, attempt epochs, stale-result rejection, and Primary credential format.

All actual Hidden PIN comparisons pass through `HiddenCredentialStore.unlock`, including direct entry, version entry, calculator entry, `pinMatches` used by Primary create/change/recovery, and the Hidden browser sensitive-action comparison. Attempts are reserved durably **before** derivation so interrupted work cannot discard an attempt. First four unsuccessful comparisons have no cooldown; the fifth imposes 30 seconds. Consecutive failures after expiry impose 60/120/240/300 seconds, capped at 300 seconds. Counts are retained until a successful PIN. Success resets only auxiliary attempt metadata. A failed metadata write never returns a recovered key.

The separate `attempts-v1` record uses established AES-GCM, purpose/namespace AAD, existing device key, AtomicFile and read-back verification. Same-boot monotonic time governs expiry; after boot/monotonic rollback the remaining bounded wall-clock interval is rebased and persisted once. Corrupt attempt metadata is replaced with a bounded 300-second recovery cooldown, not an unlimited lockout and never replacement credentials. Missing/unavailable device keys fail closed. Repeated hostile corruption or repeated clock/boot manipulation is not promised to grant immediate access; ordinary recovery remains bounded after stable storage/time resumes.

`pinMatches` returns false only for an actual mismatch (or an impossible PIN shape/no configured credential), and propagates cooldown/unavailable errors instead of assuming mismatch. Thus Primary credential mutation stops before changing its existing envelope when comparison is unavailable. Successful PIN comparison bytes and caller PIN arrays are wiped as before.

### Exact browser delta for preservation review

Only `app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenVpnBrowserBroker.kt` changed under the pinned browser tree. In `VERIFY_PRIVATE_APP_PIN`, the broad `runCatching { unlock(pin) }.getOrNull()` becomes `unlockForComparison(pin)`. That helper converts only `HiddenPinEnvelope.InvalidPin` to null; cooldown/storage/corruption errors propagate to the existing fail-closed Binder “Hidden browser session unavailable” exception path. No transport, routing, proxy, Tor, session-key comparison or local one-second interval changed. The helper's mismatch/cooldown/unavailable distinction has a focused native test.

New file SHA-256: `38cf1b02530da82dc1aeb54ea562316d8d4108542eaa0eddc983819cbee13ead`.

The final release writer must add a narrowly reviewed exact hash allowance for this file to `scripts/verify_v1_preservation1711.py`; C deliberately does not edit that release gate or relax browser checks.

## Strict portable schema and valid upgrade seed

`PortablePreferenceSchema` now delegates `nova_calculator_v1` to the same `CalculatorPreferences.validate`/history decoder used by local loads. Only these optional keys are accepted; unknown fields and incorrect types fail. Empty preferences select ordinary defaults. The namespace supplies the settings schema version; the history stream starts with binary version 1.

| Key | Exact preference type | Validation |
| --- | --- | --- |
| `angle` | String | `DEG` or `RAD` |
| `scientific` | Boolean | No coercion |
| `history-enabled` | Boolean | False requires absent/empty history |
| `memory` | String | Canonical bounded decimal, ≤34 significant digits, ≤48 characters |
| `history` | String | Canonical Base64 of bounded v1 stream, ≤50 entries and ≤110000 encoded characters |

Each history entry is modified-UTF expression, modified-UTF canonical result and byte 0/1 for DEG/RAD. Expression length and evaluator bounds are enforced. The actual expression is re-evaluated in its saved angle mode and must produce exactly the saved canonical result. Results cannot smuggle arbitrary text, unsupported values or forged calculations. The encoded size cap includes the true maximum modified-UTF width, including valid Unicode whitespace. Restore validates before staged settings promotion; failure leaves existing destination ciphertext unchanged. Arcade's actual schema is untouched.

Canonical valid seed, generated and validated by the real encoder:

```kotlin
mapOf<String, Any>(
    "angle" to "DEG",
    "scientific" to true,
    "history-enabled" to true,
    "memory" to "0.3",
    "history" to "AAAAAQAAAAEABzAuMSswLjIAAzAuMwA="
)
```

The single history entry is expression `0.1+0.2`, result `0.3`, radians `false`. Equivalent encoder:

```java
CalculatorPreferences.encode(List.of(
    new CalculatorPreferences.Entry("0.1+0.2", "0.3", false)
));
```

Do not seed concealed-entry configuration or cooldown records through Backup. Neither is portable calculator data.

## Verification and limits

Executed in this checkpoint:

- Test-first missing engine/codec/throttle APIs recorded in `/tmp/nova62-calculator-red.log`.
- `PATH=/tmp/nova59-tools/jdk-17.0.21+9/bin:$PATH bash scripts/check_calculator_host.sh`: **70 assertions and concealed-entry wiring checks passed**, `/tmp/nova62-calculator-host-final.log`.
- Pinned JDK/cache `bash scripts/check_home2_host.sh`: **passed**, `/tmp/nova62-calculator-home-host.log`. It compiles the actual CalculatorData and SpaceContext/VaultCipher alongside existing ArcadeData. Calculator memory/history/settings round-trip, history disable/clear, real Backup validation and invalid-import byte-for-byte preservation execute alongside unchanged Home/Arcade checks.
- Self-review also reproduced and fixed small-decimal formatting dropping significant digits after leading zeros (`/tmp/nova62-calculator-format-red.log`).
- Self-review reproduced and fixed UTF history sizing (`/tmp/nova62-calculator-unicode-red.log`) and cross-runtime scientific reproducibility (`/tmp/nova62-calculator-portable-science-red.log`). The latter has a concrete case: `sin(4.4)` in RAD differs by one ULP between Math and StrictMath on this host.
- The initial application/instrumentation compile succeeded. An intermediate compilation saw the new `selectLocked` test after its main-source compilation snapshot and failed unresolved-reference; the subsequent frozen-source build passed. A host assertion was also updated after the deliberate angle-change result invalidation; the final host run passes.
- Final focused Gradle results and ABI evidence follow. Existing unrelated repository deprecation/nullability warnings remain.

Native classes added for the final consolidated API37 run (compiled here; **not executed here**):

1. `uk.co.traynor.privategallery.core.calculator.CalculatorPersistenceTest` — both-Space encrypted isolation, reopen/Backup round-trip, memory/settings/history disable/clear, retired write preservation.
2. `uk.co.traynor.privategallery.core.hidden.HiddenCalculatorSecurityTest` — recreated-store cooldown, `pinMatches` and broker helper paths, unchanged Primary/Hidden envelopes, namespace separation, expired/corrupt/clock-rebased metadata recovery, opt-in authority/stale identity, fresh-PIN selection and stale authentication completion.
3. `uk.co.traynor.privategallery.home2.CalculatorUiTest` — actual drawer launch, arithmetic/memory/copy/history, cover/unfolded/landscape dimensions and enlarged text, neutral long-press semantics/nonlogging, paused and inactive-route stale callbacks.

The final selection should retain existing `HiddenCredentialStoreTest`, `HiddenFoundationDialogTest`, Hidden navigation/entry/Panic/SpaceContext preservation tests together with A/B cases. JVM/host tests do not substitute for Android Keystore/Compose runtime tests. No production signing, original-key in-place upgrade, physical Galaxy Fold acceptance or new APK release was executed by C. Physical acceptance remains **unperformed**. Independent checkpoint review/push and final consolidated release work remain with the root/release writer.

### Final frozen-source result

`python /tmp/nova62-backup-build.py :app:testDebugUnitTest --tests '*calculator*' --tests '*HiddenPinThrottleTest' --tests '*PortablePreferenceSchemaTest' :app:compileDebugAndroidTestJavaWithJavac` — **BUILD SUCCESSFUL in 1m33s**, `/tmp/nova62-calculator-frozen-build.log`. The three selected XML files contain **12 tests, 0 failures, 0 errors**: CalculatorEngineTest 5, HiddenPinThrottleTest 3, PortablePreferenceSchemaTest 4. Application and instrumentation compilation succeeded. No Android runtime test is claimed.

`javap` against `app/build/intermediates/compile_app_classes_jar/debug/bundleDebugClassesToCompileJar/classes.jar` confirms the pre-existing `HiddenCredentialStore(Context,String)` constructor, `(Context,String,int,DefaultConstructorMarker)` default bridge, `unlock(char[])`, `pinMatches(char[])`, `create(char[])` and `createDifferentFromPrimary(char[],Function1)` remain present with their original JVM signatures. `unlockForComparison(char[])` is the sole added public method. Evidence: `/tmp/nova62-calculator-credential-abi.txt`.

Final source/staged whitespace checks passed. This is a tested development checkpoint awaiting independent review, root push and the consolidated Android/upgrade/release gates; it is not a safe-production-release claim.

## Task 3 scoped fix round 1 of 5 — I1/I2

Independent review of `780b71ae` is `/tmp/nova62-calculator-review.md`: no Critical findings; Important I1 fractional-power underflow and I2 sign-control grouping required repair. The review accepted the scoped Settings authority/security design; those paths are unchanged in this repair.

I1: a positive finite fractional-power base producing floating zero is now rejected with `Scientific range exceeded`; it is never certified as exact zero or recorded as history. The existing nonzero BigDecimal-to-double zero rejection remains intact. Genuine zero-base positive powers, ordinary fractional powers and decimal integer tiny results remain valid. A related DEG-to-RAD underflow was confirmed and explicitly included by the root in the same correctness scope: nonzero trig input that converts to zero radians also raises a range error. Exact-zero trig and representable tiny RAD values remain valid; no new decimal transcendental algorithm is introduced. This is an honest smaller scientific range, not fabricated exact-zero output.

I2: sign reversal unwraps `-(...)` only when the initial opening parenthesis matches the final character. A leading negative term followed by other terms/functions is instead negated as a whole. Actual CalculatorData tests cover `-(1)+sqrt(4)`, `-(1)+(2)`, genuine nested outer negation and mixed nested terms; two toggles restore the original evaluation.

Regression evidence:

- `/tmp/nova62-calculator-r1-underflow-red.log`: test-first arithmetic reproduction failed because the old evaluator accepted positive-base fractional underflow as zero.
- `/tmp/nova62-calculator-r1-import-red.log`: real SpaceContext restore accepted the old false-zero history, reproducing the import-validation consequence before the repair.
- `/tmp/nova62-calculator-r1-sign-red.log`: actual CalculatorData sign operation turned a valid mixed expression into malformed syntax before the repair.
- `/tmp/nova62-calculator-r1-angle-red.log`: the analogous nonzero DEG conversion was reproduced before adding its guard.
- `scripts/check_calculator_host.sh`: **89 checks plus concealed-entry wiring passed**, `/tmp/nova62-calculator-r1-host-green.log`.
- The real CalculatorData/SpaceContext/VaultCipher host test now verifies whole-expression sign/double-toggle behavior and rejects four canonical false-zero history payloads before Backup overwrites, comparing all destination ciphertext byte for byte. It passed in `/tmp/nova62-calculator-r1-model-green.log`; focused compilation is recorded below.

Canonical legitimate Backup seed and the reviewed browser delta/hash/credential ABI are unchanged. Android runtime, original-key upgrade and physical Fold acceptance remain unperformed by this repair.


Round1 final frozen-source command: `python /tmp/nova62-backup-build.py :app:testDebugUnitTest --tests '*calculator*' --tests '*HiddenPinThrottleTest' --tests '*PortablePreferenceSchemaTest' :app:compileDebugAndroidTestJavaWithJavac` — **BUILD SUCCESSFUL in 2m2s**, **14 tests / 3 classes / 0 failures / 0 errors** (7 Calculator engine/codec, 3 throttle, 4 portable schema). Application and instrumentation Kotlin/Java compilation succeeded; `/tmp/nova62-calculator-r1-frozen-build.log`. Existing unrelated warnings remain. The new native model regressions are compiled, not executed. Source/staged whitespace checks pass. I1/I2 are ready for scoped independent re-review; no root push or production release is claimed.
