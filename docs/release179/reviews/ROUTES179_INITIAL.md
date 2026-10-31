# Task7a initial independent review

2026-10-31. **Spec verdict: changes required. Security/quality verdict: changes required.** Three Important findings; no Critical finding identified. These are source-review conclusions, not Android, Tor, VPN, raw-pcap, signing, upgrade or release approval.

Reviewed the task brief/recon, CONTRACTS, handback report, supplied owned-routes diff, actual Android test/factory/coordinator/owned DNS, fixtures, runner, archive/cleanup, validators and focused host tests. Also read the complete root-provided `evidence179_fixture_http.py`, `create_hidden_vpn_fixture_tls1762.py` and their tests from the stated 849022ce baseline dependency. No product edits, Git, CI, signing, subagents or passing-suite reruns.

## Important findings

### R1 — VPN-loss peer-close requirement has an unestablished delivery precondition

`EvidenceOwnedRoute179Test.kt:150` disconnects the actual WireGuard engine; `VpnModels.kt:93` immediately changes state to DISCONNECTING, while `OfficialWireGuardBackend.disconnect` asynchronously takes the tunnel DOWN. Route cancellation therefore races tunnel destruction. A locally closed/cancelled pinned socket cannot guarantee that FIN/RST/TLS closure reaches the host after its only path disappears. Nevertheless `wait_evidence179_peer_close.py:16` and `verify_evidence179_routes.py:97` require a host `slow-closed` within 60 seconds for `vpn_loss`.

The server writes only 1 MiB over about 256 seconds (`evidence179_fixture_endpoints.py:102`); buffering/retransmission can keep its writes successful well after loss. A correct fail-closed implementation can thus fail this gate without a routing defect. Conversely, a successful close in one run proves the teardown race delivered closure in that run, not guaranteed upstream-close semantics under actual path loss.

**Required resolution:** explicitly reconcile the proof precondition/claim before runtime. Retain actual admitted POST, actual pinned Network disappearance, bounded active failure/no publication, old-route rejection, independent credential retention and zero fallback DNS/origin traffic. Retain independent upstream-close proof for cancellation and authority revocation with a usable path. If the loss case must additionally prove remote closure, establish and record a delivery-capable loss setup; do not substitute cancel-before-disconnect for cancellation caused by loss. A genuinely severed path requires honest unavailable/unknown remote-close classification and explicit contract treatment, never timeout/completion relabelled as closure or an implicit weaker PASS. Keep late VPN retransmissions outside Tor negative-observation windows when repairing orchestration.

**Needed test:** controlled blackhole/tunnel-down case with a genuinely admitted TLS stream; show local cancellation and no fallback even when the host cannot receive closure. Separately exercise reachable-path cancellation/revocation and require genuine peer-close. Neither existing loopback cancellation nor synthetic receipt dictionaries exercises this precondition.

### R2 — destructive-action receipts do not establish action-caused peer closure

`EvidenceOwnedRoute179Test.kt:146–153` wraps the active request in `runCatching`, observes historical `slow-start`, performs the action and then accepts any failure from `active.await()`. The request can already have failed before the action. The host has start/end case windows, but no action boundary correlated with the closure; `verify_evidence179_routes.py:112–118` orders device timestamps separately and never proves the host close follows the action. Such a run can satisfy the claimed revocation/loss/death proof even though the action cancelled no active request.

Additionally `evidence179_fixture_endpoints.py:109` labels every non-timeout `OSError` as `slow-closed`, and validator line107 accepts generic `SSLError` while ignoring `close_errno`. A generic TLS protocol failure is not evidence of the required peer EOF/reset. The promptness check only has an upper bound, so negative host durations pass.

**Required resolution:** establish request liveness at the destructive action and a causally ordered independent action/close receipt protocol; reject pre-action failures/closures. Classify actual EOF/reset/broken-pipe evidence narrowly, record other TLS/I/O failures separately, and require nonnegative ordered host timing using the already captured monotonic clock. Preserve rejection of local timeout and normal completion.

**New targeted probes run (exit0, demonstrating acceptance defects):** using the existing `RouteEvidenceTest.fixture('vpn_loss')`, `check_events` plus `check_case` accepted (a) the last close epoch changed to start epoch minus0.5 and (b) `close_type='SSLError', close_errno=1`. These were two new adversarial probes, not a suite rerun. Add rejection tests for both and for an independently observed request failure/close before the destructive action; retain real TLS peer-close positive coverage.

### R3 — failed capture ownership check is bypassed during cleanup

`archive_evidence179_routes.py:32–51` correctly refuses to signal a PID that is not the expected tcpdump and leaves the PID file when ownership/flush is unconfirmed. `cleanup_evidence179_routes.sh:8` then invokes `cleanup_hidden_vpn_fixture1762.sh`, whose PID loop sends `sudo kill` to **any numeric** `capture.pid` (and server PID), without executable/path/start identity checks. A stale/reused capture PID rejected by the archive can therefore cause unrelated process termination during the mandatory cleanup. This contradicts the ownership boundary claimed in the handback.

**Required resolution:** make every cleanup path use the same verified ownership identity, preferably including process start identity and the actual output path/arguments. Unknown/reused PIDs must remain diagnostic failures and must not be signalled. Preserve stopped/flushed requirements, frozen archive bytes, and no PASS on capture failure.

**Needed tests:** mocked process inventory for valid tcpdump, unrelated reused PID, missing process, sudo wrapper/child, and unflushed process; assert exact signal targets across archive plus cleanup, no unrelated kill, and immutable repeated archive behavior. The reported host suites contain no such archive/cleanup regression coverage.

## Confirmed source properties and limits

The POST goes through `EvidenceRouteCoordinator.connect` and the returned actual factory route; reflection only reads diagnostics. The VPN factory pins Network sockets and `OwnedVpnDns`, checks app ownership/interface, and disables retries; Tor uses the actual isolation service, packaged process and private Unix path with origin DNS prohibited. No production endpoint override or release trust change was introduced. The synthetic fixture TLS CA remains debug-only; ordinary hostname validation and strict bounded chunked decoding are retained.

An independent source inventory found **146 unique manifest methods in20 classes**, comprising all144 focused179 methods and exactly the two specified legacy methods, with no missing identities. Original XML accounting rejects missing/extra/duplicate/skipped/failed cases. The packet validator consumes a fresh rendering of raw pcap, requires capture stop/flush and zero kernel drops, positive VPN/DNS evidence, positive TUN counters, bounded mDNS exemption and negative Tor windows. Archive allowlists avoid fixture private keys/configuration; the successful archive path is idempotent. Those properties do not resolve R1–R3.

The handback's **46 passing host methods are reported evidence**, not rerun here. All actual Android compilation/instrumentation, native Tor/WireGuard, privileged setup/cleanup and raw-pcap integration remain **UNRUN**. Do not issue routing/release approval from this review, source flags, synthetic receipts or historical178 PASS.
