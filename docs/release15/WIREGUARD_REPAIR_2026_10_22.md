# WireGuard readiness and Gallery correction

Baseline remote81b139a9ce558877e145318d4eb1265408905316 / CI597.
Owner approved repair after read-only diagnosis. No live device/profile available:
this repairs confirmed app defects, not a claim of successful IPVanish connectivity.

- UP no longer opens browser gate. Official pinned WireGuard1.0.20230706 peer
  statistics must show a nonfuture handshake no older than180 seconds. A45-second
  startup/recovery deadline produces FAILED, retaining owned tunnel protection.
- Transient25-second keepalive triggers peer exchange. Stored profiles/keys are
  unchanged. No external HTTP/DNS readiness probe, process binding or socket bypass.
- Accept one full IPv4 peer, explicit VPN DNS and included app. Any IPv6 use needs
  full IPv6 routing/address; otherwise pinned GoBackend blocks the omitted family.
  Unsupported profiles fail with safe actionable messages before establishment.
- Scheduled stats checks, per-attempt generation fencing and engine callback fencing
  prevent late success after cancellation. Pending/failed live tunnels stay owned;
  task-removal service tracks ownership, not readiness. Failed teardown retains
  ownership until DOWN is confirmed. Lifecycle grace/disconnect rules are preserved.
- Handshake verification means authenticated peer transport, NOT proven DNS,
  internet connectivity or leak-test acceptance. Existing WireGuard engine handles
  peer rehandshake; monitoring closes gate on stale evidence and allows verified
  recovery. No new unbounded reconnect loop or automatic profile replacement.
- Browser keeps persistent main-frame DNS/connection/HTTP/TLS error panels and a
 45-second no-commit timeout, with Retry. TLS cancellation remains mandatory.
- Hidden Add > device Gallery opens the full existing device Gallery. In Hidden
  context Gallery exposes Primary Vault through the fresh-PIN copy-only view.
  Device import says Copy to Hidden; move is disabled. Existing Android picker
  remains available in device Gallery. Primary and Hidden credentials/data stay separate.

Local5 readiness assertions (red then green),373 Hidden foundation assertions,
3 browser script tests and security/source checks passed. Local Gradle cannot run:
wrapper distribution unavailable/network unreachable. Android/JVM/lint pending CI.
Independent review fixed revoked-Hidden click handling, pending/failed VPN task
cleanup ownership and teardown ownership retention, and stale destroyed-renderer callback fencing. Device connectivity and leak
checks remain required. No production/main/V2 changes or owner-media operations.
