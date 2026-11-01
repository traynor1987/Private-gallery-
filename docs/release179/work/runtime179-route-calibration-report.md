# Actual route179 clock/classname/firewall implementation — review handback

**Implementation ready for fresh scoped security review; no Android/runtime/release approval.** Work follows the accepted `runtime179-route-calibration-design.md` and `reviews/ROUTE179_CLOCK_DESIGN.md` (spec PASS / quality PASS, three Minor clarifications). Root supplied baseline `18abaec525677cc82fb5f5fdb198b59a4f7eaa3b`; the original actual-route artifact source remains `29a2a7b0c3951cd8d4cdf77fe58be06ebfb235b9`. That original run remains152 PASS /1 UI FAIL with inconclusive strict route timing; its artifacts were not edited, replayed into a manufactured PASS or retrospectively calibrated.

## Changes and retained limits

1. Android receipts explicitly use `EvidenceOwnedRoute179Test::class.java.name`. Validator identity remains exact and rejects the coroutine receiver classname.
2. The firewall parser consumes the whole three-line INPUT observation and exactly one unambiguous REJECT rule. It accepts protocol only `all` or `0`, exact negated `!pg1762`, wildcard output, source `0.0.0.0/0`, destination `203.0.113.10` and the expected reject suffix. It rejects6/17/TCP/UDP, wrong/absent negation/interface/source/destination, ACCEPT, malformed counters/columns, extra rules/chains or trailing material. The raw iptables observation is unchanged and preserved.
3. `EvidenceRouteClock179.kt` is test-only and dynamically registered in the actual instrumentation target process. It requires DUMP of senders and scopes requests to the random invocation action, explicit package/user, source, phase, arm sequence/token and challenge. The existing action path waits for one clock request, rechecks active/no-failure/no-publication state, captures `SystemClock.elapsedRealtimeNanos()` once and immediately invokes the existing action. That exact counter is the asynchronous clock reply and action counter; no host acknowledgement, serialization, I/O or sleep occurs on that path. The receiver has no action callback.
4. `evidence179_clock.py` owns the original timeout/Gradle child and a bounded sampler thread. Its selected ADB transport reads serial/user/boot independently before and after; the target process directly reads the boot UUID without a shell/argument/privilege fallback. Every original request argv, host nanosecond bracket, stdout/stderr, status and timeout is archived. The shell runner preserves original Gradle, collection, clock and peer-close statuses separately. The helper never emits a route PASS.
5. The unchanged route-validator CLI requires the complete raw per-case inventory and independently replays all six exact phase samples. It checks byte-identical Android replies, one-shot challenges, process/UID/user/package/boot/source/nonce identity, raw command framing, no lost/extra/reordered attempts, recorded process/child ownership and required files. Cross-case device identity and host boot versus capture ownership must agree. Target PID need not remain alive after normal instrumentation completion: its binding is across accepted replies and the Android receipt.
6. Interval arithmetic uses exact rational values for original host floats. The old arm upper retains its5 ms margin; the independent request bracket is at most100 ms; the arm/probe intersection must be nonempty. Closure must satisfy both the intersection upper and the independent probe upper plus5 ms. There is no midpoint, rate estimate, stale-sample extrapolation or rounding C upward. The same-read sample has zero age relative to the action counter. Linux host monotonic may exclude host suspend; this is a same-monotonic-domain observation-order proof, not an inferred wall-time deadline.

The original endpoint/EOF reader, read cadence and first terminal timestamps were not edited. Live-at-action, actual failed-after-action, no normal completion/timeout/generic-TLS acceptance, packaged Tor/private Unix ownership, owned VPN, DNS/pcap/no-fallback, path-loss-only remote-close exemption and existing strict duration limits remain. No production source/auth/crypto/browser change, Android manifest component, version change, new workflow/job or153-case-manifest change was made. The existing fast-workflow host command only adds the new clock test module.

## Three review clarifications concretized

* Boot IDs are direct bounded reads of `/proc/sys/kernel/random/boot_id`, strictly parsed lowercase UUIDs in both target process and selected ADB transport. Denied reads fail without privilege fallback. Broadcast argv contains `--user` and `-p`; target UID/user/package and AndroidJUnitRunner class are checked.
* Only the dedicated HandlerThread sets/finishes `PendingResult`; timeout, completion and teardown converge on its one-shot finisher. It never waits for work queued to itself. The outer route `finally` nests clock cleanup even if earlier cleanup throws; clock teardown uses `NonCancellable`, unregisters and stops/joins its thread, then requires all expected phases and no poison. A failed close cannot produce a successful receipt. Actual Android race/cancellation behavior still requires runtime verification below.
* `Fraction(recorded_float)` retains the close observation exactly. No ceiling conversion can turn an insufficient C into a pass. Suspend wording is corrected in the design and above.

## Interface/archive/replay delta

The frozen validator CLI is unchanged. The new internal acquisition command is:

```
python3 scripts/evidence179_clock.py --case CASE --source-sha SHA \
  --output app/build/evidence179/clock/CASE -- ORIGINAL_TIMEOUT_GRADLE_COMMAND...
```

Its required directory inventory is `invocation.json`, `lifecycle.json`, `commands.jsonl`, `original-exit-status.txt`, `clock-exit-status.txt`, and contiguous per-command `000001.stdout` / `000001.stderr` pairs. The producer records all attempts, including failures; successful replay requires the complete matching inventory and exactly the expected phase samples. No successful slow sample is discarded/retried. STATE discovery can poll before readiness; SAMPLE is one-shot. Missing raw bytes fail before proof creation. Existing raw files, helper/Kotlin/validator sources and focused tests are included in the proof hash set. The existing archive already preserves files written beneath the results root; no archive helper edit was needed.

Raw directories can be relocated for the clock replay while the original recorded helper output argument remains fixed to `app/build/evidence179/clock/CASE`. This does not change existing strict packet/capture relocation constraints. The new raw/control interface is reported here for the mandatory fresh scoped review; it is not assumed approved by implementation completion.

## Executed checks and results

TDD began with eight intentional failing host tests for the missing clock/parser functions; arithmetic, exact frame parsing, strict boot UUIDs and all/0 firewall controls then passed. Later focused RED controls exposed boolean status/sequence acceptance, insufficient child binding and an original-output-versus-extraction-path mismatch; these were corrected and the controls pass. An early live-process acquisition test also exposed this container's `/proc` namespace mismatch, addressed in the test harness only as described under limitations.

Final covering command:

```
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest \
  scripts.tests.test_evidence179_clock \
  scripts.tests.test_evidence179_route_evidence \
  scripts.tests.test_evidence179_runner \
  scripts.tests.test_evidence179_fixture_endpoints \
  scripts.tests.test_evidence179_cases \
  scripts.tests.test_evidence179_process_ownership \
  scripts.tests.test_release179_runtime_controls -q
```

Result: exit0; `Ran 68 tests in 11.122s` / `OK`, no skipped tests. This includes21 clock tests. Controls cover exact interval boundaries, dominance of the independent upper even when intersection is narrower, disjoint/wide/reversed/foreign/missing data, duplicate challenge and second SAMPLE without readiness, original-float exactness, strict numerics, all required raw-file deletions, additional/symlink files, raw payload/argv mismatch and unchanged-CLI deletion/no-stale-PASS behavior. Real subprocess controls exercise clock request acquisition, timeouts, malformed/early/foreign replies and original-child exit-status preservation. Runner controls exercise collection/clock failure combinations without masking Gradle status or reaching peer-close/final success.

Required related fixture command:

```
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest \
  scripts.tests.test_ci_onion_probe \
  scripts.tests.test_hidden_tor_fixture1762 \
  scripts.tests.test_hidden_vpn_fixture1762 \
  scripts.tests.test_evidence179_fixture_http \
  scripts.tests.test_evidence179_fixture_tls -q
```

Result: exit0; `Ran 29 tests in 1.750s` / `OK`, no skips. The existing probe tests additionally print their synthetic controlled-fixture health checks. Total covering host checks:97 passing.

`bash -n scripts/run_evidence179.sh` succeeded. `ast.parse` accepted all five changed Python files (helper, validator and three test files). Read-only diffs against saved preimages confirmed the route-test edit scope and the sole workflow-line test-module addition. The frozen manifest still contains153 cases.

## Limits and required next evidence

No Kotlin/Android build, emulator invocation or actual route proof was run locally. The known-unavailable Gradle bootstrap was not retried. This environment returns a container PID from `os.getpid()` while `/proc/self/stat` exposes a different host namespace; the production helper rejects unattested PID namespaces. Host subprocess tests therefore explicitly simulate **only process snapshots**, using a disposable fake ADB executable while exercising real subprocess waits, raw files, timeouts and statuses. They do not attest Linux CI process ownership, Android lifecycle races, DUMP delivery or boot-file policy. Existing process-ownership host controls also pass, but do not replace the required live acquisition.

Fresh root-batched API37 evidence must prove compilation, target-process/DUMP/user/package delivery, independently readable matching boot IDs, six one-shot same-counter samples, no lifecycle poison/leak, host brackets<=100 ms, nonempty arm intersections and required genuine-close margins, all153 XML cases passing without skips, and unchanged route/packet/upgrade/release gates. Android duplicate/cancellation/timeout teardown behavior remains a runtime/review obligation; no source-only assertion is labeled a passing Android race test. A close arriving before the completed ADB reply plus margin, or wide ADB latency, remains an honest proof failure. No EOF rescheduling, weaker bound, alternate endpoint, application retry or privilege fallback is authorized.

Preimages of all seven existing edited files were saved before edits under `/tmp/runtime179-clock-impl-before`. This worker used no Git command, CI, signing material, secret or subagent. Root remains sole Git writer. Implementation is frozen for fresh scoped review.

## Review-file SHA256 inventory

| File | SHA256 |
|---|---|
| `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceOwnedRoute179Test.kt` | `36b3b0202f0f4bebd8161de245561cd9c0166ec37fc681eb9e97cf0d5267cf04` |
| `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceRouteClock179.kt` | `89ddfb58bf3505efbeca1458941c79842117a7461e320fb8e78b2e1c4d1439be` |
| `scripts/evidence179_clock.py` | `ec80f52412a03d37a6885411ee1fc317d6ea41bdb106594479185508377e9c32` |
| `scripts/run_evidence179.sh` | `1f6d8114c7e24ad5e9c4887db45660334b48fcf782d82d512d77743f55c56d8b` |
| `scripts/verify_evidence179_routes.py` | `bf1ae2206272af7cc1710edbc781e9f8bd24deff4fb67962af15f6d5a09560b4` |
| `scripts/tests/test_evidence179_clock.py` | `c8792d77531e2c95e8dfc42cb3521bbbf0ac019676fbb52d2e613d9f27c9fb7f` |
| `scripts/tests/test_evidence179_route_evidence.py` | `55018dba0098ac5e6e39eefcb495859e3a3f31a4ee244f82bc6cdcbe1414aa9c` |
| `scripts/tests/test_evidence179_runner.py` | `116024f32ea704448e48e9e77a37036407c269c5f37e708826e3072345e2015f` |
| `.github/workflows/fast-development.yml` | `8e3e1459575f4d1a794c441b35d10fa9de013369978f82cfd0cedd451544458a` |
| `docs/release179/work/runtime179-route-calibration-design.md` | `b826f7e47f9a097e16865118642896e6ab2def884eb0002f0e2f14a5c3dd5949` |

## Fix round1/5 — implementation review I1 and I2

Read `reviews/ROUTE179_CLOCK_IMPLEMENTATION.md` I1/I2 verbatim. This round changes only `scripts/evidence179_clock.py`, `scripts/tests/test_evidence179_clock.py` and this appended report. Preimages are `/tmp/runtime179-clock-fix1-before`. All other implementation/design/workflow/manifest files remain frozen.

**I1 corrected:** once Popen starts an original child, teardown always calls `child.wait()` and records its true result, including when a failed ownership snapshot follows an already-exited child. It retains the clock/ownership error and nonzero clock status. Sentinel2 remains for an invocation that never started. The regression uses a real subprocess with exit0 or23; a simulated ownership read waits for actual child termination using `waitid(... WEXITED | WNOWAIT)` before throwing, leaving reaping to the real Popen owner. Both original results are preserved; the wrapper returns1 for original0/clock1 and23 for original23/clock1. No failed ownership snapshot is promoted to success.

**I2 corrected:** replay derives the original command from the recorded helper argv after `--`, appends exactly the same three injected clock arguments used during acquisition, and requires exact equality with recorded child argv. It rejects duplicate/conflicting Gradle property **keys** and requires the existing11-argument timeout/Gradle runner contract, exact case selection/source, fixed VPN configuration, strict generated onion URL and `--stacktrace`. The synthetic fixture now records a possible original helper/child invocation. Controls reject the review's arbitrary `sh -c 'exit 23'` helper substitution and appended conflicting SourceSha, plus conflicts in all five identity-property keys even when both records agree, and mutually consistent but invalid runner selection/source/task/config/URL/flag changes. Relocated raw-directory replay remains positive.

Targeted RED command:

```
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.tests.test_evidence179_clock.HostProtocolProcess.test_already_exited_original_is_reaped_after_snapshot_failure scripts.tests.test_evidence179_clock.InvocationCommandBinding -q
```

Before the corrections: exit1,5 tests with12 failing subtest assertions, reproducing the status loss and missing invocation binding. After correction: exit0, `Ran 5 tests in 0.381s` / `OK`.

Final authorized covering command:

```
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.tests.test_evidence179_clock scripts.tests.test_evidence179_route_evidence scripts.tests.test_evidence179_runner scripts.tests.test_release179_runtime_controls -q
```

Result: exit0, `Ran 55 tests in 8.327s` / `OK`, no skips. Both changed Python files also passed `ast.parse`. Read-only preimage diff inspection confirmed the helper changes are limited to shared injected-argument construction, unconditional real child reaping and exact replay invocation binding. The diff command's status1 denotes changed files, not a failed syntax/test check.

Updated hashes below supersede only these two entries in the preceding inventory:

| File | SHA256 |
|---|---|
| `scripts/evidence179_clock.py` | `a4025c83441c07f6e84b9761c3b1c42ee8f79cdfab2e6f22b29121330997e8c3` |
| `scripts/tests/test_evidence179_clock.py` | `8a194955f40be181756c6e507f8b9905ca43b0976801c635b84511ae61ee2d0d` |

The external validator CLI, real EOF observations, independent upper guard,100 ms limit, ownership rejection, original evidence,153-case manifest and all other security/release gates remain unchanged. This round used no Git, CI, secrets, signing, subagents, Gradle bootstrap or Android execution. Existing local process-namespace limitations remain: snapshots are explicitly simulated in the host subprocess controls; the child itself, exit status, waits and failure transition are real.

**Separate verification gate remains open:** six happy API37 clock samples do not execute the Kotlin duplicate/cancellation/timeout/cleanup transitions. Those accepted-design negative lifecycle controls require actual execution and evidence before final approval. This round does not implement them or claim that host tests prove them. I1/I2 are now frozen for fresh narrow review; a separate lifecycle-control proposal is sent to root for authorization.
