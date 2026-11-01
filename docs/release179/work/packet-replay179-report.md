# Inactive release-control packet replay portability correction

1 November 2026. Source handback for independent scoped review, **not release/security/upgrade/signing approval**. Candidate application and Android source remain the coordinator's frozen `19e3275ef17746e780d64e6a684d94bab2cabdc3`; no application, instrumentation, workflow, fixture, route validator, version, manifest, signing or activation change was made. No Git, CI, signing, secret access, subagent, install/uninstall or data-clear operation was performed. The correction is inactive: `FROZEN_SOURCE` is zero and `.acceptance179-candidate` is absent.

## Demonstrated defect

The upstream tcpdump4.99.4 `print-sll.c` SLL2 printer calls `if_indextoname` on the replay machine, including with `-nn`. Classic SLL2 PCAP records hold interface indices, not the original interface names. The frozen validator requires literal `pg1762` and binds its original complete `packets.txt` hash; a separate signing runner cannot assume the same index/name lookup. Actual locally extracted Ubuntu tcpdump4.99.4/libpcap1.10.4 decoded the partial f76 capture as `?` for interfaces absent here, confirming the portability issue. That partial capture has no final proof and does not authorize signing.

## Small correction and preserved boundaries

- Added preserved-control helper `scripts/replay_evidence179_packets.py`. Before any privileged replay, the wrapper verifies every original proof raw/source hash against the immutable extraction/frozen checkout, requiring original PCAP and complete rendered packet text bindings.
- Strict classic microsecond SLL2 parser pairs **every** capture record with the hash-bound original rendering in order, checking exact timestamp/direction/count. Index/name associations must be one-to-one and consistent; unknown/unsafe names, incorrect loopback, malformed/truncated records, unsupported formats, missing/reordered/unexplained text fail closed. Names are never inferred from addresses or expected routing.
- Independently decode the unchanged PCAP with actual tcpdump. Preliminary comparison normalizes only the interface-name presentation token/padding, retaining every timestamp, direction, packet description, sequence, hex payload and continuation line.
- A fresh, isolated `sudo unshare --net` namespace must initially contain only loopback. Recreate only witnessed interface index/name associations as **down, unaddressed dummy decoder fixtures**. These provide name lookup only and are explicitly not a tunnel or new routing evidence. Require exact fresh rendering bytes equal the hash-bound original before running the **unchanged frozen actual route CLI**. Recheck raw capture/rendered bytes afterward.
- Original archive extraction remains untouched. Frozen CLI writes into a separate copied replay tree; existing canonical raw/source hash and claim comparison remains mandatory. Recheck immutable original proof/source hashes afterward.
- Add helper to `CONTROL_PATHS` so the reviewed helper survives frozen-source checkout and is exact-hash reviewed. Add only public `packet-replay.json` to existing curated receipt allowlist. Receipt binds original and regenerated packet hashes, normalized decode, interface witness, helper and actual tcpdump binary/version, and expressly labels dummy interface purpose.
- Existing production workflow, source freeze/approval, actual Android153-case route/DNS/EOF assertions, exact original certificate, upgrade, and post-sign delivery gates are unchanged. No archived text substitution, PCAP rewrite, assumed `pg1762`, validator monkeypatch, direct-network fallback or weakened assertion.

## Verification

Initial six new methods, before helper implementation:

```
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.tests.test_evidence179_packet_replay -q
Ran 6 tests — FAILED (failures=6): Missing original-witness-bound packet replay guard
```

After implementation and two boundary-negative methods:

```
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.tests.test_evidence179_packet_replay scripts.tests.test_release179_runtime_controls scripts.tests.test_final_scope179 -q
Ran 18 tests in 0.021s — OK
```

Coverage: witnessed order/direction/name mapping, unknown/conflicting/unsafe identities, malformed/truncated/wrong-linktype PCAP, missing/reordered records, non-interface decode mutations, raw/rendering/source-hash tampering and unsafe paths, changed independent decode rejected before privileged launch, changed witness/same-namespace rejection. Existing runtime/scope admission and curation checks remain green. AST parse of five changed Python modules passes. No broad suite or CI rerun.

Actual host binary check used `/workspace/scratch/4181ccd09b2e/tcpdump179-host/usr/bin/tcpdump` with sibling `LD_LIBRARY_PATH`, `-Z root -tttt -nn -vv -X -r` and `TZ=UTC`. `-Z root` is **local diagnostic only** because this extracted binary has no system `tcpdump` account; production helper/unchanged CLI retain installed tcpdump defaults. Actual synthetic SLL2 loopback decode/witness accepted `{'1':'lo'}`; the actual partial f76 PCAP decoded and parsed all1220 records; its current-host unknown `?` name was rejected as an original witness. Original f76 file bytes were not modified.

## Actual limitations / next mandatory evidence

Local `unshare -n` and netlink are denied (`Operation not permitted`), so namespace creation/dummy-index setup/whole frozen CLI replay **cannot be claimed executed here**. The installed Ubuntu hosted release runner requires existing `sudo`, `unshare` (util-linux), `ip` (iproute2), dummy-link support, tcpdump and the already pinned Python cryptography environment. No new package or workflow was added. Exact namespace path must execute in the existing **pre-secret** release-control step; any namespace/decoder/proof mismatch fails before original signer access. Original passing same-source full runtime proof remains a prerequisite. Independent scoped review is required before committing/activating this correction. It does not require another Android suite because no application/test/frozen validator source changed.

## Frozen handback hashes

| Path | SHA-256 |
|---|---|
| `scripts/replay_evidence179_packets.py` | `de37d0600025775e586fac01bbeec769fc56648ffee13edda9a0a8f4fb4e43cd` |
| `scripts/revalidate_runtime179.py` | `320633970ab6b00ea5a1bfbefda30554aad5f78f37696c432165fb2438847b7e` |
| `scripts/verify_final_scope179.py` | `b21237fd95c6cfcd6cb35bde89f1fd4d5ea44f8eccd301987b6a60f68ee1fa6e` |
| `scripts/curate_release179.py` | `a576cb52151b631adc5a58ddef0db2593a23272e28f2970f7e3ba5027a689fe3` |
| `scripts/tests/test_evidence179_packet_replay.py` | `0429efaad3b35de86c1cc894da32a476b86033fb39e6687c88cb4013986472b4` |

Preimages of existing three control modules are in `/tmp/replay179-before/`. Worker frozen for review.
