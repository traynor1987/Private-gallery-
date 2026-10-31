# R179-F3-1 narrow runner correction review

2026-10-31. **Specification verdict: PASS for this correction's source requirements. Implementation-quality verdict: PASS within the narrow reviewed scope. R179-F3-1: ADDRESSED. New findings: zero Critical, zero Important, zero Minor.** This is a source/control review, not Android runtime acceptance or release/signing approval.

## Scope and evidence

Read `work/runtime179-runner-fix1-brief.md` first, finding 1 verbatim in `reviews/RUNTIME179_FIX3.md`, the appended `work/runtime179-runner-continuation.md`, and the complete `/tmp/runtime179-runner-fix1-review.diff`. Reviewed only the runner correction, its focused host controls and report, with direct dependency reads of the strict case/route validator, release replay and independent peer-close helper. The earlier two Android test corrections and whole branch were not re-reviewed.

An independent read-only comparison against `/tmp/runtime179-runner-fix1-before/` reproduced the supplied three-file unified diff exactly (10 context lines), confirmed the report is append-only, and counted the unchanged manifest as 153 unique identities: 149 nonroute and four route. Command exited 0 with:

```text
Exact three-file correction diff match; 153 unique manifest identities (149 nonroute + 4 route); report append-only.
```

## Finding disposition

**R179-F3-1 — ADDRESSED.** At `scripts/run_evidence179.sh:24–55`, the actual `run_selection` resets both exposed statuses on each direct shell invocation, captures the original Gradle/timeout result before diagnostics or archival, and captures the archive Python's result independently. The real archive still copies framework XML before additional output; an exception after complete XML copying can no longer replace the original status. A nonzero original result remains the function result; original success plus collection failure returns the collection failure.

At lines 58–65, the caller writes separate original and collection receipts, then rejects collection failure before examining XML or entering routes. Thus original 124/137 plus archive failure retain original receipts and exits 124/137; original 1 plus archive failure exits 1 without continuation; original 0 plus archive failure retains original receipt 0 and fails with collection status. These conclusions follow the actual function and copy boundary, rather than a whole-function return-code stub.

With collection success, only original exit 1 may enter the existing failed-selection validator. Its exact manifest-derived unique identity set, no-skip requirement and actual testcase failure/error requirement remain intact. Other nonzero original statuses stop before routes. The final barrier at lines 112–115 still rejects a retained nonroute failure before strict 153-case/raw-route validation and focused-gate generation. No security assertion or case-scope reduction was introduced.

The same direct-call function and shared route loop serve all four ordered cases (`vpn_revoke`, `vpn_loss`, `tor_revoke`, `tor_death`). At lines 92–104, each end phase now records the true original result and separate collection result. Independent peer-close execution requires both to be zero. Original failure, collection failure or peer-close failure exits before the next route and final validation. The original peer-close result capture, mandatory check and VPN drain ordering remain. The new collection field is compatible with the direct raw validator: it reads required phase fields without requiring an exact field set; phases and runner bytes remain hash-bound. Release replay reruns that validator and compares canonical raw/control hashes, including host phases. No validator/replay change was required or made.

## Coverage and limits

`scripts/tests/test_evidence179_runner.py` now extracts the actual function, continuation/final-barrier blocks and shared route-loop body. It mocks command/device emission in temporary workspaces while retaining the real archive Python and XML/additional-output copies. Its destination collision deliberately raises `FileExistsError` after complete 149-case XML has been copied. Reviewed controls cover original 124/137, 1 and 0 plus this failure; successful collection with original 1 allowing diagnostic routes but blocking strict validation; and original 0 retaining the strict path. Route-body controls inspect original/collection end-phase provenance and prevent peer-close reachability after collection failure; the successful path retains peer-close reachability. Missing, partial, duplicate, foreign, skipped, unexplained and infrastructure controls remain.

The worker's append records the exact focused command `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.tests.test_evidence179_runner -q`, final observed output `Ran 11 tests in 0.974s`, `OK`, exit 0; and `bash -n scripts/run_evidence179.sh`, exit 0. This reviewer inspected those controls and report and did not rerun these already-passed checks or unchanged broad suites. No new uncovered behavior required another execution probe. The test markers and synthetic XML are host-control evidence only: they do not establish actual Android, route, peer or packet behavior.

## Frozen SHA256

Independently recomputed values at review:

| File | SHA256 |
| --- | --- |
| `scripts/run_evidence179.sh` | `fbc67c80135ea7a7f2074acfc59385109c0a188e075bc4233472d818492eb94b` |
| `scripts/tests/test_evidence179_runner.py` | `2b99705e47c775d0d73fda3e0216c3594a770240cb161dfd8c0d8ed3a97edb43` |
| `docs/release179/work/runtime179-runner-continuation.md` | `22d7caf3b44da60956ed5f792a97c4192fb39d43d30a6d94e60867d6a64c23d4` |
| `/tmp/runtime179-runner-fix1-review.diff` | `c4eb79d99ba8509e7401052bb009a169cc8a8a445764f3aa1604f193c3a6ad79` |
| `scripts/evidence179_cases.json` | `f2a622f5fa919e29d5465d0e8bac1dacdd1a4bbb504371b939658f3a671ee2f6` |
| `scripts/verify_evidence179_routes.py` | `9e08c0b5ba6ee478132e2a4fbb5c560b1dfa3a3ceb85dc0ed6a558ad911d8775` |
| `scripts/revalidate_runtime179.py` | `d0b4817e1d55c0aad7a9c89cb82b60c6e5ac38e61acb074b5d15646242389ef2` |

This report is the only repository write. No Git, CI, source edits, Android build/run, secrets, signing or subagents were used. Reviewed correction frozen. Actual corrected-source compilation and all 153 mandatory unskipped Android/security cases, all four actual owned-route observations, strict raw-route/source/artifact validation, independent final review and original-key upgrade/signing/physical-device gates remain mandatory. This narrow PASS cannot satisfy those gates.
