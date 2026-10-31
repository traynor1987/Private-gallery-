# Runtime179 reauthentication fix — narrow round 1 review

2026-10-31. **Specification verdict: the source correction addresses R179-F2-1. Implementation-quality verdict: no new defect identified in this narrow fix.** Android compilation and execution, including this exact case, remain pending. This is neither an Android pass nor release approval.

## Scope

Reviewed finding 1 of `RUNTIME179_FIX2.md`, `work/runtime179-fix2-brief.md`, the appended round-1 report and the complete `work/runtime179-fix2-reauth-review.diff`. Compared the current SAF test and report with `/tmp/runtime179-fix2-reauth-before.kt` and `/tmp/runtime179-fix2-reauth-report-before.md`. Read only the direct unchanged Host, owner, access and HiddenSession interfaces needed to validate ordering and side effects, plus AndroidX's LifecycleRegistry ordering implementation. This is not a broader source review.

## Finding disposition

**R179-F2-1 — ADDRESSED at source level; required Android execution remains pending.**

The old split-turn counterexample is excluded by the revised test:

- The harness registers its Activity lifecycle observer during construction, before `show()` composes Host (`EvidenceSaf179Test.kt:165–180`). Its real ON_START callback calls `HiddenSession.onForeground()` before incrementing the reconciliation count. A one-shot hook is installed on Main only after the Activity has actually moved to CREATED; the hook is cleared before invocation in the actual ON_RESUME callback (`:141–150,223–250`). The hook asserts Main, actual lifecycle RESUMED and a newly completed foreground reconciliation. It does not assign a fake lifecycle state or manually invoke the observer.
- The source ordering is consistent with LifecycleRegistry's insertion-order invariant and forward pass: the earlier harness observer receives the upward event before Host's later observer. Registry state is set before observers are notified. See the [AndroidX implementation](https://android.googlesource.com/platform/frameworks/support/+/fcce9e766701c96f0953256e8efd36f6c0302531/lifecycle/lifecycle-runtime/src/jvmCommonMain/kotlin/androidx/lifecycle/LifecycleRegistry.jvm.kt), especially its observer-map invariant, `moveToState` and `forwardPass`. This checks the ordering contract; it does not establish the resolved Android dependency or an executed device trace.
- Within that uninterrupted Main callback, the test first requires expired original authentication, a locked Hidden key, retired original access and exactly one access. Only then does it call the real `beginAuthentication`/`complete`, requiring successful new authentication, unlocked state and invalidity of the captured original authentication (`:234–240`). Real HiddenSession generation and expiry behavior is retained.
- Before dispatch, it requires the single original OpenDocument launch and original request code, zero provider reads and one access. It reads, without changing, `EvidenceOwnerSession.closed`, requires false, then requires zero dismissals immediately before `returnDocument` (`:241–247`). There is no intervening queued Main turn or owner call. The owner check detects disposal independently of the fixture's dismissal callback. No proactive owner close or `sameAuthentication()` call manufactures the precondition.
- A positive atomic flag is set only after `returnDocument` returns successfully. Every hook throwable is retained and unconditionally rethrown as an AssertionError after the transition; the flag is then required independently (`:247–252`). A missing hook, failed precondition or failed registry dispatch therefore cannot fall through to final assertions and pass.
- The unchanged Host handoff retains the original pending ticket and retires its controller. Its result callback assigns delivery to that pending ticket; Host disposal closes the owner, and dismissal both clears pending and closes the owner (`EvidenceLockerHost.kt:47–56,68`). Thus the new false-closed/zero-dismissal preconditions exclude the earlier delivered-after-disposal explanation. On subsequent resume/result processing, Host checks original-owner authentication before creating any new controller or reaching import IO (`:70–82`). The final test waits for actual Host dismissal before requiring zero reads, one access, retired original access and invalid original owner. It additionally requires the new Hidden session to remain unlocked and distinct from the original (`EvidenceSaf179Test.kt:259–261`). The final owner query occurs after observed dismissal, so it cannot produce the required dismissal itself.

Other cases leave the hook unset. The added reconciliation counter does not change their HiddenSession calls. No production Host/authentication/access change, test-name change, skip, extra permission path or timeout change is present in this round.

## Independent verification and limits

Fresh read-only Python verification exited 0:

- Applying both supplied unified-diff sections in memory to their saved preimages reproduced the current SAF file and report exactly.
- All 31 SAF test declarations/names/order are unchanged; the current file has no trailing whitespace.
- The report is an exact append to its saved preimage, whose SHA256 matches the reported `2f51f8998a5e15016c7b00cda606be95a7b82a6906405a2b122093dad426734d`.
- Current SHA256 values match the supplied freeze identities:

| File | SHA256 |
|---|---|
| `EvidenceSaf179Test.kt` | `768415be97f88806d0f3317cc18641d24cfdb7d8d36ea26f1f2266df00cf861a` |
| Unchanged `EvidenceLockerHost.kt` | `1122d84b6ce35572f5372977e34a3d8fb7ccdd2bd18e1ce87b96cbb31238fc6b` |
| `/tmp/Runtime179ReauthOrderProbe.java` | `4863179bf0337ade559d71f24d21d3f2e3d2a58e5b4cdbb2b66aadbc0f59ad4d` |

Read the probe source without rerunning it or the already-passing 67 cases. It uses real HiddenSession and a modeled serialized Main/Host admission sequence. The retained report records the old vacuous counterexample, new live-dispatch precondition and rejection of a predismissed owner. Those are model results, not Android Host, LifecycleRegistry, Compose or ActivityResultRegistry execution. They are supporting evidence only.

No new fix-induced breakage was identified. This review does not claim Android compilation, callback execution, an actual corrected test pass, or satisfaction of the other runtime/security/release gates. The exact reauthentication case and the required grouped Android checks still must execute. No Git/CI/signing operation, probe/suite rerun, subagent or source edit was performed by this reviewer; this report is the only repository write.
