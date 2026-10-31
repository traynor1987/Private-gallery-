# Evidence179 runtime correction batch 3

2026-10-31. Authoritative docs backupcb54baca0a142321ce71c0d361b272272b673386; original product/test source ddc0e84cd12440618482bac9c1ce1d8897591fe7. Read `work/runtime179-fix3-brief.md` first, both original XML failures in `/workspace/scratch/4181ccd09b2e/diagnostic179-ddc0/failures.json`, their full per-case exception/lifecycle logs and the affected source. **Only two test files and this report changed. No production edits or corrected-source runtime pass are claimed.**

Original run41248625435/job124677238601 completed149 normal nonroute cases:147PASS/2FAIL/0skip/0duplicate; four owned-route cases were not reached. Verified original artifact13061105467:2840991 bytes, SHA256 `16cafb335371bfbba1c9fbfa72e11171a70bb08a6488fed103133d0fcd9fcebe`. All prior selection-touch,28 archive and held encrypted/plaintext/native retirement cases passed on that original source. Those are retained supporting results, not final153-case acceptance.

## Primary workflow: uncomposed search header

The exact failure at `EvidenceLocker179UiTest.kt:70` is an absent `evidence-search` node during text input, after actual registration, selection and incident assignment succeeded. The search field is in the Library LazyColumn's header item; assignment does not change page or remove the field, while the previous selection/assignment scroll can leave the header uncomposed. A direct tag query cannot materialize that lazy item.

The existing Primary workflow now uses `evidenceNode(hasTestTag("evidence-search") and hasSetTextAction())` before both typing and clearing. It uses the same helper with exact actionable alpha-name text for the positive filtered result. The negative beta-result assertion remains unchanged. The remainder of this workflow was audited for the same already-observed lazy/control-selector class: reference removal and its final Confirm now use exact text plus `hasClickAction()` through the existing helper before the original injected touches. Earlier successful steps and unrelated workflows were not rewritten.

Search/correction/filter/clear, reference-only removal, one remaining catalogue record, two preserved vault originals, original bytes and reopened encrypted catalogue/incident assertions are retained. No semantic click action, retry, selector broadening, product search change or assertion relaxation was introduced.

## Hidden reauthentication: observer completion is distinct from ActivityScenario stage

The original log reports ActivityScenario RESUMED at22:47:11.191. The test then fails its immediate positive dispatch flag; its failure slot was null when read. The log does not record this custom lifecycle observer's entry/completion, so it cannot establish whether that observer was delayed, still running or never invoked. ActivityScenario stage publication is not a completion handshake for the custom observer. The source previously inspected failure/dispatch immediately after the stage call, permitting a read-before-completion result.

The existing same-Main-turn resume hook now sets a separate atomic completion flag in `finally`, after either storing its failure or finishing dispatch. After the Activity transition, the test uses a bounded15-second `compose.waitUntil` on that completion flag before mandatory failure propagation and the unchanged positive-dispatch assertion. A hook that fails still throws; a hook that never completes times out and cannot pass. The existing subsequent-dismissal wait remains15 seconds. No arbitrary sleep, manual hook invocation or owner retirement was added.

Fixed phase labels distinguish installed, entered, expired-auth verified, dispatching, dispatched and failed. A timeout records captured Activity lifecycle state, atomic foreground/reconciliation counters, pending-hook state, access/dismissal counts and provider-read count. This diagnostic does not query UI semantics or acquire authentication/store resources, and contains no evidence text or credentials. It is evidence for a future failure, not a fabricated explanation of the missing original observer timing.

Every R179-F2-1 precondition remains inside the same actual Main-thread resume hook: real ON_START reconciliation, exact resumed state, expired original authentication/key, genuinely new authentication, old controller retired, original request identity and one launch, one access/zero reads, read-only original-owner `closed == false`, and zero dismissals immediately before stale-result dispatch. Completion waiting does not move those checks outside the hook. After dispatch, the test still requires actual subsequent dismissal, zero IO, one access, no stale-controller revival, retired original owner and a still-unlocked distinct new authentication.

## Scope, commands and observed results

Preimages are `/tmp/runtime179-fix3-before/EvidenceLocker179UiTest.kt` and `/tmp/runtime179-fix3-before/EvidenceSaf179Test.kt`.

- Read-only extraction of the two original full per-case logs/exception blocks confirmed the absent editable tag and premature positive-flag failure, including actual STOPPED/STARTED/RESUMED ordering. No result file was modified or synthesized.
- Read-only Python comparison against those preimages exited0: seven Locker and31 SAF `@Test` declarations/names/order unchanged; no trailing whitespace. The complete narrow diffs were inspected.
- Read-only source audit exited0: no direct unmaterialized search tag/Confirm selector remains in the affected Primary remainder; search/original assertions and injected touches remain. The hook's `finally` completion precedes bounded waiting, failure propagation and positive dispatch checking. The original same-turn live-owner/authentication/one-access/zero-IO checks remain, with no proactive `owner.close` or `owner.sameAuthentication` call in that hook.
- Static manifest SHA256 remains `f2a622f5fa919e29d5465d0e8bac1dacdd1a4bbb504371b939658f3a671ee2f6` (153 entries unchanged). No new or disabled test was added.

No Android compiler/device is available locally; no Gradle bootstrap retry, passing147-case/host-suite rerun, Git, CI, signing, secrets or subagent operation occurred. The read-only checks are source verification, not Kotlin compilation or Android green evidence.

## Freeze identities

| Source | SHA256 |
|---|---|
| `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceLocker179UiTest.kt` | `bf241183ad13748da591009b29924e7eae9762b217070d23e1c9ff219565a54f` |
| `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceSaf179Test.kt` | `b61f10d36c6d143d4e9a706d3282a0764dcf85a7fb8db8e6b668be55e0585535` |

## Remaining evidence

Fresh scoped review and actual grouped main/test compilation plus all153 mandatory unskipped cases remain required. The Primary workflow must execute through search, clear and removal on device. The Hidden hook must actually complete; any failure must preserve its fixed diagnostics, and a future pass must retain the live-pending same-turn proof. The old log does not prove that the custom hook eventually executed, and this report does not claim it did.

All production code, helper behavior, selection touch layout, held provider/native lifetime tests, strict source/authentication/EOF/thread/lineage/snapshot/no-fallback protections and prior accepted fixes are untouched. Root owns independent route-attempt scheduling while retaining any normal nonroute failure status; this batch does not reduce scope or authorize release. Exact final source/APK/test accounting, all four owned-route cases, consolidated review, affected-feature regressions, original-certificate/code50→51 upgrade/signing/distribution and physical Fold acceptance remain open gates.
