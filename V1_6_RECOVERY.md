# Private Gallery 1.6 recovery

Status: IN DEVELOPMENT — NO-GO for production. Owner authorised autonomous implementation, continuous GitHub checkpoints, CI and permanent-key candidate signing; no merge or owner-data operations.

## Last independently verified remote checkpoint

Branch: `release/1.6-hidden-tor`
SHA: `f337d06f2fa89ca33d64fd14e22a7a870d96d706` (first implementation checkpoint verified through GitHub API).
This document records the preceding verified checkpoint; the commit containing an update is identified by Git history and independently verified after publishing, avoiding a self-referential SHA.

## Baseline

V1.5 remote and clean preserved checkout match `34a107b88072c54f39ac5c5b497f1c1df36bb3f0`. APK code source is `a46288059fd7dc8d094798dd67d4ad87af5ee6a8`. Package `uk.co.traynor.privategallery`, versionCode 29. Permanent certificate SHA256 `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`. Evidence: `docs/release15/HIDDEN_RESTORE_CANDIDATE.md`.
Separate checkout used. Main, V1.5 and V2 recovery branches are untouched.

## Completed

- Inspected active processes and source; no uncommitted work in preserved V1.5 checkout.
- Created V1.6 remote branch at exact verified V1.5 baseline, then independently fetched ref.
- Inspected existing shared browser, Hidden session, PIN auth, diagnostics and permanent-signing CI.

- Implemented seven-tap diagnostics discovery and transient fresh owner-auth gate; removed ordinary Debug card (Android UI validation pending CI).
- Added generation-fenced three-mode policy with VPN+Tor unavailable; 41 host checks pass.
- Existing Hidden foundation: 373 checks and isolation/revocation/20-100-500MiB tests pass.

## Partial / remaining

- Implement discreet diagnostics with transient seven-tap discovery and fresh owner authentication; preserve existing Hidden entry sequence.
- Implement separate Hidden browser and explicit three-mode capability/route state, never fallback.
- Select and assess maintained Tor dependency, Android storage isolation, network enforcement and encrypted downloads.
- Test lifecycle, mode changes, auth, isolation and regression; independent security review.
- Enable V1.6 CI and increased version; signed candidate only after mandatory tests.
- Physical routing/packet capture and Fold acceptance remain owner-device gates.

## Tests / blockers

- Local Gradle cannot download Gradle 8.10.2: `java.net.SocketException: Network is unreachable`; Android SDK/dependency cache absent. Use authorised existing GitHub Actions for Android compilation/instrumentation.
- Existing WireGuard handshake polling does not itself prevent a route-loss race. VPN+Tor must remain unavailable until a socket-level enforced path is demonstrated.
- WebView proxy configuration alone is not proof of WebRTC/UDP/DNS containment.
- No new APK built or signed. No security guarantees or completed features claimed.

## Exact next step

Implement and test transient diagnostics authentication access; checkpoint it with the mode gate and CI branch configuration. Continue transport implementation according to `docs/release16/IMPLEMENTATION.md`.

## Browser implementation WIP checkpoint

Tor Only adapter, process-scoped Tor shutdown, native SOCKS with no local DNS, VPN Network-bound HTTP adapter, isolated restricted WebView profile, compact tabs/settings UI and encrypted image/video downloads implemented. VPN+Tor explicitly unavailable. Hidden/Primary routing changed; mode persisted without sessions. JavaScript/forms/redirects unsupported by deliberate restriction. Android compilation and new WebView/SOCKS/diagnostics tests pending next CI. No usable/signed candidate claimed. CI617 for earlier diagnostics checkpoint still running at this checkpoint.

Next: run Android CI for this source, fix compilation/runtime findings, independently review source, test fail-closed lifecycle and signing/upgrade gates.
