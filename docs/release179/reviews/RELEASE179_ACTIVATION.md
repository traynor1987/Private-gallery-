# Evidence179 activation — scoped independent review

1 November 2026. **Specification verdict: PASS for the exact activation scope. Security/implementation-quality verdict: PASS for that scope. Findings: 0 Critical, 0 Important, 0 Minor.** This permits the reviewed activation to enter the existing gated acceptance workflow. It is not a claim that namespace replay, signing, upgrade/restart, final binary review or delivery has executed successfully.

## Exact scope and binding checks

This continues the same consolidated independent review. The canonical `docs/release179/SECURITY_REVIEW.md` remains unchanged, with SHA-256 `4ea5a1368fe4b3f7e6328b5471a1a85f4947c014f42437fdcc67cc04b02ea4c4` and its single APPROVED source/gated-build decision for `19e3275ef17746e780d64e6a684d94bab2cabdc3`. Unchanged application, runtime and release-control implementation reviews are reused; no broad source review or passing test suite was repeated.

Compared the complete workflow bytes with `/tmp/activation179-workflow-before.yml`. The sole difference is `acceptance179.env.FROZEN_SOURCE`, changing its forty zeroes to that exact source. The preimage matches the previously reviewed inactive workflow hash. The new `.acceptance179-candidate` is exactly the source plus one newline. No other workflow behavior changes in this delta.

Independently checked `FINAL_SCOPE_APPROVAL.json` against the canonical report and the actual immutable evidence:

- Its decision/source and exact machine-readable review object agree; the report hash is correct and Critical/Important counts are zero.
- All 26 required scope keys exactly match `verify_final_scope179.REQUIRED` and are PASS. Their meaning remains reviewed source/synthetic runtime scope, with physical acceptance explicitly `OWNER_REQUIRED`.
- Version 1.7.9/code51, package, original certificate `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`, required code50 baseline and source `4820e176dcb60a3aa2f1bfd7b36e2de19fafee11` agree with the accepted contract.
- All 13 `CONTROL_PATHS` entries are present exactly once and match freshly calculated current file hashes, including the activated workflow and accepted replay helper. Manifest, upgrade fixture and validator bindings independently agree with their bytes.
- All eight original evidence bindings agree with the independently verified immutable runtime summary. The repository evidence summary preserves every original summary value and explicitly retains `PENDING_MANDATORY_PRESECRET` for actual namespace replay.
- Saved original API responses bind successful run `41295673256`, source19e, job `124815899187`, all ten required successful runtime/build stages, and artifact `13079244254`. Its digest matches approval and the actual original ZIP: `sha256:ed1582ae487f84d63ec98da7dfec50f78c63d0a2d82883145c718f7b0efabfc7`. This rechecks activation identity, not a new CI query or runtime rerun.

## Gate ordering and unchanged protections

The marker-change detector, repository/event/branch conditions and non-cancelling production concurrency remain unchanged. For a push containing only this marker, acceptance workflow and release documentation, the inspected fast-development path filter does not match; the existing acceptance179 path is selected. This is a configuration conclusion, not an observed workflow launch.

The first acceptance steps verify the marker/approval/control hashes and preserve those exact controls and review bytes before checking out the frozen application source. API origin/stage/artifact checks and the downloaded ZIP digest precede independent replay. The preserved wrapper invokes the reviewed namespace replay helper, then the unchanged frozen route CLI and canonical proof/hash comparison. `verify_final_scope179.py` follows that actual replay; a merely prewritten summary cannot satisfy it. The original extraction and separate replay-tree protection remain as reviewed in `RELEASE179_PACKET_REPLAY.md`.

The replay/scope step, immutable archived code50 download/extraction, original API/package/v2/certificate verification and baseline-compatible upgrade-fixture compilation all precede the first signing-secret environment. These steps retain normal failure propagation; there is no new continue-on-error, success fabrication, bypass or alternate decoder path. **Actual hosted-runner namespace creation, exact interface lookup restoration and full frozen-CLI replay remain mandatory and unexecuted at this review.** Their failure must stop before signing access.

The unchanged build step verifies exact source and the resulting production package/signature/distribution/alignment. Actual original-code50 seed, candidate `install -r`, verification and force-stop/restart verification remain required through the preserved upgrade script. The later validator binds the upgrade receipt to the exact deliverable APK digest and source. The single-APK staging and successful artifact upload remain success-dependent after those checks. Always-run curation exposes filtered receipts only; the failure upload has only the receipt-directory path. Original signing-material cleanup remains unconditional. There is no authorization to uninstall, clear data or replace keys to obtain an upgrade pass.

Final binary/receipt acceptance before publication and owner physical Fold acceptance remain mandatory under the canonical review. The source approval's explicit authorization-scope text preserves these staged limits; none is waived by the activation.

## Reviewed byte identities

| Input | SHA-256 |
| --- | --- |
| Workflow preimage | `7a196496917cf0edb53b6e2c89379ad2e3688ca3ba602360c9b7bef9ccfb658f` |
| Activated `.github/workflows/acceptance1762.yml` | `5c8a7b852089945dcce2a28f536b50ee43ec6b539f674876155196550260728f` |
| `.acceptance179-candidate` | `1df33ea4345174cdb43c4a99f952f7bc52fd38b2ec36016de95053bdc5a925ed` |
| `docs/release179/FINAL_SCOPE_APPROVAL.json` | `0fa4ecdcc1f05786e137432001b1aee1395afd176214ab1679d4b92e0e7ca009` |
| `docs/release179/evidence/focused179-19e-verification.json` | `636d90a065d0b6ea6a21d08cb47574c30cf9edff05eebff094db74e7087545d7` |

Only this report was written. No source edits, Git operations, CI invocation, signing/secret access, namespace execution, test reruns or subagents were used. The full runtime validator was not supplied invented future PASS fields or executed against the immutable original extraction. Any later change to reviewed control bytes requires new matching review/bindings; additional historical documentation does not itself alter `CONTROL_PATHS`.
