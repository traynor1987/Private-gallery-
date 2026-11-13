# Code 80 CI recovery checkpoint — 13 November 2026

Status: IN PROGRESS. No production signing, APK publication, or VPS deployment is authorized by this checkpoint.

Repository: `traynor1987/Private-gallery-`  
Branch: `feature/novaos-sanctuary-integration`  
Current implementation source: `9f3f292f34ce060c326b9f1d7e56f9977cfb1758`

## Diagnosed failures

| Run | Exact failed job/step | Root cause |
| --- | --- | --- |
| [365](https://github.com/traynor1987/Private-gallery-/actions/runs/43508042422) | `home2-integration` / Focused Android 17 integration and privacy tests | `NovaNavigationHaptics69Test.headersAndGestureStripUseTheSameEffect`: two Back nodes matched; the second selector still expected Back to Home. |
| [366](https://github.com/traynor1987/Private-gallery-/actions/runs/43508167710) | Same | Same selector failure. |
| [367](https://github.com/traynor1987/Private-gallery-/actions/runs/43508898966) | Same | Same selector failure; no separate Nova Brain failure. |
| [368](https://github.com/traynor1987/Private-gallery-/actions/runs/43509466156) | Same | `NovaLauncherPlatform70Test.sanctuaryHomeSettingsRequiresPinPhraseAndUninterruptedHold`: expected resolver component did not include the actual system redirect. Logcat records `com.google.android.permissioncontroller/com.android.permissioncontroller.role.ui.DefaultAppActivity` displayed after the hold. |
| [370](https://github.com/traynor1987/Private-gallery-/actions/runs/43512811337) | `home2-integration` / Build isolated test identity and run JVM checks | New test-only reference to `permissionControllerPackageName` was not in the public SDK. Removed in `a88fa0d`. |

## Pushed corrections

- `d6c46dd7365fe3c18e420c652afd8b4f2e41c669`: scope Back selectors to test-owned header fixtures; preserve exact callback and haptic-count assertions.
- `8b27d5675241bd3d0dd170d283e95ad449b91c70`: recognize the observed Android 17 Home-settings redirect and print resumed component on timeout. Superseded SDK getter fixed below.
- `a88fa0d8aec15eb5998e5e12bb129735b4aeccd0`: use shell discovery of the installed system package instead of a hidden SDK getter; still require the exact observed activity.
- `9f3f292f34ce060c326b9f1d7e56f9977cfb1758`: add actual immutable Safe Notification action coverage during Panic, locked/secure Guardian routing, and independent encrypted reflection deletion preserving adjacent sessions and Guardian ciphertext.

Run 369 was skipped because its commit did not request the runtime job. Run 370 included both initial corrections. Runs 371 and 372 are verification-only full runs, without signing or APK publishing markers.

## Verification

As of 22:04 UTC, [371](https://github.com/traynor1987/Private-gallery-/actions/runs/43513701844) and [372](https://github.com/traynor1987/Private-gallery-/actions/runs/43513773380) have passed Android compilation, debug APK/test APK assembly, JVM tests, Home/encrypted adapter checks and browser/APK preservation checks. Both are still running Android 17 instrumentation. Final runtime results are pending; update this section before delivery.

Local freshly compiled Kotlin/JUnit policy suite: **47/47 passed** (Guardian policies and classifiers, Nova Brain evaluation/Knowledge Meter, personal impact, journal, Store trust rules, Sudoku challenges).

Host checks passed: Arcade 126, Calculator 89 plus concealed-entry wiring, backup scheduling 12, private-app policy/catalogue/hidden-store/command/icon/screenshot checks, VPN DNS/fixture transport checks, and emulator preflight 4 tests.

Local Android Gradle compilation was attempted but unavailable: Gradle 9.5.0 distribution was not cached and the download failed with network-unreachable. The full `check_home2_host.sh` wrapper also cannot finish locally because its encrypted adapter fixture requires an absent JDK/Gradle dependency cache; its Java policy checks passed. Use GitHub Android 17 results for compilation and instrumentation.

Baseline run 368: 673 JVM tests passed; completed instrumented groups included Guardian capture store 19, listener compatibility 2, inbox 4, and Evidence Locker learning UI 2. Those counts describe the old source, not the final checkpoint.

All recovery changes so far are instrumentation tests. Production app code, encryption formats and aliases, Sanctuary policy, Store verification, signing controls and release versions are unchanged.

## Acceptance still required

- Samsung Galaxy Z Fold: folded/unfolded layouts, live Main/Friends WhatsApp, DND and lock-screen/background notification taps, permission revocation/regrant, launcher PIN + phrase + five-second hold.
- Sudoku: physical input, fold/recreation draft restoration, all configured modes, credential recovery, emergency access and normal expiry. Pure difficulty/solution and combined-step rules are covered by JVM tests.
- Nova Store: approved synthetic APK fixtures for install/update, wrong checksum/package/signer and installed-data preservation. The approved first-party catalogue is currently `{}`; no approved APK download can be accepted end to end.
- Original-key Code 79 -> Code 80 in-place encrypted-data upgrade is NOT verified by an isolated debug build. Existing production controls still target Code 79. Keep this as a future separately authorized release gate, with disposable representative encrypted data before the owner's device.
- Knowledge Meter remains a held-out validation score; insufficient evidence remains unmeasured. Independent real owner-labelled validation is still needed to assess performance beyond the synthetic tests.

## Resume

1. Fetch the named branch and confirm its current HEAD before editing.
2. Inspect final job results and logs for runs 371 (`43513701844`) and 372 (`43513773380`). Fix any reported failure before expanding scope.
3. Run the exact failed class first where an API 37 runner is available. For the full existing verification job, push changes with `[home2-runtime] [visual-full] [verification-only]` only. Do not use workflow_dispatch, signing, probe-publication or production-authorization markers.
4. Reconcile final counts and remaining physical checks in this checkpoint and `docs/acceptance/code80.md`.
5. Keep Code 80 IN PROGRESS. Do not rename Code 81 or trigger production signing, APK publication or VPS deployment.
