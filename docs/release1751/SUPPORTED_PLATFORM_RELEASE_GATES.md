# Android16/17 complete release gates

The owner explicitly requires full regression on both supported SDKs, with Android17 Fold physical acceptance afterward. This extends the previous API36 full/API37 focused matrix; it does not remove a release assertion.

The existing full workflow remains marker-triggered and uses a single compilation/JVM/lint source checkpoint. Its existing115-case broad UI,74-case browser/security preflight,395-case unfiltered suite and synthetic38→39 debug upgrade/restart run serially on36 and37. Before changing SDK, raw results, source/fingerprint/display metadata, diagnostics and upgrade proofs are archived outside the next run's output directories. Every invocation gets fresh XML. Archive errors cannot turn a failed primary command into success or hide its exit status.

A fail-closed validator requires both exact-source/SDK identities, zero failed/skipped/missing cases at the recorded counts, actual cookie/camera process boundaries and seed/verify/after-restart debug proofs. Seven host cases reject stale source, wrong SDK, missing full cases, failure/skip and absent restart success and a mismatched upgrade APK. Both debug-upgrade candidate hashes must equal the retained tested debug APK, independently hashed by the final platform validator. Four distinct AVD names prevent cross-phase userdata reuse. The prior historical Android12 reports remain untouched and are not counted as passing.

Signing only follows both full/debug gates. Existing original-certificate/package/minimum/APK-hash checks remain. Fresh disposable original-key production-package38→39 seed/verify/restart phases run on both36 and37, with separate evidence. Final upload requires both SDK identities, exact candidate source, seed/verify/restart success, baseline and candidate original certificate, and candidate hash equality with the single signed release APK. No production key is generated or exposed; restored key cleanup remains always-on.

All operations are restricted to disposable CI emulators. Synthetic debug package cleanup occurs only before fixture seeding under the existing guarded script; production upgrade requires absent test installation before seeding and uses in-place install-r afterward. There is no owner-device operation or uninstall/data-clear requirement. Authentication, Panic/Privacy Shield, screenshot and vault isolation logic is unchanged by these gate additions.

Prepared changes must be independently reviewed and the focused five-first36/37 run must pass before the marker is advanced. Full execution/signing and physical acceptance are still pending at preparation time. No extra workflow or automatic speculative rerun is introduced.

Source preservation check: against recovery d06e27a5bcab5364687a2bd269b69ddd3fccf3de, the sole changed production source file is MainActivity.kt. Removing the two testTag modifiers and their import reproduces the original file exactly. Every other production source remains identical. Current focused source18fa461daed3c300f982e206208a75ab3ecb0132 main treee3c49337cdf978025266a913faea62e3cf983d1c; no vault/encryption/media/authentication handler changed.

Current video checkpoint: focused20 passed13 affected +69 retained cases on each supported SDK; full release remains pending. See video-repair/RUN_20_EVIDENCE.json.
