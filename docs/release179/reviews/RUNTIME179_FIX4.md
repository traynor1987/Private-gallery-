# Runtime179 fix4 scoped UI review

2026-10-31. **Specification verdict: PASS for the scoped source correction. Implementation-quality verdict: PASS for the scoped source correction. New findings: zero Critical, zero Important, zero Minor.** Corrected-source Android compilation and execution remain unrun; these verdicts do not establish that the runtime failure is resolved or authorize release/signing approval.

## Scope and evidence

Read `docs/release179/work/runtime179-fix4-brief.md` first, then `docs/release179/work/runtime179-fix4-report.md`, the complete `/tmp/runtime179-fix4-review.diff`, the current test and its preimage. Inspected only the direct dependencies needed to assess this synchronization: `Evidence179UiActions.kt`, the Locker's Library/header/keyed rows, selection callback, close path and filename-opening control. The review excludes route corrections and whole-application review.

The original immutable run 41252039247/job 124687392783 belongs to source `29a2a7b0c3951cd8d4cdf77fe58be06ebfb235b9`: **153 exact unique cases, 152 PASS, one FAIL, zero skipped**, as established in the brief and implementation report. Independently corroborated the lone failure using original `failures.json`, the job-log failure block and original case-logcat excerpts: `performClick` reaches `performTouchInput`, whose fetch cannot find the exact actionable `alpha179.txt` node at original line 202. The preceding helper returned successfully; no controller/authentication exception is recorded in that stack. This review did not rerun the implementer's XML/source-check script or any of the passing 152 cases.

The report appropriately distinguishes this observed fetch boundary from its hypothesis. Source shows search/header controls and keyed evidence rows in one LazyColumn; selection changes checkbox state and header button enablement. Original logcat contains IME activity, but no semantics/bounds capture proves which transition removed the target. A layout/lazy invalidation is plausible; neither a definitive keyboard cause nor a production defect is established. The new source comment describes the actual selection/header dependency without asserting an unproven original cause.

## Requirements and quality assessment

- The actual Alice search, one checkbox `performClick()` and one exact actionable filename `performClick()` remain. The post-selection `assertIsOn()` verifies the real selected state. Neither gesture is retried, caught, replaced with a callback nor bypassed with a semantics click.
- After selection, the test settles Compose and unconditionally materializes the filename from `evidence-list`, which source identifies as the actual LazyColumn. It then waits at most 10 seconds for exactly one matching displayed node, asserts display again, and injects the opening touch. The filename matcher and merged-tree selection remain the same as the original actionable target. No arbitrary sleep or target broadening is introduced.
- These are valid Compose test APIs. Android's official API reference documents `performScrollToNode` since 1.1 for the scrollable container, including LazyColumn; it returns the container, so the subsequent independent `onNode(openAlpha)` is appropriate. `isDisplayed()` exists since 1.6, returns false for no match and is explicitly illustrated inside `waitUntil`; the project's Compose BOM is 2024.12.01. The wait's count check requires uniqueness, and a duplicate introduced between observations still fails rather than selecting an arbitrary node. See [official API reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/package-summary).
- The timeout diagnostic reports only match count, close callback count, busy state and catalogue record count. It adds neither evidence text nor key/authentication data and does not convert a failed wait into success. Scroll failure and later touch failure also remain failures.
- Direct preimage comparison shows only selection/open preparation changed. All earlier methods are unchanged, and the entire suffix from the unsaved correction input through live Hidden lock, retired Locker/catalogue checks, unsaved-text absence, fresh Hidden authentication/access, one encrypted record with no committed corrections, two original sources and final cleanup is unchanged. No assertion is removed or weakened. The remaining `Review / correct` helper is unchanged; this case has no further selection-to-filename boundary supporting the same targeted change.

## Freeze and verification limits

Read-only `diff -u` against the saved preimage exited 1 with only the expected Hidden workflow hunk. `sha256sum` independently confirmed:

| Reviewed item | SHA256 |
| --- | --- |
| `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceLocker179UiTest.kt` | `1ce730cb65c03444cc1241b25380e596868e0cb665d4a1ddcf6607085fd3f27b` |
| `/tmp/runtime179-fix4-before/EvidenceLocker179UiTest.kt` | `bf241183ad13748da591009b29924e7eae9762b217070d23e1c9ff219565a54f` |
| `/tmp/runtime179-fix4-review.diff` | `544b1df08c83c771bf1517758437d29f07a6f981e15c3cd9b2dd00932a00e7a5` |

The frozen source hash matches the implementation report. Its recorded source checks preserve seven UI test identities and the 153-case manifest; this review did not claim a new accounting run. Source inspection supports this narrow correction, but cannot prove the native/IME transition has settled across the final touch boundary. The next actual Android run must determine that. Original 152 passes remain evidence for the original source only. All 153 mandatory unskipped cases, strict raw-route/final gates and existing release/upgrade/signing gates remain outstanding for approval.

This report is the only repository write. No source edit, Git operation, CI action, Android build/run, ad hoc source-check execution, signing/secret access or subagent work was performed.
