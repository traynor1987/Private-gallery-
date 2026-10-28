# Native Tor proxy runtime investigation — 28 October 2026

Source under test: `ff4d323ffb5948c79c8056070a92cdfde5103df3`; focused run `40745377742`. Evidence: artifact `12842407311`, ZIP SHA-256 `06033b433a41db80c460b8d761f2cf18c98d5e0e21374aaa3eb140ee890f935a`.

## Observed failures

- `HiddenRuntimeFailureTest.nativeUnixConnectTimeoutClosesPeerWithoutFallback` and `revokedNativeAuthorityClosesStalledUnixPeerAndDeniesReuse` fail at the five-second assertion before the synthetic Unix SOCKS peer receives a DOMAIN request.
- `NativeTorIsolation1762Test.packagedTorNativeFormsUploadsDownloadsAndNetworkSink` gets past packaged-Tor bootstrap/Unix readiness, then fails the broker's HTTPS CONNECT readiness request with an unexpected EOF.
- The proxy intentionally closes a request on transport exceptions, so the latter symptom does not establish a remote Tor/HTTPS defect.

## Confirmed common cause

`HiddenTorSockets.open` constructed a fresh `android.net.LocalSocket`, then called `setSoTimeout(30_000)` before `connect`. Android creates this socket's native file descriptor lazily in `connect` or `bind`. `setSoTimeout` directly invokes `LocalSocketImpl.setOption`, which throws `IOException("socket not created")` when the descriptor is null. Thus execution could never reach the SOCKS greeting or DOMAIN request. `HiddenVpnProxy` caught the exception and closed the client, explaining both deterministic controls and the CONNECT EOF.

Independently inspected primary AOSP implementation:

- https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/net/LocalSocket.java (`implCreateIfNeeded`, `connect`, `setSoTimeout`, `close`).
- https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/net/LocalSocketImpl.java (`setOption`'s null-descriptor guard).

## Contained correction and regression

Moved timeout configuration immediately after the Unix `connect`, before greeting/read/write operations. The socket remains registered with the owner before connection, preserving cancellation; timeout remains 30 seconds. No origin DNS, TCP fallback, Tor binary, routing, authentication or proxy-credential behavior changed.

Added `HiddenRuntimeFailureTest.freshUnixSocketNegotiatesDomainAndExchangesTunnelBytes`, which uses an actual app-private filesystem Unix socket, validates the unresolved `example.com:443` SOCKS DOMAIN bytes, and exchanges exact bidirectional synthetic payloads. A fresh socket now exposes any setup exception directly rather than only a peer-latch timeout. The existing natural-timeout and revocation controls remain mandatory and unchanged in their assertions.

`git diff --check` passes. Android SDK/emulator is unavailable locally, so the new test and corrected existing controls have **not** passed Android execution yet. A new exact-source focused run is required. The live WireGuard failures are a separate investigation and are not claimed fixed by this correction. No release/signing approval.
