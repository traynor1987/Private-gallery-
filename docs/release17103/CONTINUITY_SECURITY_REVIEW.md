Source: 24c29fd86e8493b00365357071d9c45e240eb3c4
Decision: REJECTED
Reviewer: /root/continuity_security_review

Review date: 2026-11-03. Scope: coordinated Hidden continuation, framework-readiness admission, and the exact frozen runtime artifact. This is not a signing authorization.

## Evidence identity and result

- GitHub run: 41688397704; job: 126100834909; artifact: 13257432448.
- Raw archive SHA-256: `11f7a4e51e016b3b20be0c657683194c936ad5b2a30d94c628f90eb74f4ceea8`.
- Archived source record matches the Source above. Independently parsed fresh Android XML: eight cases passed, zero failed or skipped. All eight required receipts and all twelve distinct Main phase/action scenarios pass receipt validation.
- The original eighteen cases are compatible evidence from source `535186021598bf64fd56f2132ab4cb763332bf17`, run 41682334113, artifact 13253433530, archive SHA-256 `87f9a4a7b829346a0ad93a89c1996016868f9d193d1b8828b4673074a09094b6`. Their raw baseline checks were independently revalidated; they are not eighteen new executions at Source.
- The mandatory full packet gate fails with `Fixture DNS appeared outside WireGuard interface`. Independent inspection also finds direct non-WireGuard STUN, so this is not merely a DNS text-classification issue. No complete runtime-route PASS exists.
- Frozen capture: `hidden-vpn1762/continuity-final/traffic.pcap`, SHA-256 `5b1ebb4d21de3a6e32ba0722bedd60fbc923dddd3a936c42ecf8c526cc64f982`.
- Packet rendering SHA-256: `31b5cf85c70dd424ca0c311d062fa164da254b2a6f54e8e29bbc31641c283c19`; event log SHA-256: `adfcc6f42eab6817ffb1ef48b59764268b6f9f3cb573ce74f6a5ac8a7f2555c5`.

## C1 — Critical: retained child emits traffic outside WireGuard during route loss

Relevant frozen source:

- `app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenVpnBrowserActivity.kt:889`: suspension covers/pauses the existing views and blocks WebView loads, then asks the parent to suspend while retaining the process.
- `app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenVpnBrowserBroker.kt:195`: ordinary IO is disabled and closed; the child is retained when the transport adapter reports denial.
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:814`: the denial adapter disconnects WireGuard and treats engine release plus disappearance of the old Network capabilities as sufficient denial, with a 2.5-second bound.
- `app/src/debug/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenContinuityFixtureApplication.kt:13`: the actual child emits a distinctive native UDP probe without an application authority pre-check, specifically to test the OS process pin.

The full capture contains six direct UDP STUN attempts on host loopback, outside `pg1762`, and six corresponding ICMP rejections. Independent parsing of the raw PCAP (Linux SLL2/link type 276) confirms interface index 1 and the packet bytes; this conclusion does not depend on trusting the packet rendering. Every direct STUN packet has source `203.0.113.10` and destination `203.0.113.10:3478`, rather than the expected WireGuard peer source `10.91.0.2`.

| Capture time (UTC, 2026-11-03) | Source port | STUN transaction payload |
| --- | --- | --- |
| 18:02:13.069478 | 40918 | `VvIcRfLTV9Xm` |
| 18:02:43.029467 | 48678 | `w34tUtX50uDB` |
| 18:02:43.220515 | 48678 | `w34tUtX50uDB` |
| 18:04:25.303583 | 46071 | `nv17103probe` |
| 18:05:39.451129 | 43616 | `nv17103probe` |
| 18:06:03.401281 | 41195 | `nv17103probe` |

The last three datagrams have exact UDP payload `000100002112a4426e76313731303370726f6265`. That marker comes from the real child probe, not host adb discovery. The other three are WebRTC-shaped STUN transactions during the two-site and denied-Panic fixtures. These records do not match the existing narrowly defined host adb multicast exclusion.

There are also two non-WireGuard DNS records at 18:02:13.089800 and 18:02:13.089923: `127.0.0.1:33970` queries `127.0.0.53:53` for AAAA `continuous-phase.fixture.invalid`, followed by NXDOMAIN. The hostname is synthetic, but the off-tunnel DNS path violates the same routing boundary.

The first STUN/DNS pair occurs in the two-site phase-B loss: child stop at 18:02:12.305, WireGuard DOWN at 12.319, and child resume at 14.807 in Android logs. The next two STUN attempts occur in denied-Panic before the child is killed. The three marked native probes map to verification/replacement, bound-before-commit/lock, and bound-before-commit/replacement. According to their recorded receipt windows, they arrive respectively about 231ms, 118ms, and 88ms before `denied_confirmed`. They therefore expose a transition routing failure even where the separate confirmed-denial interval assertion would not detect it. No timing grace or exclusion makes a non-WireGuard route acceptable.

The fixture host firewall rejected direct origin attempts. That firewall is test containment, not the application's production privacy boundary. The observed packets contain synthetic data; this report does not claim real credentials were exposed. Their route nevertheless demonstrates that the current retention design cannot establish the required no-fallback property for native child traffic. Application IO tokens, proxy closure, paused views, and a remembered process binding do not prove an OS-wide deny fence during VPN teardown.

Reproduction is present in the exact artifact above: retain the live child across owned-VPN suspension while native WebRTC/DNS or the marked native probe continues attempting IO, then inspect the entire capture rather than only successful fixture-server arrivals. A fresh run is not needed to establish this failure. Its precise platform/kernel mechanism is not fully attributed by the artifact and is not asserted here.

## Fix assessment and approval boundary

No safe bounded correction using only the existing verified admission guards is established. The framework-readiness gate addresses selection of a new connecting Network; it does not fence native traffic while the previous VPN is removed. More authority checks, status polling, WebView pause/block settings, or repeated bound-Network comparisons do not replace enforcement for native sockets that bypass those checks. Changing only the fixture, suppressing its native probe, trimming the capture, or allowing these packet shapes would conceal the failure.

Continuity requires a demonstrable OS-level deny mechanism covering the entire route transition while the process survives, followed by the existing exact-owner/authentication/grace and freshly verified route admission. That is an architectural enforcement requirement, not a verified small patch identified by this review. The existing safer fallback is hard retirement with confirmed child death before removing its protective route; it would forgo the requested continuation behavior and requires an explicit scope decision and validation. This report proposes no process-freezing, native-hook, or device-policy workaround and claims no fix has been implemented.

The implementation otherwise retains the reviewed epoch fences, exact presentation ownership, authenticated hard controls, Primary/Hidden separation, and VPN-only continuation scope. Those source properties and eight passing Android cases do not override C1. Tor remains on its original hard-retirement path. Existing Chromium app-private disk-profile persistence remains the disclosed Beta limitation; no memory-only profile or new encryption guarantee is claimed. Fresh bind and final Binder/UI commit remain separate operations, but the observed escape is not justified by the permitted freshly verified live-bind interval.

**Source/spec verdict:** rejected for release because the required continuous no-fallback routing invariant is disproven by raw traffic. **Evidence verdict:** Android/receipt assertions pass; full routing evidence fails. **Release verdict:** no security approval, no signing activation, and no code55 production delivery approval. Original-key 54-to-55 upgrade/signing verification remains unapproved. Approval markers and APPROVAL artifacts must remain absent. Only this rejected review report is created by the reviewer.
