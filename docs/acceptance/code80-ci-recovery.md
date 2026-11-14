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

[Run 373](https://github.com/traynor1987/Private-gallery-/actions/runs/43517512484) passed on `77edc336b600f741de39d879476cf6261b677a2b`. The archive contains 673 passing JVM cases, 199 passing focused instrumentation executions and 263 passing cases in the broad API 37 instrumentation report. Counts for focused and broad runs can overlap. All had zero failures, errors or skips. CI confirmed the isolated test package, Home/encrypted adapter and protected browser/core checks, plus eight real backup process-stop/restart boundaries. The dedicated visual-only and Sanctuary source-preservation steps were skipped by branch condition. The evidence archive (artifact 14084307024) has SHA-256 `988b769ebbc4a1393e1f2e171580e0348a8124d23193446c1f1f5e0a358203b2`. The test build used Android Debug signing. APK publication steps were skipped.

The recovered local focused class bundle passed 47/47 on rerun. The updated Store trust source was then compiled separately and passed its three focused JVM tests. The Store change is `26da59903aad86d4cf2da45e2883af0aba08cdd2` with [verification run 374](https://github.com/traynor1987/Private-gallery-/actions/runs/43537701581).

The Code 79 owner archive from [production run 17](https://github.com/traynor1987/Private-gallery-/actions/runs/43226350763) passed the read-only baseline verifier. The legacy `scripts.tests.test_upgrade1711_controls` suite still contains Code 64 → 65 fixture expectations against later production controls. Its failures require separate reconciliation before relying on it for a Code 80 gate.

## Acceptance still required

- Samsung Galaxy Z Fold: folded/unfolded layouts, live Main/Friends WhatsApp, DND and lock-screen/background notification taps, permission revocation/regrant, launcher PIN + phrase + five-second hold.
- Sudoku: physical input, fold/recreation draft restoration, all configured modes, credential recovery, emergency access and normal expiry. Pure difficulty/solution and combined-step rules are covered by JVM tests.
- Nova Store: approved synthetic APK fixtures for install/update, wrong checksum/package/signer and installed-data preservation. The approved first-party catalogue is currently `{}`; no approved APK download can be accepted end to end.
- Original-key Code 79 -> Code 80 in-place encrypted-data upgrade is NOT verified by an isolated debug build. Existing production controls still target Code 79. Keep this as a future separately authorized release gate, with disposable representative encrypted data before the owner's device.
- Knowledge Meter remains a held-out validation score; insufficient evidence remains unmeasured. Independent real owner-labelled validation is still needed to assess performance beyond the synthetic tests.

## Resume

1. Confirm the remote branch tip and inspect run 374 step results and XML before treating the Store change as Android-verified.
2. Prepare separate Code 80 upgrade controls against the pinned Code 79 archive. Existing signing instructions and version pins still describe Code 79.
3. Execute the Fold checklist in `docs/acceptance/code80.md` on an isolated test build. Record the actual APK and build commit.
4. Keep Code 80 in progress pending the representative in-place upgrade and physical acceptance.
