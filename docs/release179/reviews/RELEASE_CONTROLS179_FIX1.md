# Task7b release controls — scoped correction review, round1

Date: 2026-10-31. This review covers only initial finding1 and regressions introduced by its correction. It is not a whole-application review or final runtime, signing, upgrade, activation, delivery, or physical acceptance approval.

**Spec compliance verdict: PASS for this scoped correction. Security/code quality verdict: PASS for this scoped correction. Finding1: ADDRESSED. Introduced findings: none identified (0 Critical, 0 Important).**

## Scope and immutable comparison

Read `work/task-7b-release-brief.md`, `reviews/RELEASE_CONTROLS179_INITIAL.md`, the appended round1 section of `work/task-7b-release-report.md`, and `work/task-7b-fix1-review.diff`. Inspected the current runner and its narrow regression tests. The coordinator identified original controls commit `3b41d8dcd166395a2485050e214bdcc69c62ce08` and supplied `/tmp/tree179-controls-inactive.json` as its independently verified 17-entry snapshot; this reviewer did not run Git or independently fetch the commit.

Compared current bytes against that snapshot. Of its 13 release-control/fixture/test entries, only `scripts/verify_synthetic_upgrade179.sh` changed. The workflow, fixture, compilation/provenance helpers, runtime/scope/upgrade validators, curation helper and other included tests remain byte-identical. The runner's only delta is the package-admission block at lines 19–24. Reverse-applying all three supplied diff sections in memory matched current file content; the restored runner and report exactly matched the snapshot. Restored runner SHA256 is the initially reviewed `ce8b74c3a71813d322a38776d546482c2adda6121af024cb31d980c152cc83d5`; restored report SHA256 is `f1247ef52f86cb23bf52ba0a746a33679f6abb248becc1261444bc306810f5de`. The snapshot does not contain `test_upgrade179_runner.py`; its change was reviewed through the complete supplied diff and current file.

## Finding1 — ADDRESSED

At `scripts/verify_synthetic_upgrade179.sh:20`, the runner now captures `adb shell pm list packages "$fixture_package"` through `tr -d '\r'`, with an explicit failure branch on the assignment. Under the retained `set -euo pipefail`, nonzero status from either the adb query/transport or the filter makes the pipeline and assignment fail. The branch exits before downstream artifact creation, signing or installation, regardless of whether the failing query emitted nothing or partial output. There is no blanket success coercion.

Successful empty output reaches the subsequent code, correcting the clean-emulator liveness failure caused by `pm path`. Lines 21–24 use `grep -Fxq` on the complete `package:uk.co.traynor.privategallery` line, so an installed production package is rejected while suffix-only test/debug package results do not create a false production match. CRLF normalization is retained. The query's failure branch runs before the exact-match check, so partial output cannot conceal a failing query.

The three added test methods at `scripts/tests/test_upgrade179_runner.py:31–58` extract and execute the actual narrow admission block with `set -euo pipefail`. The mock accepts only the intended read-only query. The assertions cover successful empty and suffix-only results, an exact production match among suffix results, and statuses 1/42 with empty or partial output. They check both rejection and absence of the continuation marker. This is appropriate focused regression coverage for the reported defect; it does not exercise a real Android package manager or upgrade.

No concrete new defect was identified in this delta. The original certificate, baseline/candidate identity checks, single compilation/provenance path and original-key signing path are unchanged. Baseline install/seed, candidate `install -r`, verification and force-stop/restart verification remain in their original order. No uninstall or data-clear operation was introduced.

## Verification and evidence

This reviewer ran `bash -n scripts/verify_synthetic_upgrade179.sh` successfully (exit0), checked the supplied diff against current and immutable snapshot bytes in memory, and independently parsed the workflow to assert acceptance179 `FROZEN_SOURCE == '0' * 40` and absence of `.acceptance179-candidate`. The workflow SHA256 remains the initially reviewed value below. The six passing same-source host methods were **not rerun**; their reported result was inspected together with the test implementation. No other host suite, Git operation, CI action, secrets access, signing, device operation or subagent was used. The only repository write by this reviewer is this report.

| Reviewed current input | SHA256 |
|---|---|
| `work/task-7b-fix1-review.diff` | `5b03c41b706ca6894d5584b1c7826d01005af5273f6cbd446dc8e78f64b1a192` |
| `work/task-7b-release-report.md` | `6e28da47913dd3927846ccfbd3f13ea0aa8dbde1c7d1fa6169e079c61f8284d5` |
| `scripts/verify_synthetic_upgrade179.sh` | `a7b4033f896ec90f7b09368794fc432fe7725ad80dbd559bb25f58b1e4b63515` |
| `scripts/tests/test_upgrade179_runner.py` | `a3eff069da9bea0e01a64346d681446f1a08a12c696a52169c939fe81bccde2c` |
| `.github/workflows/acceptance1762.yml` | `7a196496917cf0edb53b6e2c89379ad2e3688ca3ba602360c9b7bef9ccfb658f` |

## Disposition and limits

Initial finding1 is closed for these exact correction bytes. This report grants no signing or activation authority. Keep `FROZEN_SOURCE` zero and the marker absent until the coordinator completes the required exact-source runtime and consolidated approval gates. Actual baseline-compatible Android instrumentation compilation, original certificate verification, production identity/distribution/alignment gates, and the original50 seed → exact signed51 in-place upgrade → verify → restart verification remain required at their configured stages. Host tests and this source review satisfy none of those runtime/signing gates. Physical acceptance remains owner-required. No final release-approval machine block is issued.
