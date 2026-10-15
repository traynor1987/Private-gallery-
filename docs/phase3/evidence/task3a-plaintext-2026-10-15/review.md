# Independent Task 3A review and dispositions

A fresh read-only whole-phase reviewer inspected the source diff from 3fd3c623.
No implementer delegation, reviewer edits, Gradle runs or remote mutations occurred.

- Important: migrated decryptExactly still sniffed the file with an unaccounted
  RandomAccessFile before its scoped input/reader. Fixed with a scoped preclaimed
  IO descriptor and dispatch-only retirement, leaving unrelated legacy callers
  unchanged. The admission guard failed before the fix; exhausted-IO regression
  now denies before opening and succeeds after actual original retirement.
- Important: exclusive borrowing rejected save/generation/replacement preview
  while synchronous old preview rendering still consumed the same source.
  Fixed with a bounded count of at most16 read-only synchronous users of the
  same physical original. No new array, resource slot or worker is created by
  borrowing. Nested exhaustion/revoke and two-thread actual-return tests prove
  retirement cannot wipe another live borrower. The preceding exclusive source
  failed the regression. UI job cancellation does not pretend Native return.
- Minor: native ranged reads beyond EOF threw rather than returning -1. The
  preceding source failed the test; all positions at/beyond EOF now return -1.

The reviewer found no Critical issue and did not accept the whole Phase3
programme. Root inspected the findings, reproduced the regressions, applied the
fixes and ran the final focused verification (49 tests,0 failures/errors/skips).
The review was not repeated against another identical phase. Scope limitations
in the checkpoint report remain mandatory.
