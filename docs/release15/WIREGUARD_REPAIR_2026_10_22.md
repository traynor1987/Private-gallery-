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

## Final verification — CI600

Exact tested source: `1432b3b89ad96a6e9ac57cdddc4b58f5ca14e80b`.
Run: https://github.com/traynor1987/Private-gallery-/actions/runs/39694625137
Artifact: https://github.com/traynor1987/Private-gallery-/actions/runs/39694625137/artifacts/12391103300

CI600 passed JVM tests, lint, app/instrumentation assembly, security/source checks,
TEST ONLY identity/signing gates and all192 Android tests (0failed,0errors,0skipped).
Downloaded Android XML confirms count. Includes profile policy, handshake gating,
per-tab errors, real45-second timeout, stale renderer fencing, full Gallery routing,
Primary copy and existing Hidden/navigation/screenshot regressions.

Earlier runs did fail: CI598 missing Compose DisposableEffect qualification; CI599
Android test fixture attempted package-private WebResourceError construction.
Both corrected. Stale-renderer test now uses constructible HTTP503 callback with
same replacement-protection assertions. No test disabled or acceptance gate relaxed.

Archive SHA-256 `3fb155b9be610f1d25b8fa3cc84b1f14527b99a30ae76ade649576537c1286c3`.
APK SHA-256 `ac3eda87415af2c5d92a4daf622ffd20aa51e6025ee68aca800691e9cc18555f`.
Package `uk.co.traynor.privategallery.hiddenprototype`, versionCode28,
versionName `1.0.27-TEST-ONLY`,38173312 bytes. Android Debug certificate SHA-256
`f70d622d2b363b9ae9afce46a47b6a79e191bbcf5070e5709796889243bf9efa`.
CI apksigner and independent APKv2 signature/content-digest checks passed.
Production V1 has a different package and cannot be overwritten by this APK.
Signer differs from CI597: cannot update that test installation in place.
Do not uninstall a test installation containing needed data; use an isolated
compatible test installation/device/profile. No production release authorized.

## Required physical acceptance

1. Keep Require VPN enabled and retain TLS verification. Connect the existing
   imported profile. Note Android's active VPN owner; do not run a second VPN
   concurrently as a purported identical-tunnel comparison.
2. Inspect Settings > VPN: handshake age and RX/TX diagnostics. A verified peer
   should permit navigation; absent/stale handshake must leave Browser blocked.
   Do not send keys/full profile. A valid profile/server still needs device testing.
3. Open https://ipinfo.io then a second HTTPS site. Record page/error code and
   VPN diagnostics. A peer handshake alone does not prove DNS/internet forwarding.
4. Check Wi-Fi/mobile transition, background/foreground, screen lock, permission
   revocation and other-VPN takeover. Confirm browser blocks on lost authorization,
   reconnect/retry recovers where available, and verify DNS/IPv4/IPv6 leak behaviour.
5. In Hidden, Add media > From device Gallery must show the existing full Gallery.
   Gallery > Primary Vault must request fresh Primary PIN before showing content.
   Copy synthetic media and verify both copies, screenshots, tabs and Fold layouts.

Independent security review found no remaining blocker after lifecycle/ownership
and stale-renderer corrections. Technical gates passed; actual owner connectivity,
privacy/leak testing and physical acceptance remain pending. No claim that the
owner's original white-page network root cause is conclusively reproduced/fixed.
