# Read-only actual-route diagnostic: source29a2a7b0

Inspected original extracted artifacts at `/workspace/scratch/4181ccd09b2e/diagnostic179-29a2/artifact/evidence179`, supplied by root from run41252039247/job124687392783, artifact13062102813. Root supplied ZIP size1803656 and SHA256 `11cd7a5cc68ed31422fe21c1df2c90f7013af85c9efae208bb2f3812814a746e`; this diagnostic did not redownload or recreate the ZIP. Original `source-sha.txt` is `29a2a7b0c3951cd8d4cdf77fe58be06ebfb235b9`.

**This is diagnostic evidence, not route/release approval.** Original XML has153 cases:152 without failure/error/skip and one failure, `EvidenceLocker179UiTest#hiddenInnerLockClearsSearchSelectionsAndDialogsWithoutDeletingEncryptedEvidence`. All four actual route cases have successful XML outcomes, and their original/collection/peer statuses are0. The full runner correctly remains blocked by the unrelated failed case. No XML or receipt was altered, no validator was patched/monkeypatched, no strict gate was bypassed or run to manufacture approval, and no `route-proof.json` was produced. Prior `ROUTES179_FIX1.md` is unchanged.

## Three concrete blockers exposed behind the existing failed-XML gate

### 1. All four Android receipt classnames identify the coroutine receiver

All four original top-level `evidence179-route-*.json` files contain:

```
"classname": "kotlinx.coroutines.SupervisorCoroutine"
```

Their copies under `android-output/<case>/...` are byte-identical. XML correctly names `uk.co.traynor.privategallery.core.evidence.EvidenceOwnedRoute179Test`. The source expression in `EvidenceOwnedRoute179Test.kt:97` is `javaClass.name` inside `supervisorScope`, so it resolves to the coroutine receiver. `verify_evidence179_routes.py:93` correctly rejects this as a case identity mismatch.

Affected implementation path: `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceOwnedRoute179Test.kt`. A future narrow correction should explicitly name the enclosing test class (`EvidenceOwnedRoute179Test::class.java.name` or a qualified enclosing receiver), while retaining exact validator identity equality. Original receipts must remain untouched and cannot be promoted by a rewrite. Add a regression against coroutine-receiver identity leakage, not acceptance of the wrong classname.

### 2. The real numeric firewall rendering uses protocol0, not the text `all`

Original `vpn/input-firewall.txt` contains:

```
0  0 REJECT  0  --  !pg1762 *  0.0.0.0/0  203.0.113.10  reject-with icmp-port-unreachable
```

The preserved command is `iptables -L INPUT -nv`; numeric protocol0 is its rendering of an unspecified/all-protocol rule. The validator's literal `REJECT\s+all...` regex does not match the actual output. This is a concrete presentation/parser mismatch, not evidence that the intended `!pg1762` rejection rule was absent. No weaker interface/origin rule should be accepted.

Affected paths for a future narrow correction: `scripts/verify_evidence179_routes.py` and its focused host tests; optionally the archive's firewall observation command if a stable machine-readable representation is chosen. A strict parser should recognize exactly the intended all-protocol representation (`all` or numeric `0`, or canonical `iptables -S` with protocol omitted), retain REJECT, negated exact `pg1762`, wildcard output, exact source and exact destination, and reject TCP-only/UDP-only protocols6/17, missing interface negation, another interface, another origin, ACCEPT, missing/malformed observations and ambiguous extra rules. The raw observation must remain preserved.

### 3. Two actual Tor EOF observations are inconclusive under the existing strict causal upper bound

The current formula is unchanged:

```
upper = host_arm.monotonic + (device_action_ns - device_arm_send_ns) / 1e9 + 0.005
require(host_close.monotonic >= upper)
```

Original receipts produce these diagnostic calculations:

| Case/phase | Arm round trip ms | Actual worker failure after action ms | Host EOF minus required upper bound ms |
|---|---:|---:|---:|
| vpn_revoke / cancel |18.315816|1.354904|+105.129583|
| vpn_revoke / revoke |16.755859|1.803177|+107.875472|
| tor_revoke / cancel |1143.491458|1.844918|**−528.126990**|
| tor_revoke / revoke |1116.460018|3.546499|+19010.138409|
| tor_death / death |1788.301618|12.894539|**−602.854080**|
| vpn_loss / loss |21.534511|3.947002|Unavailable by the explicit severed-path contract|

All five required host closes are recorded as `peer_eof`, errno0, evidence `tls_eof`; the corresponding arm sequence/token fields match, and actual worker failure timestamps follow invocation. Nevertheless the two negative margins **must fail the existing strict gate**. This diagnostic does not claim that either EOF physically preceded the action. It establishes that the Tor observation round-trip interval is too wide to independently establish the required ordering in those two samples. No route fallback is established by that uncertainty either.

Relevant paths: `EvidenceOwnedRoute179Test.kt` observation/action instrumentation; `scripts/evidence179_fixture_endpoints.py` arm/EOF observations; `scripts/verify_evidence179_routes.py:73–87`; `scripts/run_evidence179.sh` if an independent clock-observation channel is introduced; focused endpoint/route-evidence tests. The bound must not be lowered, replaced with a midpoint, or bypassed for these receipts.

## Smallest honest correction proposal for the timing uncertainty

**Preferred direction: tighten the clock interval with additional independent low-latency observations; retain the strict after-action assertion and the original arm/EOF data.** The current Tor arm exchange remains necessary to establish unique admission and local liveness. It is a poor clock-calibration channel because its round trip is1.1–1.8seconds. A host↔Android ADB clock observation can provide a much narrower interval without altering the POST route, endpoint, action, application failure timestamp or actual host EOF read time. Intersect this independently established clock interval with the existing arm interval; require the intervals to be consistent, and compare the untouched EOF observation against the resulting conservative upper bound. This is additional evidence, not an arbitrary numerical relaxation.

A small candidate is a host-timestamped ADB read of the device boot-time counter immediately around each relevant phase, preserving the raw counter text, host monotonic send/receive times, device/boot identity, quantization and a justified outward drift allowance. Before implementation, independently verify that the exact counter is the same clock as Android17 `SystemClock.elapsedRealtimeNanos`, and justify precision/rate bounds; `/proc/uptime` must not be assumed equivalent without that check. A nonce-bound instrumentation clock request/reply returning the actual `SystemClock.elapsedRealtimeNanos` counter is a stronger alternative if that equivalence cannot be established. Missing/stale/foreign-boot/disjoint/inconclusive calibration must fail. Multiple clock samples are observation calibration, not retries of application assertions. This proposal needs scoped design review before code; no new calibration can retrospectively repair these original artifacts.

**Controlled EOF-observation-window alternative: technically possible, but its claim must be explicit.** A fixture could, after an arm receipt and while retaining the uniquely admitted stream, record a bounded interval in which it intentionally performs no socket I/O, then perform an actual EOF/read operation and record its real monotonic read time and raw result. It must preserve every earlier read/error with its original timestamp, reject any earlier terminal event, retain before-action liveness and actual failure-after-action, and never catch an early EOF then sleep and timestamp it later. Delay-before-action alone does not help: both `action−send` and the EOF shift equally.

However, deferring reads deliberately establishes only that **EOF was observed** after the action bound; it does not establish when FIN/EOF arrived at the kernel. A pre-action closure whose notification is still in flight could be hidden by an observation pause. The same concern applies to delaying event logging after a real read, which is unacceptable. Therefore a pause alone is not recommended as a drop-in fix for an assertion meant to establish actual closure causality. It would require explicit claim/precondition adjudication and adversarial pre-action-close coverage, or an independent original arrival observation. Keeping the raw first observation, actual timestamps and the strict rejection of pre-action/inconclusive cases is essential. The independent clock-interval proposal avoids deliberately delaying or relabelling EOF observations and is the preferred correction to evaluate.

## Other original invariants inspected (diagnostic observations only)

* Four unique nonces and route identities; original source SHA consistent; route owner HIDDEN; captured Network owner UID equals each test UID; `tun0` and positive Network handles; process-binding-null and cleanup flags present. Tor Unix parent0700, socket type49152, UID matches, bootstrap100; both Tor sink deltas are v4=2/v6=1/UDP=3.
* Original VPN event file has15 consecutive sequenced events; onion has16. Canonical synthetic body hashes/lengths match the Android operation metadata and all corresponding host receipts. Each submitted phase has one POST; no retired-phase receipt or DNS query was observed in these files. VPN accepted phases have A+AAAA source10.91.0.2. Onion backend source is127.0.0.1. All events fall inside their corresponding host windows, with monotonic sequence order. VPN-loss ends at action-arm with no remote-close event, matching its explicit unavailable-after-path-loss contract.
* Independent TLS readiness precedes the first route window by0.081202507seconds. Its exact readiness bytes hash matches. Both leaf signatures verify against their saved CA, whose public key matches the debug anchor. VPN SANs are exactly `fixture.invalid`/`*.fixture.invalid`; onion SAN is the exact generated56-character hostname. No hostname/trust bypass was used in this diagnostic.
* Host windows do not overlap. Controlled VPN cleanup is after loss window end1793489915.7447171, from1793489915.8046715 through1793489918.0593634, before Tor start1793489918.078892. Saved server stop/socket-destroy/socket-check statuses are0 and remaining sockets0.
* Capture metadata records verified stop/ownership. Original tcpdump identity includes its Linux start/boot identity, UID107 and exact original runner output path. `capture.log` reports1703 captured packets and zero kernel drops. Its2563 “received by filter” count is a separate kernel/filter counter, not itself proof of a dropped capture. Saved WireGuard handshake epoch1793489908 lies within capture start/end.

## Raw pcap inspection and technical limits

Original raw pcap:297832bytes, SHA256 `dc450e712aed1c7f6c06b1ed854ae7ab92195a7fe8e994f32f6fd022c05f2c84`. Read-only binary-header inspection found1703 complete pcap records, exact EOF boundary, Linux cooked v2 linktype276 and snaplen256. Packet timestamps range1793489219.054923–1793490029.058854, inside capture metadata1793489193.7975285–1793490052.760264.367 packet payloads are snaplen-truncated; that is expected from the configured256-byte capture and limits payload inspection.

A narrow read-only SLL2/IP/UDP/DNS header inspection (not a replacement validator or emitted packet rendering) found all363 unicast synthetic-origin TCP packets on interface index5, between10.91.0.2 and203.0.113.10. All ten fresh phase A/AAAA questions likewise appear on index5 from10.91.0.2. No retired-phase DNS question, exact onion DNS question or2001:db8::10 packet was observed in the decoded captured headers. There are no synthetic-origin/DNS packets in the Tor-revoke window. The one203.0.113.10 occurrence in Tor-death is a loopback-index1, TTL1, UDP5353 multicast query to224.0.0.251 with exactly the three bounded adb ANY/ANY questions and DNS length99/IP length127. The other sixteen loopback origin-address records are the same bounded adb query families or IGMP reports. These wire details are consistent with the existing narrow exemption, not a reason to broaden it.

**Local tcpdump is unavailable.** The actual `tcpdump -tttt -nn -vv -X` rendering and exact text classifier were not executed. Interface indices cannot be independently resolved to the original runner's interface names here; index5's WireGuard attribution is consistent with source/peer records but the required actual-runner name check remains unexecuted. The archive also intentionally binds the original `/home/runner/work/.../traffic.pcap` path, so a relocated local extraction is not an interchangeable full-validator environment. No synthesized `packets.txt`, alternate pcap, altered XML or proof file was written. The original packet/text validation still must execute on the intended runner after the established blockers are corrected and all original case outcomes succeed.

Only this diagnostic Markdown file was added. No product/test/validator/fixture/workflow source, prior review, Git state, CI run, signing input or original artifact was modified. The three established source/observation blockers need serialized corrections and fresh scoped review; existing153-case evidence remains failed/incomplete as a whole.
