# Evidence179 Task6 — independent fix2 F1 review

Reviewed 2026-10-31 against `INTEGRATION179_FIX1.md`, the appended Task6 report and `/workspace/scratch/4181ccd09b2e/review179-integration-fix2.diff` (SHA-256 `f5a46caa33a2497cb03b04ed51a1149838806772e9c8abb156b1f0d1f5b8b20b`). Scope: the exact one-line production change and new breakage from it only.

**F1: ADDRESSED. Specification and quality verdict for this scoped fix: accepted at source-review level. No new fix2 finding established.**

`EvidenceLockerHost.kt:142` now calls `it.write(uri,consume=write)`. The function argument explicitly binds to `consume`, matching `EvidenceDocumentIo.write(uri:Uri,protectedSource:Uri?=null,consume:(OutputStream)->T)`, while ordinary newly created export destinations retain the intended null protected-source default. The previous positional argument mismatch is removed without changing callback ownership or cancellation behavior.

The actual archive re-export still calls `io.write(destination,protectedSource=source){...}`. Its dialog disposal, retirement and partial-cleanup calls still carry the incoming source; the Host ticket cleanup also retains `ticket.source`. This one-line fix does not weaken the source/destination identity guard.

Prior **I1–I5/M1–M2 remain ADDRESSED at source level** as recorded in fix1. This review does not reopen or broaden those dispositions or the separate export/model reviews.

Evidence: inspected the exact diff, current signature/caller and protected archive calls, and the report's source-verification commands. No product edits, host tests, Kotlin compilation, Git, CI, signing or subagents. The report's prior host passes were not rerun and do not establish Kotlin compilation.

**All 51 affected Android cases and all other mandatory Evidence179 Android cases remain UNCOMPILED/UNRUN for this corrected source.** Grouped main/test Kotlin and API37 execution, actual provider/lifecycle/native behavior, real VPN/Tor permission/process/DNS/packet/revocation proof, consolidated review, regressions, original-key code50→51 upgrade, signing/APK identity and physical Fold acceptance remain mandatory pending gates. Closing F1 in source is not build, runtime or release approval.

## Narrow addendum — UI credential test adapter compiler fix

Reviewed the exact two-method test-only delta in `/workspace/scratch/4181ccd09b2e/review179-ui-credential-adapter.diff` (SHA-256 `2a80ad47a486725f52c8a9fa8286af4986992aa14a73ad2a66496dd6ae223569`) against the actual `EvidenceCredentials.Backend` and `AiCredentialStore.PreparedChange` declarations. **The reported adapter mismatch is ADDRESSED in source; no new finding or weakened test/authentication semantics established.**

`EvidenceAi179UiTest` replaces obsolete no-op `save`/`clear` overrides with the required `stage(ByteArray): PreparedChange` and `removal(): PreparedChange` methods. Both explicitly reject unexpected mutation in this read-only configured-credential fixture. `configured`, credential read/counter, captured access/commit guard, route behavior, zero POST/key-read before consent, explicit send, separate owner application and retained-advisory/egress assertions are unchanged. This fixture still does not claim to test credential setup/removal; those paths are not silently simulated as successful.

Root reports actual CI `41233045184` at `849022ce` passed main Kotlin compilation, then failed AndroidTest Kotlin compilation on the missing staged-backend implementation and obsolete overrides. This supersedes the earlier blanket main-compilation status for that exact CI identity only. The two-method correction has not yet compiled or executed; grouped AndroidTest compilation and all affected Android runtime/provider/route-proof gates remain pending. This review did not run tests or CI, edit product code, use Git or delegate work. Existing F1 and I1–I5/M1–M2 source dispositions remain unchanged.
