# Minimal 1.7.9 stdin isolation review

**Spec verdict: PASS for the narrow correction.** Both subprocess launch boundaries now explicitly use `stdin=subprocess.DEVNULL`: Recorder's noninteractive identity/ADB commands and the original timeout/Gradle child. They therefore see EOF instead of consuming the enclosing Bash route-list pipe. These commands receive their inputs through argv/environment; EOF is compatible with their intended noninteractive invocation. The helper itself does not read stdin. Static comparison against the preserved preimage confirms exactly those two launch-argument changes.

**Quality/security verdict: PASS for this diff; no new blockers.** Argv and injected properties, source/transport identity, child ownership capture, monotonic clock bracketing, timeout handling, original and independent clock statuses, raw recording/replay validation, and runner collection/peer-close failure barriers are unchanged. The change neither manufactures missing XML nor weakens the strict completeness checks.

**New findings:** Critical 0; Important 0; Minor 0.

The added regression runs a real Bash four-row process-substitution loop, real Recorder, and a real stdin-draining child; it checks all four identities plus recorded EOF, stderr, status and timeout state. The original-child regression uses real Popen/read/wait and verifies EOF with original statuses 0/23 and independent clock success/failure. Its existing harness simulates only process-identity snapshots for the local `/proc` limitation and uses disposable fake ADB responses; it does not mock the function or stdin boundary under review. This meaningfully detects both removed stdin arguments, as the recorded RED run demonstrates.

Evidence reused: `work/minimal179-stdin-report.md` records RED (2 tests, 4 failures), GREEN (2 tests), the covering 41 host checks passing, Python syntax/scope checks and Bash syntax. I inspected the complete diff, relevant helper/runner code and test implementations, independently matched the hashes and exact two-line production scope, and did not rerun passing tests or launch CI.

| Reviewed input | SHA-256 |
| --- | --- |
| `/tmp/minimal179-stdin-review.diff` (12,235 bytes) | `d84e7a9bd0dd0d772f72b056519264a9e9e8f3d6c0ec363815b72e3b8d8f113f` |
| `scripts/evidence179_clock.py` | `4d43aa230f985633f084da06bcff6dd2e7d7619807e7e66ff1ca4270e050f8f9` |
| `scripts/tests/test_evidence179_clock.py` | `53a8efe627fb16d9e1099ebb8c106afabb8d3390500b124ea0986d5f4e8fb6cc` |
| `scripts/tests/test_evidence179_runner.py` (unchanged) | `3187e7613c4309937e0637b83b737cdc7b38d2b63a428f9bd91685fe637dee4a` |

Limits: baseline `ae93b06a2a57cbfec9226b4b78e5532f3f438f68`; review covers this stdin correction only. Host regressions establish FD isolation, not Android execution. The prior 150 passing original cases and valid controls/clock observations remain partial evidence; three required routes are still unexecuted in that evidence. No full 153-case, packet, or final release approval is granted. Source/Git/CI remain untouched by this reviewer; frozen after this review document.
