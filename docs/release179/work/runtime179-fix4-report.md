# Runtime179 fix4: Hidden selection-to-open synchronization

Scope: only `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceLocker179UiTest.kt` and this report. Preimage saved before editing at `/tmp/runtime179-fix4-before/EvidenceLocker179UiTest.kt`. No shared helper, product, route, validator, runner, manifest, version or workflow edits.

## Original evidence and diagnosis

Read `runtime179-fix4-brief.md`, original `diagnostic179-29a2/failures.json`, complete job log around this failure, and the original case logcat under `artifact/evidence179/android-results/focused/test(AVD) - 17/`. Independently parsed all archived Android XML: exact 153 unique reviewed identities, 152 PASS, one FAIL, zero skipped. The sole failure is `hiddenInnerLockClearsSearchSelectionsAndDialogsWithoutDeletingEncryptedEvidence`, original line 202. Both XML and logcat show `performTouchInput` could not fetch the exact `hasText("alpha179.txt") and hasClickAction()` node. The stack is inside physical `performClick`, after `evidenceNode` had returned; it is not a recorded controller/authentication exception.

Source investigation: Library search/header controls and keyed evidence rows share the `evidence-list` LazyColumn. Selection updates header button enablement and the checkbox state. Search was edited immediately before selection; logcat records IME/back callback activity, but contains no semantics/bounds snapshot establishing which layout transition removed the node. `evidenceNode` checks initial existence, conditionally materializes the row, scrolls if applicable, and returns a selector. That initial observation does not itself prove the row remains actionable at the next touch fetch. A dynamic layout/lazy invalidation explains this boundary, but its precise trigger is unproven. No production defect or definitive keyboard cause is claimed.

## Narrow correction

Keep the search for Alice and the same actual selection and filename-opening targets. After the single selection gesture, explicitly assert the checkbox is On, settle Compose, then unconditionally materialize the exact actionable filename through the known Library list. Wait up to 10 seconds for exactly one displayed matching node before injecting the single opening gesture. A timeout reports matching-node count, retirement callback count, busy state and catalogue record count. No injected gesture is caught or retried, no semantics-click bypass is used, and no arbitrary sleep is added.

The same workflow's later `Review / correct` target still uses the existing lazy helper. There is no subsequent selection-to-filename transition in this case requiring the same synchronization. The entire suffix beginning with the unsaved correction entry is byte-identical: live Hidden lock, retired UI/catalogue checks, unsaved-text absence, genuinely fresh Hidden authentication/access, one encrypted evidence record with zero saved corrections, two original sources, and cleanup all remain. All seven test method identities in this file and the 153-case manifest are unchanged. The new checked-state and displayed-target assertions strengthen the preconditions.

## Commands and results

From `/workspace/scratch/4181ccd09b2e/recovered-gallery`:

- `diff -u /tmp/runtime179-fix4-before/EvidenceLocker179UiTest.kt app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceLocker179UiTest.kt`: expected exit 1, only the Hidden workflow's selection/open preparation differs.
- `python3 /tmp/runtime179-fix4-before/check-source.py`: exit 0. Output: `Original XML: 153 exact unique, 152 PASS / 1 FAIL / 0 skipped; lone Hidden workflow failure confirmed` and `Source checks: 7 UI test identities and other methods unchanged; full unsaved-text/lock/fresh-auth/storage/cleanup tail byte-identical; exactly one selection/open gesture and no semantic action bypass`. The one-off script parses original XML against the manifest, compares all method names/unchanged sections to the preimage, checks ordering of selected-state assertion and materialization before the one opening gesture, and rejects semantic action bypass/sleeps.
- `sha256sum app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceLocker179UiTest.kt scripts/evidence179_cases.json /tmp/runtime179-fix4-before/EvidenceLocker179UiTest.kt`: source `1ce730cb65c03444cc1241b25380e596868e0cb665d4a1ddcf6607085fd3f27b`; unchanged manifest `f2a622f5fa919e29d5465d0e8bac1dacdd1a4bbb504371b939658f3a671ee2f6`; preimage `bf241183ad13748da591009b29924e7eae9762b217070d23e1c9ff219565a54f`.

An initial ad hoc check printed “8 UI test identities” as a hardcoded output label despite comparing the actual name lists correctly; the saved check computes and prints the correct count seven. No test was added or removed.

Limitations: these are source/accounting checks, not Kotlin compilation or Android execution. Actual Android is unavailable locally; no passing-152 rerun, CI, raw packet proof, upgrade/signing operation or release approval is claimed. Whether the synchronization resolves the observed node-loss boundary requires the next actual Android run. Frozen for fresh scoped review.
