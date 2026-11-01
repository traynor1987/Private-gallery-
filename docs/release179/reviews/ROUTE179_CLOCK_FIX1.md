# Route179 clock fix round 1 — narrow independent re-review

**Specification verdict: PASS for I1/I2 and breakage introduced by this fix diff. Quality verdict: PASS for that same scope. I1: ADDRESSED. I2: ADDRESSED. New findings: Critical 0, Important 0, Minor 0.** This closes the two host implementation findings; it is not Android, lifecycle-control, actual-route, upgrade, signing or release approval.

I read the original I1/I2 findings verbatim in `ROUTE179_CLOCK_IMPLEMENTATION.md`, the calibration brief, original diagnostic, accepted design and design review, the final Fix round1/5 report section, the complete frozen fix diff and both current changed files. The existing shell runner was supporting context for its exact original command/status contract. The review is limited to `scripts/evidence179_clock.py` and `scripts/tests/test_evidence179_clock.py`; any separate Kotlin lifecycle-control work is outside this review.

The supplied diff is 26382 bytes, SHA256 `348c34e47c7d3f72734becedd09d519fb4d7137fa11538660d5496342064d5ce`. I independently checked that size/hash and both current source hashes against the final fix report:

| File | SHA256 |
|---|---|
| `scripts/evidence179_clock.py` | `a4025c83441c07f6e84b9761c3b1c42ee8f79cdfab2e6f22b29121330997e8c3` |
| `scripts/tests/test_evidence179_clock.py` | `8a194955f40be181756c6e507f8b9905ca43b0976801c635b84511ae61ee2d0d` |

## I1 — ADDRESSED

At helper lines 208–216, every successfully started child now reaches `original=child.wait()`, independently of whether `poll()` reports that it has already exited. An early original exit followed by a failed ownership snapshot therefore retains the child's true result instead of the initialized sentinel 2. A still-running owned Popen child retains the prior terminate/bounded-wait/kill cleanup before the final reap. A normal earlier `wait()` is safe to read again. Sentinel 2 remains for the not-started path.

The ownership exception remains in `errors`, `clock_exit_status` remains 1 and failed child ownership is not recorded as a successful snapshot. Lifecycle JSON and `original-exit-status.txt` receive the actual original result. The unchanged return expression preserves a nonzero original result; original 0 plus clock failure returns 1. The shell runner continues reading original and clock statuses separately, so this repair does not mask the clock failure or alter collection/failure precedence.

The focused regression at test lines 259–269 exercises both original 0 and 23. Its simulated snapshot waits for actual child termination with `waitid(WEXITED | WNOWAIT)` before throwing; the real Popen owner then reaps the real subprocess. Assertions cover lifecycle/status-file values, wrapper status 1 or 23, the retained ownership error, absent successful child identity and absence of samples. These controls reproduce the precise exited-child transition identified by I1. Only process snapshots are simulated; they do not establish live Linux ownership in this container.

## I2 — ADDRESSED

Helper lines 239–249 preserve the exact helper prefix/output binding, take the original argv strictly after its sole `--`, and require:

```
recorded_child_argv == recorded_original_argv + clock_arguments(identity)
```

The shared construction used by acquisition and replay adds exactly the run nonce, explicit Android user and selected serial properties, in that order. All original/child arguments must be strings. An unrelated helper command after `--` can no longer coexist with the recorded Gradle child. Missing, extra, reordered or altered injected arguments cannot satisfy exact equality.

Replay also rejects duplicate Gradle property keys, splitting at the first `=` rather than counting an expected complete string. This catches an additional conflicting value even when helper and child records are changed consistently. The original command must contain exactly the runner's eleven arguments: literal timeout and its TERM/kill-after/duration options; `./gradlew :app:connectedDebugAndroidTest`; exact case/method selection; fixed VPN configuration; the runner's strict generated HTTPS onion URL form; exact source SHA; and `--stacktrace`. Supporting inspection of `run_evidence179.sh:32–35` and its existing URL guard confirms this accepts the current runner contract. The tighter literal timeout requirement matches that actual invocation.

The synthetic fixture now describes a constructible helper/original/child relationship. The new controls cover the original arbitrary `sh -c 'exit 23'` helper substitution, an appended conflicting SourceSha in the child, duplicates of all five identity-property keys with both records consistent, and mutually consistent invalid selection/source/task/VPN/URL/flag changes. A positive control loads the valid inventory from a relocated raw directory while retaining the original recorded output argument. The earlier relocation positive remains as well.

## Regression assessment and verification limits

No new breakage was identified in this diff. The changes are confined to shared injected-argument construction, true child reaping, strict replay command validation and the corresponding host fixture/controls. There is no new success path for failed ownership; no weakening of raw inventory, device/source/nonce/process/boot checks; and no change to interval arithmetic, the independent probe upper plus 5 ms, the 100 ms maximum bracket, or required exact action counter. The external validator CLI, original endpoint/EOF observations and read cadence, six required phase samples, frozen 153-case manifest and existing route/security/release contracts are unchanged by this diff.

The final fix report records a targeted RED reproduction followed by 5 passing focused tests, then 55 passing covering tests with no skips and syntax parsing of both changed Python files. I inspected the controls and the report; I did not rerun those tests because this review found no concrete reason to repeat the authorized covering run. No Kotlin build, Android execution, CI or original-evidence replay was performed by this reviewer.

The original run remains 152 PASS / 1 UI FAIL with incomplete strict route proof. Fresh API37 compilation and actual runtime evidence must still establish live Linux/process ownership, DUMP/user/package delivery, independent boot identity, six one-shot same-counter samples, valid brackets/intersections and genuine close margins with all 153 XML cases passing without skips. Negative Kotlin duplicate/cancellation/timeout/cleanup transitions still require their own actual execution and evidence; happy-path samples and these host tests do not discharge that gate. Packet/no-fallback, controlled drain, upgrade, signing and release gates remain pending.

Only this review file was written. No source, original artifact, Git state, CI, secret, signing input or other worker's task was changed. Reviewer is frozen pending root direction.
