# Actual Kotlin clock lifecycle controls — scoped review handback

**Implementation frozen for fresh scoped review. Actual Kotlin/API37 execution remains pending; these host results are not Android lifecycle proof.** Root accepted this separate task in `runtime179-clock-lifecycle-brief.md`. The pre-lifecycle snapshot is `/tmp/runtime179-clock-lifecycle-before` (all ten original review paths plus report, copied before edits); root subsequently committed that accepted core as `ce88e6203c202a1940dcd56c58985aae0d4649aa`. The accepted I1/I2 files `evidence179_clock.py` and `test_evidence179_clock.py` remain byte-identical to that snapshot.

## Actual control integration

`EvidenceRouteClockLifecycle179.run` executes unconditionally inside the existing mandatory `vpn_revoke` test before actual transport setup and before its positive clock receiver registration. It creates eight separate actual `EvidenceRouteClock179` instances with fresh run/route/arm/challenge nonces. It uses the actual target process, original DUMP-protected registration and `UiAutomation.executeShellCommand` to run package/user-scoped ordered `am broadcast` commands. It does not call `onReceive` semantically or substitute a host model. The eight instances use a one-phase `vpn_loss` clock identity only as an isolated synthetic control protocol; none is a VPN-loss route invocation or one of the six positive samples.

| Required scenario | Actual operation and required result |
|---|---|
| `competing_samples` | Release two shell requests from one coroutine latch; request intervals must overlap. Exactly one PendingResult is accepted, the duplicate poisons the instance, both replies reject, and the accepted result finishes once. Android still serializes receiver callbacks; this tests competing submissions/one-shot ownership, not simultaneous `onReceive` execution. |
| `cancel_before_claim` | Wait for real STATE readiness, cancel/await the prepare coroutine, then send SAMPLE. No PendingResult is accepted; cancellation poison rejects the request/read/final receipt. |
| `cancel_after_claim` | Obtain the real accepted claim, enter a caller cancellation boundary, cancel that caller and execute shared teardown. Preserve actual CancellationException and its suppressed clock-close error; finish the pending reply once. |
| `timeout_before_read` | Obtain an actual claim, perform no read, await the real one-second receiver deadline reply, then attempt the prohibited read. No successful sample is published. |
| `completion_deadline_race` | Schedule one main-handler latch at the actual claim deadline; release must be within−1 ms/+50 ms of that deadline. Attempt the actual read once. Either the read wins with its exact counter/raw success and healthy close, or timeout wins with zero successful sample and rejection. No retry forces a desired winner. |
| `late_stale_request` | Await the real prepare timeout, then send a late current-arm request and a separate stale-arm request. Both reject; no PendingResult is accepted. |
| `cleanup_throws` | With a real pending request, inject `IllegalStateException("injected-route-cleanup")` through the same teardown function used by the route. Preserve that primary exception and the suppressed close error while retiring receiver/thread and finishing the pending reply. |
| `cancelled_caller` | Cancel the calling coroutine before shared teardown; its cleanup observes cancellation. The helper's NonCancellable retirement must still complete and preserve primary/suppressed errors. |

All scenarios verify the actual post-unregister shell STATE result finds no receiver. Every poisoned instance rejects a later `actionTimestamp` specifically with its poison message and rejects final receipt success. Deadline success rejects a further read because the helper is closed. Native checks assert exact poison reasons, expected exception types, successful-sample counts and one owner-thread finish per accepted PendingResult. Awaited child failures cannot silently disappear. Unexpected errors propagate through the existing JUnit case; no control artifact is written unless all eight complete. Each scenario is bounded by8 s, the prelude by45 s, and each shell broadcast by `timeout 3`. Condition waits and latch/timer boundaries coordinate transitions; no route EOF read or observation timestamp is deferred or rewritten.

`EvidenceRouteClock179` adds only read-only identity/lifecycle snapshots and accepted/finish/owner counters. It adds no mutable test mode, timing override or alternate positive path. `retireRouteClock179` is shared by the real route's finally and the injected cleanup/cancellation controls; it always invokes existing NonCancellable receiver/thread retirement and preserves a primary cleanup exception with a suppressed teardown exception. No accepted-control fix to the action/clock interval was needed.

## Required artifact and replay interface

The prelude writes one immutable `evidence179-clock-lifecycle.json` to the existing additional-output directory. Existing runner collection copies its exact bytes to the results root and retains the original copy beneath `android-output/vpn_revoke`. A new separate `evidence179_clock_lifecycle.py` decoder is called by the **unchanged route-validator CLI** after the six positive clock observations are loaded. It requires both byte-identical copies, exactly the eight ordered scenarios and all raw request/reply records. Both artifacts, the new Kotlin/decoder/test sources and existing changed helper/runner sources enter the proof hash set. The existing workflow command adds only the new host test module; no workflow/job or external CLI was added.

Typed version1 artifact contract:

* Top-level: exact kind `android_clock_lifecycle_controls`, source SHA, existing `vpn_revoke` class/method, parent positive route nonce, process identity and eight `scenarios`. Identity contains integer PID/UID/user and exact package/boot UUID/AndroidJUnitRunner; all must match the real positive VPN receipt.
* Each scenario: exact name, fresh32-hex run/route/arm nonces, ordered `events`, final `observation`, actual action counter (integer0 only when no successful read), close success state, later-read rejection message, caller cancellation state, primary error classification, actual close exception class/message and actual suppressed exception class/message list. The deadline scenario additionally records target/release elapsed-nanosecond counters; others require both0.
* Each raw event: exact argv list, untouched merged shell stdout/stderr including `E179_CLOCK_STATUS`, and original integer elapsed-nanosecond send/receive counters. Framing, shell status0, result code, exact decoded payload, source/process/nonce/phase/arm/challenge and user/package targeting are independently checked. The parser permits real0/178/179 outcomes only in their required positions. SAMPLE errors distinguish the one accepted pending failure from unaccepted rejection. A final real result0 after all prior replies proves no registered receiver is found.
* Final observation: registered/thread-alive booleans must both be false; positive owner-thread ID; exact poison string or null; one claim with integer accepted/finish counts, finisher-thread ID, pending=false, exact successful sample counter and actual claimed counter. Accepted requests must finish exactly once on the recorded owner thread; unaccepted requests have no finisher or claim timestamp. Claimed/sample counters must lie in their raw request intervals. Timeout replies cannot predate the one-second actual deadline; a successful racing read must follow latch release and stay within that deadline.

Missing, duplicate, foreign, inconsistent, generic-PASS-only, pending/leaked, double-finished, wrong-owner, premature-timeout, early-deadline, wrong-source or changed raw controls fail. Poisoned sample publication and lost primary/suppressed cleanup errors fail. These controls cannot be counted among the six positive clock samples. The independently calibrated positive upper guard,100 ms maximum bracket, actual EOF reader, transport/packet/DNS/no-fallback, original statuses,153-case manifest and release/upgrade gates remain unchanged.

## Executed host verification

TDD first ran `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.tests.test_evidence179_clock_lifecycle -q`:3 expected failures for the absent decoder. Decoder/typed raw replay controls then passed. A separate new runner collection test failed on the missing top-level lifecycle copy before the collection change, then passed with exact byte preservation.

Final changed-file covering command:

```
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.tests.test_evidence179_clock_lifecycle scripts.tests.test_evidence179_runner scripts.tests.test_evidence179_route_evidence scripts.tests.test_evidence179_clock.RawClockReplay.test_same_validator_cli_requires_raw_calibration_and_removes_stale_pass -q
```

Result: exit0; `Ran 31 tests in 3.176s` / `OK`, no skips. This covers both synthetic deadline outcomes, raw deletion/copy mismatch, exact scenario inventory, process/source/nonce binding, wrong targeting/status/reply, double finish/pending/wrong owner, sample after poison, suppressed cleanup error removal, deadline mutation, exact collection bytes and the same external CLI's semantic rejection/stale-PASS deletion. The complete synthetic lifecycle input advances only to the deliberately absent next host-phase gate; it creates no route PASS. Synthetic host fixtures are not Android evidence.

`bash -n scripts/run_evidence179.sh` succeeded. `ast.parse` accepted the four changed Python files. Byte comparisons confirmed both accepted I1/I2 Python files are unchanged. The frozen manifest still contains153 cases. No unrelated passing suite was rerun.

## Empirical limits and next gate

No Gradle bootstrap, Kotlin compilation, emulator, CI, actual shell broadcast or Android lifecycle control was executed locally. Compilation/API37 behavior—including shell timeout availability/framing, concurrent UiAutomation request delivery, actual deadline winner, cancellation exception details, raw close results, source-bound receipts and no leaked receiver/thread—must be established in root's next coherent existing153-case API37 job after fresh scoped review. A slow or differently ordered device may honestly fail a control; there is no retry, skipped scenario or fabricated fallback. One racing attempt records whichever winner actually occurs; it does not claim both race outcomes occurred on Android. Six happy samples remain insufficient evidence for these eight transitions.

No production/auth/crypto/browser/manifest/version/EOF source, original run artifacts, Git, signing material, secrets or subagents were used or changed by this worker. Implementation is frozen for lifecycle-scoped review; root alone owns Git and the next CI decision.

## Frozen review-file SHA256

| File | SHA256 |
|---|---|
| `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceOwnedRoute179Test.kt` | `ab0175e8282ab5b2c18deec127a9d46ca0ef4f87ed028041cbe4a428c2215b17` |
| `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceRouteClock179.kt` | `f78f4e3b073757d16db140c4fc22b160a95a0e412fd0cb7c0bdec59121f2a4aa` |
| `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceRouteClockLifecycle179.kt` | `4212191d36f906b4f2192826deb82ed5ba7684294e20ef0ab2213ee366d1ff51` |
| `scripts/evidence179_clock_lifecycle.py` | `fcdd4d07a267e1522480d4cdd3187d6e627a8f2ad67b48ab0fc3807bd5f7b699` |
| `scripts/verify_evidence179_routes.py` | `5d1f1c83c24d3376bc1c55d6f54f3eed57bd66d6ac4b7a813c52920702e9a0a3` |
| `scripts/run_evidence179.sh` | `888849ed171e2f71870fda34e226e68d1e2a1fdfc0fc72a52b5efd716ee650e9` |
| `scripts/tests/test_evidence179_clock_lifecycle.py` | `413db6c635949529c774f73b7883d4410903860fcbd448bb522a59a411ed2821` |
| `scripts/tests/test_evidence179_runner.py` | `3187e7613c4309937e0637b83b737cdc7b38d2b63a428f9bd91685fe637dee4a` |
| `.github/workflows/fast-development.yml` | `5b1d5d153f1747f32c1b67a64643bf538858c7a2421af75b7fe658139d40722e` |

## Lifecycle fix round1/5 — review I1/I2

Read `reviews/ROUTE179_CLOCK_LIFECYCLE.md` I1/I2 verbatim. Preimages are `/tmp/runtime179-lifecycle-fix1-before`. This amendment supersedes the earlier transport description and competing-SAMPLE outcome: that earlier command path was invalid, and the earlier two-rejection duplicate scenario could reach deadline poison instead of the duplicate guard. No earlier Android success is claimed.

**I1 — explicit shell transport corrected.** The Kotlin control now invokes only the constant two-token `executeShellCommandRwe("sh -s")`. The complete validated script is written as UTF-8 to stdin and that descriptor is closed. Separate bounded stdout/stderr readers run concurrently; all returned descriptors close in finally. The public [UiAutomation API reference](https://developer.android.com/reference/android/app/UiAutomation) was checked this round: Rwe is public from API34 and its descriptor order is stdout0, stdin1, stderr2. API37 is the existing required image. No quote-containing `sh -c` command string is used.

Each raw event additionally requires `entrypoint="sh -s"`, the exact stdin `script`, `stdout_b64` and `stderr_b64` (canonical base64url without padding). `raw` remains an exact UTF-8 representation of stdout; replay verifies byte equality. Stderr is preserved separately and must be empty for accepted controls. Replay reconstructs the exact script from the recorded bounded argv. Its script runs the existing `timeout 3 am broadcast ...`, captures that command's real `$?`, prints the required status trailer and exits with that same status. Entry/script/argv/streams/status disagreement fails. The existing package/user/run/source/nonce bindings remain.

The new narrow regression executes **real host Java Runtime.exec**, not a tokenizer model: the quoted executable string is rejected; constant `sh -s` accepts the script on stdin. It extracts the script body from the actual Kotlin source and checks it against replay's construction, checks the Rwe call/FD mapping, then runs it against a disposable host `am` fixture. For fixture exits0 and23 it verifies exact received argv, separate stdout/stderr bytes, real trailer status and shell exit result. This confirms the Java command/stream contract on the host, not Android UiAutomation execution or API37 delivery.

**I2 — reachable ordered duplicate transition corrected.** Both shell submissions are launched from a latch; the control waits until both scripts have been written and one actual claim exists. It then performs **one actual clock read** and lets its real PendingResult finish, releasing ordered delivery. The queued duplicate can then execute the consumed-request guard. Exactly one SAMPLE succeeds with the exact read counter, the other returns `clock_rejected`, and the instance must acquire the specific duplicate poison. Final receipt success and a later read still reject. Two deadline failures do not satisfy this control.

Minimal read-only helper observations now include first-poison elapsed time/order and claim sample order, finish order and finish elapsed time. Sequence allocation is under the existing lock; finish completion is recorded after `PendingResult.finish()` on its owner thread. The negative decoder requires sample order1, finish order2, duplicate-poison order3, compatible actual counters, and poison inside the rejected raw request interval. It independently binds the successful reply's challenge/counter to the accepted request. Thus a first sample **before poison** is permitted only for this separate negative scenario; publication at/after poison, missing duplicate rejection, deadline-poison relabelling, multiple acceptance/finish or healthy final close fails. The six positive samples still require their original healthy, no-extra-request contract.

Overlapping shell submissions are not simultaneous callbacks: Android's ordered delivery remains active through the first PendingResult finish. Which submitted request is delivered first is not assumed; one success and one duplicate rejection are required. The deadline scenario remains one attempt with whichever actual winner occurs, and is not repurposed as duplicate proof.

### Focused verification

Before correction, the targeted three-test review-control command reproduced the missing explicit script contract and rejected the proposed first-success/order artifact under the old decoder (one failure, one error; the mutation-only test was already fail-closed). After correction, all new transport and duplicate-order controls pass.

Final changed-scope command:

```
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.tests.test_evidence179_clock_lifecycle scripts.tests.test_evidence179_clock.RawClockReplay.test_same_validator_cli_requires_raw_calibration_and_removes_stale_pass -q
```

Result: exit0, `Ran 8 tests in 2.315s` / `OK`, no skips. This includes the real host Java source-file execution described above, mutation rejection for command/stream discrepancies and sample/finish/poison misordering, both deadline replay outcomes, complete required negative inventory and same-CLI deletion/semantic/stale-PASS controls. Both changed Python files pass `ast.parse`.

Byte/hash checks confirmed the accepted I1/I2 Python core files and the other five lifecycle review paths are unchanged. Only the two Kotlin control/observation files, separate lifecycle decoder/tests and this appended report changed. The following hashes supersede their prior entries:

| File | SHA256 |
|---|---|
| `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceRouteClock179.kt` | `78051b74ad35d22201a63a6e680c6eead79de46a7da6363f23d5ea7dd85a53ef` |
| `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceRouteClockLifecycle179.kt` | `eab79fcf775fe9a2633f5ef874d25ec35274c05fb285cf2fa13e2271d986f301` |
| `scripts/evidence179_clock_lifecycle.py` | `112530bfdc24692239f188ea8f6f82744e19ae72f9fab333f8e815d59fefa28c` |
| `scripts/tests/test_evidence179_clock_lifecycle.py` | `0d7fc4daf5949b90494fe9d8011cb7b7f7a65a4c35b54e5df7819e8e83279f7e` |

No local Kotlin/Android compile, emulator, CI, Git, signing, secret or subagent operation occurred. Runner/archive/validator CLI, positive clock bounds, EOF behavior, production files,153-case identities and workflows remain unchanged this round. Actual Rwe delivery, FD behavior, ordered-queue transition, all eight lifecycle scenarios and six positive samples remain pending root's coherent API37 evidence after fresh narrow review. This fix round is frozen.
