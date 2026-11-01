# Route179 clock/classname/firewall implementation review

**Specification verdict: FAIL — two narrow mandatory-contract defects require correction. Quality verdict: CHANGES REQUIRED. Critical: none. Important: two. Minor: none newly raised.** The exact-action clock mechanism, conservative interval calculation, classname correction and strict firewall representation repair otherwise match the accepted design. This is not Android/runtime, route-proof, upgrade or APK approval.

Review scope is the three actual blockers from source `29a2a7b0c3951cd8d4cdf77fe58be06ebfb235b9` and breakage introduced by their repair, against baseline `18abaec525677cc82fb5f5fdb198b59a4f7eaa3b`. I read the brief, original diagnostic, accepted calibration design, `ROUTE179_CLOCK_DESIGN.md`, implementation report, changed implementations and concrete host controls. All ten file hashes match the implementation report. The supplied frozen diff is 122354 bytes, SHA256 `3408a80781a81fc192e3441ea3b8549bf7e7a61e6658f3b1ca65f53dad71a4bb`.

## Important findings

### I1 — An exited original child loses its true exit status on ownership-capture failure

`scripts/evidence179_clock.py:172` starts the original child and immediately captures its `/proc` identity. That capture can throw if the child has already exited (`process_identity` explicitly rejects a zombie), or if an ownership read fails. The exception bypasses `original=child.wait()` at line 195. In `finally`, lines 204–208 assign `original` only when `child.poll() is None`. When the child is already terminated, `poll()` obtains its actual return code but the implementation discards it and writes the initialized `original=2` into both lifecycle JSON and `original-exit-status.txt`.

I executed a new, isolated control with a real subprocess that exits 23, simulated identity reads, and a deterministic ownership-snapshot exception after that subprocess exits. The result was:

```text
real_child_exit=23
reported_original=2
wrapper_status=2
clock_status=1
errors=["Owned process already exited"]
```

This remains fail-closed for proof, but it breaks the explicit requirement to preserve the true original status separately from clock/ownership failure. The shell runner trusts that status file, so its host-phase original status also becomes incorrect. The existing subprocess tests patch every process snapshot to success and wait normally; the runner tests replace the helper with a shell stub, so neither covers this transition.

**Minimal correction:** whenever a child was actually started, reap/read its true result even if it has already exited; keep ownership failure in `clock_exit_status`/errors. Retain the sentinel only when no child started. Add a focused control for early original exit plus identity-capture failure, including a nonzero original result and an already-successful original result with clock failure. Preserve ownership checks and do not convert an ownership failure to success.

### I2 — Replay does not bind the helper's original command to the recorded child

`scripts/evidence179_clock.py:231–236` validates the helper argv only **before** its `--`, then independently checks the child command's prefix and the presence of selected full property strings. It never compares the command after `--` with the child command that acquisition actually constructs at lines 168–172. Nor does counting one expected full property string reject a second property with the same key and a conflicting value.

Two new isolated mutation controls reached successful `load_case` completion:

* Replace the helper's entire original command after `--` with `['sh', '-c', 'exit 23']`, while retaining the recorded timeout/Gradle child and zero original status. Replay accepts both required phase samples.
* Append a second `-Pandroid.testInstrumentationRunnerArguments.evidence179SourceSha=` property with a different SHA to the child argv. Replay still accepts it.

The first contradiction is already present in the synthetic replay fixture: its helper records `timeout synthetic`, while its child records the expected Gradle invocation. The new controls used temporary synthetic inventories only; no actual evidence or route PASS was created. `verify_evidence179_routes.py` directly uses `load_case` and performs no later helper-to-child command consistency check.

This falls short of the accepted exact invocation/source/process ownership binding. Hashing the contradictory records during replay cannot repair that missing validation.

**Minimal correction:** derive the expected child argv from the recorded helper command after `--`, append exactly the three clock arguments acquisition adds, and require exact agreement with the recorded child. Also reject duplicate/conflicting identity-property keys and enforce the existing runner's required original selection/source command contract. Keep the frozen external validator CLI unchanged. Correct the synthetic fixture to describe a possible invocation, then add the two mutation controls above and a relocated-raw-directory positive control. No privilege, endpoint, timing or action change is needed.

## Mechanism and retained gates inspected

The Kotlin helper is Android-test-only and dynamically registered using the actual target context. Its registration supplies `android.permission.DUMP`, a dedicated HandlerThread and `RECEIVER_EXPORTED`; there is no manifest receiver or production endpoint. Host requests use explicit `adb -s`, `--user`, package scope and a random per-invocation action. Payloads bind source, invocation/request/route nonces, exact test/method, phase, arm token/sequence, target package, PID/UID/user, boot and runner. The target checks its own UID against the target application UID. The host independently reads selected serial/user/boot before and after and retains raw attempts. The replay ownership gap is specifically I2, not a claim that these other bindings are missing.

`prepare` installs ordered phase claims under a lock, awaits a request with a one-second coroutine timeout, and cannot replace an unfinished prior phase. `SAMPLE` claims once; duplicates, stale/wrong identity or cancellation poison the invocation. The test repeats active/not-completed/no-failure/no-publication checks after the rendezvous. `actionTimestamp` reads `elapsedRealtimeNanos()` once, publishes that integer and queues reply finishing; it returns the identical integer immediately to the original cancellation, session-lock, VPN-disconnect or Tor-kill call. There is no host ACK, serialization, file I/O, sleep or await between the read and action. The receiver has no action callback. No concrete second-action or successful-poisoned-receipt path was found in this source review.

Only the HandlerThread sets or finishes a PendingResult, guarded by its finished flag. It never waits for a coroutine on that same thread. Cancellation, the delayed deadline and close converge there. The route's nested finally reaches clock teardown even if earlier cleanup throws; `NonCancellable` protects drain/unregister/quit/join, and successful receipt construction requires all expected phases, no pending result, retired thread and no poison. API37 execution and adversarial transition checks remain required; this source review is not an executed Android race test.

The host bracket surrounds the actual synchronous ADB command with `monotonic_ns`; raw status/stdout/stderr are retained. Replay requires complete per-case inventories, consecutive command records and exact raw pairs, rejects extra/symlink files, requires six phase samples and unique challenges/run nonces, and compares the Android's exact encoded reply bytes. The validator CLI remains unchanged and removes stale `route-proof.json` before validation. Required clock sources and raw files enter the proof hashes. The existing deletion controls exercise `load_case`; the same-CLI controls demonstrate deletion failure and stale-PASS removal. I2 adds a missing contradictory-command mutation, rather than replacing those controls.

`check_interval` uses exact `Fraction` arithmetic for the original arm and close floats, strict integer device/sample/bracket fields, the same action counter, the 100 ms maximum bracket, nonempty arm/probe intersection and both upper-bound comparisons. In particular it independently requires `C >= Hr + 5,000,000 ns`, even when the intersection is narrower. It does not round the close upward, midpoint-calibrate, extrapolate another sample or accept optional legacy calibration. Local failed-after-action, live/no-publication, duration/order/token checks and genuine EOF/reset/broken-pipe rules remain.

The receipt classname now explicitly names `EvidenceOwnedRoute179Test`; validator equality remains exact and the coroutine-class regression is rejected. The firewall parser consumes the whole three-line INPUT observation and exactly one rule, accepting only protocol `all` or `0` with exact REJECT/negated interface/output/source/destination/reject suffix and numeric counters. Extra chains/rules, ACCEPT, TCP/UDP/6/17 and malformed/ambiguous rows remain rejected. Raw firewall collection is unchanged.

The frozen 153-case manifest and full case/no-skip validator are retained. The nonroute failure barrier, packaged Tor/private Unix ownership, owned VPN, fresh DNS/pcap/no-fallback gates, path-loss-only remote-close contract, controlled drain and release/upgrade boundaries remain. The endpoint/EOF reader, cadence and original first terminal timestamps are outside this diff. The only workflow change adds the new host test module to the existing command; it adds no workflow/job. Original run29a2 remains 152 PASS / 1 UI FAIL and incomplete strict proof; no retrospective calibration or PASS is authorized.

## Disposition of the three design Minors

| Design clarification | Source disposition | Remaining evidence |
|---|---|---|
| Independent boot acquisition and user/package targeting | Addressed: direct bounded target-process boot read, strict UUID; selected ADB boot reads and explicit user/package; no denied-read privilege fallback. | Actual API37 readability and DUMP-protected target-process delivery. |
| Single finisher and unconditional teardown | Addressed in source: HandlerThread owns PendingResult, bounded coroutine coordination, nested finally and NonCancellable retirement; serialization stays off the critical action path. | Actual cancellation/timeout/duplicate races, no leaks and successful thread retirement. I1 separately concerns the new host child status failure path. |
| Suspend qualification and conservative float conversion | Addressed: same-host-monotonic ordering claim, qualified suspend wording and exact rational close comparisons. | Fresh actual brackets/intersections/close margins, without any weakened bound. |

## Verification limits and next gate

I did not rerun the reported 97 passing host checks. I inspected their actual sources and ran only the three targeted new controls described in I1/I2, which exposed the gaps. No Kotlin build or Android invocation was run. The local `/proc` namespace restriction prevents genuine local process-ownership attestation; the status control therefore simulates identity reads while exercising the real subprocess result. It is not Linux CI or Android proof.

The current host controls exercise real subprocess acquisition using fake ADB, strict parsing/arithmetic, synthetic replay mutations and shell status/collection paths. They do **not** execute the Kotlin receiver's competing SAMPLE, cancellation-before/after-claim, timeout/completion, or teardown-after-route-cleanup-failure transitions. The accepted design's adversarial lifecycle controls remain an explicit verification obligation; six happy-path samples alone must not be reported as those controls.

Correct I1 and I2 and obtain a fresh narrow implementation review. Then root's existing API37 verification must establish compilation, live Linux ownership, DUMP/user/package/process and boot delivery, six one-shot same-counter samples, lifecycle failure handling/cleanup, brackets at most 100 ms, nonempty intersections and required genuine close margins, with all 153 XML cases passing without skips. All packet, security, upgrade and release gates remain mandatory before APK delivery. Wide latency, early close or inaccessible boot evidence must remain a failure/inconclusive result.

Primary platform references checked: [Context registration permissions/handler/package semantics](https://developer.android.com/reference/android/content/Context?hl=en), [PendingResult threading and finish contract](https://developer.android.google.cn/reference/android/content/BroadcastReceiver.PendingResult), and [AOSP ordered shell broadcast completion](https://android.googlesource.com/platform/frameworks/base.git/+/1da59c15951d03765c9ed6d55d6c51fc065e9db3/services/core/java/com/android/server/am/ActivityManagerShellCommand.java). These support the API mechanism, not a claim that the selected API37 image has passed it.

Only this review file was added. No implementation, original artifact, Git state, CI, secret or signing input was changed. Reviewer is frozen pending root direction.
