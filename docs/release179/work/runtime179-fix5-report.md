# Runtime179 fix5: Hidden selection setup and Android ICU literal brace

Scope: `EvidenceLocker179UiTest.kt`, the single regex literal in `EvidenceRouteClockLifecycle179.kt`, this report, and the bounded native ICU probe `work/probes/runtime179_fix5_icu.py`. Both source preimages saved before editing in `/tmp/runtime179-fix5-before/`. No product/shared-helper/protocol/runner/validator/manifest/workflow/version changes, Git, CI, Gradle, signing, secrets or subagents.

## Original evidence and established boundaries

Read `runtime179-fix5-brief.md`, original `diagnostic179-d006/failures.json`, current shared helper/layout and both affected source files, original case logcat, and original focused XML. Run 41258646179 at source d006ba9f2ce635249819b0fb9278ec221c250ef3 completed 149 exact unique nonroute cases: 148 PASS, one Hidden UI FAIL, zero skipped. The first route case failed in the mandatory clock lifecycle prelude; the other three route cases were not reached. Original artifacts are unchanged.

UI: the new failure is original line 203, inside `evidenceNode(selectAlpha).assertIsOn()`, specifically the shared helper's `performScrollTo()` at line 14. The helper's earlier existence/ancestor observations no longer resolve the checkbox when it scrolls the child. This happens before fix4's later settling and explicit filename materialization, so that synchronization cannot protect this earlier added check. The actual case log records the 840x900 window and IME bottom insets 328 at 01:04:28.039, 411 at .763, and 415 at .901; it establishes a changing viewport after editing search. There is no semantics/bounds snapshot proving which layout transition removed the node. IME/lazy recomposition is a supported hypothesis, not a proven production defect or sole trigger.

Clock: Android's original `PatternSyntaxException` names the lifecycle parser's exact regex and offset 34. It escapes the opening literal brace but leaves the closing brace bare. Audit of the introduced clock sources finds this one `Broadcasting: Intent` pattern. No other clock framing/parser/protocol change is needed.

## Corrections

The Hidden workflow now closes the actual keyboard with Espresso's supported `closeSoftKeyboard` action on the explicit dialog root. It captures that root and waits for its actual IME inset visibility to become false, then asserts the Alice query remains. This normalizes the viewport without clearing search or navigating back.

Selection/open preparation uses the known `evidence-list` LazyColumn directly instead of `evidenceNode` for these targets. After settling Compose it materializes the checkbox, waits for one displayed match, and taps once. After selection recomposition it rematerializes via the known list, waits for the actual On and displayed checkbox, and asserts that state directly without the helper's conditional child-scroll step. The existing exact actionable filename is then materialized, condition-waited, and tapped once. No injected gesture is caught/retried and no semantics-click/direct callback/sleep bypass is used. The entire suffix beginning with unsaved correction entry remains byte-identical: live Hidden lock, retirement/catalogue/UI cleanup, unsaved-text absence, real new Hidden authentication, one encrypted record with zero corrections, two originals and cleanup. All seven UI test names and all 153 manifest identities remain unchanged.

The clock change is only `[^\\r\\n]*}` to `[^\\r\\n]*\\}` in the Kotlin literal. Literal closing-brace semantics become valid for ICU; full-frame matching, result-code alternatives, data alphabet/captures, status marker, blank lines, nonce/source/privacy, Rwe handling, six positive samples, eight lifecycle negatives and ordering/interval checks are unchanged. Byte comparison against the preimage confirms no other lifecycle file difference.

## Verification

Commands run from `/workspace/scratch/4181ccd09b2e/recovered-gallery`:

- `ldconfig -p | rg 'libicui18n|libicuuc'`: native `libicui18n.so.74` and `libicuuc.so.74` available. No dependency installed.
- `PYTHONDONTWRITEBYTECODE=1 python3 docs/release179/work/probes/runtime179_fix5_icu.py`: exit 0. Output: `Native ICU 74.2.0.0`; `OLD RED: U_REGEX_RULE_SYNTAX status=66305 offset=34`; `FIXED GREEN: 4 complete frames/captures accepted; 10 malformed/status/extra/truncated frames rejected`. The probe derives the fixed regex from the actual Kotlin literal and reconstructs only its former bare closing brace. Through native ICU C APIs it checks whole-frame matching and both captures for result 0 without data, 178/179 with data and CRLF framing; rejects missing braces, status 1, result 180, invalid data alphabet, prefix/suffix, truncated final newline, missing blank line and embedded line breaks. This proves host ICU regex behavior only, not Android parser execution, JSON validation or route correctness.
- `python3 /tmp/runtime179-fix5-before/check-source.py`: exit 0. Output: `PASS source: 7 UI identities unchanged; one selection/open gesture; no postselection one-shot helper; Alice/checked/displayed preconditions; lock/fresh-auth/storage/cleanup tail byte-identical`; `PASS source: clock differs only by literal closing-brace escape; all lifecycle/control/protocol checks unchanged; manifest153unique unchanged`; `Original focused XML:149unique,148PASS/1FAIL/0skip (pre-fix evidence only)`.
- `diff -u /tmp/runtime179-fix5-before/EvidenceLocker179UiTest.kt app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceLocker179UiTest.kt` and corresponding lifecycle preimage/source diff: expected exit 1, only the described workflow preparation and one closing-brace escape differ.
- `sha256sum` on frozen sources/probe/manifest returned the hashes below.

SHA256:

- UI source: `d88a0bc0d33a5dd81da1ed9accfdb405bf8a68b98c55e2baf1846f8d630a5534`
- Lifecycle source: `58caed48b61af8fc33b84eb7bc601017b46e11f65d09f51776d8b3a3495595f8`
- Native ICU probe: `4dc7803c96c112d851d74ebd0918d222560e4a498fb473b13ee3876e838d352f`
- Unchanged 153-case manifest: `f2a622f5fa919e29d5465d0e8bac1dacdd1a4bbb504371b939658f3a671ee2f6`

Limits: Android/Kotlin compilation and execution are unavailable locally and were not attempted. No unchanged 148-case suite, six actual clock samples or eight actual lifecycle controls were rerun locally. UI synchronization still requires actual Android verification; native ICU 74.2 reproduces the syntax defect but is not the Android ICU build. No raw route/security/release approval is claimed. Frozen for fresh scoped review before the next grouped 153-case run.
