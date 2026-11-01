# Evidence179 release controls — signing correction round 2

1 November 2026. **Specification verdict: PASS for the scoped correction and retry bindings. Security/implementation-quality verdict: PASS for this scope. New findings: 0 Critical, 0 Important, 0 Minor.** The correction is accepted for one retry of the existing production acceptance workflow. Actual corrected instrumentation signing and the complete original50→51 upgrade/restart remain unexecuted and mandatory before delivery. No application change or renewed whole-source approval is implied.

## Actual failure and completed gates

The coordinator identifies first production run `41298260070`, job `124823621070`, release controls `d2bb6153238c392e72426e981d4a275eed34d8dc`. Independently inspected the supplied original failure ZIP and all 35 extracted files at `/workspace/scratch/4181ccd09b2e/production179-failure`. The ZIP is exactly 76,474 bytes with SHA-256 `b5e1fedd2de5041b54ffcf59bccc12ba21481d4292e908d77d7aa0dcc8e38719`, artifact identity `13080218184` supplied by the coordinator. Entries have unique safe paths and every extracted file matches its ZIP bytes. No new API/CI query was made.

The first production attempt **did execute and pass the previously pending pre-secret namespace/frozen-validator replay**. Its archived `packet-replay.json` records the separate offline decoder namespace, accepted helper hash, truthful original interface witness and identical original/replayed full packet-rendering hashes. Independently checked its PCAP/rendering hashes against the immutable focused artifact. The archived original proof is byte-identical to that artifact's proof; the independently replayed proof matches `focused-run.json`'s digest and passes the unchanged canonical claim/raw-hash comparison. The canonical raw hash-map digest remains `6e7a94d242d250dcf4773cde7c4582a32161d7754e0377b505a6dce26bcebeb0`. These are actual archived production receipts, not the earlier local normalized-only decode. The retry must still execute the same gate; this result does not authorize bypassing it.

The archive also contains actual baseline-compatible fixture `COMPILED` evidence for source19e, matching reviewed fixture/compiler identities. Original50 and candidate application signature receipts show v2true, one original signer, and the native alignment receipt ends in successful verification. These passed preparatory gates do not establish application upgrade execution.

The decisive instrumentation receipt, `from50/upgrade-instrumentation-signature.txt`, hashes to `47c847f717e50523d9e2089a15ec3d0c381b2a9d5b4d6fe29f992c2f9dfa6eb8`: **v2false, v3true, one signer, original certificate** `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`. The runner's unchanged v2true check follows signing verification and precedes either application install. The instrumentation-hash continuation receipt, seed, verify, restart and final upgrade-result receipts are absent. This was a fail-closed instrumentation-signature admission failure **before baseline/candidate installation and seed**, not an executed upgrade failure or a newly established application defect. No upgrade/restart success is claimed.

## Exact correction and regression assessment

Inspected the complete runner and added `InstrumentationSigning179` tests, plus the round2 handback in `work/task-7b-release-report.md`. Removing the single added ` --v2-signing-enabled true` byte sequence from the current runner exactly recovers archived runner SHA-256 `a7b4033f896ec90f7b09368794fc432fe7725ad80dbd559bb25f58b1e4b63515`. This establishes the complete production delta without Git operations: one explicit scheme-selection option in the existing apksigner command for the disposable instrumentation copy.

The command retains the same original keystore path, alias and password environment references. It does not create a key, modify the immutable compiled instrumentation, change the application signer or disable v3. Subsequent actual apksigner verification, exact original-certificate, mandatory v2true and one-signer gates remain byte-identical. Source/fixture/compiled-byte validation, disposable-copy cleanup, package/API checks, initial package absence, baseline install/seed, candidate `install -r`, verify and force-stop/restart verify are unchanged. No uninstall, clear-data operation, fallback or weakened verification was introduced.

The focused tests execute the actual extracted production signing/verification block with a temporary mocked external apksigner. The positive case asserts the complete emitted argv, including explicit v2true and unchanged original-key/environment-reference arguments. Five negative subcases exercise v2false, foreign certificate, multiple signers, signer failure and verifier failure, all requiring rejection before the continuation marker. No test creates a keystore/APK or supplies a password. This is useful command-contract and failure-propagation coverage, accurately distinguished from real cryptographic signing.

The worker records a meaningful RED result from the missing option, then both focused methods passing after the one-line correction, plus `bash -n` success and actual local apksigner help confirming the scheme-selection option. I inspected that evidence and the test implementation; I did not rerun those unchanged passing checks or invoke any signer. Real original-key signing with this option remains a retry gate.

## Approval binding and staged retry

Compared current `FINAL_SCOPE_APPROVAL.json` structurally with the copy archived by the first production run. It differs **only** in `controls["scripts/verify_synthetic_upgrade179.sh"]`. All other 12 control hashes remain unchanged, and all 13 now match current bytes. Source `19e3275ef17746e780d64e6a684d94bab2cabdc3`, 153-case manifest, successful focused run/artifact, eight original evidence hashes, original50/certificate/package/version constraints and the canonical report hash `4ea5a1368fe4b3f7e6328b5471a1a85f4947c014f42437fdcc67cc04b02ea4c4` remain unchanged. The prior source approval and actual 153-case evidence therefore remain applicable.

The retry marker is exactly:

```text
19e3275ef17746e780d64e6a684d94bab2cabdc3
retry: explicit-v2-test-signature; previous-run:41298260070
```

Its required first line still binds the same frozen source; its second line records the retry cause/run and makes the existing marker-change trigger eligible. The workflow itself is unchanged from the accepted activation hash. Root's stated staging—commit script/test/report/approval/review with `[skip ci]` semantics, followed by a marker-only production-trigger commit—keeps implementation changes separate from the trigger. The existing fast-development path filter does not match a marker-only push. No new workflow, fixture or Android-suite rerun is required by this narrow correction. This is an inspection of the intended staging and existing configuration, not a claim that commits or the retry have occurred.

The retry must retain all pre-secret replay/approval/baseline/compilation gates, original-key application/instrumentation checks and actual original50→51 install-r/verify/restart. Final upgrade receipt/APK digest validation, single-APK curation and independent final binary acceptance remain mandatory before publication; physical Fold acceptance remains `OWNER_REQUIRED`. A later failure requires its own concrete diagnosis, not removal of a gate.

## Reviewed hashes and limits

| Input | SHA-256 |
| --- | --- |
| `scripts/verify_synthetic_upgrade179.sh` | `233246421b783cb3901dde3efb96a16ee84f5d71beaa0e8891f3e8c34fc64f9f` |
| `scripts/tests/test_upgrade179_runner.py` | `8229ec344d1f1442e28896978dbd18695ef993aa8841f914b0b91aca584bbbd4` |
| `docs/release179/work/task-7b-release-report.md` | `762bde628747182999c3c131ce05bb3029d756c342a5413bd5813f09a6142807` |
| `docs/release179/FINAL_SCOPE_APPROVAL.json` | `5998f77a7061b4a0936ee0ba348b9af4e31defc88b5c255d0f5b118da468a27e` |
| Retry `.acceptance179-candidate` | `562c4b97ab04b17c803d4e18a43590033b2d92ed4ab6bd1389e40f87f835319e` |

Only this review report was written. No source edits, Git operations, CI invocation, signing/secret access, tests, device operations or subagents were used. The reviewer performed read-only byte/binding/proof checks; it did not run the upgrade script or change original evidence. Historical pending-stage language in earlier reports describes those checkpoints; this report records the actual first-run replay pass while preserving all still-unexecuted upgrade gates.
