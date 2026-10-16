# Independent Task3B review — 2026-10-15

Fresh-context reviewer: /root/task3b_continuation_review, gpt-6-astra, read-only.
Range: Task3A5124de13 through bfd64caa plus continuation working diff.
No reviewer test execution or whole-Task3B approval. Judgment: PARTIAL; acceptance BLOCKED.

- P1: AndroidX biometric1.1.0 Activity-shared ViewModel callback/fragment means old queued
  success/error events can be delivered to replacement callback. Reviewer inspected cached
  dependency bytecode: BiometricFragment$9/$10 obtain current client callback when run;
  BiometricPrompt.init replaces it; cancelAuthentication resolves shared tagged fragment.
  Lexical attempt identity alone does not establish original platform request provenance.
  Exact original Cipher identity must reject rerouted success before provider use.
  Error has no Cipher; full app-owned terminal lifecycle coordinator/barrier remains required.
- P1 newly exposed by candidate: cancelPrimaryPrompt closed old operation, whose legacy
  asynchronous close could cancel current shared fragment again. Removing operation cleanup
  would also lose foreground revoke/expiry cleanup. Requires one owned coordinated retirement.
- P2 candidate: acquiring sensitive operation before old cancellation could leak new operation
  if cancellation throws, before it is stored/returned. Entire acquisition/start needs cleanup.
- Outer SecondaryBiometricRequest still exposed raw Cipher after Pending close; quarantine
  must apply to full request boundary, not just inner pending object.
- Candidate sensitive success requested key retirement but did not await actual original ack.
- Inherited blockers: request close under controller gate; workers/store children not fully
  prefunded; raw Hidden promotion; Main biometric/recovery/restore Native calls and manifests.
- No new inversion found in OwnedTemporaryKey; callbacks outside its private gate and
  future completion outside it. Root separately requires actual release invocation return.
  Add overlapping/reentrant rejected-use tests and held-release success ordering coverage.
- Exception/residual-risk documentation appropriately narrow; no internal Native retirement
  positively observed. Main/PR59, Task3A/formats and Phase3NO-GO must remain protected.

## Coordinator dispositions (not acceptance)

Unaccepted Main prompt rewrite is preserved in execution-evidence.zip → unaccepted-prompt-candidate.patch, and removed
from executable source. This eliminates candidate's new double-cancel path, operation leak
ordering and premature sensitive success publication rather than retaining unsafe changes.
Existing Main prompt lifecycle/cleanup defects remain explicit Task3B blockers; baseline
transitional cleanup is preserved, not relabelled as acknowledgement.
Exact crypto-identity and outer-request regressions are being run RED before their fixes.
Final verification, totals and remote checkpoint are recorded separately when actually done.

## Fix pass observed results

The two new behavioral regressions both failed in task3b-review-rerouting-red.log:
old CryptoObject accepted under replacement callback identity; outer closed request
still exposed Cipher. Both passed in task3b-review-rerouting-green.log. Primary
success now binds original Cipher before claiming/provider use. Outer Hidden request
has no second stored Cipher and delegates access to its pending original's closed/
consumed guard. These do not supply the missing shared prompt lifecycle barrier.

Candidate-specific replacement/exception paths were removed from executable source,
including its fixture API changes; baseline resource cleanup remains unchanged.
PIN comparison now explicitly awaits its original's actual retirement acknowledgement
before success and preserves interrupt status on interrupted waiting. Added reentrant
borrow and held-release-return regressions: completed stage and wiped bytes alone
cannot discharge a still-running original release or admit fresh authentication.
Source-freeze focused verification passed116 tests, zero failures/errors/skips, and
Android test Kotlin compilation. Broader verification is recorded separately.
No second review or complete Task3B/security acceptance is implied.
