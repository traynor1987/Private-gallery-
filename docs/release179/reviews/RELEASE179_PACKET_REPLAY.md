# Evidence179 packet replay portability — scoped independent review

1 November 2026. **Specification verdict: PASS for the scoped source correction. Security/implementation-quality verdict: PASS within this scope. New findings: 0 Critical, 0 Important, 0 Minor.** Actual privileged namespace creation and complete frozen-CLI replay remain unexecuted locally. This is not final runtime, consolidated security, activation, signing or release approval.

## Scope and identity

This continues the same consolidated review; it does not reopen unchanged application source or the accepted SEC179-1/SEC179-2 correction wave. Read `work/packet-replay179-report.md`, the new helper/test module and the three changed existing control modules. Compared those three modules against `/tmp/replay179-before/`. Inspected the unchanged frozen route CLI, its packet/raw-hash checks and the existing pre-secret workflow invocation as direct interfaces only.

The existing-module differences are exactly: helper imports and separate replay-tree invocation with original-hash verification in `revalidate_runtime179.py`; one helper entry in `verify_final_scope179.py`'s `CONTROL_PATHS`; and one public receipt entry in `curate_release179.py`. The other three supplied paths are the new helper, its focused tests and implementation report. No application, instrumentation, manifest, version, workflow, route-validator, signing or activation modification is part of this correction. The observed workflow remains inactive with zero `FROZEN_SOURCE`; `.acceptance179-candidate` is absent.

Candidate source `19e3275ef17746e780d64e6a684d94bab2cabdc3`, run `41295673256` / job `124815899187`, remains in progress according to the coordinator at this checkpoint. No successful final artifact was supplied or inferred. The partial f76 evidence, including its 150 passing identities, cannot satisfy the three missing route cases or final raw-route gates.

## Boundary review

| Boundary | Finding |
|---|---|
| Original evidence admission | The unchanged wrapper first checks the downloaded archive digest, strict extraction paths/quotas, source, exact XML and bound final/static receipts. New `verify_original_hashes` additionally verifies every original raw/source binding against the original extraction or frozen checkout before privilege is requested, and explicitly requires both PCAP and full original `packets.txt`. It rejects unsafe/duplicate canonical bindings and hash changes. |
| Truthful interface witness | `interface_witness` parses the whole classic microsecond SLL2 capture, requires complete bounded records, supported format, valid indices/directions, and pairs every raw record with the original rendering in order. Exact timestamp/direction/count checks precede a consistent one-to-one index/name map. Unknown `?`, unsafe names, contradictory mappings and incorrect loopback identity fail. No address, expected-route predicate or assumed `pg1762` supplies a name. This is an original capture-time name witness, not a new independent observation of a live tunnel. |
| Independent packet decoding | Actual `tcpdump -tttt -nn -vv -X -r` freshly decodes the unchanged PCAP. Preliminary normalization removes only the first-line interface-name token and its defined padding; timestamp, direction/padding, decoded description, hex output and all continuation lines remain compared. Record counts and malformed/unrecognized output fail closed. The original text is never returned as a substitute decoder result. |
| Isolated lookup restoration | The privileged command is a fixed argv list, not shell interpolation. It invokes `sudo unshare --net --` with the same Python executable and preserved helper. The child verifies the witness hash/source and a different network-namespace identity, requires the initial interface inventory to be exactly loopback, recreates only witnessed non-loopback indices/names using dummy links, then checks the exact complete interface inventory and absence of addresses or UP flags. These down, unaddressed links restore decoder lookup only. |
| Exact final rendering | Inside the namespace, another actual decode must equal the original rendering byte for byte and match the independently normalized digest. The unchanged frozen route CLI then executes with its original arguments and current frozen checkout. It must independently decode/validate the same PCAP again. The helper rechecks the PCAP hash and original packet-rendering bytes after that CLI returns. No frozen check, PCAP index or packet text is patched. |
| Original/replayed proof separation | CLI output goes into a copied `replay/evidence179` tree. The original extraction is not used as a write target. Existing `route_comparison` still requires equivalent claims and identical canonical raw/source hashes, including the original packet-rendering hash. The wrapper verifies all original hashes again and checks original proof bytes after replay. `focused-run.json` is marked PASS only after those checks. |
| Preserved controls and receipts | Adding the helper to `CONTROL_PATHS` causes the existing workflow to preserve and review-hash it before checking out frozen application source. The wrapper imports that preserved sibling. The new public receipt records helper/tcpdump binary/version, PCAP/rendering/normalized hashes and witnessed mapping, and explicitly labels the dummy fixtures as lookup aids rather than routing proof. The curation delta adds only that receipt to the existing bounded public allowlist. Existing proof comparisons remain authoritative; the receipt alone cannot approve runtime evidence. |
| Signing boundary | Existing production workflow calls the corrected wrapper in its original independent-revalidation step before baseline/signer access. Any namespace, link, fresh decode, frozen-CLI or comparison failure propagates nonzero and stops there. No new workflow, network fallback, secret access or signing authorization is introduced. |

No concrete Critical/Important defect or weakened security assertion was established in these changes. The exact original rendering is deliberately required: an unsupported tcpdump format/version difference, changing original interface identity or unresolvable witness must fail rather than be silently normalized further.

## Verification and remaining limits

Independent read-only comparison reproduced the three existing-module diffs described above. All five changed Python modules parsed with `ast.parse`; independently calculated hashes match the handback. These are source/syntax checks, not runtime tests.

The implementation report records 18 focused methods passing and actual extracted tcpdump loopback decoding plus parsing of all 1,220 records in the partial f76 capture. I inspected the eight new test methods and the existing test interfaces without rerunning these passing checks. The tests cover witnessed identities/order/direction, malformed and missing records, changed non-interface decode text, original raw/rendering hashes and unsafe paths, rejection before privileged launch on decode disagreement, and changed-witness/same-namespace rejection. They do not execute the privileged success path or the complete real frozen CLI. The report's actual binary/probe outcomes are worker evidence, not new reviewer executions.

Local namespace/netlink execution is reported denied with EPERM. Therefore explicit-index dummy-link creation, fresh namespace inventory, installed tcpdump behavior, preserved Python environment and complete unchanged validator replay must execute successfully on the intended hosted runner in the existing pre-secret step. Source acceptance here cannot replace that execution. The full original same-source Android/route proof must first pass and be independently identified; no mapping may be invented from the current partial capture.

The review used no test reruns, Android execution, CI, Git operations, source changes, subagents, secrets or signing. Only this review report was written. The canonical `SECURITY_REVIEW.md` and any final APPROVED machine-readable decision remain withheld pending actual mandatory evidence and the continuing consolidated review.

## Reviewed hashes

| Path | SHA-256 |
|---|---|
| `scripts/replay_evidence179_packets.py` | `de37d0600025775e586fac01bbeec769fc56648ffee13edda9a0a8f4fb4e43cd` |
| `scripts/revalidate_runtime179.py` | `320633970ab6b00ea5a1bfbefda30554aad5f78f37696c432165fb2438847b7e` |
| `scripts/verify_final_scope179.py` | `b21237fd95c6cfcd6cb35bde89f1fd4d5ea44f8eccd301987b6a60f68ee1fa6e` |
| `scripts/curate_release179.py` | `a576cb52151b631adc5a58ddef0db2593a23272e28f2970f7e3ba5027a689fe3` |
| `scripts/tests/test_evidence179_packet_replay.py` | `0429efaad3b35de86c1cc894da32a476b86033fb39e6687c88cb4013986472b4` |
| `docs/release179/work/packet-replay179-report.md` | `3f8fdb6897b3da514467086550137619974599a80df548c7a14bf05088be974e` |
| Inspected frozen `scripts/verify_evidence179_routes.py` | `5d1f1c83c24d3376bc1c55d6f54f3eed57bd66d6ac4b7a813c52920702e9a0a3` |
| Inspected `.github/workflows/acceptance1762.yml` | `7a196496917cf0edb53b6e2c89379ad2e3688ca3ba602360c9b7bef9ccfb658f` |
