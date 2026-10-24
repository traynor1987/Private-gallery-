# Run #18 Android 17 player-preparation blocker — NO-GO

## Verified outcomes

Run 40140036506 (focused18), source `18fa461daed3c300f982e206208a75ab3ecb0132`, production main tree `e3c49337cdf978025266a913faea62e3cf983d1c`.
Artifact 12578739317 SHA256 `c16f523289ef04e1d90fcbbf57872f687180f32c266f7d056f5d67c3e2258677`; digest, ZIP CRC, raw XML, source/SDK metadata and counts independently verified.

| Gate | Passed | Failed | Skipped |
|---|---:|---:|---:|
| JVM |349|0|0|
| Android 16 five affected methods |5|0|0|
| Android 16 complete focused suite |69|0|0|
| Android 17 five affected methods |5|0|0|
| Android 17 complete focused suite |68|1|0|

The five original ProfessionalUi failures are now verified repaired on both supported SDKs. Unique control identifiers and visible navigation preserved every original fresh-PIN, cancellation, stale-biometric-response, hide-content and screenshot assertion. The only production changes since recovery are two testTag modifiers and their import; removing them reproduces the original MainActivity exactly. No authentication/privacy/encryption handler changed.

## Exact outstanding failure

`PrivacyShield175Test.panicDuringRealPlayerPlaybackStopsImmediatelyAndRevokesSession`, line102: `Real player must prepare before Panic`.
The existing 15-second READY precondition failed (test duration 15.352 seconds). The code below the assertion, including Panic invocation and stop/session-revocation assertions, was NOT reached. This does not prove Panic itself failed; its required active-playback verification remains unsatisfied in this run.

The raw test log and global native log show:

- 23:10:47.442 Activity was already RESUMED (lifecycle callback47.366), main window not yet focused; focus acquired47.474.
- Player/codec setup begins about47.58; an app-owned Dialog window appears47.739 and its back callback is removed47.919.
- Main native focus false at48.000, then true48.163, while Activity remains RESUMED until failure cleanup at23:11:02.6.
- Codec release begins around48.129, before the 15-second readiness assertion expires.
- Goldfish decoder/configuration warnings also occur in passing tests. They do not establish corruption or the cause of this failure. No concrete ExoPlayer error code is present in the retained failure evidence.

Source contains a protected-video loading Dialog and fail-closed focus/preview concealment as a possible handoff path. The logged Dialog has not been definitively identified as that loading Dialog. However the log lacks the actual cover/preview epoch, owned-root classification, final player state/error and disposal reason. A production loading-dialog/focus race is possible; a fixture-only or flaky classification is NOT proven. Independent review explicitly rejects extending the deadline, skipping the case, automatically reopening concealed media or weakening focus/privacy protection to obtain green CI.

## Decision and exact continuation

NO-GO. Do not advance `.release1751-candidate`, run full regression, sign or deliver code 39 yet. The complete Android 16/17 release gates are prepared and independently reviewed at `dc0e63efe367a201c68d305dda1ef0111e948185`, but remain unexecuted. Their389-case expectation on each SDK is source-audited:391 test methods minus the two historical maxSdk32 cases. WebView capability skips remain failures of the mandatory zero-skip gate.

Next safe work is a bounded disposable Android 17 reproduction with non-sensitive diagnostics captured before cleanup: native focus and lifecycle; owned loading-window attach/detach; cover visibility and preview epoch; player identity, playback state and error code; authenticated-session state; existing bounded VaultPlaybackDiagnostics events and actual disposal reason. Preserve the 15-second READY precondition and every Panic assertion. Compare with the passing Android 16 evidence and previous run17 Android 17 case. Prove the causal chain locally or in an independently reviewed diagnostic harness before proposing a correction. Another unchanged rerun or guessed production change would be speculative.

No additional workflow was launched after run18. No full release, code 38→39 debug or original-key production upgrade/restart proof, production signing or signed acceptance APK exists for this candidate. Original recorded certificate remains `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`; it has not been freshly exercised by this stopped release. Package/version remain `uk.co.traynor.privategallery`,1.7.5.1/code 39/minSdk36. No owner media/data/PIN/key operation occurred, no previous branch was changed, and no replacement signing key was created. Android12 remains unresolved and unsupported. All Galaxy Z Fold physical acceptance items remain pending.

Exact raw XML, native test logs and file hashes are retained under `run18/` and `RUN_18_EVIDENCE.json`; the original complete artifact remains identified above. The checkpoint carrying this report must be non-force pushed with `[skip ci]` and independently SHA/tree verified before handoff.

Earlier handoff and candidate reports remain directly available under `history/`, as well as in Git history.
